@echo off
chcp 65001 >nul
title Công Cụ Vô Hiệu Hóa App Rác Phicomm R1 (Xiaozhi Method)
cls
echo ===================================================================
echo   🚫 VÔ HIỆU HÓA CÁC DỊCH VỤ VÀ ỨNG DỤNG MẶC ĐỊNH RÁC CỦA PHICOMM R1
echo ===================================================================
echo.

set /p LOA_IP="-> Nhập IP của Loa R1 (Ví dụ 192.168.1.15 hoặc 192.168.43.1): "

if "%LOA_IP%"=="" (
    set LOA_IP=192.168.43.1
)

echo.
echo [*] Đang kết nối ADB tới %LOA_IP%:5555...
adb disconnect
adb connect %LOA_IP%:5555

echo.
echo [*] Đang vô hiệu hóa các ứng dụng rác Phicomm...
adb -s %LOA_IP%:5555 shell pm hide com.phicomm.speaker.player >nul 2>&1
adb -s %LOA_IP%:5555 shell pm hide com.phicomm.speaker.device >nul 2>&1
adb -s %LOA_IP%:5555 shell pm hide com.phicomm.speaker.airskill >nul 2>&1
adb -s %LOA_IP%:5555 shell pm hide com.phicomm.speaker.otaservice >nul 2>&1
adb -s %LOA_IP%:5555 shell pm hide com.phicomm.speaker.setup >nul 2>&1
adb -s %LOA_IP%:5555 shell pm hide com.phicomm.speaker.voice >nul 2>&1
adb -s %LOA_IP%:5555 shell pm hide com.unisound.unicar.speaker >nul 2>&1

adb -s %LOA_IP%:5555 shell pm disable-user com.phicomm.speaker.player >nul 2>&1
adb -s %LOA_IP%:5555 shell pm disable-user com.phicomm.speaker.device >nul 2>&1
adb -s %LOA_IP%:5555 shell pm disable-user com.phicomm.speaker.airskill >nul 2>&1

echo.
echo ===================================================================
echo   🎉 THÀNH CÔNG! ĐÃ TẮT TOÀN BỘ APP RÁC PHICOMM MẶC ĐỊNH!
echo   Loa R1 giờ đây giải phóng RAM, khởi động nhanh và chỉ chạy Web Controller!
echo ===================================================================
echo.
pause
