# sniff_network.ps1
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  PHICOMM R1 - NETWORK SNIFFER SCRIPT     " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$adbDevices = (adb devices | Select-String -Pattern "device$")
if ($adbDevices.Count -eq 0) {
    Write-Host "Error: No ADB device found." -ForegroundColor Red
    exit 1
}
$DeviceID = $adbDevices[0].Line.Split("`t")[0]
Write-Host "Using device: $DeviceID`n" -ForegroundColor Cyan

Write-Host "Clearing old logcat..." -ForegroundColor Yellow
adb -s $DeviceID logcat -c

Write-Host "==========================================" -ForegroundColor Magenta
Write-Host " PLEASE SAY 'XIAO FEI XIAO FEI' TO THE SPEAKER " -ForegroundColor Green
Write-Host " AND WAIT 5-10 SECONDS TO CAPTURE THE NETWORK LOG " -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Magenta
Write-Host "Listening (Press Ctrl+C to stop)..." -ForegroundColor Yellow

# Filter logcat for common DNS or HTTP requests
adb -s $DeviceID logcat | Select-String -Pattern "http|https|peiluyou|phicomm|aispeech|dui\.ai|dns|resolve|audio"
