@echo off
chcp 65001 >nul
title Khoi Phuc Ung Dung Goc De Cai Dat XiaoZhi
color 0a

echo =================================================================
echo        KHOI PHUC CAC UNG DUNG GOC CHO LOA PHICOMM R1
echo        [Chuan bi moi truong de chay Cai_Dat_Nhanh.bat]
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

:: Tao script shell tren thu muc temp cua Windows roi push vao loa
set "TMP_SH=%TEMP%\unhide_r1.sh"
echo #!/system/bin/sh > "%TMP_SH%"
echo echo "=== DANG MO LAI CAC DICH VU HE THONG ===" >> "%TMP_SH%"
echo pm unhide com.phicomm.speaker.device >> "%TMP_SH%"
echo pm unhide com.phicomm.speaker.player >> "%TMP_SH%"
echo pm unhide com.phicomm.speaker.airskill >> "%TMP_SH%"
echo pm unhide com.phicomm.speaker.ijetty >> "%TMP_SH%"
echo pm unhide com.phicomm.speaker.systemtool >> "%TMP_SH%"
echo pm unhide com.phicomm.speaker.otaservice >> "%TMP_SH%"
echo pm unhide com.phicomm.speaker.productiontest >> "%TMP_SH%"
echo pm unhide com.phicomm.speaker.bugreport >> "%TMP_SH%"
echo pm unhide com.phicomm.speaker.exceptionreporter >> "%TMP_SH%"
echo echo "=== HOAN TAT KHOI PHUC ===" >> "%TMP_SH%"

echo [*] Dang nap lenh khoi phuc vao loa...
"%ADB%" -s %DEVIP% push "%TMP_SH%" /data/local/tmp/unhide_r1.sh
del /f /q "%TMP_SH%" >nul 2>&1

echo [*] Dang thuc thi mo lai cac ung dung he thong tren loa...
"%ADB%" -s %DEVIP% shell chmod 755 /data/local/tmp/unhide_r1.sh <nul
"%ADB%" -s %DEVIP% shell /system/bin/sh /data/local/tmp/unhide_r1.sh <nul

echo.
echo =================================================================
echo  [V] DA KHOI PHUC TOAN BO UNG DUNG GOC CAN THIET!
echo  - com.phicomm.speaker.device [Nen tang cai XiaoZhi Unisound]
echo  - com.phicomm.speaker.player [Nen tang cai XiaoZhi Web Console]
echo =================================================================
echo.

set "CHOICE="
set /p "CHOICE=Ban co muon khoi dong ngay Cai_Dat_Nhanh.bat de cai dat XiaoZhi khong? [y/n]: "
if /i "%CHOICE%"=="y" (
    echo.
    echo [*] Dang mo trinh cai dat Cai_Dat_Nhanh.bat...
    call "%~dp0Cai_Dat_Nhanh.bat"
) else (
    echo.
    echo Hoan tat. Khi nao can cai dat ban chi can chay file Cai_Dat_Nhanh.bat.
    pause
)
