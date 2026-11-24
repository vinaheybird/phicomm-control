@echo off
chcp 65001 >nul
title Theo Doi Log Nhan Dien Giong Noi Loa R1
python -u "%~dp0xem_log.py"
pause
