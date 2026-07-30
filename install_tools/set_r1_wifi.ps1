# Script gui thong tin Wi-Fi truc tiep den loa Phicomm R1
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  📶 CÔNG CỤ TRUYỀN THÔNG TIN WI-FI TRỰC TIẾP ĐẾN LOA PHICOMM R1  " -ForegroundColor Yellow
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host ""

$ssid = Read-Host "-> Nhap Ten Wi-Fi (SSID) nha ban"
$pass = Read-Host "-> Nhap Mat khau Wi-Fi"

if ([string]::IsNullOrWhiteSpace($ssid)) {
    Write-Host "[!] Ten Wi-Fi khong duoc de trong!" -ForegroundColor Red
    exit
}

Write-Host "`n[*] Đang gui goi tin truyen Wi-Fi den loa R1 (192.168.43.1)..." -ForegroundColor Green

# 1. Thu ket noi qua ADB neu loa dang mo ADB
try {
    adb connect 192.168.43.1:5555
    adb -s 192.168.43.1:5555 shell "cmd wifi connect-network '$ssid' wpa2 '$pass'"
} catch {}

# 2. Gui goi tin UDP Broadcast Wi-Fi (Port 10000 & 8000 cho ROM Phicomm/SmallQ)
try {
    $client = New-Object System.Net.Sockets.UdpClient
    $jsonObj = @{ ssid = $ssid; password = $pass; key = $pass } | ConvertTo-Json -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($jsonObj)
    
    $endPoints = @(
        New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse("192.168.43.1"), 10000),
        New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse("192.168.43.1"), 8000),
        New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse("192.168.43.255"), 10000),
        New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse("192.168.43.255"), 8000)
    )

    foreach ($ep in $endPoints) {
        $client.Send($bytes, $bytes.Length, $ep)
    }
    $client.Close()
    Write-Host "[✅ ĐÃ GỬI] Đã gửi gói tin Wi-Fi thành công qua UDP!" -ForegroundColor Green
} catch {
    Write-Host "[!] Loi gui UDP: $_" -ForegroundColor Red
}

# 3. Thu gui HTTP POST den cac cong web pho bien
$ports = @(8080, 8000, 8088, 80)
foreach ($p in $ports) {
    try {
        $url = "http://192.168.43.1:$p/wifi"
        $body = @{ ssid = $ssid; password = $pass }
        Invoke-RestMethod -Uri $url -Method Post -Body $body -TimeoutSec 1 -ErrorAction SilentlyContinue
    } catch {}
}

Write-Host "`n===================================================================" -ForegroundColor Cyan
Write-Host "  📌 Loa R1 dang nhan Wi-Fi va ket noi... Vui long cho 10-15 giay." -ForegroundColor Yellow
Write-Host "===================================================================" -ForegroundColor Cyan
