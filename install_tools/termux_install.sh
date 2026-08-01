#!/bin/sh
# Termux / iSH Shell script - install Web Controller APK & set Wi-Fi for Phicomm R1
echo "==================================================================="
echo "  CAI DAT WEB CONTROLLER LOA PHICOMM R1 (TERMUX / ISH)"
echo "==================================================================="
echo ""

# 1. Kiem tra va cai dat adb & curl
if ! command -v adb >/dev/null 2>&1; then
    echo "[1/4] Dang cai dat adb & curl..."
    if command -v pkg >/dev/null 2>&1; then
        pkg install -y android-tools curl
    elif command -v apk >/dev/null 2>&1; then
        apk add --no-cache android-tools curl
    fi
fi

APK_URL="https://github.com/vinaheybird/phicomm-control/releases/download/v1.0.0/PhicommGemini.apk"

if [ ! -f "PhicommGemini.apk" ]; then
    echo "[2/4] Dang tai PhicommGemini.apk tu GitHub..."
    curl -sSL -o PhicommGemini.apk "$APK_URL" 2>/dev/null || wget -q -O PhicommGemini.apk "$APK_URL" 2>/dev/null
fi

if [ -f "PhicommGemini.apk" ]; then
    echo "[OK] Da tai xong file APK!"
else
    echo "[ERROR] Khong tai duoc PhicommGemini.apk! Kiem tra ket noi Internet/4G."
    exit 1
fi

echo ""
echo "-------------------------------------------------------------------"
echo "BUOC TIEP THEO: KET NOI VAO WI-FI CUA LOA PHICOMM R1"
echo "-------------------------------------------------------------------"
echo "1. Vao Cai dat Wi-Fi tren dien thoai."
echo "2. Ket noi vao Wi-Fi phat ra tu loa (Ten: Phicomm R1 / Phicomm_R1_xxxx)."
echo "3. Quay lai day va nhan [ENTER] de tiep tuc..."
echo "-------------------------------------------------------------------"
read DUMMY </dev/tty 2>/dev/null || read DUMMY

echo ""
echo "[3/4] Dang ket noi ADB toi loa Phicomm R1 (192.168.43.1:5555)..."

adb start-server >/dev/null 2>&1
adb disconnect >/dev/null 2>&1

CONNECTED=0
RETRY=1
while [ "$RETRY" -le 6 ]; do
    echo "[*] Thu ket noi ADB (Lan $RETRY/6)..."
    adb connect 192.168.43.1:5555 >/dev/null 2>&1
    sleep 2
    DEV=$(adb devices 2>/dev/null | grep "192.168.43.1:5555")
    if echo "$DEV" | grep -q "device"; then
        CONNECTED=1
        echo "[OK] Ket noi ADB thanh cong!"
        break
    fi
    RETRY=$((RETRY + 1))
done

if [ "$CONNECTED" -eq 0 ]; then
    echo "[ERROR] Khong the ket noi toi loa R1. Kiem tra da ket noi Wi-Fi Phicomm R1 chua!"
    exit 1
fi

echo ""
echo "[4/4] Dang vo hieu hoa app rac va cai APK..."
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.player >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.device >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.airskill >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.otaservice >/dev/null 2>&1

echo "[*] Dang nap PhicommGemini.apk len loa..."
adb -s 192.168.43.1:5555 push PhicommGemini.apk /data/local/tmp/PhicommGemini.apk
# pm install lam ADB dong ket noi (error: closed) - BINH THUONG
adb -s 192.168.43.1:5555 shell pm install -r /data/local/tmp/PhicommGemini.apk

echo "[*] Doi ADB phuc hoi sau khi cai APK..."
sleep 5
adb disconnect >/dev/null 2>&1
sleep 2
adb connect 192.168.43.1:5555 >/dev/null 2>&1
sleep 3

DEV2=$(adb devices 2>/dev/null | grep "192.168.43.1:5555")
if echo "$DEV2" | grep -q "device"; then
    echo "[OK] ADB da ket noi lai!"
else
    echo "[!] ADB mat ket noi, thu lan 2..."
    adb connect 192.168.43.1:5555 >/dev/null 2>&1
    sleep 4
    DEV3=$(adb devices 2>/dev/null | grep "192.168.43.1:5555")
    if echo "$DEV3" | grep -q "device"; then
        echo "[OK] ADB ket noi lai thanh cong!"
    else
        echo "[!] ADB van mat ket noi - buoc tiep theo co the bi loi!"
    fi
fi

echo "[*] Cap quyen va khoi chay dich vu..."
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH_ADMIN >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell am startservice -n com.phicomm.gemini/.service.PhicommGeminiService >/dev/null 2>&1
sleep 2
adb -s 192.168.43.1:5555 shell am start -n com.phicomm.gemini/.MainActivity >/dev/null 2>&1

echo ""
echo "==================================================================="
echo "  CAU HINH WI-FI NHA CHO LOA PHICOMM R1"
echo "==================================================================="
echo ""

