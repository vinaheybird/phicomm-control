@echo off
chcp 65001 >nul
title CÃ´ng Cá»¥ Náº¡p App Web Controller Bluetooth LÃªn Loa Phicomm R1 (Xiaozhi Method)
cls
echo ===================================================================
echo   ðŸ”Š CÃ€I Äáº¶T WEB CONTROLLER BLUETOOTH TRá»°C TIáº¾P LÃŠN LOA (ADB 192.168.43.1)
echo ===================================================================
echo.
echo ðŸ“Œ HÆ¯á»šNG DáºªN:
echo 1. HÃ£y káº¿t ná»‘i Wi-Fi cá»§a mÃ¡y tÃ­nh vÃ o máº¡ng phÃ¡t ra tá»« loa (tÃªn: Phicomm R1 / Phicomm_R1_xxxx).
echo 2. áº¤n phÃ­m báº¥t ká»³ bÃªn dÆ°á»›i Ä‘á»ƒ tá»± Ä‘á»™ng náº¡p App Web Controller lÃªn loa...
echo.
pause

echo.
echo [*] Äang káº¿t ná»‘i ADB tá»›i loa táº¡i 192.168.43.1:5555...
adb disconnect
adb connect 192.168.43.1:5555

echo.
echo [*] Äang vÃ´ hiá»‡u hÃ³a cÃ¡c dá»‹ch vá»¥ cÅ© vÃ  á»©ng dá»¥ng rÃ¡c cá»§a Phicomm...
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.player >nul 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.device >nul 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.airskill >nul 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.otaservice >nul 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.setup >nul 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.voice >nul 2>&1

echo.
echo [*] Äang náº¡p file PhicommGemini.apk lÃªn loa R1...
adb -s 192.168.43.1:5555 push PhicommGemini.apk /data/local/tmp/PhicommGemini.apk
adb -s 192.168.43.1:5555 shell pm install -r /data/local/tmp/PhicommGemini.apk

echo.
echo [*] Äang cáº¥p Ä‘áº§y Ä‘á»§ quyá»n há»‡ thá»‘ng vÃ  Bluetooth...
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH >nul 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH_ADMIN >nul 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.ACCESS_FINE_LOCATION >nul 2>&1
adb -s 192.168.43.1:5555 shell appops set com.phicomm.gemini SYSTEM_ALERT_WINDOW allow >nul 2>&1

echo.
echo [*] Äang khá»Ÿi cháº¡y Dá»‹ch vá»¥ Controller trÃªn loa Phicomm R1...
adb -s 192.168.43.1:5555 shell am startservice -n com.phicomm.gemini/.service.PhicommGeminiService
adb -s 192.168.43.1:5555 shell am start -n com.phicomm.gemini/.MainActivity

echo.
echo ===================================================================
echo   ðŸŽ‰ THÃ€NH CÃ”NG! ÄÃƒ CÃ€I XONG WEB CONTROLLER LÃŠN LOA PHICOMM R1!
echo ===================================================================
echo.
echo ðŸ“Œ Ná»I WI-FI NHÃ€ CHO LOA:
echo Nháº­p TÃªn & Máº­t kháº©u Wi-Fi nhÃ  báº¡n Ä‘á»ƒ loa tá»± káº¿t ná»‘i vÃ o máº¡ng nhÃ :
echo.
set /p HOME_SSID="-> Nháº­p TÃªn Wi-Fi nhÃ  báº¡n (SSID): "
set /p HOME_PASS="-> Nháº­p Máº­t Kháº©u Wi-Fi nhÃ  báº¡n: "

if not "%HOME_SSID%"=="" (
    echo.
    echo [*] Äang gá»­i thÃ´ng tin Wi-Fi %HOME_SSID% tá»›i loa...
    adb connect 192.168.43.1:5555 >nul 2>&1
    adb -s 192.168.43.1:5555 shell "svc wifi enable" >nul 2>&1
    adb -s 192.168.43.1:5555 shell cmd wifi connect-network "%HOME_SSID%" wpa2 "%HOME_PASS%" >nul 2>&1
    adb -s 192.168.43.1:5555 shell am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "%HOME_SSID%" --es password "%HOME_PASS%" >nul 2>&1
    adb -s 192.168.43.1:5555 shell am broadcast -a com.phicomm.gemini.SET_WIFI --es ssid "%HOME_SSID%" --es password "%HOME_PASS%" >nul 2>&1
    
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$client = New-Object System.Net.Sockets.UdpClient; $jsonObj = @{ ssid = '%HOME_SSID%'; password = '%HOME_PASS%'; key = '%HOME_PASS%' } | ConvertTo-Json -Compress; $bytes = [System.Text.Encoding]::UTF8.GetBytes($jsonObj); $endPoints = @(New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse('192.168.43.1'), 10000), New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse('192.168.43.1'), 8000), New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse('192.168.43.255'), 10000), New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse('192.168.43.255'), 8000)); foreach ($ep in $endPoints) { try { $client.Send($bytes, $bytes.Length, $ep) } catch {} }; $client.Close()" >nul 2>&1
)

echo.
echo ===================================================================
echo   ðŸŽ‰ HOÃ€N Táº¤T!
echo   BÃ¢y giá» má»Ÿ Wi-Fi mÃ¡y tÃ­nh káº¿t ná»‘i láº¡i Wi-Fi nhÃ  báº¡n vÃ  truy cáº­p:
echo   ðŸ‘‰ http://phicomm.local:8080
echo ===================================================================
echo.
pause
