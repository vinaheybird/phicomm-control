#!/system/bin/sh
# Shell script running inside Phicomm R1 Android speaker to configure Wi-Fi

if [ -f /data/local/tmp/wifi_info.txt ]; then
    SSID=$(head -n 1 /data/local/tmp/wifi_info.txt)
    PASS=$(sed -n '2p' /data/local/tmp/wifi_info.txt)
else
    SSID="$1"
    PASS="$2"
fi

if [ -z "$SSID" ]; then
    exit 1
fi

CONF="/data/misc/wifi/wpa_supplicant.conf"
if [ -f "$CONF" ]; then
    echo "" >> "$CONF"
    echo "network={" >> "$CONF"
    echo "    ssid=\"$SSID\"" >> "$CONF"
    if [ -n "$PASS" ]; then
        echo "    psk=\"$PASS\"" >> "$CONF"
        echo "    key_mgmt=WPA-PSK" >> "$CONF"
    else
        echo "    key_mgmt=NONE" >> "$CONF"
    fi
    echo "    priority=10" >> "$CONF"
    echo "}" >> "$CONF"
    chmod 660 "$CONF"
    chown system:wifi "$CONF"
fi

wpa_cli -i wlan0 reconfigure >/dev/null 2>&1
wpa_cli -i wlan0 remove_network all >/dev/null 2>&1
NID=$(wpa_cli -i wlan0 add_network 2>/dev/null | tr -cd '0-9' | cut -c1)
if [ -n "$NID" ]; then
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

svc wifi disable >/dev/null 2>&1
sleep 1
svc wifi enable >/dev/null 2>&1

am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "$SSID" --es password "$PASS" >/dev/null 2>&1
am broadcast -a com.phicomm.gemini.SET_WIFI --es ssid "$SSID" --es password "$PASS" >/dev/null 2>&1