HOME_SSID=""
while [ -z "$HOME_SSID" ]; do
    printf "-> Nhap TEN Wi-Fi nha ban (SSID): "
    read HOME_SSID </dev/tty 2>/dev/null || read HOME_SSID
    if [ -z "$HOME_SSID" ]; then
        echo "[!] Ten Wi-Fi khong duoc de trong!"
    fi
done

printf "-> Nhap MAT KHAU Wi-Fi (bam ENTER neu khong co): "
read HOME_PASS </dev/tty 2>/dev/null || read HOME_PASS

echo ""
echo "[*] Dang thiet lap Wi-Fi cho loa: $HOME_SSID"
adb connect 192.168.43.1:5555 >/dev/null 2>&1
sleep 1

# Tao script cai dat Wi-Fi chay truc tiep tren loa (Phuong phap chuan cua Xiaozhi/R1 Community)
# Dung cat << 'EOF' (Single quoted EOF) de khong parse bat ky quote/variable nao tren iSH/Termux
cat << 'EOF' > ./set_r1_wifi.sh
#!/system/bin/sh
SSID="$1"
PASS="$2"

CONF="/data/misc/wifi/wpa_supplicant.conf"
if [ -f "$CONF" ]; then
    echo "" >> "$CONF"
    echo "network={" >> "$CONF"
    echo "    ssid=\"$SSID\"" >> "$CONF"
    if [ -n "$PASS" ]; then
        echo "    psk=\"$PASS\"" >> "$CONF"
        echo "    key_mgmt=WPA-PSK" >> "$CONF"
    else
        echo "    key_mgmt=NONE" >> "$CONF"
    fi
    echo "    priority=10" >> "$CONF"
    echo "}" >> "$CONF"
    chmod 660 "$CONF"
    chown system:wifi "$CONF"
fi

wpa_cli -i wlan0 reconfigure >/dev/null 2>&1
wpa_cli -i wlan0 remove_network all >/dev/null 2>&1
NID=$(wpa_cli -i wlan0 add_network 2>/dev/null | tr -cd '0-9' | cut -c1)
if [ -n "$NID" ]; then
    wpa_cli -i wlan0 set_network $NID ssid "\"$SSID\"" >/dev/null 2>&1
    if [ -n "$PASS" ]; then
        wpa_cli -i wlan0 set_network $NID psk "\"$PASS\"" >/dev/null 2>&1
    else
        wpa_cli -i wlan0 set_network $NID key_mgmt NONE >/dev/null 2>&1
    fi
    wpa_cli -i wlan0 enable_network $NID >/dev/null 2>&1
    wpa_cli -i wlan0 save_config >/dev/null 2>&1
    wpa_cli -i wlan0 select_network $NID >/dev/null 2>&1
fi

svc wifi disable >/dev/null 2>&1
sleep 1
svc wifi enable >/dev/null 2>&1

am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "$SSID" --es password "$PASS" >/dev/null 2>&1
am broadcast -a com.phicomm.gemini.SET_WIFI --es ssid "$SSID" --es password "$PASS" >/dev/null 2>&1
EOF

# Push script len loa va thuc thi qua ADB
if adb -s 192.168.43.1:5555 push ./set_r1_wifi.sh /data/local/tmp/set_r1_wifi.sh >/dev/null 2>&1; then
    echo "[+] Da gui cau hinh Wi-Fi sang loa Phicomm R1..."
    adb -s 192.168.43.1:5555 shell "chmod 755 /data/local/tmp/set_r1_wifi.sh" >/dev/null 2>&1
    adb -s 192.168.43.1:5555 shell "/data/local/tmp/set_r1_wifi.sh \"$HOME_SSID\" \"$HOME_PASS\" &" >/dev/null 2>&1 || true
    sleep 2
fi

# Fallback: GUI cmd wifi & broadcast tu ADB ngoai
if [ -n "$HOME_PASS" ]; then
    adb -s 192.168.43.1:5555 shell "cmd wifi connect-network \"$HOME_SSID\" wpa2 \"$HOME_PASS\"" >/dev/null 2>&1 || true
else
    adb -s 192.168.43.1:5555 shell "cmd wifi connect-network \"$HOME_SSID\" open" >/dev/null 2>&1 || true
fi

echo "[*] Doi loa ket noi Wi-Fi (15s)..."
sleep 15

echo ""
echo "==================================================================="
echo "  [HOAN TAT!]"
echo "-------------------------------------------------------------------"
echo "  Loa dang ket noi vao Wi-Fi: $HOME_SSID"
echo ""
echo "  BUOC TIEP THEO:"
echo "  1. Ket noi dien thoai vao Wi-Fi nha ban ($HOME_SSID)."
echo "  2. Mo trinh duyet, thu:"
echo "     http://phicomm.local:8080"
echo "     (Neu loi: vao router xem IP loa, truy cap http://[IP]:8080)"
echo "==================================================================="
echo ""
echo "Nhan [ENTER] de ket thuc..."
read FINISH </dev/tty 2>/dev/null || read FINISH
