@echo off
set "SUBNET=192.168.88"
for /L %%i in (1,1,254) do (
    start /b cmd /c "ping -n 1 -w 500 %SUBNET%.%%i >nul && echo [ALIVE] %SUBNET%.%%i"
)
