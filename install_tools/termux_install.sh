#!/bin/sh
# Termux / iSH Shell script - install Web Controller APK & set Wi-Fi for Phicomm R1

echo "==================================================================="
echo "  CAI DAT WEB CONTROLLER LOA PHICOMM R1 (TERMUX / ISH)"
echo "==================================================================="
echo ""

# ================================================================
# BUOC 1: Kiem tra va cai dat adb & curl
# ================================================================
if ! command -v adb > /dev/null 2>&1; then
    echo "[1/4] Dang cai dat adb & curl..."
    if command -v pkg > /dev/null 2>&1; then
        # Termux (Android)
        pkg install -y android-tools curl
    elif command -v apk > /dev/null 2>&1; then
        # Alpine Linux (iSH, Docker, etc.) - update cache truoc
        apk update > /dev/null 2>&1
        apk add --no-cache android-tools curl
    else
        echo "[ERROR] Khong xac dinh duoc package manager (pkg/apk). Cai adb thu cong."
        exit 1
    fi
    # Kiem tra adb da cai xong chua
    if ! command -v adb > /dev/null 2>&1; then
        echo "[ERROR] Cai dat adb that bai! Kiem tra ket noi mang hoac cai thu cong:"
        echo "  Termux: pkg install android-tools"
        echo "  Alpine: apk add android-tools"
        exit 1
    fi
else
    echo "[1/4] adb da co san: $(adb version 2>/dev/null | head -n 1)"
fi

# Kiem tra curl hoac wget
if ! command -v curl > /dev/null 2>&1 && ! command -v wget > /dev/null 2>&1; then
    echo "[ERROR] Khong co curl hay wget. Cai dat thu cong."
    exit 1
fi

# ================================================================
# BUOC 2: Tai PhicommGemini.apk
# ================================================================
RAW_APK_URL="https://raw.githubusercontent.com/vinaheybird/phicomm-control/main/install_tools/PhicommGemini.apk"
RELEASE_APK_URL="https://github.com/vinaheybird/phicomm-control/releases/download/v1.0.0/PhicommGemini.apk"

if [ ! -f "PhicommGemini.apk" ]; then
    echo "[2/4] Dang tai PhicommGemini.apk tu GitHub..."
    curl -sSL -o PhicommGemini.apk "$RAW_APK_URL" 2>/dev/null \
        || wget -q -O PhicommGemini.apk "$RAW_APK_URL" 2>/dev/null \
        || curl -sSL -o PhicommGemini.apk "$RELEASE_APK_URL" 2>/dev/null
fi

if [ -f "PhicommGemini.apk" ] && [ -s "PhicommGemini.apk" ]; then
    echo "[OK] Da co file APK ($(du -k PhicommGemini.apk | cut -f1)KB)"
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
adb -s 192.168.43.1:5555 shell pm uninstall com.phicomm.gemini > /dev/null 2>&1

# pm install lam ADB dong ket noi (error: closed) - BINH THUONG tren Android 5.1
echo "[*] Dang cai APK tren loa (co the mat ket noi ADB 20-30s)..."
adb -s 192.168.43.1:5555 shell pm install -r -d /data/local/tmp/PhicommGemini.apk

# Cho loa hoi phuc sau pm install
echo "[*] Cho loa hoi phuc sau khi cai APK..."
sleep 8
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
        echo "[OK] ADB da ket noi lai!"
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

# ================================================================
# BUOC 4: Vo hieu hoa app rac & khoi chay dich vu
# ================================================================
echo ""
echo "[4/4] Dang vo hieu hoa cac ung dung rac & AI mac dinh cua Phicomm..."

# Vo hieu hoa (pm hide) tat ca ung dung mac dinh va tro ly AI cua Phicomm
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.player > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.device > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.airskill > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.otaservice > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.setup > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.voice > /dev/null 2>&1

echo "[*] Khoi chay dich vu Web Controller tren loa..."
adb -s 192.168.43.1:5555 shell "svc wifi enable" > /dev/null 2>&1

# 1. Mo MainActivity (se tu khoi chay PhicommGeminiService)
adb -s 192.168.43.1:5555 shell am start -n com.phicomm.gemini/.MainActivity > /dev/null 2>&1
sleep 2

# 2. Khoi chay Service theo ComponentName va Action intent-filter
adb -s 192.168.43.1:5555 shell am startservice -n com.phicomm.gemini/.PhicommGeminiService > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell am startservice -a com.phicomm.gemini.START_SERVICE > /dev/null 2>&1
sleep 1

# ================================================================
# HOAN TAT
# ================================================================
echo ""
echo "==================================================================="
echo "  [HOAN TAT CAI DAT!]"
echo "-------------------------------------------------------------------"
echo ""
echo "  App PhicommGemini da duoc cai dat va khoi chay tren loa."
echo "  Web server dang chay tai: http://192.168.43.1:8080"
echo ""
echo "  BUOC TIEP THEO - KET NOI WI-FI NHA:"
echo ""
echo "  1. Giu nguyen ket noi Wi-Fi dien thoai vao loa (Phicomm_R1_xxxx)"
echo ""
echo "  2. Mo trinh duyet tren dien thoai, truy cap:"
echo "     >>> http://192.168.43.1:8080 <<<"
echo ""
echo "  3. Trang 'Ket Noi WiFi Nha' se hien ra tu dong."
echo "     Nhap ten va mat khau WiFi nha ban roi nhan 'Ket Noi WiFi'."
echo ""
echo "  4. Sau khi nhan nut Ket Noi:"
echo "     - Cho 15-30 giay de loa ket noi vao WiFi nha"
echo "     - Điện thoại se tu ngat ket noi khoi Wi-Fi loa"
echo "     - Ket noi dien thoai vao WiFi nha ban, truy cap:"
echo "         http://phicomm.local:8080"
echo "         (Hoac tim IP loa trong router nha ban)"
echo ""
echo "==================================================================="
echo ""
echo "Nhan [ENTER] de ket thuc..."
read FINISH < /dev/tty 2>/dev/null || read FINISH
