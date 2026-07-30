# Script quet IP tu dong cho loa Phicomm R1
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  CONG CU TU DONG TIM DIA CHI IP LOA PHICOMM R1 TRONG MANG" -ForegroundColor Yellow
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host ""

$ipNet = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.*' -and $_.InterfaceAlias -notlike '*Loopback*' }
if (-not $ipNet) {
    Write-Host "[!] Khong tim thay mang IPv4 dang ket noi." -ForegroundColor Red
    exit
}

$myIp = $ipNet[0].IPAddress
$subnet = $myIp.Substring(0, $myIp.LastIndexOf('.'))
Write-Host "-> IP may tinh hien tai: $myIp" -ForegroundColor Yellow
Write-Host "-> Dai mang noi bo dang quet: $subnet.1 den $subnet.254`n" -ForegroundColor Cyan
Write-Host "[*] 1. Dang kiem tra cong ADB (5555) va quet ping thiet bi..." -ForegroundColor Green

$foundList = [System.Collections.ArrayList]::new()
$threads = @()

1..254 | ForEach-Object {
    $ip = "$subnet.$_"
    $thread = [System.Threading.Tasks.Task]::Run([Action]{
        # 1. Quet cong ADB 5555
        $tcp = New-Object System.Net.Sockets.TcpClient
        try {
            $ar = $tcp.BeginConnect($ip, 5555, $null, $null)
            if ($ar.AsyncWaitHandle.WaitOne(120, $false)) {
                $tcp.EndConnect($ar)
                [void]$foundList.Add($ip)
                Write-Host "`n[TIM THAY LOA PHICOMM R1 ADB] Dia chi IP: $ip" -ForegroundColor Green
            }
        } catch {}
        finally {
            $tcp.Close()
        }

        # 2. Ping nhe de cap nhat bang ARP
        $ping = New-Object System.Net.NetworkInformation.Ping
        try {
            [void]$ping.SendAsync($ip, 120)
        } catch {}
    })
    $threads += $thread
}

try { [System.Threading.Tasks.Task]::WaitAll($threads) } catch {}
Start-Sleep -Milliseconds 500

if ($foundList.Count -eq 0) {
    Write-Host "`n[!] Chua thay thiet bi nao mo cong ADB (5555)." -ForegroundColor Yellow
    Write-Host "[*] 2. Danh sach tat ca cac thiet bi dang noi Wi-Fi (Bang ARP):`n" -ForegroundColor Cyan
    
    $arpList = arp -a | Select-String -Pattern "$subnet\."
    if ($arpList) {
        foreach ($line in $arpList) {
            $cleanLine = $line.ToString().Trim()
            if ($cleanLine -match 'd4-ee-07|fc-7c-02') {
                Write-Host "[PHICOMM R1 MAC MATCH] $cleanLine" -ForegroundColor Green
            } elseif ($cleanLine -notmatch "$myIp") {
                Write-Host "  IP thiet bi: $cleanLine" -ForegroundColor White
            }
        }
    }
}

Write-Host "`n===================================================================" -ForegroundColor Cyan
Write-Host " Meo: Neu biet IP cua loa, ban mo install.bat dan IP vao de cai!" -ForegroundColor Yellow
Write-Host "===================================================================" -ForegroundColor Cyan
