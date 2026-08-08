param (
    [switch]$SkipBuild = $false
)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "      PHICOMM GEMINI - DEPLOY SCRIPT      " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Build APK
if (-not $SkipBuild) {
    Write-Host "`n[1/3] Đang build APK..." -ForegroundColor Yellow
    cmd.exe /c "java -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleDebug 2>&1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Lỗi: Build APK thất bại!" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "`n[1/3] Bỏ qua bước build APK (--SkipBuild)" -ForegroundColor Yellow
}

$ApkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $ApkPath)) {
    Write-Host "Lỗi: Không tìm thấy file APK tại $ApkPath" -ForegroundColor Red
    exit 1
}

# Lấy ID của device đang cắm ADB
$adbDevices = (adb devices | Select-String -Pattern "device$")
if ($adbDevices.Count -eq 0) {
    Write-Host "Lỗi: Không tìm thấy thiết bị ADB nào đang kết nối. Vui lòng kết nối ADB trước (adb connect ...)." -ForegroundColor Red
    exit 1
}
$DeviceID = $adbDevices[0].Line.Split("`t")[0]
Write-Host "Sử dụng thiết bị: $DeviceID" -ForegroundColor Cyan

# 2. Push APK vào /sdcard/ (bắt buộc như user yêu cầu)
Write-Host "`n[2/3] Đang copy APK vào loa (/sdcard/PhicommGemini.apk)..." -ForegroundColor Yellow
adb -s $DeviceID push $ApkPath /sdcard/PhicommGemini.apk
if ($LASTEXITCODE -ne 0) {
    Write-Host "Lỗi: Copy APK vào loa thất bại!" -ForegroundColor Red
    exit 1
}

# 3. Cài đặt APK từ /sdcard/
Write-Host "`n[3/3] Đang cài đặt APK..." -ForegroundColor Yellow
$installOutput = adb -s $DeviceID shell /system/bin/pm install -r -t /sdcard/PhicommGemini.apk
Write-Host $installOutput
if ($installOutput -match "Success") {
    Write-Host "`n✅ Cài đặt thành công!" -ForegroundColor Green
    
    Write-Host "Đang đóng băng app gốc của loa để tránh xung đột Bluetooth..." -ForegroundColor Yellow
    adb -s $DeviceID shell pm hide com.phicomm.speaker.player
    adb -s $DeviceID shell pm disable-user --user 0 com.phicomm.speaker.player

    Write-Host "Đang khởi động lại dịch vụ trên loa..." -ForegroundColor Yellow
    adb -s $DeviceID shell am force-stop com.phicomm.gemini
    adb -s $DeviceID shell am startservice com.phicomm.gemini/.PhicommGeminiService
    Write-Host "Đã xong! Bạn có thể vào Web Dashboard tại cổng 8080." -ForegroundColor Green
} else {
    Write-Host "`n❌ Cài đặt thất bại! Vui lòng kiểm tra log ở trên." -ForegroundColor Red
    exit 1
}
