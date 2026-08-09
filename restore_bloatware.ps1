Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  PHICOMM R1 - RESTORE BLOATWARE SCRIPT   " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$adbDevices = (adb devices | Select-String -Pattern "device$")
if ($adbDevices.Count -eq 0) {
    Write-Host "Lỗi: Không tìm thấy thiết bị ADB nào đang kết nối. Vui lòng kết nối ADB trước (adb connect ...)." -ForegroundColor Red
    exit 1
}
$DeviceID = $adbDevices[0].Line.Split("`t")[0]
Write-Host "Sử dụng thiết bị: $DeviceID`n" -ForegroundColor Cyan

# Danh sách các app gốc (bloatware) của Phicomm
$apps = @(
    "launcher", 
    "player", 
    "airskill", 
    "exceptionreporter", 
    "ijetty", 
    "netctl", 
    "systemtool", 
    "device", 
    "otaservice", 
    "productiontest", 
    "bugreport"
)

foreach ($app in $apps) {
    $pkg = "com.phicomm.speaker.$app"
    Write-Host "Đang khôi phục: $pkg" -ForegroundColor Yellow
    adb -s $DeviceID shell /system/bin/pm unhide $pkg
}

Write-Host "`n✅ Đã khôi phục xong bloatware! Vui lòng khởi động lại loa (adb reboot) để có hiệu lực hoàn toàn." -ForegroundColor Green
