#!/bin/sh
# Termux / iSH Shell script to install Web Controller APK & provision Phicomm R1 Wi-Fi
echo "==================================================================="
echo "  CAI DAT WEB CONTROLLER LOA PHICOMM R1 QUA DIEN THOAI (TERMUX / ISH)"
echo "==================================================================="
echo ""

# 1. Kiem tra va cai dat android-tools & curl
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
    echo "[OK] Da tai xong file APK va cong cu cai dat!"
else
    echo "[ERROR] Khong tai duoc file PhicommGemini.apk! Vui long kiem tra ket noi Internet/4G."
    exit 1
fi

echo ""
echo "-------------------------------------------------------------------"
echo "BUOC TIEP THEO: KET NOI VAO WI-FI CUA LOA PHICOMM R1"
echo "-------------------------------------------------------------------"
echo "1. Vao Cai dat Wi-Fi tren dien thoai."
echo "2. Ket noi vao Wi-Fi phat ra tu loa (Ten: Phicomm R1 hoac Phicomm_R1_xxxx)."
echo "3. Quay lai day va nhan [ENTER] de tiep tuc cai dat..."
echo "-------------------------------------------------------------------"
read DUMMY </dev/tty 2>/dev/null || read DUMMY

echo ""
echo "[3/4] Dang ket noi ADB toi loa Phicomm R1 (192.168.43.1:5555)..."

adb start-server >/dev/null 2>&1
adb disconnect >/dev/null 2>&1

CONNECTED=0
RETRY=1

while [ $RETRY -le 6 ]; do
    echo "[*] Thu ket noi ADB toi loa (Lan $RETRY/6)..."
    adb connect 192.168.43.1:5555 >/dev/null 2>&1
    sleep 2
    DEV_STATUS=$(adb devices 2>/dev/null | grep "192.168.43.1:5555")
    if echo "$DEV_STATUS" | grep -qE "device|connected"; then
        CONNECTED=1
        echo "[OK] Ket noi ADB thanh cong!"
        break
    fi
    RETRY=$((RETRY + 1))
done

if [ $CONNECTED -eq 0 ]; then
    echo "[ERROR] Khong the ket noi toi loa R1 qua 192.168.43.1:5555."
    echo "        Vui long kiem tra dien thoai da ket noi dung Wi-Fi Phicomm R1 chua va thu lai!"
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
echo "==================================================================="
echo " CAU HINH WI-FI NHA DE LOA PHICOMM R1 KET NOI VAO MANG"
echo "==================================================================="
echo ""

HOME_SSID=""
while [ -z "$HOME_SSID" ]; do
    printf "-> Nhap TEN Wi-Fi nha ban (SSID): "
    read HOME_SSID </dev/tty 2>/dev/null || read HOME_SSID
    if [ -z "$HOME_SSID" ]; then
        echo "[!] Ten Wi-Fi khong duoc de trong. Vui long nhap lai!"
    fi
done

printf "-> Nhap MAT KHAU Wi-Fi (Bam ENTER neu khong co mat khau): "
read HOME_PASS </dev/tty 2>/dev/null || read HOME_PASS

echo ""
echo "[*] Dang thiet lap Wi-Fi '$HOME_SSID' tren loa Phicomm R1..."

# 1. Dung wpa_cli de nhan Wi-Fi truc tiep tren Android 5.1/7.0 cua R1
adb -s 192.168.43.1:5555 shell "wpa_cli -i wlan0 remove_network all" >/dev/null 2>&1
NID=$(adb -s 192.168.43.1:5555 shell "wpa_cli -i wlan0 add_network" 2>/dev/null | tr -d '\r\n')
if [ -n "$NID" ]; then
    adb -s 192.168.43.1:5555 shell "wpa_cli -i wlan0 set_network $NID ssid '\"$HOME_SSID\"'" >/dev/null 2>&1
    if [ -n "$HOME_PASS" ]; then
        adb -s 192.168.43.1:5555 shell "wpa_cli -i wlan0 set_network $NID psk '\"$HOME_PASS\"'" >/dev/null 2>&1
    else
        adb -s 192.168.43.1:5555 shell "wpa_cli -i wlan0 set_network $NID key_mgmt NONE" >/dev/null 2>&1
    fi
    adb -s 192.168.43.1:5555 shell "wpa_cli -i wlan0 enable_network $NID" >/dev/null 2>&1
    adb -s 192.168.43.1:5555 shell "wpa_cli -i wlan0 save_config" >/dev/null 2>&1
    adb -s 192.168.43.1:5555 shell "wpa_cli -i wlan0 select_network $NID" >/dev/null 2>&1
fi

# 2. Gui broadcast intent sang ung dung Phicomm R1
adb -s 192.168.43.1:5555 shell am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "$HOME_SSID" --es password "$HOME_PASS" >/dev/null 2>&1

echo ""
echo "==================================================================="
echo "  [HOAN TAT CAI DAT WEB CONTROLLER!]"
echo "-------------------------------------------------------------------"
echo "  1. Loa Phicomm R1 dang tu ket noi vao Wi-Fi nha ban ($HOME_SSID)."
echo "  2. Ket noi lai dien thoai vao Wi-Fi nha ban ($HOME_SSID)."
echo "  3. Mo trinh duyiet web truy cap dia chi:"
echo "     http://phicomm.local:8080"
echo "==================================================================="
echo ""
echo "Nhan [ENTER] de ket thuc..."
read FINISH </dev/tty 2>/dev/null || read FINISH
