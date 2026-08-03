#!/bin/sh
# Termux / iSH Shell script - install Web Controller APK & set Wi-Fi for Phicomm R1

echo "==================================================================="
echo "  CAI DAT WEB CONTROLLER LOA PHICOMM R1 (TERMUX / ISH)"
echo "==================================================================="
echo ""

# ================================================================
# BUOC 1: Kiem tra va cai dat adb & wget
# ================================================================
if ! command -v adb > /dev/null 2>&1 || ! command -v wget > /dev/null 2>&1; then
    echo "[1/4] Dang cai dat adb & wget..."
    if command -v pkg > /dev/null 2>&1; then
        # Termux (Android)
        pkg install -y android-tools wget curl
    elif command -v apk > /dev/null 2>&1; then
        # Alpine Linux (iSH, Docker, etc.)
        apk update > /dev/null 2>&1
        apk add --no-cache android-tools wget curl
    else
        echo "[ERROR] Khong xac dinh duoc package manager (pkg/apk). Cai adb thu cong."
        exit 1
    fi
    # Kiem tra adb da cai xong chua
    if ! command -v adb > /dev/null 2>&1; then
        echo "[ERROR] Cai dat adb that bai! Kiem tra ket noi mang hoac cai thu cong:"
        echo "  Termux: pkg install android-tools"
        echo "  iSH: apk add android-tools"
        exit 1
    fi
else
    echo "[1/4] adb da co san: $(adb version 2>/dev/null | head -n 1)"
fi

# ================================================================
# BUOC 2: Tai PhicommGemini.apk va adb-join-wifi.apk tu GitHub
# ================================================================
TS=$(date +%s 2>/dev/null || echo "1")
RAW_APK_URL="https://raw.githubusercontent.com/vinaheybird/phicomm-control/main/install_tools/PhicommGemini.apk?t=$TS"
ADB_JOIN_WIFI_URL="https://github.com/steinwurf/adb-join-wifi/releases/download/1.0.1/adb-join-wifi.apk"

rm -f PhicommGemini.apk adb-join-wifi.apk 2>/dev/null

echo "[2/4] Dang tai PhicommGemini.apk va adb-join-wifi.apk tu GitHub..."
wget -q --no-check-certificate -O PhicommGemini.apk "$RAW_APK_URL" 2>/dev/null \
    || curl -sSL -o PhicommGemini.apk "$RAW_APK_URL" 2>/dev/null

wget -q --no-check-certificate -O adb-join-wifi.apk "$ADB_JOIN_WIFI_URL" 2>/dev/null \
    || curl -sSL -o adb-join-wifi.apk "$ADB_JOIN_WIFI_URL" 2>/dev/null

if [ -f "PhicommGemini.apk" ] && [ -s "PhicommGemini.apk" ]; then
    echo "[OK] Da tai xong APK PhicommGemini ($(du -k PhicommGemini.apk | cut -f1)KB)"
else
    echo "[ERROR] Khong tai duoc PhicommGemini.apk! Kiem tra ket noi Internet/4G."
    exit 1
fi

# ================================================================
# BUOC 3: Ket noi ADB toi loa Phicomm R1 va cai dat cac APK
# ================================================================
echo ""
echo "-------------------------------------------------------------------"
echo "BUOC TIEP THEO: KET NOI VAO WI-FI CUA LOA PHICOMM R1"
echo "-------------------------------------------------------------------"
echo "1. Vao Cai dat Wi-Fi tren dien thoai."
echo "2. Ket noi vao Wi-Fi phat ra tu loa (Ten: Phicomm R1 / Phicomm_R1_xxxx)."
echo "3. Quay lai day va nhan [ENTER] de tiep tuc..."
echo "-------------------------------------------------------------------"
read DUMMY < /dev/tty 2>/dev/null || read DUMMY

echo ""
echo "[3/4] Dang ket noi ADB toi loa Phicomm R1 (192.168.43.1:5555)..."

adb start-server > /dev/null 2>&1
adb disconnect > /dev/null 2>&1

adb_connect() {
    local RETRY=1
    local MAX_RETRY=8
    while [ "$RETRY" -le "$MAX_RETRY" ]; do
        echo "[*] Thu ket noi ADB (Lan $RETRY/$MAX_RETRY)..."
        adb connect 192.168.43.1:5555 > /dev/null 2>&1
        sleep 2
        DEV=$(adb devices 2>/dev/null | grep "192.168.43.1:5555")
        if echo "$DEV" | grep -q "device$"; then
            echo "[OK] Ket noi ADB thanh cong!"
            return 0
        fi
        RETRY=$((RETRY + 1))
    done
    return 1
}

if ! adb_connect; then
    echo "[ERROR] Khong the ket noi toi loa R1 sau 8 lan thu."
    echo "[!] Kiem tra:"
    echo "    - Dien thoai da ket noi Wi-Fi 'Phicomm_R1_xxxx' chua?"
    echo "    - Loa co dang o che do AP (nhan nut reset ~6 giay) khong?"
    exit 1
fi

echo ""
echo "[*] Dang nap cac APK len loa R1..."
adb -s 192.168.43.1:5555 push PhicommGemini.apk /data/local/tmp/PhicommGemini.apk > /dev/null 2>&1
if [ -f "adb-join-wifi.apk" ]; then
    adb -s 192.168.43.1:5555 push adb-join-wifi.apk /data/local/tmp/adb-join-wifi.apk > /dev/null 2>&1
fi

adb -s 192.168.43.1:5555 shell pm uninstall com.phicomm.gemini > /dev/null 2>&1

echo "[*] Dang cai dat PhicommGemini.apk..."
adb -s 192.168.43.1:5555 shell pm install -r -d /data/local/tmp/PhicommGemini.apk

