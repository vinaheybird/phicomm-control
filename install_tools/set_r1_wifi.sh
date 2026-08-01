#!/system/bin/sh
# Shell script running inside Phicomm R1 Android speaker to configure Wi-Fi

LOG="/data/local/tmp/wifi_setup.log"
exec > "$LOG" 2>&1

echo "=========================================="
echo "LOG CAU HINH WI-FI PHICOMM R1 - $(date)"
echo "=========================================="

if [ -f /data/local/tmp/wifi_info.txt ]; then
    SSID=$(head -n 1 /data/local/tmp/wifi_info.txt | tr -d '\r\n')
    PASS=$(sed -n '2p' /data/local/tmp/wifi_info.txt | tr -d '\r\n')
else
    SSID=$(echo "$1" | tr -d '\r\n')
    PASS=$(echo "$2" | tr -d '\r\n')
fi

echo "[*] SSID: '$SSID'"
echo "[*] PASS: '$PASS'"

if [ -z "$SSID" ]; then
    echo "[ERROR] SSID rong!"
    exit 1
fi

# 1. Update /data/misc/wifi/wpa_supplicant.conf directly (Phuong phap chinh, khong phu thuoc wpa_cli)
CONF="/data/misc/wifi/wpa_supplicant.conf"
if [ -f "$CONF" ]; then
    echo "[1/3] Ghi truc tiep vao $CONF..."
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
    echo "[OK] Da cap nhat wpa_supplicant.conf"
fi

# 2. wpa_cli (Neu co san tren ROM)
if command -v wpa_cli >/dev/null 2>&1; then
    echo "[2/3] Run wpa_cli..."
    wpa_cli -i wlan0 reconfigure >/dev/null 2>&1
    RAW_NID=$(wpa_cli -i wlan0 add_network 2>/dev/null | tail -n 1 | tr -cd '0-9')
    if [ -n "$RAW_NID" ]; then
        NID="$RAW_NID"
        wpa_cli -i wlan0 set_network $NID ssid "\"$SSID\"" >/dev/null 2>&1
        if [ -n "$PASS" ]; then
            wpa_cli -i wlan0 set_network $NID psk "\"$PASS\"" >/dev/null 2>&1
        else
            wpa_cli -i wlan0 set_network $NID key_mgmt NONE >/dev/null 2>&1
        fi
        wpa_cli -i wlan0 enable_network $NID >/dev/null 2>&1
        wpa_cli -i wlan0 save_config >/dev/null 2>&1
        wpa_cli -i wlan0 select_network $NID >/dev/null 2>&1
    fi
    wpa_cli -i wlan0 reassociate >/dev/null 2>&1
else
    echo "[2/3] wpa_cli khong co san tren ROM - Bo qua wpa_cli (Dung wpa_supplicant.conf + svc wifi)"
fi

# 3. Restart Wi-Fi stack & Broadcast Intents
echo "[3/3] Khoi dong lai Wi-Fi & gui Broadcast..."
svc wifi disable
sleep 2
svc wifi enable
sleep 4

am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "$SSID" --es password "$PASS" >/dev/null 2>&1
am broadcast -a com.phicomm.speaker.device.SET_WIFI --es ssid "$SSID" --es password "$PASS" >/dev/null 2>&1
am broadcast -a com.phicomm.gemini.SET_WIFI --es ssid "$SSID" --es password "$PASS" >/dev/null 2>&1

sleep 3
echo "=========================================="
echo "IP ADDRESS TRONG MANG WI-FI:"
netcfg 2>/dev/null | grep wlan0 || ifconfig wlan0 2>/dev/null || ip addr show wlan0 2>/dev/null
echo "=========================================="
