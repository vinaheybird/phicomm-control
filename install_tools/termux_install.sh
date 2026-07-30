#!/bin/sh
# Termux / iSH Shell script to install Web Controller APK & provision Phicomm R1 Wi-Fi
echo "==================================================================="
echo "  🔊 CÀI ĐẶT WEB CONTROLLER LOA PHICOMM R1 QUA ĐIỆN THOẠI (TERMUX / ISH)"
echo "==================================================================="
echo ""

# 1. Kiem tra va cai dat android-tools & curl (Can Internet 4G / Wi-Fi)
if ! command -v adb >/dev/null 2>&1; then
    echo "[1/4] Dang cai dat cong cu adb & curl..."
    if command -v pkg >/dev/null 2>&1; then
        pkg install -y android-tools curl
    elif command -v apk >/dev/null 2>&1; then
        apk add --no-cache android-tools curl
    fi
fi

# Link GitHub Release APK
APK_URL="https://github.com/vinaheybird/phicomm-control/releases/download/v1.0.0/PhicommGemini.apk"

# Tai file APK ve may
if [ ! -f "PhicommGemini.apk" ]; then
    echo "[2/4] Dang tai PhicommGemini.apk tu GitHub Release..."
    curl -sSL -o PhicommGemini.apk "$APK_URL" 2>/dev/null || wget -q -O PhicommGemini.apk "$APK_URL" 2>/dev/null
fi

if [ -f "PhicommGemini.apk" ]; then
    echo "[✅] Da tai xong file APK va cong cu cai dat!"
else
    echo "[!] Khong tai duoc file PhicommGemini.apk! Vui long kiem tra ket noi Internet/4G."
    exit 1
fi

echo ""
echo "-------------------------------------------------------------------"
echo "📶 BƯỚC TIẾP THEO: KẾT NỐI VÀO WI-FI CỦA LOA PHICOMM R1"
echo "-------------------------------------------------------------------"
echo "1. Vao Cài đặt Wi-Fi trên điện thoại."
echo "2. Kết nối vào Wi-Fi phát ra từ loa (Tên: Phicomm R1 hoặc Phicomm_R1_xxxx)."
echo "3. Quay lại đây và nhấn [ENTER] để tiếp tục cài đặt..."
echo "-------------------------------------------------------------------"
read DUMMY </dev/tty 2>/dev/null || read DUMMY

echo ""
echo "[3/4] Dang ket noi ADB toi loa Phicomm R1 (192.168.43.1:5555)..."

# Khoi chay adb server truoc de tranh treo subshell trong iSH / Termux
adb start-server >/dev/null 2>&1
adb disconnect >/dev/null 2>&1

CONNECTED=0
RETRY=1

while [ $RETRY -le 6 ]; do
    echo "[*] Thu ket noi ADB toi loa (Lan $RETRY/6)..."
    adb connect 192.168.43.1:5555 >/dev/null 2>&1
    sleep 2
    DEV_STATUS=$(adb devices 2>/dev/null | grep "192.168.43.1:5555")
    if echo "$DEV_STATUS" | grep -qE "device|connected"; then
        CONNECTED=1
        echo "[✅] Ket noi ADB thanh cong!"
        break
    fi
    RETRY=$((RETRY + 1))
done

if [ $CONNECTED -eq 0 ]; then
    echo "[!] Khong the ket noi toi loa R1 qua 192.168.43.1:5555."
    echo "    Vui long kiem tra dien thoai da ket noi dung Wi-Fi Phicomm R1 chua va thu lai!"
    exit 1
fi

echo ""
echo "[4/4] Dang vo hieu hoa ung dung rac Phicomm..."
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.player >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.device >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.airskill >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm hide com.phicomm.speaker.otaservice >/dev/null 2>&1

echo ""
echo "[*] Dang nap va cai dat PhicommGemini.apk len loa Phicomm R1..."
adb -s 192.168.43.1:5555 push PhicommGemini.apk /data/local/tmp/PhicommGemini.apk
adb -s 192.168.43.1:5555 shell pm install -r /data/local/tmp/PhicommGemini.apk

echo ""
echo "[*] Cap quyen Bluetooth va khoi chay dich vu..."
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.BLUETOOTH_ADMIN >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell pm grant com.phicomm.gemini android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell am startservice -n com.phicomm.gemini/.service.PhicommGeminiService >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell am start -n com.phicomm.gemini/.MainActivity >/dev/null 2>&1

echo ""
echo "==================================================================="
echo "📶 CẤU HÌNH WI-FI NHÀ ĐỂ LOA PHICOMM R1 KẾT NỐI VÀO MẠNG"
echo "==================================================================="
echo ""

# Vong lap dam bao Ten Wi-Fi khong bi de trong
HOME_SSID=""
while [ -z "$HOME_SSID" ]; do
    printf "👉 Nhập TÊN Wi-Fi nhà bạn (SSID): "
    read HOME_SSID </dev/tty 2>/dev/null || read HOME_SSID
    if [ -z "$HOME_SSID" ]; then
        echo "[!] Tên Wi-Fi không được để trống. Vui lòng nhập lại!"
    fi
done

printf "👉 Nhập MẬT KHẨU Wi-Fi (Bấm ENTER nếu Wi-Fi không có mật khẩu): "
read HOME_PASS </dev/tty 2>/dev/null || read HOME_PASS

echo ""
echo "[*] Đang gửi thông tin Wi-Fi '$HOME_SSID' sang loa Phicomm R1..."
adb -s 192.168.43.1:5555 shell cmd wifi connect-network "$HOME_SSID" wpa2 "$HOME_PASS" >/dev/null 2>&1
adb -s 192.168.43.1:5555 shell am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "$HOME_SSID" --es password "$HOME_PASS" >/dev/null 2>&1

echo ""
echo "==================================================================="
echo "  🎉 [HOÀN TẤT CÀI ĐẶT WEB CONTROLLER!]"
echo "-------------------------------------------------------------------"
echo "  1. Loa Phicomm R1 đang tự kết nối vào Wi-Fi nhà bạn ($HOME_SSID)."
echo "  2. Kết nối lại điện thoại vào Wi-Fi nhà bạn ($HOME_SSID)."
echo "  3. Mở trình duyệt web bất kỳ và truy cập địa chỉ:"
echo "     👉 http://phicomm.local:8080"
echo "==================================================================="
echo ""
echo "Nhấn [ENTER] để kết thúc..."
read FINISH </dev/tty 2>/dev/null || read FINISH
