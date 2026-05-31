#!/usr/bin/env bash
# kill.sh — Stop backend (port 8080)
# Platforms: macOS, Ubuntu/Debian Linux
# Usage: ./kill.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
PID_FILE="$LOG_DIR/backend.pid"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }

# ── port → PIDs helper — lsof → ss → fuser → netstat fallback ────────────────
get_port_pids() {
  local port="$1"
  if command -v lsof &>/dev/null; then
    lsof -ti:"$port" 2>/dev/null || true
  elif command -v ss &>/dev/null; then
    ss -tlnp 2>/dev/null | awk -v p="$port" '
      $0 ~ ":"p" " || $0 ~ ":"p"$" {
        match($0, /pid=([0-9]+)/, a); if (a[1]) print a[1]
      }'
  elif command -v fuser &>/dev/null; then
    fuser "${port}/tcp" 2>/dev/null | tr ' ' '\n' | grep -E '^[0-9]+$' || true
  else
    netstat -tlnp 2>/dev/null | awk -v p="$port" '
      $0 ~ ":"p" " { split($NF, a, "/"); if (a[1]+0>0) print a[1] }'
  fi
}

echo ""
info "Stopping Banking Fixed-Length File Generator backend..."
echo ""

killed=0

# ── 1. PID-file kill ─────────────────────────────────────────────────────────
if [ -f "$PID_FILE" ]; then
  pid=$(cat "$PID_FILE" | tr -d '[:space:]')
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null
    sleep 1
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null || true
    success "Stopped backend process (PID $pid)"
    killed=$((killed + 1))
  else
    warn "PID ${pid:-?} not running"
  fi
  rm -f "$PID_FILE"
fi

# ── 2. Pattern-based kill (safety net) ───────────────────────────────────────
if pkill -f 'FixedLengthConvertersApplication' 2>/dev/null; then
  success "Stopped FixedLengthConvertersApplication"
  killed=$((killed + 1))
fi
if pkill -f 'spring-boot:run' 2>/dev/null; then
  success "Stopped Maven spring-boot:run"
  killed=$((killed + 1))
fi

# ── 3. Port-based kill (final safety net) ────────────────────────────────────
pids=$(get_port_pids 8080)
if [ -n "$pids" ]; then
  echo "$pids" | while IFS= read -r pid; do
    [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null && \
      success "Freed port 8080 (killed PID $pid)"
  done
  killed=$((killed + 1))
fi

echo ""
if [ "$killed" -gt 0 ]; then
  success "Done — $killed process group(s) stopped."
else
  warn "No matching processes found — nothing to kill."
fi
echo ""
