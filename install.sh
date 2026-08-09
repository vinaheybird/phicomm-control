#!/bin/bash
echo "=========================================="
echo "  PHICOMM R1 - AUTO INSTALL SCRIPT"
echo "=========================================="

# Kiểm tra adb
if ! command -v adb &> /dev/null; then
    echo "Lỗi: Không tìm thấy lệnh 'adb'. Vui lòng cài đặt adb trước (ví dụ: apt install adb / apk add android-tools)."
    exit 1
fi

WIFI_SSID=$1
WIFI_PWD=$2

if [ -z "$WIFI_SSID" ]; then
    read -p "Nhập Tên Wi-Fi nhà bạn (SSID): " WIFI_SSID
fi
if [ -z "$WIFI_PWD" ]; then
    read -p "Nhập Mật khẩu Wi-Fi: " WIFI_PWD
fi

echo ""
echo "=> Hãy kết nối điện thoại/máy tính của bạn vào mạng Wi-Fi của loa (Phicomm_R1_xxxx)"
read -p "=> Bấm [ENTER] khi đã kết nối xong để tiếp tục..."

# Thử kết nối ADB (Thường IP Hotspot của loa Android là 192.168.43.1)
echo "Đang thử kết nối tới loa..."
adb connect 192.168.43.1:5555
sleep 2

# Nếu 192.168.43.1 không được, có thể loa ở IP khác, ta cứ dùng default adb device
DEVICE=$(adb devices | grep "device$" | head -n 1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo "Lỗi: Không tìm thấy thiết bị kết nối. Vui lòng kiểm tra lại kết nối Wi-Fi với loa."
    exit 1
fi

echo "Tìm thấy thiết bị: $DEVICE"

# Tải APK (Sửa link RAW này trỏ tới release hoặc thư mục apk nếu cần)
APK_URL="https://github.com/vinaheybird/phicomm-control/raw/main/install_tools/PhicommGemini.apk"
echo "Đang tải PhicommGemini.apk..."
wget -q -O PhicommGemini.apk "$APK_URL"

if [ ! -f "PhicommGemini.apk" ]; then
    echo "Lỗi: Tải APK thất bại!"
    exit 1
fi

echo "Đang cài đặt APK..."
adb -s $DEVICE install -r -g PhicommGemini.apk

echo "Đang vô hiệu hóa các ứng dụng gốc (bloatware) của Phicomm..."
apps=(
    "player"
    "airskill"
    "exceptionreporter"
    "ijetty"
    "netctl"
    "systemtool"
    "device"
    "otaservice"
    "productiontest"
    "bugreport"
)

for app in "${apps[@]}"; do
    echo "Ẩn $app..."
    adb -s $DEVICE shell /system/bin/pm hide "com.phicomm.speaker.$app"
done

# Truyền Wi-Fi cho loa qua broadcast
echo "Đang gửi cấu hình Wi-Fi ($WIFI_SSID) tới loa..."
adb -s $DEVICE shell am broadcast -a com.phicomm.speaker.ACTION_WIFI_SET --es ssid "$WIFI_SSID" --es pwd "$WIFI_PWD"

echo ""
echo "======================================================"
echo "✅ Cài đặt hoàn tất!"
echo "🔥 QUAN TRỌNG: Đang KHỞI ĐỘNG LẠI (REBOOT) loa để hoàn tất việc ẩn app gốc."
echo "Việc reboot là BẮT BUỘC để tránh lỗi EchoService tự động ngắt kết nối Bluetooth!"
echo "======================================================"
adb -s $DEVICE reboot

echo "Sau khi loa khởi động lên, hãy kết nối thiết bị của bạn về mạng Wi-Fi nhà ($WIFI_SSID)."
echo "Truy cập Web Dashboard tại: http://phicomm.local:8080"
