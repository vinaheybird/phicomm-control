#!/system/bin/sh
# Script chay BEN TRONG loa Phicomm R1 (Android 5.1 stripped ROM)
# Chi dung shell builtin - KHONG dung head/tr/sed/wc/awk

LOG="/data/local/tmp/wifi_setup.log"
rm -f "$LOG"
echo "=== LOG CAU HINH WI-FI PHICOMM R1 ===" > "$LOG"

# ---- Doc SSID / Password (dung read builtin + fd, khong dung head/sed) ----
SSID=""
PASS=""
if [ -f /data/local/tmp/wifi_info.txt ]; then
    exec 3< /data/local/tmp/wifi_info.txt
    read SSID <&3
    read PASS <&3
    exec 3<&-
else
    SSID="$1"
    PASS="$2"
fi

echo "[*] SSID: '$SSID'" >> "$LOG"

if [ -z "$SSID" ]; then
    echo "[ERROR] SSID rong! Thoat." >> "$LOG"
    exit 1
fi

# ================================================================
# DIAGNOSTIC: Tim vi tri wpa_supplicant.conf thuc su
# ================================================================
echo "" >> "$LOG"
echo "--- DIAGNOSTIC ---" >> "$LOG"
echo "[D] /data/misc thu muc:" >> "$LOG"
ls /data/misc/ >> "$LOG" 2>&1
echo "[D] /data thu muc:" >> "$LOG"
ls /data/ >> "$LOG" 2>&1
echo "[D] init.rc wpa_supplicant:" >> "$LOG"
grep -i "wpa_supplicant" /init.rc >> "$LOG" 2>&1 || echo "(khong tim thay trong /init.rc)" >> "$LOG"
echo "[D] /system/etc/wifi:" >> "$LOG"
ls /system/etc/wifi/ >> "$LOG" 2>&1 || echo "(khong co)" >> "$LOG"
echo "--- END DIAGNOSTIC ---" >> "$LOG"
echo "" >> "$LOG"

# ================================================================
# BUOC 1: Xac dinh va ghi wpa_supplicant.conf
# Thu tao /data/misc/wifi neu chua co
# ================================================================
CONF="/data/misc/wifi/wpa_supplicant.conf"
TMP_CONF="/data/local/tmp/wpa_new.conf"

echo "[1/3] Xu ly wpa_supplicant.conf..." >> "$LOG"

# Tao thu muc neu chua co (thay vi bao loi va bo qua)
if [ ! -d /data/misc/wifi ]; then
    echo "[*] /data/misc/wifi chua co - dang tao..." >> "$LOG"
    mkdir /data/misc 2>/dev/null
    mkdir /data/misc/wifi 2>/dev/null
    mkdir /data/misc/wifi/sockets 2>/dev/null
    chmod 771 /data/misc/wifi 2>/dev/null
    chmod 771 /data/misc/wifi/sockets 2>/dev/null
    chown system:wifi /data/misc/wifi 2>/dev/null
    chown system:wifi /data/misc/wifi/sockets 2>/dev/null
    echo "[*] Ket qua tao thu muc: $?" >> "$LOG"
    ls -la /data/misc/wifi/ >> "$LOG" 2>&1
fi

# Doc ctrl_interface header tu file hien tai (dung while/case thay grep)
CTRL_IFACE=""
if [ -f "$CONF" ]; then
    while read line; do
        case "$line" in
            ctrl_interface=*) CTRL_IFACE="$line"; break;;
        esac
    done < "$CONF"
    cp "$CONF" /data/local/tmp/wpa_supplicant.conf.bak 2>/dev/null
fi

if [ -z "$CTRL_IFACE" ]; then
    CTRL_IFACE="ctrl_interface=DIR=/data/misc/wifi/sockets GROUP=wifi"
    echo "[*] Dung ctrl_interface mac dinh Android 5.1" >> "$LOG"
fi
echo "[*] ctrl_interface: $CTRL_IFACE" >> "$LOG"

# Tat WiFi truoc khi ghi
svc wifi disable >> "$LOG" 2>&1
sleep 2

