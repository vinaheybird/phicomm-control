#!/bin/sh
# Shell script cai dat PhicommGemini & Set Wi-Fi cho Phicomm R1 (Dung cho iSH / Termux / Linux)

RAW_SCRIPT="https://raw.githubusercontent.com/vinaheybird/phicomm-control/main/install_tools/termux_install.sh"

# Neu chay doc lap tu local
if [ -f "install_tools/termux_install.sh" ]; then
    exec sh install_tools/termux_install.sh "$@"
fi

# Luon xoa file cu de dam bao tai phien ban moi nhat
rm -f termux_install.sh PhicommGemini.apk 2>/dev/null

# Tải và thực thi termux_install.sh bằng wget / curl
echo "[*] Dang tai script cai dat tu GitHub..."
if command -v wget > /dev/null 2>&1; then
    wget -q --no-check-certificate -O termux_install.sh "$RAW_SCRIPT" && sh termux_install.sh "$@"
elif command -v curl > /dev/null 2>&1; then
    curl -sSL -o termux_install.sh "$RAW_SCRIPT" && sh termux_install.sh "$@"
else
    echo "[ERROR] Vui long cai dat wget hoac curl."
    exit 1
fi
