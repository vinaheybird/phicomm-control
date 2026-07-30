#!/bin/sh
# Termux / iSH Shell script to install Web Controller APK & provision Phicomm R1 Wi-Fi
echo "==================================================================="
echo "  🔊 CÀI ĐẶT WEB CONTROLLER LOA PHICOMM R1 QUA ĐIỆN THOẠI (TERMUX / ISH)"
echo "==================================================================="
echo ""

# 1. Kiem tra va cai adb / curl / wget
if ! command -v adb >/dev/null 2>&1; then
    echo "[*] Dang cai dat android-tools va curl..."
    if command -v pkg >/dev/null 2>&1; then
        pkg install -y android-tools curl
    elif command -v apk >/dev/null 2>&1; then
        apk add --no-cache android-tools curl
    fi
fi

# Link GitHub Releases / Raw APK (Tu dong cap nhat)
APK_URL="https://github.com/zkenz/phicomm/releases/download/v1.0.0/PhicommGemini.apk"

# Neu chua co file local, tu dong tai ve bang curl
if [ ! -f "PhicommGemini.apk" ]; then
    echo "[*] Chuc co file APK tai thu muc, đang tu dong tai PhicommGemini.apk tu GitHub..."
    curl -sSL -o PhicommGemini.apk "$APK_URL" 2>/dev/null || wget -q -O PhicommGemini.apk "$APK_URL" 2>/dev/null
fi

echo "[*] Dang ket noi ADB toi loa tai 192.168.43.1:5555..."
adb disconnect >/dev/null 2>&1
adb connect 192.168.43.1:5555

echo ""
echo "[*] Dang vo hieu hoa cac ung dung rac Phicomm..."
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.player >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.device >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.airskill >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.otaservice >/dev/null 2>&1

echo ""
if [ -f "PhicommGemini.apk" ]; then
    echo "[*] Dang nap va cai dat file PhicommGemini.apk len loa Phicomm R1..."
    adb -s 192.168.43.1:5555 push PhicommGemini.apk /data/local/tmp/PhicommGemini.apk
    adb -s 192.168.43.1:5555 shell pm install -r /data/local/tmp/PhicommGemini.apk
else
    echo "[!] Không tìm thấy file PhicommGemini.apk. Vui lòng tải file APK vào cùng thư mục!"
fi

echo ""
echo "[*] Cap quyền Bluetooth va khoi chay dich vu..."
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH_ADMIN >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell am startservice -n com.phicomm.gemini/.service.PhicommGeminiService >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell am start -n com.phicomm.gemini/.MainActivity >/dev/null 2>&1

echo ""
echo "-> Nhap Ten Wi-Fi nha ban (SSID):"
read HOME_SSID
echo "-> Nhap Mat Khau Wi-Fi nha ban:"
read HOME_PASS

if [ -n "$HOME_SSID" ]; then
    echo "[*] Dang gui thong tin Wi-Fi $HOME_SSID toi loa..."
    adb -s 192.168.43.1:5555 shell cmd wifi connect-network "$HOME_SSID" wpa2 "$HOME_PASS" >/dev/null 2>&1
    adb -s 192.168.43.1:5555 shell am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "$HOME_SSID" --es password "$HOME_PASS" >/dev/null 2>&1
    echo "[✅ HOAN TAT] Da cai xong Web Controller va Loa R1 dang ket noi vao Wi-Fi nha ban!"
fi
