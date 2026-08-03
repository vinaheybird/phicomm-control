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
# BUOC 2: Tai PhicommGemini.apk moi nhat tu GitHub Raw
# ================================================================
TS=$(date +%s 2>/dev/null || echo "1")
RAW_APK_URL="https://raw.githubusercontent.com/vinaheybird/phicomm-control/main/install_tools/PhicommGemini.apk?t=$TS"

# Luon xoa file APK cu de dam bao tai ban moi nhat tu GitHub
rm -f PhicommGemini.apk 2>/dev/null

echo "[2/4] Dang tai phien ban PhicommGemini.apk moi nhat tu GitHub Raw..."
wget -q --no-check-certificate -O PhicommGemini.apk "$RAW_APK_URL" 2>/dev/null \
    || curl -sSL -o PhicommGemini.apk "$RAW_APK_URL" 2>/dev/null

if [ -f "PhicommGemini.apk" ] && [ -s "PhicommGemini.apk" ]; then
    echo "[OK] Da tai xong APK moi nhat ($(du -k PhicommGemini.apk | cut -f1)KB)"
else
    echo "[ERROR] Khong tai duoc PhicommGemini.apk! Kiem tra ket noi Internet/4G."
    exit 1
fi

# ================================================================
# BUOC 3: Ket noi ADB toi loa Phicomm R1 va cai dat APK
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

# Ham ket noi ADB co retry
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
echo "[*] Dang nap PhicommGemini.apk len loa..."
adb -s 192.168.43.1:5555 push PhicommGemini.apk /data/local/tmp/PhicommGemini.apk
PUSH_RESULT=$?
if [ "$PUSH_RESULT" -ne 0 ]; then
    echo "[ERROR] Khong push duoc APK len loa (exit code: $PUSH_RESULT)."
    echo "[!] Kiem tra dung luong loa hoac ket noi ADB."
    exit 1
fi

# Go bo ban cu (neu co) de Android 5.1 xoa cache PackageManager va reload AndroidManifest.xml moi
adb -s 192.168.43.1:5555 shell /system/bin/pm uninstall com.phicomm.gemini > /dev/null 2>&1

echo "[*] Dang cai dat APK PhicommGemini tren loa (Android 5.1 legacy pm)..."
# Chay pm install qua /system/bin/pm truc tiep tren file da push
adb -s 192.168.43.1:5555 shell "/system/bin/pm install -r -d /data/local/tmp/PhicommGemini.apk" > /dev/null 2>&1

# Cho loa xu ly va hoi phuc connection
echo "[*] Cho loa xu ly va khoi dong lai dich vu..."
sleep 5
adb disconnect > /dev/null 2>&1
sleep 2

# Retry ket noi ADB sau install
RETRY=1
MAX_RETRY=10
ADB_OK=0
while [ "$RETRY" -le "$MAX_RETRY" ]; do
    echo "[*] Thu ket noi lai ADB (Lan $RETRY/$MAX_RETRY)..."
    adb connect 192.168.43.1:5555 > /dev/null 2>&1
    sleep 3
    DEV2=$(adb devices 2>/dev/null | grep "192.168.43.1:5555")
    if echo "$DEV2" | grep -q "device$"; then
        echo "[OK] ADB da ket noi lai thanh cong!"
        ADB_OK=1
        break
    fi
    RETRY=$((RETRY + 1))
done

if [ "$ADB_OK" -eq 0 ]; then
    echo "[ERROR] Khong ket noi lai duoc ADB sau khi cai APK."
    echo "[!] Loa co the da reboot. Vui long ket noi lai Wi-Fi loa va chay lai script."
    exit 1
fi

# CRITICAL: Cho adbd tren loa on dinh socket (5 giay) truoc khi gui lenh shell
sleep 5

