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
    echo "[1/5] Dang cai dat adb & curl..."
    if command -v pkg > /dev/null 2>&1; then
        # Termux (Android)
        pkg install -y android-tools curl
    elif command -v apk > /dev/null 2>&1; then
        # Alpine Linux (iSH, Docker, etc.) - can update cache truoc
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
    echo "[1/5] adb da co san: $(adb version 2>/dev/null | head -n 1)"
fi

# Kiem tra curl hoac wget
if ! command -v curl > /dev/null 2>&1 && ! command -v wget > /dev/null 2>&1; then
    echo "[ERROR] Khong co curl hay wget. Cai dat thu cong."
    exit 1
fi

# ================================================================
# BUOC 2: Tai PhicommGemini.apk
# ================================================================
APK_URL="https://github.com/vinaheybird/phicomm-control/releases/download/v1.0.0/PhicommGemini.apk"

if [ ! -f "PhicommGemini.apk" ]; then
    echo "[2/5] Dang tai PhicommGemini.apk tu GitHub..."
    curl -sSL -o PhicommGemini.apk "$APK_URL" 2>/dev/null \
        || wget -q -O PhicommGemini.apk "$APK_URL" 2>/dev/null
fi

if [ -f "PhicommGemini.apk" ] && [ -s "PhicommGemini.apk" ]; then
    echo "[OK] Da co file APK ($(du -k PhicommGemini.apk | cut -f1)KB)"
else
    echo "[ERROR] Khong tai duoc PhicommGemini.apk! Kiem tra ket noi Internet/4G."
    exit 1
fi

# ================================================================
# BUOC 3: Luon tai moi set_r1_wifi.sh tu GitHub
# Khong dung file local de tranh dung ban cu bi loi
# ================================================================
WIFI_SCRIPT_URL="https://raw.githubusercontent.com/vinaheybird/phicomm-control/main/install_tools/set_r1_wifi.sh"

echo "[3/5] Dang tai set_r1_wifi.sh tu GitHub (phien ban moi nhat)..."
curl -sSL -o set_r1_wifi.sh "$WIFI_SCRIPT_URL" 2>/dev/null \
    || wget -q -O set_r1_wifi.sh "$WIFI_SCRIPT_URL" 2>/dev/null

# Kiem tra file tai xong hop le chua
if [ ! -f "./set_r1_wifi.sh" ] || [ ! -s "./set_r1_wifi.sh" ]; then
    echo "[ERROR] Khong tai duoc set_r1_wifi.sh! Kiem tra ket noi mang."
    exit 1
else
    echo "[OK] Da co set_r1_wifi.sh ($(du -k ./set_r1_wifi.sh | cut -f1)KB)"
fi

# ================================================================
# BUOC 4: Ket noi ADB toi loa Phicomm R1
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
echo "[4/5] Dang ket noi ADB toi loa Phicomm R1 (192.168.43.1:5555)..."

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

# ================================================================
# BUOC 4b: Vo hieu hoa bloatware & Cai APK
# ================================================================
echo ""
echo "[4b/5] Dang vo hieu hoa app rac va cai APK..."
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.player > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.device > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.airskill > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.otaservice > /dev/null 2>&1

echo "[*] Dang nap PhicommGemini.apk len loa..."
adb -s 192.168.43.1:5555 push PhicommGemini.apk /data/local/tmp/PhicommGemini.apk
PUSH_RESULT=$?
if [ "$PUSH_RESULT" -ne 0 ]; then
    echo "[ERROR] Khong push duoc APK len loa (exit code: $PUSH_RESULT)."
    echo "[!] Kiem tra dung luong loa hoac ket noi ADB."
    exit 1
fi

# pm install lam ADB dong ket noi (error: closed) - BINH THUONG tren Android 5.1
echo "[*] Dang cai APK tren loa (co the mat ket noi ADB - binh thuong)..."
adb -s 192.168.43.1:5555 shell pm install -r /data/local/tmp/PhicommGemini.apk

# Cho loa hoi phuc sau pm install (Android 5.1 reset wpa_supplicant -> ADB mat ket noi 15-30s)
echo "[*] Cho loa hoi phuc sau khi cai APK (co the mat 20-30 giay)..."
sleep 8
adb disconnect > /dev/null 2>&1
sleep 2

