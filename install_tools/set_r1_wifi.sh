#!/system/bin/sh
# Shell script running inside Phicomm R1 Android speaker to configure Wi-Fi

LOG="/data/local/tmp/wifi_setup.log"
rm -f "$LOG"

echo "=== LOG CAU HINH WI-FI PHICOMM R1 ===" > "$LOG"

if [ -f /data/local/tmp/wifi_info.txt ]; then
    SSID=$(head -n 1 /data/local/tmp/wifi_info.txt | tr -d '\r\n')
    PASS=$(sed -n '2p' /data/local/tmp/wifi_info.txt | tr -d '\r\n')
else
    SSID=$(echo "$1" | tr -d '\r\n')
    PASS=$(echo "$2" | tr -d '\r\n')
fi

echo "[*] SSID: '$SSID'" >> "$LOG"
echo "[*] PASS: '$PASS'" >> "$LOG"

if [ -z "$SSID" ]; then
    echo "[ERROR] SSID rong!" >> "$LOG"
    exit 1
fi

# 1. Update /data/misc/wifi/wpa_supplicant.conf directly (Phuong phap chinh, khong phu thuoc wpa_cli)
CONF="/data/misc/wifi/wpa_supplicant.conf"
if [ -f "$CONF" ]; then
    echo "[1/3] Ghi truc tiep vao $CONF..." >> "$LOG"
    echo "" >> "$CONF"
    echo "network={" >> "$CONF"
    echo "    ssid=\"$SSID\"" >> "$CONF"
    if [ -n "$PASS" ]; then
        echo "    psk=\"$PASS\"" >> "$CONF"
        echo "    key_mgmt=WPA-PSK" >> "$CONF"
    else
        echo "    key_mgmt=NONE" >> "$CONF"
    fi
    echo "    priority=99" >> "$CONF"
    echo "}" >> "$CONF"
    chmod 660 "$CONF"
    chown system:wifi "$CONF"
    echo "[OK] Da cap nhat wpa_supplicant.conf" >> "$LOG"
fi

# 2. wpa_cli (Kiem tra file thay vi command -v de tuong thich Android 5.1 mksh)
WPA_CLI=""
if [ -x /system/bin/wpa_cli ]; then
    WPA_CLI="/system/bin/wpa_cli"
elif [ -x /system/xbin/wpa_cli ]; then
    WPA_CLI="/system/xbin/wpa_cli"
fi

if [ -n "$WPA_CLI" ]; then
    echo "[2/3] Run $WPA_CLI..." >> "$LOG"
    $WPA_CLI -i wlan0 reconfigure >> "$LOG" 2>&1
    RAW_NID=$($WPA_CLI -i wlan0 add_network 2>/dev/null | tail -n 1 | tr -cd '0-9')
    if [ -n "$RAW_NID" ]; then
        NID="$RAW_NID"
        $WPA_CLI -i wlan0 set_network $NID ssid "\"$SSID\"" >> "$LOG" 2>&1
        if [ -n "$PASS" ]; then
            $WPA_CLI -i wlan0 set_network $NID psk "\"$PASS\"" >> "$LOG" 2>&1
        else
            $WPA_CLI -i wlan0 set_network $NID key_mgmt NONE >> "$LOG" 2>&1
        fi
        $WPA_CLI -i wlan0 enable_network $NID >> "$LOG" 2>&1
        $WPA_CLI -i wlan0 save_config >> "$LOG" 2>&1
        $WPA_CLI -i wlan0 select_network $NID >> "$LOG" 2>&1
    fi
    $WPA_CLI -i wlan0 reassociate >> "$LOG" 2>&1
else
    echo "[2/3] wpa_cli khong co san tren ROM - Bo qua wpa_cli (Dung wpa_supplicant.conf + svc wifi)" >> "$LOG"
fi

# 3. Restart Wi-Fi stack & Broadcast Intents
echo "[3/3] Khoi dong lai Wi-Fi & gui Broadcast..." >> "$LOG"
svc wifi disable >> "$LOG" 2>&1
sleep 2
svc wifi enable >> "$LOG" 2>&1
sleep 4

am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "$SSID" --es password "$PASS" >> "$LOG" 2>&1
am broadcast -a com.phicomm.speaker.device.SET_WIFI --es ssid "$SSID" --es password "$PASS" >> "$LOG" 2>&1
am broadcast -a com.phicomm.gemini.SET_WIFI --es ssid "$SSID" --es password "$PASS" >> "$LOG" 2>&1

sleep 3
echo "==========================================" >> "$LOG"
echo "IP ADDRESS TRONG MANG WI-FI:" >> "$LOG"
netcfg 2>/dev/null | grep wlan0 >> "$LOG" || ifconfig wlan0 2>/dev/null >> "$LOG" || ip addr show wlan0 2>/dev/null >> "$LOG"
echo "==========================================" >> "$LOG"
