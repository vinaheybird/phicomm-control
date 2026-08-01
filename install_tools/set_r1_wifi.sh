#!/system/bin/sh
# Script chay BEN TRONG loa Phicomm R1 (Android 5.1 stripped ROM)
# Cau hinh Wi-Fi bang cach ghi lai toan bo wpa_supplicant.conf dung chuan

LOG="/data/local/tmp/wifi_setup.log"
rm -f "$LOG"

echo "=== LOG CAU HINH WI-FI PHICOMM R1 ===" > "$LOG"
echo "[*] Shell: $(readlink /proc/$$/exe 2>/dev/null || echo unknown)" >> "$LOG"

# ---- Doc SSID / Password ----
if [ -f /data/local/tmp/wifi_info.txt ]; then
    SSID=$(head -n 1 /data/local/tmp/wifi_info.txt | tr -d '\r\n')
    PASS=$(sed -n '2p' /data/local/tmp/wifi_info.txt | tr -d '\r\n')
else
    SSID=$(echo "$1" | tr -d '\r\n')
    PASS=$(echo "$2" | tr -d '\r\n')
fi

echo "[*] SSID: '$SSID'" >> "$LOG"
# Khong log password de bao mat
echo "[*] PASS len: $(echo -n "$PASS" | wc -c) ky tu" >> "$LOG"

if [ -z "$SSID" ]; then
    echo "[ERROR] SSID rong! Thoat." >> "$LOG"
    exit 1
fi

# ================================================================
# BUOC 1: Ghi lai toan bo wpa_supplicant.conf dung chuan Android 5.1
# QUAN TRONG: Phai giu nguyen header ctrl_interface + update_config
# Neu khong co header nay, wpa_supplicant daemon se khong khoi dong
# duoc sau khi reboot (Android 5.1 bat buoc can header nay).
# ================================================================
CONF="/data/misc/wifi/wpa_supplicant.conf"
CONF_DIR="/data/misc/wifi"
TMP_CONF="/data/local/tmp/wpa_new.conf"

echo "[1/4] Chuan bi ghi wpa_supplicant.conf..." >> "$LOG"

# Kiem tra xem thu muc ton tai khong
if [ ! -d "$CONF_DIR" ]; then
    echo "[ERROR] Thu muc $CONF_DIR khong ton tai - ROM bi strip qua?" >> "$LOG"
else
    # Doc ctrl_interface hien tai tu file cu (neu co)
    CTRL_IFACE=""
    if [ -f "$CONF" ]; then
        CTRL_IFACE=$(grep "^ctrl_interface" "$CONF" | head -n 1 | tr -d '\r\n')
        echo "[*] ctrl_interface hien tai: '$CTRL_IFACE'" >> "$LOG"
        # Backup file cu
        cp "$CONF" /data/local/tmp/wpa_supplicant.conf.bak 2>/dev/null
        echo "[*] Da backup file cu vao /data/local/tmp/wpa_supplicant.conf.bak" >> "$LOG"
    fi

    # Neu khong tim thay ctrl_interface, dung gia tri mac dinh Android 5.1
    if [ -z "$CTRL_IFACE" ]; then
        CTRL_IFACE="ctrl_interface=DIR=/data/misc/wifi/sockets GROUP=wifi"
        echo "[!] Khong tim thay ctrl_interface - dung gia tri mac dinh Android 5.1" >> "$LOG"
    fi

    # Tao file config moi hoan chinh (REWRITE, khong phai append)
    # Phai co du header + network block
    {
        echo "$CTRL_IFACE"
        echo "update_config=1"
        echo ""
        echo "network={"
        echo "    ssid=\"$SSID\""
        if [ -n "$PASS" ]; then
            echo "    psk=\"$PASS\""
            echo "    key_mgmt=WPA-PSK"
        else
            echo "    key_mgmt=NONE"
        fi
        echo "    priority=100"
        echo "    disabled=0"
        echo "}"
    } > "$TMP_CONF"

    echo "[*] Noi dung file moi:" >> "$LOG"
    # Log file nhung an password
    grep -v "psk=" "$TMP_CONF" >> "$LOG"

    # Tat WiFi truoc khi ghi de tranh daemon lock file
    svc wifi disable >> "$LOG" 2>&1
    sleep 2

    # Copy file moi vao vi tri chinh thuc
    cp "$TMP_CONF" "$CONF" 2>> "$LOG"
    COPY_RESULT=$?
    echo "[*] cp result: $COPY_RESULT" >> "$LOG"

    # Set quyen - Android 5.1: thu ca hai cach owner
    chmod 660 "$CONF" >> "$LOG" 2>&1
    # Thu system:wifi truoc (ROM Phicomm goc)
    chown system:wifi "$CONF" 2>/dev/null || chown wifi:wifi "$CONF" 2>/dev/null
    echo "[*] Quyen sau khi set: $(ls -la $CONF 2>/dev/null)" >> "$LOG"

    # Fix SELinux context (Android 5.1 enforcing mode)
    # Neu khong co restorecon thi bo qua (stripped ROM)
    if [ -x /system/bin/restorecon ]; then
        /system/bin/restorecon "$CONF" >> "$LOG" 2>&1
        echo "[*] Da chay restorecon" >> "$LOG"
    elif [ -x /sbin/restorecon ]; then
        /sbin/restorecon "$CONF" >> "$LOG" 2>&1
        echo "[*] Da chay restorecon (sbin)" >> "$LOG"
    else
        echo "[!] restorecon khong co san - bo qua SELinux context fix" >> "$LOG"
    fi

    echo "[OK] Da ghi wpa_supplicant.conf" >> "$LOG"
