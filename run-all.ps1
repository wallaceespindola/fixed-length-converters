# run-all.ps1 — Build and start backend (port 8080)
# Platforms: Windows 11+, macOS, Linux  (requires PowerShell Core 7+ on Mac/Linux)
# Usage: .\run-all.ps1   or   pwsh ./run-all.ps1

param(
    [int]$BackendPort = 8080,
    [int]$WaitSecs    = 90,
    [string]$Profile  = "dev"
)

$ErrorActionPreference = "Stop"

if ($null -eq (Get-Variable 'IsWindows' -ErrorAction SilentlyContinue)) {
    $IsWindows = $true; $IsMacOS = $false; $IsLinux = $false
}

$ScriptDir  = $PSScriptRoot
$LogDir     = Join-Path $ScriptDir "logs"
$BackendLog = Join-Path $LogDir "backend.log"
$BackendErr = Join-Path $LogDir "backend-err.log"
$BuildLog   = Join-Path $LogDir "build.log"
$BPidFile   = Join-Path $LogDir "backend.pid"
$HealthUrl  = "http://localhost:$BackendPort/actuator/health"

function Write-Info { param($m) Write-Host "[INFO]  $m" -ForegroundColor Cyan }
function Write-Ok   { param($m) Write-Host "[OK]    $m" -ForegroundColor Green }
function Write-Warn { param($m) Write-Host "[WARN]  $m" -ForegroundColor Yellow }
function Write-Err  { param($m) Write-Host "[ERROR] $m" -ForegroundColor Red }

function Test-PortInUse([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try { $client.Connect("localhost", $Port); $client.Close(); return $true }
    catch { return $false }
}

function Start-BackgroundProcess {
    param([string]$Command, [string]$WorkDir, [string]$OutLog, [string]$ErrLog)
    if ($IsWindows) {
        return Start-Process "cmd" `
            -ArgumentList "/c", $Command `
            -WorkingDirectory $WorkDir `
            -RedirectStandardOutput $OutLog `
            -RedirectStandardError  $ErrLog `
            -WindowStyle Hidden -PassThru
    } else {
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
    Write-Err "Backend did not start within ${WaitSecs}s -- check $BackendLog"
    exit 1
}

$platform = if ($IsWindows) { "Windows" } elseif ($IsMacOS) { "macOS" } else { "Linux" }
Write-Host ""
Write-Host "============================================================" -ForegroundColor Magenta
Write-Host "  Banking Fixed-Length File Generator -- Backend Launcher" -ForegroundColor Magenta
Write-Host "  Platform: $platform" -ForegroundColor Magenta
Write-Host "============================================================" -ForegroundColor Magenta
Write-Host ""

# ---- pre-flight -------------------------------------------------------
foreach ($cmd in @("java", "mvn")) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        Write-Err "Required command not found: $cmd"; exit 1
    }
}
if (Test-PortInUse $BackendPort) { Write-Warn "Port $BackendPort already in use -- run kill-all.ps1 first." }

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# ---- 1. Maven build ---------------------------------------------------
Write-Info "Building project (Maven clean install, no tests)..."
Set-Location $ScriptDir
$buildLines = & mvn clean install -DskipTests 2>&1
$buildLines | Out-File -FilePath $BuildLog -Encoding utf8
$buildLines | Where-Object { $_ -match '(BUILD|ERROR|\[ERROR\])' } | ForEach-Object { Write-Host $_ }

if (-not ($buildLines | Where-Object { $_ -match 'BUILD SUCCESS' })) {
    Write-Err "Maven build failed -- see $BuildLog"; exit 1
}
Write-Ok "Build complete"

# ---- 2. Start backend -------------------------------------------------
Write-Info "Starting Spring Boot backend (profile: $Profile, port: $BackendPort)..."
$mvnCmd = "mvn spring-boot:run -Dspring-boot.run.profiles=$Profile"
$bProc = Start-BackgroundProcess -Command $mvnCmd -WorkDir $ScriptDir -OutLog $BackendLog -ErrLog $BackendErr
$bProc.Id | Out-File -FilePath $BPidFile -Encoding utf8
Write-Info "Backend PID: $($bProc.Id)  (log: $BackendLog)"

Wait-ForBackend

# ---- summary ----------------------------------------------------------
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  Backend running" -ForegroundColor Green
Write-Host "  App     ->  http://localhost:8080" -ForegroundColor Green
Write-Host "  Swagger ->  http://localhost:8080/swagger-ui.html" -ForegroundColor Green
Write-Host "  Health  ->  http://localhost:8080/actuator/health" -ForegroundColor Green
Write-Host "" -ForegroundColor Green
Write-Host "  Stop:  pwsh ./kill-all.ps1" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
