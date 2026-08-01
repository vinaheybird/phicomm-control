#!/system/bin/sh
# Script chay BEN TRONG loa Phicomm R1 (Android 5.1 - ho tro ca Root & Unrooted)
# Chi dung shell builtin - KHONG dung head/tr/sed/wc/awk

LOG="/data/local/tmp/wifi_setup.log"
rm -f "$LOG" 2>/dev/null
echo "=== LOG CAU HINH WI-FI PHICOMM R1 ===" > "$LOG"

# ---- Kiem tra quyen Root ----
IS_ROOT=0
if [ "$(id -u 2>/dev/null)" = "0" ]; then
    IS_ROOT=1
elif [ -z "$R1_SU_RUNNING" ]; then
    export R1_SU_RUNNING=1
    if command -v su >/dev/null 2>&1 || [ -x /system/xbin/su ] || [ -x /system/bin/su ]; then
        exec su 0 sh "$0" "$@" 2>/dev/null || exec su -c "sh $0 $@" 2>/dev/null || exec su sh "$0" "$@" 2>/dev/null
    fi
fi

echo "[*] UID hien tai: $(id -u 2>/dev/null) (IsRoot=$IS_ROOT)" >> "$LOG"

# ---- Doc SSID / Password tu file wifi_info.txt ----
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
# METHOD 1: Public Broadcast Intents (Hoat dong khong can Root)
# ================================================================
echo "[1/4] Gui Public Android Broadcasts..." >> "$LOG"
svc wifi enable >> "$LOG" 2>&1
am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "$SSID" --es password "$PASS" --es key "$PASS" >> "$LOG" 2>&1
am broadcast -a com.phicomm.speaker.ACTION_WIFI_SET --es ssid "$SSID" --es password "$PASS" >> "$LOG" 2>&1
am broadcast -a com.phicomm.gemini.SET_WIFI --es ssid "$SSID" --es password "$PASS" >> "$LOG" 2>&1
cmd wifi connect-network "$SSID" wpa2 "$PASS" >> "$LOG" 2>&1

# ================================================================
# METHOD 2: Ghi wpa_supplicant.conf (Chi thuc hien neu co ROOT)
# ================================================================
CONF="/data/misc/wifi/wpa_supplicant.conf"
TMP_CONF="/data/local/tmp/wpa_new.conf"

if [ "$IS_ROOT" -eq 1 ]; then
    echo "[2/4] Dang ghi wpa_supplicant.conf (Quyen ROOT)..." >> "$LOG"
    CTRL_IFACE=""
    if [ -f "$CONF" ]; then
        while read line; do
            case "$line" in
                ctrl_interface=*) CTRL_IFACE="$line"; break;;
            esac
        done < "$CONF"
    fi

    if [ -z "$CTRL_IFACE" ]; then
        CTRL_IFACE="ctrl_interface=DIR=/data/misc/wifi/sockets GROUP=wifi"
    fi

    svc wifi disable >> "$LOG" 2>&1
    sleep 2

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

    cp "$TMP_CONF" "$CONF" 2>> "$LOG"
    chmod 660 "$CONF" 2>/dev/null
    chown system:wifi "$CONF" 2>/dev/null || chown wifi:wifi "$CONF" 2>/dev/null
    restorecon "$CONF" 2>/dev/null

    echo "[OK] Da ghi wpa_supplicant.conf" >> "$LOG"
    svc wifi enable >> "$LOG" 2>&1
    sleep 5
else
    echo "[2/4] Bo qua ghi wpa_supplicant.conf (Loa khong root - dung Public API Broadcast)" >> "$LOG"
fi

# ================================================================
# METHOD 3: wpa_cli (neu co)
# ================================================================
echo "[3/4] Thu wpa_cli..." >> "$LOG"
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
fi

# ================================================================
# METHOD 4: Kiem tra IP
# ================================================================
echo "[4/4] Kiem tra IP sau khi thiet lap..." >> "$LOG"
sleep 5
echo "==========================================" >> "$LOG"
netcfg 2>/dev/null >> "$LOG" || ip addr show wlan0 2>/dev/null >> "$LOG"
echo "==========================================" >> "$LOG"

echo "=== HOAN TAT ===" >> "$LOG"
