@echo off
chcp 65001 >nul
title Công Cụ Cài Đặt 1-Click Loa Phicomm R1 Gemini Assistant
cls
echo ===================================================================
echo   🔊 CÔNG CỤ CÀI ĐẶT 1-CLICK LOA PHICOMM R1 TRỢ LÝ GEMINI FREE
echo ===================================================================
echo.
echo 📌 HƯỚNG DẪN LỰA CHỌN:
echo [1] Loa đang phát Wi-Fi Phicomm R1 (Gõ 192.168.43.1 để nạp trực tiếp)
echo [2] Loa đã kết nối Wi-Fi nhà (Nhập địa chỉ IP nhà hoặc phicomm.local)
echo.

set /p R1_HOST="-> Nhập IP/Host (Ấn Enter để dùng 192.168.43.1): "

if "%R1_HOST%"=="" set R1_HOST=192.168.43.1

echo.
echo [*] Đang kết nối ADB tới loa Phicomm R1 tại %R1_HOST%:5555 ...
adb disconnect >nul 2>&1
adb connect %R1_HOST%:5555

echo.
echo [*] Đang nạp ứng dụng PhicommGemini.apk vào loa Phicomm R1...
adb -s %R1_HOST%:5555 push PhicommGemini.apk /data/local/tmp/PhicommGemini.apk
adb -s %R1_HOST%:5555 shell pm install -r /data/local/tmp/PhicommGemini.apk

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [X] Cài đặt thất bại! Hãy chắc chắn máy tính đang nối Wi-Fi Phicomm R1.
    pause
    exit /b
)

echo.
echo [*] Đang cấp đầy đủ quyền hệ thống ngầm cho ứng dụng...
adb -s %R1_HOST%:5555 shell pm grant com.phicomm.gemini android.permission.RECORD_AUDIO
adb -s %R1_HOST%:5555 shell pm grant com.phicomm.gemini android.permission.WRITE_EXTERNAL_STORAGE
adb -s %R1_HOST%:5555 shell pm grant com.phicomm.gemini android.permission.READ_EXTERNAL_STORAGE
adb -s %R1_HOST%:5555 shell appops set com.phicomm.gemini SYSTEM_ALERT_WINDOW allow

echo.
echo [*] Đang kích hoạt Dịch vụ ngầm Gemini trên loa Phicomm R1...
adb -s %R1_HOST%:5555 shell am startservice -n com.phicomm.gemini/.service.PhicommGeminiService
adb -s %R1_HOST%:5555 shell am start -n com.phicomm.gemini/.MainActivity

echo.
echo ===================================================================
echo   🎉 THÀNH CÔNG! ĐÃ CÀI XONG TRỢ LÝ GEMINI LÊN LOA PHICOMM R1!
echo ===================================================================
echo.
if "%R1_HOST%"=="192.168.43.1" (
    echo 📌 NỐI WI-FI NHÀ CHO LOA:
    set /p HOME_SSID="-> Nhập Tên Wi-Fi nhà bạn (SSID): "
    set /p HOME_PASS="-> Nhập Mật Khẩu Wi-Fi nhà bạn: "
    if not "%HOME_SSID%"=="" (
        adb -s 192.168.43.1:5555 shell cmd wifi connect-network "%HOME_SSID%" wpa2 "%HOME_PASS%" >nul 2>&1
        adb -s 192.168.43.1:5555 shell am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "%HOME_SSID%" --es password "%HOME_PASS%" >nul 2>&1
    )
)

echo.
echo 📌 CẤU HÌNH GEMINI API KEY:
echo 1. Mở Wi-Fi máy tính kết nối lại Wi-Fi nhà bạn.
echo 2. Mở trình duyệt web truy cập: 👉 http://phicomm.local:8080
echo 3. Nhập Google Gemini API Key miễn phí của bạn và bấm Lưu!
echo.
pause
