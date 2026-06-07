@echo off
setlocal enabledelayedexpansion

REM Zify local dev startup script (Windows)
REM Usage: double-click start.bat or run in cmd
REM Flow: check env -> build -> start backend -> health check -> start frontend

set "ROOT_DIR=%~dp0"
set "ROOT_DIR=%ROOT_DIR:~0,-1%"
set "PID_FILE=%ROOT_DIR%\zify.pid"
set "LOG_FILE=%ROOT_DIR%\logs\zify.log"
set "JAR=%ROOT_DIR%\zify-app\target\zify-app-0.1.0-SNAPSHOT.jar"

set "BACKEND_PORT=8080"
set "FRONTEND_PORT=5173"
set "HEALTH_URL=http://localhost:%BACKEND_PORT%/api/health"
set "HEALTH_TIMEOUT=90"
set "BACKEND_PID="

goto :main

REM ================================================================
REM Functions
REM ================================================================

:cleanup
if defined BACKEND_PID (
    echo.
    echo [INFO]  Stopping backend PID=!BACKEND_PID! ...
    taskkill /pid !BACKEND_PID! /f >nul 2>&1
    set "BACKEND_PID="
)
if exist "%PID_FILE%" del /f "%PID_FILE%" >nul 2>&1
echo [INFO]  Done
goto :eof

:print_log_tail
echo ------------------------------------------
powershell -command "if (Test-Path '%LOG_FILE%') { Get-Content '%LOG_FILE%' -Tail 30 } else { echo '(log file not found)' }"
echo ------------------------------------------
goto :eof

REM ================================================================
REM Main
REM ================================================================

:main

echo.
echo === Zify Dev Startup ===

REM -- Step 1: Prerequisites --

echo.
echo [1/5] Checking prerequisites ...

REM Java
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] java not found. Install JDK 21+ and add to PATH.
    exit /b 1
)
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER_RAW=%%v"
)
set "JAVA_VER_RAW=%JAVA_VER_RAW:"=%"
for /f "tokens=1 delims=." %%v in ("%JAVA_VER_RAW%") do set "JAVA_MAJOR=%%v"
if !JAVA_MAJOR! lss 21 (
    echo [ERROR] Java 21+ required, current: %JAVA_VER_RAW%
    exit /b 1
)
echo [INFO]  Java !JAVA_MAJOR! OK

REM Maven
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] mvn not found. Install Maven and add to PATH.
    exit /b 1
)
echo [INFO]  Maven OK

REM MySQL
where mysqladmin >nul 2>&1
if !errorlevel! equ 0 (
    mysqladmin ping -h localhost -P 3306 -u root --password=123456 --silent >nul 2>&1
    if !errorlevel! equ 0 (
        echo [INFO]  MySQL localhost:3306 OK
    ) else (
        echo [WARN]  MySQL localhost:3306 not available, backend may fail
    )
) else (
    echo [WARN]  mysqladmin not found, skipping MySQL check
)

REM Redis
where redis-cli >nul 2>&1
if !errorlevel! equ 0 (
    redis-cli -h localhost -p 6379 ping >nul 2>&1
    if !errorlevel! equ 0 (
        echo [INFO]  Redis localhost:6379 OK
    ) else (
        echo [WARN]  Redis localhost:6379 not available, backend may fail
    )
) else (
    echo [WARN]  redis-cli not found, skipping Redis check
)

REM curl
where curl >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] curl not found, required for health check
    exit /b 1
)

REM -- Step 2: Build backend --

echo.
echo [2/5] Building backend ...
echo [INFO]  mvn package -DskipTests

cd /d "%ROOT_DIR%"
call mvn package -DskipTests -q
if %errorlevel% neq 0 (
    echo [ERROR] Maven build failed
    exit /b 1
)
echo [INFO]  Build OK

if not exist "%JAR%" (
    echo [ERROR] JAR not found after build: %JAR%
    exit /b 1
)

REM -- Step 3: Start backend --

echo.
echo [3/5] Starting backend ...

if exist "%PID_FILE%" (
    set /p OLD_PID=<"%PID_FILE%"
    taskkill /pid !OLD_PID! /f >nul 2>&1
    del /f "%PID_FILE%" >nul 2>&1
    echo [INFO]  Stopped old backend process
    timeout /t 3 /nobreak >nul
)

if not exist "%ROOT_DIR%\logs" mkdir "%ROOT_DIR%\logs"

echo [INFO]  JAR  : %JAR%
echo [INFO]  Log  : %LOG_FILE%
echo [INFO]  Port : %BACKEND_PORT%

start /b "" java -jar "%JAR%" >> "%LOG_FILE%" 2>&1

timeout /t 2 /nobreak >nul

set "BACKEND_PID="
for /f "tokens=2 delims==" %%p in ('wmic process where "commandline like '%%zify-app%%'" get processid /value 2^>nul ^| findstr ProcessId') do (
    for /f "tokens=*" %%c in ("%%p") do (
        set "BACKEND_PID=%%c"
    )
)

if not defined BACKEND_PID (
    echo [ERROR] Failed to get backend PID
    echo [INFO]  Last log:
    call :print_log_tail
    exit /b 1
)

echo !BACKEND_PID!> "%PID_FILE%"
echo [INFO]  Backend PID=!BACKEND_PID!

REM -- Step 4: Health check --

echo.
echo [4/5] Waiting for backend ...
echo [INFO]  Health: %HEALTH_URL% (timeout %HEALTH_TIMEOUT%s)
echo [INFO]  Log  : %LOG_FILE%

set /a "elapsed=0"
set /a "STEP=2"

:health_check
curl -sf "%HEALTH_URL%" >nul 2>&1
if !errorlevel! equ 0 goto :health_ok

tasklist /fi "pid eq !BACKEND_PID!" /nh 2>nul | findstr /i "!BACKEND_PID!" >nul 2>&1
if !errorlevel! neq 0 (
    echo.
    echo [ERROR] Backend process exited. Last log:
    call :print_log_tail
    del /f "%PID_FILE%" >nul 2>&1
    exit /b 1
)

set /a "elapsed+=STEP"
if !elapsed! geq %HEALTH_TIMEOUT% (
    echo.
    echo [ERROR] Backend startup timeout (%HEALTH_TIMEOUT%s). Last log:
    call :print_log_tail
    taskkill /pid !BACKEND_PID! /f >nul 2>&1
    del /f "%PID_FILE%" >nul 2>&1
    exit /b 1
)

<nul set /p="."
timeout /t %STEP% /nobreak >nul
goto :health_check

:health_ok
echo.
echo [INFO]  Backend ready OK

REM -- Step 5: Start frontend --

echo.
echo [5/5] Starting frontend ...

cd /d "%ROOT_DIR%\zify-web"

if not exist "node_modules" (
    echo [INFO]  Installing frontend dependencies ...
    call npm install
    if !errorlevel! neq 0 (
        echo [ERROR] npm install failed
        call :cleanup
        exit /b 1
    )
)

echo.
echo [INFO]  Frontend : http://localhost:%FRONTEND_PORT%
echo [INFO]  Backend  : http://localhost:%BACKEND_PORT%
echo [INFO]  API proxy: /api -^> http://localhost:%BACKEND_PORT%
echo.
echo [INFO]  Press Ctrl+C to exit (backend will be stopped)
echo ------------------------------------------

call npm run dev
call :cleanup