# XAC NHAN THUC TE APK DA DUOC CAI DAT CHUA (THU 5 LAN CO RECONNECT KHONG VO VAP)
CHECK_INSTALL=""
for i in 1 2 3 4 5; do
    echo "[*] Dang kiem tra xac nhan APK (Lan $i/5)..."
    CHECK_INSTALL=$(adb -s 192.168.43.1:5555 shell "/system/bin/pm path com.phicomm.gemini 2>/dev/null" | grep "package:")
    if [ -n "$CHECK_INSTALL" ]; then
        break
    fi
    sleep 3
    adb connect 192.168.43.1:5555 > /dev/null 2>&1
    sleep 2
done

# Phuong phap du phong: Neu pm install qua adb shell bi ngat, thu Root Direct Copy
if [ -z "$CHECK_INSTALL" ]; then
    echo "[!] 'pm install' ngat ket noi. Dang thu phuong phap Root Direct Copy..."
    adb -s 192.168.43.1:5555 shell "su 0 sh -c '
mkdir -p /data/app/com.phicomm.gemini-1
cp /data/local/tmp/PhicommGemini.apk /data/app/com.phicomm.gemini-1/base.apk
chmod 755 /data/app/com.phicomm.gemini-1
chmod 644 /data/app/com.phicomm.gemini-1/base.apk
chown -R system:system /data/app/com.phicomm.gemini-1
/system/bin/pm install -r -d /data/app/com.phicomm.gemini-1/base.apk
'" > /dev/null 2>&1
    sleep 5
    adb connect 192.168.43.1:5555 > /dev/null 2>&1
    sleep 3
    CHECK_INSTALL=$(adb -s 192.168.43.1:5555 shell "/system/bin/pm path com.phicomm.gemini 2>/dev/null" | grep "package:")
fi

if [ -n "$CHECK_INSTALL" ]; then
    echo "[OK] XAC NHAN: APK PhicommGemini da duoc cai dat thanh cong ($CHECK_INSTALL)!"
else
    echo "[ERROR] THAT BAI: Khong the cai dat APK PhicommGemini len loa!"
    echo "[!] Kiem tra dung luong bo nho loa (df -h) hoac khoi dong lai loa va thu lai."
    exit 1
fi

# ================================================================
# BUOC 4: Vo hieu hoa app rac & khoi chay dich vu
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

echo ""
echo "[4/4] Dang vo hieu hoa cac ung dung rac & AI mac dinh cua Phicomm..."

# Vo hieu hoa (pm hide) tat ca ung dung mac dinh va tro ly AI cua Phicomm
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.player > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.device > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.airskill > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.otaservice > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.setup > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.voice > /dev/null 2>&1

echo "[*] Bật Wi-Fi và khởi chạy dịch vụ Controller trên loa..."
adb -s 192.168.43.1:5555 shell "svc wifi enable" > /dev/null 2>&1

# 1. Mo MainActivity & gui Intent Wi-Fi (adb-join-wifi method + R1 Root wpa_supplicant + ubus method)
if [ -n "$WIFI_SSID" ]; then
    echo "[*] Dang gui Intent noi Wi-Fi '$WIFI_SSID' cho loa (trich xuat quotes)..."
    adb -s 192.168.43.1:5555 shell "am start -n com.phicomm.gemini/.MainActivity --es ssid '$WIFI_SSID' --es password '$WIFI_PASS'" > /dev/null 2>&1
    adb -s 192.168.43.1:5555 shell "am broadcast -a com.phicomm.gemini.SET_WIFI --es ssid '$WIFI_SSID' --es password '$WIFI_PASS'" > /dev/null 2>&1

    echo "[*] Thoi Onboarding Service mac dinh cua Phicomm R1 (ubus)..."
    adb -s 192.168.43.1:5555 shell "ubus call onboarding connect '{\"ssid\":\"$WIFI_SSID\", \"password\":\"$WIFI_PASS\"}'" > /dev/null 2>&1

    echo "[*] Dang thiet lap wpa_supplicant.conf va khoi dong lai Wi-Fi Client mode..."
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

