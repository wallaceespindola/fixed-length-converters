# run-all.ps1 — Build and start backend (port 8080) + frontend dev server (port 3000)
# Platforms: macOS, Ubuntu/Debian Linux, Windows 11+  (requires PowerShell Core 7+ on Mac/Linux)
# Usage: .\run-all.ps1   or   pwsh ./run-all.ps1

param(
    [int]$BackendPort  = 8080,
    [int]$FrontendPort = 3000,
    [int]$WaitSecs     = 90,
    [string]$Profile   = "dev"
)

$ErrorActionPreference = "Stop"

# ── PS 5.x / Windows PowerShell compat shim ($IsWindows undefined there) ─────
if ($null -eq (Get-Variable 'IsWindows' -ErrorAction SilentlyContinue)) {
    $IsWindows = $true; $IsMacOS = $false; $IsLinux = $false
}

$ScriptDir   = $PSScriptRoot
$FrontendDir = Join-Path $ScriptDir (Join-Path "src" (Join-Path "main" "frontend"))
$LogDir      = Join-Path $ScriptDir "logs"
$BackendLog  = Join-Path $LogDir "backend.log"
$BackendErr  = Join-Path $LogDir "backend-err.log"
$FrontendLog = Join-Path $LogDir "frontend.log"
$FrontendErr = Join-Path $LogDir "frontend-err.log"
$BuildLog    = Join-Path $LogDir "build.log"
$BPidFile    = Join-Path $LogDir "backend.pid"
$FPidFile    = Join-Path $LogDir "frontend.pid"
$HealthUrl   = "http://localhost:$BackendPort/actuator/health"

# ── helpers ───────────────────────────────────────────────────────────────────
function Write-Info { param($m) Write-Host "[INFO]  $m" -ForegroundColor Cyan }
function Write-Ok   { param($m) Write-Host "[OK]    $m" -ForegroundColor Green }
function Write-Warn { param($m) Write-Host "[WARN]  $m" -ForegroundColor Yellow }
function Write-Err  { param($m) Write-Host "[ERROR] $m" -ForegroundColor Red }

function Test-PortInUse([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try { $client.Connect("localhost", $Port); $client.Close(); return $true }
    catch { return $false }
}

# Launch a background process in a platform-safe way.
# On Mac/Linux, mvn/npm are shell scripts — must be invoked via bash.
# On Windows, Start-Process can invoke them directly.
function Start-BackgroundProcess {
    param(
        [string]$Command,      # full command string (with args)
        [string]$WorkDir,
        [string]$OutLog,
        [string]$ErrLog
    )
    if ($IsWindows) {
        return Start-Process "cmd" `
            -ArgumentList "/c", $Command `
            -WorkingDirectory $WorkDir `
            -RedirectStandardOutput $OutLog `
            -RedirectStandardError  $ErrLog `
            -WindowStyle Hidden -PassThru
    } else {
        # bash -c inherits PATH (nvm, sdkman, etc.)
        # Redirect inside the shell so the PID we get is the actual child process.
        return Start-Process "bash" `
            -ArgumentList "-c", "$Command >'$OutLog' 2>'$ErrLog'" `
            -WorkingDirectory $WorkDir `
            -NoNewWindow -PassThru
    }
}

function Wait-ForBackend {
    Write-Info "Waiting for backend on port $BackendPort (up to ${WaitSecs}s)..."
    for ($i = 1; $i -le $WaitSecs; $i++) {
        try {
            $r = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($r.StatusCode -eq 200) { Write-Ok "Backend is up (${i}s)"; return }
        } catch {}
        Start-Sleep -Seconds 1
    }
    Write-Err "Backend did not start within ${WaitSecs}s — check $BackendLog"
    exit 1
}

# ── banner ────────────────────────────────────────────────────────────────────
$platform = if ($IsWindows) { "Windows" } elseif ($IsMacOS) { "macOS" } else { "Linux" }
Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════════╗" -ForegroundColor Magenta
Write-Host "║  Banking Fixed-Length File Generator — Full Stack Launcher   ║" -ForegroundColor Magenta
Write-Host "║  Platform: $($platform.PadRight(50))║" -ForegroundColor Magenta
Write-Host "╚══════════════════════════════════════════════════════════════╝" -ForegroundColor Magenta
Write-Host ""

# ── pre-flight ────────────────────────────────────────────────────────────────
foreach ($cmd in @("java", "mvn", "npm")) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        Write-Err "Required command not found: $cmd"
        exit 1
    }
}

