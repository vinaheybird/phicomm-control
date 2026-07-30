#!/bin/bash

echo "==================================================================="
echo "  🚫 VÔ HIỆU HÓA APP RÁC MẶC ĐỊNH PHICOMM R1 (XIAOZHI METHOD)"
echo "==================================================================="
echo ""

read -p "-> Nhập IP của Loa R1 (Mặc định 192.168.43.1): " LOA_IP
LOA_IP=${LOA_IP:-192.168.43.1}

echo ""
echo "[*] Đang kết nối ADB tới $LOA_IP:5555..."
adb disconnect
adb connect $LOA_IP:5555

echo ""
echo "[*] Đang ẩn và tắt dịch vụ rác Phicomm..."
adb -s $LOA_IP:5555 shell pm hide com.phicomm.speaker.player 2>/dev/null
adb -s $LOA_IP:5555 shell pm hide com.phicomm.speaker.device 2>/dev/null
adb -s $LOA_IP:5555 shell pm hide com.phicomm.speaker.airskill 2>/dev/null
adb -s $LOA_IP:5555 shell pm hide com.phicomm.speaker.otaservice 2>/dev/null
adb -s $LOA_IP:5555 shell pm hide com.phicomm.speaker.setup 2>/dev/null
adb -s $LOA_IP:5555 shell pm hide com.phicomm.speaker.voice 2>/dev/null
adb -s $LOA_IP:5555 shell pm hide com.unisound.unicar.speaker 2>/dev/null

adb -s $LOA_IP:5555 shell pm disable-user com.phicomm.speaker.player 2>/dev/null
adb -s $LOA_IP:5555 shell pm disable-user com.phicomm.speaker.device 2>/dev/null
adb -s $LOA_IP:5555 shell pm disable-user com.phicomm.speaker.airskill 2>/dev/null

echo ""
echo "==================================================================="
echo "  🎉 THÀNH CÔNG! ĐÃ VÔ HIỆU HÓA TOÀN BỘ APP RÁC PHICOMM R1!"
echo "==================================================================="
