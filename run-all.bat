@echo off
REM run-all.bat — Build and start backend (port 8080)
REM Platform: Windows 11+  (CMD)
REM Usage: run-all.bat
REM Requires: Java 21+, Maven 3.9+, curl (built-in on Win10+)

setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "LOG_DIR=%SCRIPT_DIR%logs"
set "BACKEND_LOG=%LOG_DIR%\backend.log"
set "BUILD_LOG=%LOG_DIR%\build.log"
set "BACKEND_PORT=8080"
set "HEALTH_URL=http://localhost:%BACKEND_PORT%/actuator/health"
set "WAIT_SECS=90"
set "SPRING_PROFILE=dev"

echo.
echo ============================================================
echo   Banking Fixed-Length File Generator - Backend Launcher
echo   Platform: Windows
echo ============================================================
echo.

REM ── create log directory ──────────────────────────────────────────────────────
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

REM ── pre-flight: check required tools ──────────────────────────────────────────
where java >nul 2>&1 || (echo [ERROR] java not found in PATH & exit /b 1)
where mvn  >nul 2>&1 || (echo [ERROR] mvn not found in PATH  & exit /b 1)
where curl >nul 2>&1 || (echo [ERROR] curl not found in PATH & exit /b 1)

REM ── check port ────────────────────────────────────────────────────────────────
netstat -ano | findstr ":%BACKEND_PORT%\b" | findstr "LISTENING" >nul 2>&1
if %errorlevel% equ 0 (
    echo [WARN]  Port %BACKEND_PORT% already in use - run kill-all.bat first.
)

REM ── 1. Maven build ────────────────────────────────────────────────────────────
echo [INFO]  Building project (Maven clean install, no tests)...
cd /d "%SCRIPT_DIR%"
call mvn clean install -DskipTests > "%BUILD_LOG%" 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Maven build failed - see %BUILD_LOG%
    exit /b 1
)
findstr /c:"BUILD SUCCESS" "%BUILD_LOG%" >nul
if %errorlevel% neq 0 (
    echo [ERROR] Maven build did not succeed - see %BUILD_LOG%
    exit /b 1
)
echo [OK]    Build complete

REM ── 2. Start backend ──────────────────────────────────────────────────────────
echo [INFO]  Starting Spring Boot backend (profile: %SPRING_PROFILE%, port: %BACKEND_PORT%)...
cd /d "%SCRIPT_DIR%"
start "SpringBoot-Backend" cmd /c "mvn spring-boot:run -Dspring-boot.run.profiles=%SPRING_PROFILE% > "%BACKEND_LOG%" 2>&1"
echo [INFO]  Backend starting... (log: %BACKEND_LOG%)

REM ── wait for backend health ───────────────────────────────────────────────────
echo [INFO]  Waiting for backend on port %BACKEND_PORT% (up to %WAIT_SECS%s)...
set /a waited=0
:wait_loop
    if %waited% geq %WAIT_SECS% (
        echo [ERROR] Backend did not start within %WAIT_SECS%s - check %BACKEND_LOG%
        exit /b 1
    )
    curl -sf "%HEALTH_URL%" >nul 2>&1
    if %errorlevel% equ 0 (
        echo [OK]    Backend is up after %waited%s
        goto :backend_ready
    )
    timeout /t 1 /nobreak >nul
    set /a waited+=1
goto :wait_loop
:backend_ready

REM ── summary ───────────────────────────────────────────────────────────────────
echo.
echo ============================================================
echo   Backend running
echo   App      -^>  http://localhost:8080
echo   Swagger  -^>  http://localhost:8080/swagger-ui.html
echo   Health   -^>  http://localhost:8080/actuator/health
echo.
echo   Stop:  kill-all.bat
echo ============================================================
echo.

endlocal
