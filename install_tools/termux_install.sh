#!/bin/sh
# Termux / iSH Shell script to install Web Controller APK & provision Phicomm R1 Wi-Fi
echo "==================================================================="
echo "  🔊 CÀI ĐẶT WEB CONTROLLER LOA PHICOMM R1 QUA ĐIỆN THOẠI (TERMUX / ISH)"
echo "==================================================================="
echo ""

# 1. Kiem tra va cai dat android-tools & curl (Can Internet 4G / Wi-Fi)
if ! command -v adb >/dev/null 2>&1; then
    echo "[1/4] Dang cai dat cong cu adb & curl..."
    if command -v pkg >/dev/null 2>&1; then
        pkg install -y android-tools curl
    elif command -v apk >/dev/null 2>&1; then
        apk add --no-cache android-tools curl
    fi
fi

# Link GitHub Release APK
APK_URL="https://github.com/vinaheybird/phicomm-control/releases/download/v1.0.0/PhicommGemini.apk"

# Tai file APK ve may
if [ ! -f "PhicommGemini.apk" ]; then
    echo "[2/4] Dang tai PhicommGemini.apk tu GitHub Release..."
    curl -sSL -o PhicommGemini.apk "$APK_URL" 2>/dev/null || wget -q -O PhicommGemini.apk "$APK_URL" 2>/dev/null
fi

if [ -f "PhicommGemini.apk" ]; then
    echo "[✅] Da tai xong file APK va cong cu cai dat!"
else
    echo "[!] Khong tai duoc file PhicommGemini.apk! Vui long kiem tra ket noi Internet/4G."
    exit 1
fi

echo ""
echo "-------------------------------------------------------------------"
echo "📶 BƯỚC TIẾP THEO: KẾT NỐI VÀO WI-FI CỦA LOA PHICOMM R1"
echo "-------------------------------------------------------------------"
echo "1. Vao Cài đặt Wi-Fi trên điện thoại."
echo "2. Kết nối vào Wi-Fi phát ra từ loa (Tên: Phicomm R1 hoặc Phicomm_R1_xxxx)."
echo "3. Quay lại đây và nhấn [ENTER] để tiếp tục cài đặt..."
echo "-------------------------------------------------------------------"
read DUMMY </dev/tty 2>/dev/null || read DUMMY

echo ""
echo "[3/4] Dang ket noi ADB toi loa Phicomm R1 (192.168.43.1:5555)..."

adb disconnect >/dev/null 2>&1
CONNECTED=0
RETRY=1

while [ $RETRY -le 5 ]; do
    echo "[*] Thu ket noi ADB toi loa (Lan $RETRY/5)..."
    OUTPUT=$(adb connect 192.168.43.1:5555 2>&1)
    if echo "$OUTPUT" | grep -q "connected"; then
        CONNECTED=1
        echo "[✅] Ket noi ADB thanh cong!"
        break
    fi
    sleep 2
    RETRY=$((RETRY + 1))
done

if [ $CONNECTED -eq 0 ]; then
    echo "[!] Khong the ket noi toi loa R1 qua 192.168.43.1:5555."
    echo "    Vui long kiem tra dien thoai da ket noi dung Wi-Fi Phicomm R1 chưa va thu lai!"
    exit 1
fi

echo ""
echo "[4/4] Dang vo hieu hoa ung dung rac Phicomm..."
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.player >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.device >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.airskill >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.otaservice >/dev/null 2>&1

echo ""
echo "[*] Dang nap va cai dat PhicommGemini.apk len loa Phicomm R1..."
adb -s 192.168.43.1:5555 push PhicommGemini.apk /data/local/tmp/PhicommGemini.apk
adb -s 192.168.43.1:5555 shell pm install -r /data/local/tmp/PhicommGemini.apk

echo ""
echo "[*] Cap quyen Bluetooth va khoi chay dich vu..."
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH_ADMIN >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell am startservice -n com.phicomm.gemini/.service.PhicommGeminiService >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell am start -n com.phicomm.gemini/.MainActivity >/dev/null 2>&1

echo ""
echo "-> Nhap Ten Wi-Fi nha ban (SSID):"
read HOME_SSID </dev/tty 2>/dev/null || read HOME_SSID
echo "-> Nhap Mat Khau Wi-Fi nha ban:"
read HOME_PASS </dev/tty 2>/dev/null || read HOME_PASS

if [ -n "$HOME_SSID" ]; then
    echo "[*] Dang gui thong tin Wi-Fi $HOME_SSID toi loa..."
    adb -s 192.168.43.1:5555 shell cmd wifi connect-network "$HOME_SSID" wpa2 "$HOME_PASS" >/dev/null 2>&1
    adb -s 192.168.43.1:5555 shell am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "$HOME_SSID" --es password "$HOME_PASS" >/dev/null 2>&1
    echo ""
    echo "==================================================================="
    echo "  🎉 [HOÀN TẤT] ĐÃ CÀI XONG WEB CONTROLLER!"
    echo "  Loa Phicomm R1 dang ket noi vao Wi-Fi nha ban ($HOME_SSID)."
    echo "  Hay ket noi dien thoai lai Wi-Fi nha ban va truy cap:"
    echo "  http://phicomm.local:8080"
    echo "==================================================================="
fi
