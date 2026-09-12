@echo off
setlocal

set "NGINX_EXE=D:\develop\back\TakeOut\nginx-1.20.2\nginx.exe"
set "NGINX_PREFIX=%~dp0deploy\nginx"

if not exist "%NGINX_PREFIX%\logs\nginx.pid" (
    echo Rehab frontend Nginx is not running.
    pause
    exit /b 0
)

"%NGINX_EXE%" -p "%NGINX_PREFIX%" -c nginx.conf -s quit
echo Rehab frontend Nginx has been stopped.
ping 127.0.0.1 -n 2 >nul
endlocal