fi

# ================================================================
# BUOC 2: wpa_cli (neu co - stripped ROM thuong khong co)
# ================================================================
WPA_CLI=""
if [ -x /system/bin/wpa_cli ]; then
    WPA_CLI="/system/bin/wpa_cli"
elif [ -x /system/xbin/wpa_cli ]; then
    WPA_CLI="/system/xbin/wpa_cli"
fi

echo "" >> "$LOG"
if [ -n "$WPA_CLI" ]; then
    echo "[2/4] Dung wpa_cli de inject mang..." >> "$LOG"
    $WPA_CLI -i wlan0 reconfigure >> "$LOG" 2>&1
    sleep 1
    # Them mang moi qua wpa_cli (song song voi conf da ghi)
    RAW_NID=$($WPA_CLI -i wlan0 add_network 2>/dev/null | tail -n 1 | tr -cd '0-9')
    if [ -n "$RAW_NID" ]; then
        NID="$RAW_NID"
        $WPA_CLI -i wlan0 set_network $NID ssid "\"$SSID\"" >> "$LOG" 2>&1
        if [ -n "$PASS" ]; then
            $WPA_CLI -i wlan0 set_network $NID psk "\"$PASS\"" >> "$LOG" 2>&1
            $WPA_CLI -i wlan0 set_network $NID key_mgmt WPA-PSK >> "$LOG" 2>&1
        else
            $WPA_CLI -i wlan0 set_network $NID key_mgmt NONE >> "$LOG" 2>&1
        fi
        $WPA_CLI -i wlan0 set_network $NID priority 100 >> "$LOG" 2>&1
        $WPA_CLI -i wlan0 enable_network $NID >> "$LOG" 2>&1
        $WPA_CLI -i wlan0 save_config >> "$LOG" 2>&1
        $WPA_CLI -i wlan0 select_network $NID >> "$LOG" 2>&1
        echo "[OK] wpa_cli inject thanh cong (NID=$NID)" >> "$LOG"
    else
        echo "[!] wpa_cli add_network that bai - ROM co the khong co socket" >> "$LOG"
    fi
    $WPA_CLI -i wlan0 reassociate >> "$LOG" 2>&1
else
    echo "[2/4] wpa_cli khong co tren ROM nay (stripped) - chi dua vao wpa_supplicant.conf" >> "$LOG"
fi

# ================================================================
# BUOC 3: Khoi dong lai WiFi stack
# Loa Phicomm R1 stripped ROM: svc wifi co the bi han che
# Thu nhieu phuong phap de dam bao WiFi bat duoc
# ================================================================
echo "" >> "$LOG"
echo "[3/4] Khoi dong lai WiFi stack..." >> "$LOG"

# Tat WiFi (da tat o buoc 1 nhung tat lai cho chac)
svc wifi disable >> "$LOG" 2>&1
sleep 3

# Bat WiFi
svc wifi enable >> "$LOG" 2>&1
sleep 5

# Kiem tra trang thai WiFi
WIFI_STATE=$(dumpsys wifi 2>/dev/null | grep "Wi-Fi is" | head -n 1 | tr -d '\r\n')
echo "[*] WiFi state: $WIFI_STATE" >> "$LOG"

# ================================================================
# BUOC 4: Kiem tra ket noi & log IP
# ================================================================
echo "" >> "$LOG"
echo "[4/4] Kiem tra ket noi..." >> "$LOG"
sleep 5

echo "==========================================" >> "$LOG"
echo "IP ADDRESS HIEN TAI TREN WLAN0:" >> "$LOG"
netcfg 2>/dev/null | grep wlan0 >> "$LOG" \
    || ifconfig wlan0 2>/dev/null >> "$LOG" \
    || ip addr show wlan0 2>/dev/null >> "$LOG"
echo "==========================================" >> "$LOG"

# Kiem tra xem da lay duoc IP chua
IP_CHECK=$(netcfg 2>/dev/null | grep wlan0 | grep -v "0\.0\.0\.0" \
    || ip addr show wlan0 2>/dev/null | grep "inet " | grep -v "127\.")

if [ -n "$IP_CHECK" ]; then
    echo "[OK] LOA DA LAY DUOC IP - KET NOI WI-FI THANH CONG!" >> "$LOG"
    echo "$IP_CHECK" >> "$LOG"
else
    echo "[!] Chua thay IP tren wlan0." >> "$LOG"
    echo "[!] Co the can them thoi gian de DHCP phan hoi." >> "$LOG"
    echo "[!] Hoac can reboot loa de wpa_supplicant doc lai config." >> "$LOG"
fi

echo "" >> "$LOG"
echo "=== HOAN TAT ===" >> "$LOG"
echo "[i] Neu chua co IP, hay reboot loa roi kiem tra lai." >> "$LOG"
echo "[i] Config da duoc luu vao wpa_supplicant.conf va se tu dong ket noi sau reboot." >> "$LOG"
