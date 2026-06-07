@echo off
setlocal enabledelayedexpansion

REM Stop Zify backend and frontend gracefully
REM Usage: stop.bat

set "ROOT_DIR=%~dp0"
set "ROOT_DIR=%ROOT_DIR:~0,-1%"
set "PID_FILE=%ROOT_DIR%\zify.pid"
set "GRACEFUL_TIMEOUT=10"

echo Stopping Zify ...

REM -- Stop backend via PID file --

if exist "%PID_FILE%" (
    set /p PID=<"%PID_FILE%"

    REM Check if process is alive
    tasklist /fi "pid eq !PID!" /nh 2>nul | findstr /i "!PID!" >nul 2>&1
    if !errorlevel! equ 0 (
        echo [INFO]  Sending SIGTERM to backend (PID=!PID!) ...
        taskkill /pid !PID! >nul 2>&1

        REM Wait for graceful shutdown
        set /a "waited=0"
        :wait_backend
        tasklist /fi "pid eq !PID!" /nh 2>nul | findstr /i "!PID!" >nul 2>&1
        if !errorlevel! neq 0 goto :backend_stopped

        if !waited! geq %GRACEFUL_TIMEOUT% (
            echo [WARN]  Backend did not stop within %GRACEFUL_TIMEOUT%s, force killing ...
            taskkill /pid !PID! /f >nul 2>&1
            goto :backend_stopped
        )

        timeout /t 1 /nobreak >nul
        set /a "waited+=1"
        <nul set /p="."
        goto :wait_backend

        :backend_stopped
        echo.
        echo [INFO]  Backend stopped (PID=!PID!)
    ) else (
        echo [INFO]  Backend process !PID! already stopped
    )
    del /f "%PID_FILE%" >nul 2>&1
) else (
    echo [INFO]  PID file not found, backend not running
)

REM -- Stop frontend (node/vite processes on port 5173) --

set "FOUND_FRONTEND=0"

REM Find node processes listening on port 5173
for /f "tokens=5" %%p in ('netstat -aon 2^>nul ^| findstr ":5173 .*LISTENING"') do (
    set "FPID=%%p"
    if defined FPID (
        set "FOUND_FRONTEND=1"
        echo [INFO]  Sending SIGTERM to frontend (PID=!FPID!) ...
        taskkill /pid !FPID! >nul 2>&1

        set /a "waited=0"
        :wait_frontend
        tasklist /fi "pid eq !FPID!" /nh 2>nul | findstr /i "!FPID!" >nul 2>&1
        if !errorlevel! neq 0 goto :frontend_stopped

        if !waited! geq %GRACEFUL_TIMEOUT% (
            echo [WARN]  Frontend did not stop within %GRACEFUL_TIMEOUT%s, force killing ...
            taskkill /pid !FPID! /f >nul 2>&1
            goto :frontend_stopped
        )

        timeout /t 1 /nobreak >nul
        set /a "waited+=1"
        <nul set /p="."
        goto :wait_frontend

        :frontend_stopped
        echo.
        echo [INFO]  Frontend stopped (PID=!FPID!)
    )
)

if "!FOUND_FRONTEND!"=="0" (
    echo [INFO]  No frontend process found on port 5173
)

echo [INFO]  All stopped