# Retry ket noi ADB sau install - can nhieu thoi gian hon
RETRY=1
MAX_RETRY=10
ADB_OK=0
while [ "$RETRY" -le "$MAX_RETRY" ]; do
    echo "[*] Thu ket noi lai ADB sau install (Lan $RETRY/$MAX_RETRY)..."
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
    echo "[!] Loa co the da reboot. Vui long:"
    echo "    1. Ket noi lai Wi-Fi loa tren dien thoai"
    echo "    2. Chay lai script nay de cau hinh Wi-Fi"
    exit 1
fi

echo "[*] Cap quyen va khoi chay dich vu..."
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH_ADMIN > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.ACCESS_FINE_LOCATION > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell am startservice -n com.phicomm.gemini/.service.PhicommGeminiService > /dev/null 2>&1
sleep 2
adb -s 192.168.43.1:5555 shell am start -n com.phicomm.gemini/.MainActivity > /dev/null 2>&1

# ================================================================
# BUOC 5: Cau hinh Wi-Fi nha cho loa
# ================================================================
echo ""
echo "==================================================================="
echo "  CAU HINH WI-FI NHA CHO LOA PHICOMM R1"
echo "==================================================================="
echo ""

HOME_SSID=""
while [ -z "$HOME_SSID" ]; do
    printf "-> Nhap TEN Wi-Fi nha ban (SSID): "
    read HOME_SSID < /dev/tty 2>/dev/null || read HOME_SSID
    HOME_SSID=$(echo "$HOME_SSID" | tr -d '\r\n')
    if [ -z "$HOME_SSID" ]; then
        echo "[!] Ten Wi-Fi khong duoc de trong!"
    fi
done

printf "-> Nhap MAT KHAU Wi-Fi (bam ENTER neu khong co): "
read HOME_PASS < /dev/tty 2>/dev/null || read HOME_PASS
HOME_PASS=$(echo "$HOME_PASS" | tr -d '\r\n')

echo ""
echo "[5/5] Dang thiet lap Wi-Fi '$HOME_SSID' cho loa..."

# Kiem tra ADB con ket noi khong truoc khi push
adb connect 192.168.43.1:5555 > /dev/null 2>&1
sleep 1
DEV_CHECK=$(adb devices 2>/dev/null | grep "192.168.43.1:5555")
if ! echo "$DEV_CHECK" | grep -q "device$"; then
    echo "[ERROR] ADB mat ket noi truoc buoc cau hinh Wi-Fi."
    echo "[!] Ket noi lai Wi-Fi loa tren dien thoai roi chay lai script."
    exit 1
fi

# Ghi SSID va Password vao file text (sach khong co \r)
printf "%s\n" "$HOME_SSID" > ./wifi_info.txt
printf "%s\n" "$HOME_PASS" >> ./wifi_info.txt

# Push file thong tin WiFi len loa
adb -s 192.168.43.1:5555 push ./wifi_info.txt /data/local/tmp/wifi_info.txt > /dev/null 2>&1

# Push va chay script set_r1_wifi.sh tren loa
echo "[*] Dang nap script va thuc thi cau hinh Wi-Fi tren loa..."
adb -s 192.168.43.1:5555 push ./set_r1_wifi.sh /data/local/tmp/set_r1_wifi.sh > /dev/null 2>&1
adb -s 192.168.43.1:5555 shell chmod 755 /data/local/tmp/set_r1_wifi.sh > /dev/null 2>&1

# Chay dong bo de lay duoc log day du
adb -s 192.168.43.1:5555 shell /data/local/tmp/set_r1_wifi.sh
echo "[OK] Da thuc thi script Wi-Fi tren loa!"

# Lay log tu loa
echo ""
echo "[*] NHAT KY KET NOI WI-FI TRUC TIEP TU LOA PHICOMM R1:"
echo "-------------------------------------------------------------------"
adb -s 192.168.43.1:5555 shell cat /data/local/tmp/wifi_setup.log
echo "-------------------------------------------------------------------"

# ================================================================
# HOAN TAT
# ================================================================
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
echo ""
echo "  LUU Y: Neu log tren cho thay chua co IP, hay:"
echo "     - Cho them 30 giay de loa tu ket noi"
echo "     - Hoac reboot loa (rut dien 5 giay) roi kiem tra lai"
echo "==================================================================="
echo ""
echo "Nhan [ENTER] de ket thuc..."
read FINISH < /dev/tty 2>/dev/null || read FINISH
