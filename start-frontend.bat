@echo off
setlocal

set "NGINX_EXE=D:\develop\back\TakeOut\nginx-1.20.2\nginx.exe"
set "NGINX_PREFIX=%~dp0deploy\nginx"

if not exist "%NGINX_EXE%" (
    echo [ERROR] Nginx was not found:
    echo %NGINX_EXE%
    pause
    exit /b 1
)

if not exist "%NGINX_PREFIX%\logs" mkdir "%NGINX_PREFIX%\logs"
if not exist "%NGINX_PREFIX%\temp" mkdir "%NGINX_PREFIX%\temp"

"%NGINX_EXE%" -p "%NGINX_PREFIX%" -c nginx.conf -t
if errorlevel 1 (
    echo [ERROR] Nginx configuration check failed.
    pause
    exit /b 1
)

if exist "%NGINX_PREFIX%\logs\nginx.pid" (
    for %%P in ("%NGINX_PREFIX%\logs\nginx.pid") do if %%~zP EQU 0 del /q "%%~fP"
)

if /I "%~1"=="--check" (
    echo [OK] Nginx configuration check passed.
    exit /b 0
)

if exist "%NGINX_PREFIX%\logs\nginx.pid" (
    "%NGINX_EXE%" -p "%NGINX_PREFIX%" -c nginx.conf -s reload
    if errorlevel 1 (
        del /q "%NGINX_PREFIX%\logs\nginx.pid" >nul 2>&1
        powershell.exe -NoProfile -Command "Start-Process -FilePath $env:NGINX_EXE -ArgumentList '-p', $env:NGINX_PREFIX, '-c', 'nginx.conf' -WindowStyle Hidden"
    )
) else (
    powershell.exe -NoProfile -Command "Start-Process -FilePath $env:NGINX_EXE -ArgumentList '-p', $env:NGINX_PREFIX, '-c', 'nginx.conf' -WindowStyle Hidden"
)

ping 127.0.0.1 -n 2 >nul
if /I not "%~1"=="--no-browser" start "" "http://localhost:8090/"
endlocal
