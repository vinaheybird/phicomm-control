Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   PHICOMM R1 - HIDE BLOATWARE SCRIPT     " -ForegroundColor Cyan
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
    Write-Host "Đang ẩn: $pkg" -ForegroundColor Yellow
    adb -s $DeviceID shell /system/bin/pm hide $pkg
}

Write-Host "`n✅ Đã ẩn xong bloatware! Khuyên dùng: Khởi động lại loa (adb reboot) để giải phóng hoàn toàn RAM." -ForegroundColor Green