if (Test-PortInUse $BackendPort)  { Write-Warn "Port $BackendPort already in use — run kill-all.ps1 first." }
if (Test-PortInUse $FrontendPort) { Write-Warn "Port $FrontendPort already in use — Vite may auto-pick another." }

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# ── 1. Maven build ────────────────────────────────────────────────────────────
Write-Info "Building project (Maven full build including frontend assets)..."
Set-Location $ScriptDir
# Capture output while still streaming to console
$buildLines = & mvn clean package -DskipTests 2>&1
$buildLines | Out-File -FilePath $BuildLog -Encoding utf8
$buildLines | Where-Object { $_ -match '(BUILD|ERROR|\[ERROR\])' } | ForEach-Object { Write-Host $_ }

if (-not ($buildLines | Where-Object { $_ -match 'BUILD SUCCESS' })) {
    Write-Err "Maven build failed — see $BuildLog"
    exit 1
}
Write-Ok "Build complete"

# ── 2. Start backend ──────────────────────────────────────────────────────────
Write-Info "Starting Spring Boot backend (profile: $Profile, port: $BackendPort)..."
$mvnCmd = "mvn spring-boot:run -Pskip-frontend -Dspring-boot.run.profiles=$Profile"
$bProc = Start-BackgroundProcess -Command $mvnCmd -WorkDir $ScriptDir -OutLog $BackendLog -ErrLog $BackendErr
$bProc.Id | Out-File -FilePath $BPidFile -Encoding utf8
Write-Info "Backend PID: $($bProc.Id)  (log: $BackendLog)"

Wait-ForBackend

# ── 3. npm install (if needed) ────────────────────────────────────────────────
Set-Location $FrontendDir
if (-not (Test-Path "node_modules")) {
    Write-Info "Installing frontend dependencies (npm install)..."
    $npmInstall = & npm install 2>&1
    $npmInstall | Out-File -FilePath (Join-Path $LogDir "npm-install.log") -Encoding utf8
    Write-Ok "npm install complete"
}

# ── 4. Start Vite frontend dev server ─────────────────────────────────────────
Write-Info "Starting Vite frontend dev server (port $FrontendPort)..."
$fProc = Start-BackgroundProcess -Command "npm run dev" -WorkDir $FrontendDir -OutLog $FrontendLog -ErrLog $FrontendErr
$fProc.Id | Out-File -FilePath $FPidFile -Encoding utf8
Write-Info "Frontend PID: $($fProc.Id)  (log: $FrontendLog)"

Start-Sleep -Seconds 4
if ($fProc.HasExited) {
    Write-Err "Frontend failed to start — check $FrontendLog"
    exit 1
}
Write-Ok "Frontend dev server started"

# ── summary ───────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║  All services running                                        ║" -ForegroundColor Green
Write-Host "║  Backend  →  http://localhost:8080                           ║" -ForegroundColor Green
Write-Host "║  Frontend →  http://localhost:3000                           ║" -ForegroundColor Green
Write-Host "║  Swagger  →  http://localhost:8080/swagger-ui.html           ║" -ForegroundColor Green
Write-Host "║  Health   →  http://localhost:8080/actuator/health           ║" -ForegroundColor Green
Write-Host "║                                                              ║" -ForegroundColor Green
Write-Host "║  Stop all:  pwsh ./kill-all.ps1                              ║" -ForegroundColor Green
Write-Host "╚══════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
