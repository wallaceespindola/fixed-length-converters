#!/usr/bin/env bash
# run-all.sh — Build and start backend (port 8080) + frontend dev server (port 3000)
# Platforms: macOS, Ubuntu/Debian Linux
# Usage: ./run-all.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/src/main/frontend"
LOG_DIR="$SCRIPT_DIR/logs"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"
BUILD_LOG="$LOG_DIR/build.log"
BACKEND_PORT=8080
FRONTEND_PORT=3000
HEALTH_URL="http://localhost:${BACKEND_PORT}/actuator/health"
WAIT_SECS=90

# ── colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; }

# ── OS detection ──────────────────────────────────────────────────────────────
OS="unknown"
case "$(uname -s)" in
  Darwin) OS="macos" ;;
  Linux)  OS="linux" ;;
esac

# ── port helpers — lsof (macOS + most Linux) → ss (Ubuntu) → netstat fallback ─
port_in_use() {
  local port="$1"
  if command -v lsof &>/dev/null; then
    lsof -ti:"$port" >/dev/null 2>&1
  elif command -v ss &>/dev/null; then
    ss -tlnp 2>/dev/null | grep -q ":${port} " || ss -tlnp 2>/dev/null | grep -q ":${port}$"
  else
    netstat -tlnp 2>/dev/null | grep -q ":${port} "
  fi
}

get_port_pids() {
  local port="$1"
  if command -v lsof &>/dev/null; then
    lsof -ti:"$port" 2>/dev/null || true
  elif command -v ss &>/dev/null; then
    ss -tlnp 2>/dev/null | awk -v p="$port" '
      $0 ~ ":"p" " || $0 ~ ":"p"$" {
        match($0, /pid=([0-9]+)/, a); if (a[1]) print a[1]
      }'
  else
    netstat -tlnp 2>/dev/null | awk -v p="$port" '
      $0 ~ ":"p" " { split($NF, a, "/"); if (a[1]+0>0) print a[1] }'
  fi
}

# ── banner ────────────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Banking Fixed-Length File Generator — Full Stack Launcher   ║"
echo "║  Platform: $OS                                               ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# ── pre-flight checks ─────────────────────────────────────────────────────────
for cmd in java mvn npm curl; do
  if ! command -v "$cmd" &>/dev/null; then
    error "Required command not found: $cmd"
    exit 1
  fi
done

if port_in_use "$BACKEND_PORT"; then
  warn "Port ${BACKEND_PORT} already in use — run ./kill-all.sh first."
fi
if port_in_use "$FRONTEND_PORT"; then
  warn "Port ${FRONTEND_PORT} already in use — Vite may auto-pick another port."
fi

mkdir -p "$LOG_DIR"

# ── 1. Maven build ────────────────────────────────────────────────────────────
info "Building project (Maven full build including frontend assets)..."
cd "$SCRIPT_DIR"
mvn clean package -DskipTests 2>&1 | tee "$BUILD_LOG" | grep -E "(BUILD|ERROR|\[ERROR\])" || true

if ! grep -q "BUILD SUCCESS" "$BUILD_LOG"; then
  error "Maven build failed — see $BUILD_LOG"
  exit 1
fi
success "Build complete"

# ── 2. Start backend ──────────────────────────────────────────────────────────
info "Starting Spring Boot backend (dev profile, port ${BACKEND_PORT})..."
cd "$SCRIPT_DIR"
nohup mvn spring-boot:run -Pskip-frontend -Dspring-boot.run.profiles=dev \
  >"$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$LOG_DIR/backend.pid"
info "Backend PID: $BACKEND_PID  (log: $BACKEND_LOG)"

# ── wait for backend health ───────────────────────────────────────────────────
info "Waiting for backend on port ${BACKEND_PORT} (up to ${WAIT_SECS}s)..."
for i in $(seq 1 "$WAIT_SECS"); do
  if curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
    success "Backend is up (${i}s)"
    break
  fi
  if [ "$i" -eq "$WAIT_SECS" ]; then
    error "Backend did not start within ${WAIT_SECS}s — check $BACKEND_LOG"
    exit 1
  fi
  sleep 1
done

# ── 3. Install frontend deps if missing ───────────────────────────────────────
cd "$FRONTEND_DIR"
if [ ! -d node_modules ]; then
  info "Installing frontend dependencies (npm install)..."
  npm install 2>&1 | tee "$LOG_DIR/npm-install.log" | tail -3
  success "npm install complete"
fi

# ── 4. Start Vite frontend dev server ─────────────────────────────────────────
info "Starting Vite frontend dev server (port ${FRONTEND_PORT})..."
nohup npm run dev >"$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!
echo "$FRONTEND_PID" > "$LOG_DIR/frontend.pid"
info "Frontend PID: $FRONTEND_PID  (log: $FRONTEND_LOG)"

sleep 3
if ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
  error "Frontend failed to start — check $FRONTEND_LOG"
  exit 1
fi
success "Frontend dev server started"

# ── summary ───────────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  All services running                                        ║"
echo "║  Backend  →  http://localhost:8080                           ║"
echo "║  Frontend →  http://localhost:3000                           ║"
echo "║  Swagger  →  http://localhost:8080/swagger-ui.html           ║"
echo "║  Health   →  http://localhost:8080/actuator/health           ║"
echo "║                                                              ║"
echo "║  Stop all:  ./kill-all.sh                                    ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
