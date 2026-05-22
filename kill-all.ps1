# kill-all.ps1 -- Stop backend (8080) processes
# Platforms: Windows 11+, macOS, Linux  (requires PowerShell Core 7+ on Mac/Linux)
# Usage: .\kill-all.ps1   or   pwsh ./kill-all.ps1

param(
    [int[]]$Ports = @(8080)
)

if ($null -eq (Get-Variable 'IsWindows' -ErrorAction SilentlyContinue)) {
    $IsWindows = $true; $IsMacOS = $false; $IsLinux = $false
}

$ScriptDir = $PSScriptRoot
$LogDir    = Join-Path $ScriptDir "logs"

function Write-Info { param($m) Write-Host "[INFO]  $m" -ForegroundColor Cyan }
function Write-Ok   { param($m) Write-Host "[OK]    $m" -ForegroundColor Green }
function Write-Warn { param($m) Write-Host "[WARN]  $m" -ForegroundColor Yellow }

function Get-PortPIDs([int]$Port) {
    if ($IsWindows) {
        $lines = & netstat -ano 2>$null | Select-String ":$Port\s" |
                 Where-Object { $_ -match 'LISTENING' }
        return $lines | ForEach-Object {
            $parts = $_.Line.Trim() -split '\s+'
            if ($parts[-1] -match '^\d+$') { [int]$parts[-1] }
        } | Where-Object { $_ -gt 0 } | Select-Object -Unique
    } else {
        if (Get-Command lsof -ErrorAction SilentlyContinue) {
            $raw = & lsof -ti:$Port 2>/dev/null
            if ($raw) { return $raw | ForEach-Object { [int]$_.Trim() } | Where-Object { $_ -gt 0 } }
        }
        if (Get-Command ss -ErrorAction SilentlyContinue) {
            $raw = & ss -tlnp 2>/dev/null
            return $raw | Where-Object { $_ -match ":$Port[ $]" } |
                ForEach-Object { if ($_ -match 'pid=(\d+)') { [int]$Matches[1] } } |
                Where-Object { $_ -gt 0 }
        }
        if (Get-Command fuser -ErrorAction SilentlyContinue) {
            $raw = & bash -c "fuser ${Port}/tcp 2>/dev/null" 2>/dev/null
            if ($raw) { return $raw.Trim() -split '\s+' | ForEach-Object { [int]$_ } | Where-Object { $_ -gt 0 } }
        }
        return @()
    }
}

function Stop-PID([int]$ProcId, [string]$Label) {
    try {
        if ($IsWindows) { Stop-Process -Id $ProcId -Force -ErrorAction Stop }
        else            { & kill -9 $ProcId 2>/dev/null }
        Write-Ok "Stopped $Label (PID $ProcId)"
        return $true
    } catch { return $false }
}

Write-Host ""
Write-Info "Stopping Banking Fixed-Length File Generator services..."
Write-Host ""

$killed = 0

# ---- 1. PID-file kill ------------------------------------------------
$pidFile = Join-Path $LogDir "backend.pid"
if (Test-Path $pidFile) {
    $procId = [int]((Get-Content $pidFile -Raw).Trim())
    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if ($proc) {
        if (Stop-PID $procId "backend") { $killed++ }
    } else {
        Write-Warn "backend PID $procId not running"
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

# ---- 2. Pattern-based kill (safety net) ------------------------------
if ($IsWindows) {
    $javaProcs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'spring-boot|FixedLength|fixed-length' }
    foreach ($p in $javaProcs) {
        if (Stop-PID $p.ProcessId "Java/Spring") { $killed++ }
    }
} else {
    foreach ($pattern in @('spring-boot:run', 'FixedLengthConvertersApplication')) {
        $r = & bash -c "pkill -f '$pattern' 2>/dev/null; echo \$?" 2>/dev/null
        if ($r -eq "0") { Write-Ok "Stopped processes matching: $pattern"; $killed++ }
    }
}

# ---- 3. Port-based cleanup (final safety net) ------------------------
foreach ($port in $Ports) {
    $portPids = Get-PortPIDs $port
    foreach ($procId in $portPids) {
        if ($procId -gt 0 -and (Stop-PID $procId "port $port")) { $killed++ }
    }
}

# ---- cleanup PID files -----------------------------------------------
if (Test-Path $LogDir) {
    Remove-Item (Join-Path $LogDir "*.pid") -Force -ErrorAction SilentlyContinue
}

Write-Host ""
if ($killed -gt 0) { Write-Ok "Done -- $killed process(es) stopped." }
else               { Write-Warn "No matching processes found -- nothing to kill." }
Write-Host ""
