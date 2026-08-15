# hijack_dns.ps1
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  PHICOMM R1 - DNS HIJACKING SCRIPT       " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$adbDevices = (adb devices | Select-String -Pattern "device$")
if ($adbDevices.Count -eq 0) {
    Write-Host "Error: No ADB device found." -ForegroundColor Red
    exit 1
}
$DeviceID = $adbDevices[0].Line.Split("`t")[0]
Write-Host "Using device: $DeviceID`n" -ForegroundColor Cyan

$PC_IP = Read-Host "Please enter the IP address of this computer (where Fake Server will run, e.g., 192.168.1.x)"

if (-not $PC_IP) {
    Write-Host "Error: IP address cannot be empty." -ForegroundColor Red
    exit 1
}

Write-Host "Restarting ADB as root..." -ForegroundColor Yellow
adb -s $DeviceID root
Start-Sleep -Seconds 3

Write-Host "Remounting /system as read-write..." -ForegroundColor Yellow
adb -s $DeviceID remount
Start-Sleep -Seconds 2

# The domains we discovered from logcat
$domains = @(
    "aios-home.hivoice.cn",
    "home.phicomm.com"
)

foreach ($domain in $domains) {
    Write-Host "Hijacking domain $domain -> $PC_IP" -ForegroundColor Green
    adb -s $DeviceID shell "echo '$PC_IP $domain' >> /system/etc/hosts"
}

Write-Host "Checking /etc/hosts content..." -ForegroundColor Yellow
adb -s $DeviceID shell cat /system/etc/hosts

Write-Host "==========================================" -ForegroundColor Magenta
Write-Host " DNS HIJACKING COMPLETE! " -ForegroundColor Green
Write-Host " Please reboot the speaker (adb reboot) for changes to take effect." -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Magenta
