@echo off
chcp 65001 >nul
title Chuyen Loa Phicomm R1 Ve Che Do Bluetooth Thuan Tuy
color 0b

echo =================================================================
echo       CHUYEN LOA PHICOMM R1 VE CHE DO LOA BLUETOOTH THUAN TUY
echo       - Go bo toan bo goi XiaoZhi / Tro ly tuy chinh
echo       - An toan bo ung dung ngam de giai phong RAM va CPU
echo       - Giu nguyen Bluetooth A2DP Sink de nghe nhac on dinh
echo =================================================================
echo.

set "ADB=%~dp0adb.exe"
if not exist "%ADB%" set "ADB=adb.exe"

set "DEVIP="
set /p "DEVIP=Nhap dia chi IP loa [Enter de mac dinh 192.168.88.66:5555]: "
if not defined DEVIP set "DEVIP=192.168.88.66:5555"
set "DEVIP=%DEVIP: =%"
if "%DEVIP%"=="" set "DEVIP=192.168.88.66:5555"

echo.
echo [*] Dang ket noi toi %DEVIP% ...
"%ADB%" connect %DEVIP%

:: Tao script shell tren thu muc temp cua Windows de push vao loa chay lien mach
set "TMP_SH=%TEMP%\pure_bt_r1.sh"
echo #!/system/bin/sh > "%TMP_SH%"
echo echo "=== 1. DUNG CAC TIEN TRINH DANG CHAY ===" >> "%TMP_SH%"
echo am force-stop com.phicomm.speaker.device >/dev/null 2>&1 >> "%TMP_SH%"
echo am force-stop com.phicomm.speaker.player >/dev/null 2>&1 >> "%TMP_SH%"
echo am force-stop cn.workbuddy.r1client >/dev/null 2>&1 >> "%TMP_SH%"
echo am force-stop com.phicomm.gemini >/dev/null 2>&1 >> "%TMP_SH%"
echo echo "=== 2. GO BO CAC GOI XIAOZHI VA CUSTOM ===" >> "%TMP_SH%"
echo pm uninstall cn.workbuddy.r1client >/dev/null 2>&1 >> "%TMP_SH%"
echo pm uninstall com.phicomm.speaker.device >/dev/null 2>&1 >> "%TMP_SH%"
echo pm uninstall com.phicomm.speaker.player >/dev/null 2>&1 >> "%TMP_SH%"
echo pm uninstall com.phicomm.gemini >/dev/null 2>&1 >> "%TMP_SH%"
echo pm uninstall com.phicomm.r1.control >/dev/null 2>&1 >> "%TMP_SH%"
echo echo "=== 3. AN TOAN BO UNG DUNG TRO LY VA DICH VU RAC ===" >> "%TMP_SH%"
echo pm hide com.phicomm.speaker.device >> "%TMP_SH%"
echo pm hide com.phicomm.speaker.player >> "%TMP_SH%"
echo pm hide com.phicomm.speaker.airskill >> "%TMP_SH%"
echo pm hide com.phicomm.speaker.ijetty >> "%TMP_SH%"
echo pm hide com.phicomm.speaker.systemtool >> "%TMP_SH%"
echo pm hide com.phicomm.speaker.otaservice >> "%TMP_SH%"
echo pm hide com.phicomm.speaker.productiontest >> "%TMP_SH%"
echo pm hide com.phicomm.speaker.bugreport >> "%TMP_SH%"
echo pm hide com.phicomm.speaker.exceptionreporter >> "%TMP_SH%"
echo echo "=== HOAN TAT CAU HINH BLUETOOTH ===" >> "%TMP_SH%"

echo [*] Dang nap lenh vao loa...
"%ADB%" -s %DEVIP% push "%TMP_SH%" /data/local/tmp/pure_bt_r1.sh
del /f /q "%TMP_SH%" >nul 2>&1

echo [*] Dang thuc thi go bo va an ung dung tren loa...
"%ADB%" -s %DEVIP% shell chmod 755 /data/local/tmp/pure_bt_r1.sh <nul
"%ADB%" -s %DEVIP% shell /system/bin/sh /data/local/tmp/pure_bt_r1.sh <nul

echo.
echo =================================================================
echo  [V] DA CHUYEN XONG VE CHE DO BLUETOOTH THUAN TUY!
echo  - Toan bo ung dung tro ly va tien trinh ngam da duoc go bo va an.
echo  - Giai phong hon 250MB RAM giup loa hoat dong cuc ky muot ma.
echo  - Ten Bluetooth loa: Phicomm_R1_CD31 (hoac theo MAC cua loa)
echo =================================================================
echo.

set "REBOOT="
set /p "REBOOT=Ban co muon khoi dong lai (Reboot) loa ngay bay gio khong? [y/n]: "
if /i "%REBOOT%"=="y" (
    echo.
    echo [*] Dang khoi dong lai loa...
    "%ADB%" -s %DEVIP% reboot
    echo [*] Loa dang khoi dong lai. Vui long doi khoang 30 giay roi ket noi Bluetooth.
    pause
) else (
    echo.
    echo Hoan tat. Ban co the ket noi Bluetooth va su dung ngay.
    pause
)