# Tao file config moi - chi dung echo tung dong
echo "$CTRL_IFACE" > "$TMP_CONF"
echo "update_config=1" >> "$TMP_CONF"
echo "" >> "$TMP_CONF"
echo "network={" >> "$TMP_CONF"
echo "    ssid=\"$SSID\"" >> "$TMP_CONF"
if [ -n "$PASS" ]; then
    echo "    psk=\"$PASS\"" >> "$TMP_CONF"
    echo "    key_mgmt=WPA-PSK" >> "$TMP_CONF"
else
    echo "    key_mgmt=NONE" >> "$TMP_CONF"
fi
echo "    priority=100" >> "$TMP_CONF"
echo "    disabled=0" >> "$TMP_CONF"
echo "}" >> "$TMP_CONF"

echo "[*] Noi dung config (khong hien psk):" >> "$LOG"
while read line; do
    case "$line" in
        *psk=*) echo "    psk=***" >> "$LOG";;
        *)      echo "$line" >> "$LOG";;
    esac
done < "$TMP_CONF"

# Ghi vao vi tri chinh thuc
cp "$TMP_CONF" "$CONF"
echo "[*] cp wpa_supplicant.conf exit: $?" >> "$LOG"
chmod 660 "$CONF" 2>/dev/null
chown system:wifi "$CONF" 2>/dev/null || chown wifi:wifi "$CONF" 2>/dev/null
ls -la "$CONF" >> "$LOG" 2>&1

# Fix SELinux context
restorecon "$CONF" 2>/dev/null

echo "[OK] Da ghi wpa_supplicant.conf" >> "$LOG"

# Bat lai WiFi
echo "[*] Bat lai WiFi..." >> "$LOG"
svc wifi enable >> "$LOG" 2>&1
sleep 6

# Kiem tra sau khi bat
echo "[*] Trang thai sau svc wifi enable:" >> "$LOG"
netcfg 2>/dev/null >> "$LOG"

# ================================================================
# BUOC 2: wpa_cli (neu co)
# ================================================================
echo "" >> "$LOG"
echo "[2/3] Thu wpa_cli..." >> "$LOG"

WPA_CLI=""
if [ -x /system/bin/wpa_cli ]; then
    WPA_CLI="/system/bin/wpa_cli"
elif [ -x /system/xbin/wpa_cli ]; then
    WPA_CLI="/system/xbin/wpa_cli"
fi

if [ -n "$WPA_CLI" ]; then
    $WPA_CLI -i wlan0 reconfigure >> "$LOG" 2>&1
    sleep 1
    NID=$($WPA_CLI -i wlan0 add_network 2>/dev/null)
    case "$NID" in
        ''|*[!0-9]*) NID="";;
    esac
    if [ -n "$NID" ]; then
        $WPA_CLI -i wlan0 set_network $NID ssid "\"$SSID\"" >> "$LOG" 2>&1
        if [ -n "$PASS" ]; then
            $WPA_CLI -i wlan0 set_network $NID psk "\"$PASS\"" >> "$LOG" 2>&1
        else
            $WPA_CLI -i wlan0 set_network $NID key_mgmt NONE >> "$LOG" 2>&1
        fi
        $WPA_CLI -i wlan0 enable_network $NID >> "$LOG" 2>&1
        $WPA_CLI -i wlan0 select_network $NID >> "$LOG" 2>&1
        $WPA_CLI -i wlan0 save_config >> "$LOG" 2>&1
    fi
    $WPA_CLI -i wlan0 reassociate >> "$LOG" 2>&1
else
    echo "[*] wpa_cli khong co tren ROM nay" >> "$LOG"
fi

# ================================================================
# BUOC 3: Kiem tra IP
# ================================================================
echo "" >> "$LOG"
echo "[3/3] Kiem tra IP..." >> "$LOG"
sleep 5
echo "==========================================" >> "$LOG"
netcfg 2>/dev/null >> "$LOG"
echo "==========================================" >> "$LOG"

echo "=== HOAN TAT ===" >> "$LOG"
