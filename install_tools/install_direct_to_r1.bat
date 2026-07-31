@echo off
chcp 65001 >nul
title Công Cụ Nạp App Web Controller Bluetooth Lên Loa Phicomm R1 (Xiaozhi Method)
cls
echo ===================================================================
echo   🔊 CÀI ĐẶT WEB CONTROLLER BLUETOOTH TRỰC TIẾP LÊN LOA (ADB 192.168.43.1)
echo ===================================================================
echo.
echo 📌 HƯỚNG DẪN:
echo 1. Hãy kết nối Wi-Fi của máy tính vào mạng phát ra từ loa (tên: Phicomm R1 / Phicomm_R1_xxxx).
echo 2. Ấn phím bất kỳ bên dưới để tự động nạp App Web Controller lên loa...
echo.
pause

echo.
echo [*] Đang kết nối ADB tới loa tại 192.168.43.1:5555...
adb disconnect
adb connect 192.168.43.1:5555

echo.
echo [*] Đang vô hiệu hóa các dịch vụ cũ và ứng dụng rác của Phicomm...
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.player >nul 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.device >nul 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.airskill >nul 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.otaservice >nul 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.setup >nul 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.voice >nul 2>&1

echo.
echo [*] Đang nạp file PhicommGemini.apk lên loa R1...
adb -s 192.168.43.1:5555 push PhicommGemini.apk /data/local/tmp/PhicommGemini.apk
adb -s 192.168.43.1:5555 shell pm install -r /data/local/tmp/PhicommGemini.apk

echo.
echo [*] Đang cấp đầy đủ quyền hệ thống và Bluetooth...
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH >nul 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH_ADMIN >nul 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.ACCESS_FINE_LOCATION >nul 2>&1
adb -s 192.168.43.1:5555 shell appops set com.phicomm.gemini SYSTEM_ALERT_WINDOW allow >nul 2>&1

echo.
echo [*] Đang khởi chạy Dịch vụ Controller trên loa Phicomm R1...
adb -s 192.168.43.1:5555 shell am startservice -n com.phicomm.gemini/.service.PhicommGeminiService
adb -s 192.168.43.1:5555 shell am start -n com.phicomm.gemini/.MainActivity

echo.
echo ===================================================================
echo   🎉 THÀNH CÔNG! ĐÃ CÀI XONG WEB CONTROLLER LÊN LOA PHICOMM R1!
echo ===================================================================
echo.
echo 📌 NỐI WI-FI NHÀ CHO LOA:
echo Nhập Tên & Mật khẩu Wi-Fi nhà bạn để loa tự kết nối vào mạng nhà:
echo.
set /p HOME_SSID="-> Nhập Tên Wi-Fi nhà bạn (SSID): "
set /p HOME_PASS="-> Nhập Mật Khẩu Wi-Fi nhà bạn: "

if not "%HOME_SSID%"=="" (
    echo.
    echo [*] Đang gửi thông tin Wi-Fi %HOME_SSID% tới loa...
    adb connect 192.168.43.1:5555 >nul 2>&1
    adb -s 192.168.43.1:5555 shell "svc wifi enable" >nul 2>&1
    adb -s 192.168.43.1:5555 shell cmd wifi connect-network "%HOME_SSID%" wpa2 "%HOME_PASS%" >nul 2>&1
    adb -s 192.168.43.1:5555 shell am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "%HOME_SSID%" --es password "%HOME_PASS%" >nul 2>&1
    adb -s 192.168.43.1:5555 shell am broadcast -a com.phicomm.gemini.SET_WIFI --es ssid "%HOME_SSID%" --es password "%HOME_PASS%" >nul 2>&1
    
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$client = New-Object System.Net.Sockets.UdpClient; $jsonObj = @{ ssid = '%HOME_SSID%'; password = '%HOME_PASS%'; key = '%HOME_PASS%' } | ConvertTo-Json -Compress; $bytes = [System.Text.Encoding]::UTF8.GetBytes($jsonObj); $endPoints = @(New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse('192.168.43.1'), 10000), New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse('192.168.43.1'), 8000), New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse('192.168.43.255'), 10000), New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse('192.168.43.255'), 8000)); foreach ($ep in $endPoints) { try { $client.Send($bytes, $bytes.Length, $ep) } catch {} }; $client.Close()" >nul 2>&1
)

echo.
echo ===================================================================
echo   🎉 HOÀN TẤT!
echo   Bây giờ mở Wi-Fi máy tính kết nối lại Wi-Fi nhà bạn và truy cập:
echo   👉 http://phicomm.local:8080
echo ===================================================================
echo.
pause
