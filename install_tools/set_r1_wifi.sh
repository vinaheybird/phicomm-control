#!/system/bin/sh
# Script chay BEN TRONG loa Phicomm R1 (Android 5.1 stripped ROM)
# QUAN TRONG: Chi dung shell builtin - KHONG dung head/tr/sed/wc/awk
# vi ROM bi strip manh, cac lenh nay KHONG CO

LOG="/data/local/tmp/wifi_setup.log"
rm -f "$LOG"
echo "=== LOG CAU HINH WI-FI PHICOMM R1 ===" > "$LOG"

# ---- Doc SSID / Password tu file (dung read builtin + fd thay head/sed) ----
SSID=""
PASS=""
if [ -f /data/local/tmp/wifi_info.txt ]; then
    # Doc dong 1 va dong 2 bang read builtin + file descriptor (POSIX sh chuan)
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

CONF="/data/misc/wifi/wpa_supplicant.conf"
TMP_CONF="/data/local/tmp/wpa_new.conf"

echo "[1/3] Xu ly $CONF..." >> "$LOG"

if [ ! -d /data/misc/wifi ]; then
    echo "[ERROR] /data/misc/wifi khong ton tai - ROM bi strip?" >> "$LOG"
else
    # Doc ctrl_interface tu file hien tai
    # Dung while/case thay grep (grep co the khong co tren ROM strip)
    CTRL_IFACE=""
    if [ -f "$CONF" ]; then
        while read line; do
            case "$line" in
                ctrl_interface=*) CTRL_IFACE="$line"; break;;
            esac
        done < "$CONF"
        cp "$CONF" /data/local/tmp/wpa_supplicant.conf.bak 2>/dev/null
        echo "[*] Backup xong" >> "$LOG"
    fi

    if [ -z "$CTRL_IFACE" ]; then
        CTRL_IFACE="ctrl_interface=DIR=/data/misc/wifi/sockets GROUP=wifi"
        echo "[*] Dung ctrl_interface mac dinh Android 5.1" >> "$LOG"
    fi
    echo "[*] ctrl_interface: $CTRL_IFACE" >> "$LOG"

    # Tat WiFi truoc khi ghi de tranh daemon lock file
    svc wifi disable >> "$LOG" 2>&1
    sleep 2

    # Tao file config moi - chi dung echo (khong dung compound {})
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

    echo "[*] Noi dung config moi:" >> "$LOG"
    cat "$TMP_CONF" >> "$LOG" 2>/dev/null

    # Ghi vao vi tri chinh thuc
    cp "$TMP_CONF" "$CONF" 2>> "$LOG"
    echo "[*] cp exit: $?" >> "$LOG"

    chmod 660 "$CONF" 2>/dev/null
    chown system:wifi "$CONF" 2>/dev/null || chown wifi:wifi "$CONF" 2>/dev/null

    # Fix SELinux context
    restorecon "$CONF" 2>/dev/null

    echo "[OK] Da ghi wpa_supplicant.conf" >> "$LOG"

    # Bat lai WiFi
    svc wifi enable >> "$LOG" 2>&1
    sleep 5
fi

# ================================================================
# BUOC 2: wpa_cli (neu co - stripped ROM thuong khong co)
# ================================================================
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
    # add_network tra ve 1 so - dung $() truc tiep, khong can tail/tr
    NID=$($WPA_CLI -i wlan0 add_network 2>/dev/null)
    # Kiem tra NID la so nguyen (khong dung tr -cd '0-9')
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
        echo "[OK] wpa_cli inject NID=$NID" >> "$LOG"
    fi
    $WPA_CLI -i wlan0 reassociate >> "$LOG" 2>&1
else
    echo "[*] wpa_cli khong co tren ROM nay" >> "$LOG"
fi

# ================================================================
# BUOC 3: Kiem tra IP
# ================================================================
echo "[3/3] Kiem tra IP..." >> "$LOG"
sleep 5
echo "==========================================" >> "$LOG"
netcfg 2>/dev/null >> "$LOG" || ip addr show wlan0 2>/dev/null >> "$LOG"
echo "==========================================" >> "$LOG"

echo "=== HOAN TAT ===" >> "$LOG"