if [ -f "adb-join-wifi.apk" ]; then
    echo "[*] Dang cai dat adb-join-wifi.apk..."
    adb -s 192.168.43.1:5555 shell pm install -r -d /data/local/tmp/adb-join-wifi.apk > /dev/null 2>&1
fi

echo "[*] Cho loa hoi phuc sau khi cai APK..."
sleep 8
adb disconnect > /dev/null 2>&1
sleep 2

RETRY=1
MAX_RETRY=10
ADB_OK=0
while [ "$RETRY" -le "$MAX_RETRY" ]; do
    echo "[*] Thu ket noi lai ADB (Lan $RETRY/$MAX_RETRY)..."
    adb connect 192.168.43.1:5555 > /dev/null 2>&1
    sleep 3
    DEV2=$(adb devices 2>/dev/null | grep "192.168.43.1:5555")
    if echo "$DEV2" | grep -q "device$"; then
        echo "[OK] ADB da ket noi lai!"
        ADB_OK=1
        break
    fi
    RETRY=$((RETRY + 1))
done

if [ "$ADB_OK" -eq 0 ]; then
    echo "[ERROR] Khong ket noi lai duoc ADB sau khi cai APK."
    exit 1
fi

# ================================================================
# BUOC 4: Nhap thong tin Wi-Fi & gui Intent ket noi cho loa R1
# ================================================================
echo ""
echo "-------------------------------------------------------------------"
echo "  THIET LAP WI-FI NHA CHO LOA PHICOMM R1"
echo "-------------------------------------------------------------------"

WIFI_SSID="$1"
WIFI_PASS="$2"

if [ -z "$WIFI_SSID" ]; then
    printf "Nhap Tên Wi-Fi (SSID) nha ban: "
    read WIFI_SSID < /dev/tty 2>/dev/null || read WIFI_SSID
fi

if [ -n "$WIFI_SSID" ] && [ -z "$WIFI_PASS" ]; then
    printf "Nhap Mat Khau Wi-Fi nha ban (de trong neu la mang Open): "
    read WIFI_PASS < /dev/tty 2>/dev/null || read WIFI_PASS
fi

echo "[*] Bật Wi-Fi trên loa..."
adb -s 192.168.43.1:5555 shell "svc wifi enable" > /dev/null 2>&1

if [ -n "$WIFI_SSID" ]; then
    echo "[*] [Cách 1] Gui Intent qua adb-join-wifi..."
    adb -s 192.168.43.1:5555 shell "am start -n com.steinwurf.adbjoinwifi/.MainActivity --es ssid '$WIFI_SSID' --es password_type WPA --es password '$WIFI_PASS'" > /dev/null 2>&1
    sleep 1

    echo "[*] [Cách 2] Gui Intent qua PhicommGemini..."
    adb -s 192.168.43.1:5555 shell "am start -n com.phicomm.gemini/.MainActivity --es ssid '$WIFI_SSID' --es password '$WIFI_PASS'" > /dev/null 2>&1
    adb -s 192.168.43.1:5555 shell "am broadcast -a com.phicomm.gemini.SET_WIFI --es ssid '$WIFI_SSID' --es password '$WIFI_PASS'" > /dev/null 2>&1

    echo "[*] [Cách 3] Goi Onboarding Service ubus..."
    adb -s 192.168.43.1:5555 shell "ubus call onboarding connect '{\"ssid\":\"$WIFI_SSID\", \"password\":\"$WIFI_PASS\"}'" > /dev/null 2>&1

    echo "[*] [Cách 4] Ghi wpa_supplicant.conf & restart Wi-Fi Client mode..."
    adb -s 192.168.43.1:5555 shell "su 0 sh -c '
cat << EOF > /data/misc/wifi/wpa_supplicant.conf
ctrl_interface=DIR=/data/misc/wifi/sockets GROUP=wifi
update_config=1

network={
    ssid=\"$WIFI_SSID\"
    psk=\"$WIFI_PASS\"
    key_mgmt=WPA-PSK
    priority=100
}
EOF
chown system:wifi /data/misc/wifi/wpa_supplicant.conf 2>/dev/null
chmod 660 /data/misc/wifi/wpa_supplicant.conf 2>/dev/null
svc wifi disable
sleep 2
svc wifi enable
'" > /dev/null 2>&1
else
    adb -s 192.168.43.1:5555 shell "am start -n com.phicomm.gemini/.MainActivity" > /dev/null 2>&1
fi
sleep 2

# ================================================================
# HOAN TAT
# ================================================================
echo ""
echo "==================================================================="
echo "  [HOAN TAT CAI DAT!]"
echo "-------------------------------------------------------------------"
echo ""
if [ -n "$WIFI_SSID" ]; then
    echo "  ✅ Da gui lenh ket noi Wi-Fi '$WIFI_SSID' toi loa Phicomm R1."
    echo "  - Loa dang gia nhap Wi-Fi nha ban (mat khoảng 15-30 giây)."
    echo "  - Vui long chuyen Wi-Fi dien thoai/may tinh sang Wi-Fi '$WIFI_SSID'."
    echo "  - Mo trinh duyet truy cap: http://phicomm.local:8080"
    echo "    (hoac kiem tra IP moi cua loa trong Router nha ban)."
else
    echo "  App PhicommGemini da duoc cai dat va khoi chay tren loa."
    echo "  Web server dang chay tai: http://192.168.43.1:8080"
    echo "  Ban co the truy cap link tren de nhap Wi-Fi nha."
fi
echo ""
echo "==================================================================="
echo ""
echo "Nhan [ENTER] de ket thuc..."
read FINISH < /dev/tty 2>/dev/null || read FINISH

