#!/bin/sh
# Termux / iSH Shell script - install Web Controller APK & set Wi-Fi for Phicomm R1

echo "==================================================================="
echo "  CAI DAT WEB CONTROLLER LOA PHICOMM R1 (FULL LOG)"
echo "==================================================================="
echo ""

# ================================================================
# BUOC 1: Kiem tra va cai dat adb & wget
# ================================================================
if ! command -v adb > /dev/null 2>&1 || ! command -v wget > /dev/null 2>&1; then
    echo "[1/4] Dang cai dat adb & wget..."
    if command -v pkg > /dev/null 2>&1; then
        pkg install -y android-tools wget curl
    elif command -v apk > /dev/null 2>&1; then
        apk update > /dev/null 2>&1
        apk add --no-cache android-tools wget curl
    fi
else
    echo "[1/4] adb da co san: $(adb version 2>/dev/null | head -n 1)"
fi

# ================================================================
# BUOC 2: Tai PhicommGemini.apk moi nhat tu GitHub Raw
# ================================================================
TS=$(date +%s 2>/dev/null || echo "1")
RAW_APK_URL="https://raw.githubusercontent.com/vinaheybird/phicomm-control/main/install_tools/PhicommGemini.apk?t=$TS"

rm -f PhicommGemini.apk 2>/dev/null
echo "[2/4] Dang tai PhicommGemini.apk tu GitHub..."
wget -q --no-check-certificate -O PhicommGemini.apk "$RAW_APK_URL" 2>/dev/null \
    || curl -sSL -o PhicommGemini.apk "$RAW_APK_URL" 2>/dev/null

if [ -f "PhicommGemini.apk" ] && [ -s "PhicommGemini.apk" ]; then
    echo "[OK] Da tai xong APK ($(du -k PhicommGemini.apk | cut -f1)KB)"
else
    echo "[ERROR] Khong tai duoc PhicommGemini.apk! Kiem tra ket noi Internet."
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

adb start-server 2>&1
adb disconnect 2>&1

echo "[*] Gui lenh ket noi ADB..."
adb connect 192.168.43.1:5555

sleep 2
echo "[*] Danh sach thiet bi ADB phat hien:"
adb devices

echo ""
echo "[*] Dang push PhicommGemini.apk sang loa (/data/local/tmp/)..."
adb -s 192.168.43.1:5555 push PhicommGemini.apk /data/local/tmp/PhicommGemini.apk

echo ""
echo "[*] Dang go bo ban com.phicomm.gemini cu (neu co)..."
adb -s 192.168.43.1:5555 shell "/system/bin/pm uninstall com.phicomm.gemini"

echo ""
echo "[*] Dang cai dat PhicommGemini.apk qua /system/bin/pm install..."
adb -s 192.168.43.1:5555 shell "/system/bin/pm install -r -d -t /data/local/tmp/PhicommGemini.apk"

echo ""
echo "[*] Kiem tra duong dan package com.phicomm.gemini tren loa:"
adb -s 192.168.43.1:5555 shell "/system/bin/pm path com.phicomm.gemini"

# ================================================================
# BUOC 4: Nhap Wi-Fi & gui Intent ket noi
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
echo "[4/4] Dang vo hieu hoa cac ung dung rac mac dinh..."
adb -s 192.168.43.1:5555 shell "pm hide com.phicomm.speaker.player"
adb -s 192.168.43.1:5555 shell "pm hide com.phicomm.speaker.device"
adb -s 192.168.43.1:5555 shell "pm hide com.phicomm.speaker.airskill"
adb -s 192.168.43.1:5555 shell "pm hide com.phicomm.speaker.otaservice"
adb -s 192.168.43.1:5555 shell "pm hide com.phicomm.speaker.setup"
adb -s 192.168.43.1:5555 shell "pm hide com.phicomm.speaker.voice"

echo ""
echo "[*] Dang bat Wi-Fi client mode..."
adb -s 192.168.43.1:5555 shell "svc wifi enable"

if [ -n "$WIFI_SSID" ]; then
    echo ""
    echo "[*] Dang gui Intent mo MainActivity & noi Wi-Fi '$WIFI_SSID'..."
    adb -s 192.168.43.1:5555 shell "am start -n com.phicomm.gemini/.MainActivity --es ssid '$WIFI_SSID' --es password '$WIFI_PASS'"
    adb -s 192.168.43.1:5555 shell "am broadcast -a com.phicomm.gemini.SET_WIFI --es ssid '$WIFI_SSID' --es password '$WIFI_PASS'"

    echo ""
    echo "[*] Gui lenh ket noi Wi-Fi cho onboarding service Phicomm..."
    adb -s 192.168.43.1:5555 shell "ubus call onboarding connect '{\"ssid\":\"$WIFI_SSID\", \"password\":\"$WIFI_PASS\"}'"

    echo ""
    echo "[*] Gui lenh wpa_cli ket noi Wi-Fi '$WIFI_SSID'..."
    if [ -n "$WIFI_PASS" ]; then
        adb -s 192.168.43.1:5555 shell "su 0 sh -c '
NID=\$(wpa_cli -i wlan0 add_network)
wpa_cli -i wlan0 set_network \$NID ssid \"\\\"$WIFI_SSID\\\"\"
wpa_cli -i wlan0 set_network \$NID psk \"\\\"$WIFI_PASS\\\"\"
wpa_cli -i wlan0 enable_network \$NID
wpa_cli -i wlan0 select_network \$NID
wpa_cli -i wlan0 save_config
wpa_cli -i wlan0 reassociate
'"
    else
        adb -s 192.168.43.1:5555 shell "su 0 sh -c '
NID=\$(wpa_cli -i wlan0 add_network)
wpa_cli -i wlan0 set_network \$NID ssid \"\\\"$WIFI_SSID\\\"\"
wpa_cli -i wlan0 set_network \$NID key_mgmt NONE
wpa_cli -i wlan0 enable_network \$NID
wpa_cli -i wlan0 select_network \$NID
wpa_cli -i wlan0 save_config
wpa_cli -i wlan0 reassociate
'"
    fi
else
    adb -s 192.168.43.1:5555 shell "am start -n com.phicomm.gemini/.MainActivity"
fi

echo ""
echo "==================================================================="
echo "  [HOAN TAT CAI DAT!]"
echo "-------------------------------------------------------------------"
echo ""
if [ -n "$WIFI_SSID" ]; then
    echo "  ✅ Da gui lenh ket noi Wi-Fi '$WIFI_SSID' toi loa Phicomm R1."
    echo "  - Vui long chuyen Wi-Fi dien thoai/may tinh sang Wi-Fi '$WIFI_SSID'."
    echo "  - Mo trinh duyet truy cap: http://phicomm.local:8080"
    echo "    (hoac kiem tra IP moi cua loa trong Router nha ban)."
else
    echo "  App PhicommGemini da duoc cai dat va khoi chay tren loa."
    echo "  Web server dang chay tai: http://192.168.43.1:8080"
fi
echo ""
echo "==================================================================="
echo "Nhan [ENTER] de ket thuc..."
read FINISH < /dev/tty 2>/dev/null || read FINISH
