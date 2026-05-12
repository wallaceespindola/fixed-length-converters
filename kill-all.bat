@echo off
REM kill-all.bat — Stop backend (8080) and frontend (3000) processes
REM Platform: Windows 11+  (CMD)
REM Usage: kill-all.bat

setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "LOG_DIR=%SCRIPT_DIR%logs"
set /a killed=0

echo.
echo [INFO]  Stopping Banking Fixed-Length File Generator services...
echo.

REM ── kill by port 8080 (Spring Boot backend) ───────────────────────────────────
echo [INFO]  Checking port 8080 (Spring Boot backend)...
for /f "tokens=5" %%p in ('netstat -ano 2^>nul ^| findstr ":8080 " ^| findstr "LISTENING"') do (
    if "%%p" neq "" (
        echo [INFO]  Killing PID %%p on port 8080...
        taskkill /PID %%p /F >nul 2>&1
        if !errorlevel! equ 0 (
            echo [OK]    Stopped process on port 8080 ^(PID %%p^)
            set /a killed+=1
        ) else (
            echo [WARN]  Could not stop PID %%p on port 8080
        )
    )
)

REM ── kill by port 3000 (Vite frontend) ─────────────────────────────────────────
echo [INFO]  Checking port 3000 (Vite frontend)...
for /f "tokens=5" %%p in ('netstat -ano 2^>nul ^| findstr ":3000 " ^| findstr "LISTENING"') do (
    if "%%p" neq "" (
        echo [INFO]  Killing PID %%p on port 3000...
        taskkill /PID %%p /F >nul 2>&1
        if !errorlevel! equ 0 (
            echo [OK]    Stopped process on port 3000 ^(PID %%p^)
            set /a killed+=1
        ) else (
            echo [WARN]  Could not stop PID %%p on port 3000
        )
    )
)

REM ── kill any remaining java.exe (spring-boot:run / Spring Boot jar) ───────────
echo [INFO]  Checking for remaining java.exe processes...
tasklist /fi "imagename eq java.exe" /nh 2>nul | findstr /i "java.exe" >nul
if %errorlevel% equ 0 (
    taskkill /IM java.exe /F >nul 2>&1
    if !errorlevel! equ 0 (
        echo [OK]    Stopped java.exe
        set /a killed+=1
    )
)

REM ── kill any remaining node.exe (Vite / npm run dev) ──────────────────────────
echo [INFO]  Checking for remaining node.exe processes...
tasklist /fi "imagename eq node.exe" /nh 2>nul | findstr /i "node.exe" >nul
if %errorlevel% equ 0 (
    taskkill /IM node.exe /F >nul 2>&1
    if !errorlevel! equ 0 (
        echo [OK]    Stopped node.exe
        set /a killed+=1
    )
)

REM ── remove pid files ──────────────────────────────────────────────────────────
if exist "%LOG_DIR%\backend.pid"  del /f /q "%LOG_DIR%\backend.pid"  >nul 2>&1
if exist "%LOG_DIR%\frontend.pid" del /f /q "%LOG_DIR%\frontend.pid" >nul 2>&1

echo.
if %killed% gtr 0 (
    echo [OK]    Done - %killed% process group^(s^) stopped.
) else (
    echo [WARN]  No matching processes found - nothing to kill.
)
echo.

endlocal
