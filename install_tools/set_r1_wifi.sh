#!/system/bin/sh
# Script chay BEN TRONG loa Phicomm R1 (Android 5.1, mksh)
# Cau hinh Wi-Fi bang cach ghi lai toan bo wpa_supplicant.conf

LOG="/data/local/tmp/wifi_setup.log"
rm -f "$LOG"
echo "=== LOG CAU HINH WI-FI PHICOMM R1 ===" > "$LOG"

# ---- Doc SSID / Password ----
if [ -f /data/local/tmp/wifi_info.txt ]; then
    SSID=$(head -n 1 /data/local/tmp/wifi_info.txt | tr -d '\r\n')
    PASS=$(sed -n '2p' /data/local/tmp/wifi_info.txt | tr -d '\r\n')
else
    SSID=$(echo "$1" | tr -d '\r\n')
    PASS=$(echo "$2" | tr -d '\r\n')
fi

echo "[*] SSID: '$SSID'" >> "$LOG"
echo "[*] PASS len: $(echo -n "$PASS" | wc -c) ky tu" >> "$LOG"

if [ -z "$SSID" ]; then
    echo "[ERROR] SSID rong! Thoat." >> "$LOG"
    exit 1
fi

CONF="/data/misc/wifi/wpa_supplicant.conf"
TMP_CONF="/data/local/tmp/wpa_new.conf"

# ================================================================
# BUOC 1: Ghi lai wpa_supplicant.conf (REWRITE, khong phai append)
# Viet tung dong de tuong thich mksh Android 5.1
# ================================================================
echo "[1/3] Xu ly $CONF..." >> "$LOG"

if [ ! -d /data/misc/wifi ]; then
    echo "[ERROR] /data/misc/wifi khong ton tai - ROM bi strip qua?" >> "$LOG"
else
    # Doc ctrl_interface header tu file hien tai (bat buoc cho wpa_supplicant)
    CTRL_IFACE=""
    if [ -f "$CONF" ]; then
        CTRL_IFACE=$(grep "^ctrl_interface" "$CONF" 2>/dev/null | head -n 1 | tr -d '\r\n')
        cp "$CONF" /data/local/tmp/wpa_supplicant.conf.bak 2>/dev/null
        echo "[*] Backup xong" >> "$LOG"
    fi

    # Neu khong co header, dung gia tri mac dinh Android 5.1
    if [ -z "$CTRL_IFACE" ]; then
        CTRL_IFACE="ctrl_interface=DIR=/data/misc/wifi/sockets GROUP=wifi"
        echo "[*] Dung ctrl_interface mac dinh Android 5.1" >> "$LOG"
    fi
    echo "[*] ctrl_interface: $CTRL_IFACE" >> "$LOG"

    # Tat WiFi truoc khi ghi de tranh daemon ghi de lai
    svc wifi disable >> "$LOG" 2>&1
    sleep 2

    # Tao file config moi: viet TUNG DONG (khong dung compound {})
    # Vi mksh Android 5.1 co the khong ho tro { } > file redirect
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

    echo "[*] Noi dung file moi (an psk):" >> "$LOG"
    grep -v "psk=" "$TMP_CONF" >> "$LOG"

    # Ghi vao vi tri chinh thuc
    cp "$TMP_CONF" "$CONF" 2>> "$LOG"
    echo "[*] cp result: $?" >> "$LOG"

    # Set quyen dung cho Android 5.1
    chmod 660 "$CONF" 2>/dev/null
    chown system:wifi "$CONF" 2>/dev/null || chown wifi:wifi "$CONF" 2>/dev/null
    echo "[*] Quyen: $(ls -la $CONF 2>/dev/null)" >> "$LOG"

    # Fix SELinux context neu co restorecon
    if [ -x /system/bin/restorecon ]; then
        /system/bin/restorecon "$CONF" 2>/dev/null
        echo "[*] Da chay restorecon" >> "$LOG"
    fi

    echo "[OK] Da ghi wpa_supplicant.conf" >> "$LOG"

    # Bat lai WiFi
    echo "[*] Bat lai WiFi..." >> "$LOG"
    svc wifi enable >> "$LOG" 2>&1
    sleep 5
fi

# ================================================================
# BUOC 2: wpa_cli (neu co - stripped ROM thuong khong co)
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
    NID=$($WPA_CLI -i wlan0 add_network 2>/dev/null | tail -n 1 | tr -cd '0-9')
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
        echo "[OK] wpa_cli inject thanh cong (NID=$NID)" >> "$LOG"
    fi
    $WPA_CLI -i wlan0 reassociate >> "$LOG" 2>&1
else
    echo "[*] wpa_cli khong co tren ROM nay (stripped)" >> "$LOG"
fi

# ================================================================
# BUOC 3: Kiem tra IP
# ================================================================
echo "" >> "$LOG"
echo "[3/3] Kiem tra IP..." >> "$LOG"
sleep 5

echo "==========================================" >> "$LOG"
echo "IP TREN WLAN0:" >> "$LOG"
netcfg 2>/dev/null | grep wlan0 >> "$LOG" \
    || ip addr show wlan0 2>/dev/null >> "$LOG" \
    || ifconfig wlan0 2>/dev/null >> "$LOG"
echo "==========================================" >> "$LOG"

IP_CHECK=$(netcfg 2>/dev/null | grep wlan0 | grep -v " 0\.0\.0\.0")
if [ -n "$IP_CHECK" ]; then
    echo "[OK] CO IP - KET NOI WI-FI THANH CONG!" >> "$LOG"
    echo "$IP_CHECK" >> "$LOG"
else
    echo "[!] Chua lay duoc IP tren wlan0" >> "$LOG"
    echo "[!] Hay reboot loa (rut dien 5 giay) roi thu lai" >> "$LOG"
fi

echo "" >> "$LOG"
echo "=== HOAN TAT ===" >> "$LOG"
