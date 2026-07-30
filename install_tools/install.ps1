# PowerShell One-Click Installer for Phicomm R1 Gemini Speaker
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  🔊 CÔNG CỤ CÀI ĐẶT 1-CLICK LOA PHICOMM R1 TRỢ LÝ GEMINI FREE   " -ForegroundColor Yellow
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host ""

$ip = Read-Host "-> Nhập địa chỉ IP của loa Phicomm R1 (Ví dụ 192.168.1.50)"

if ([string]::IsNullOrWhiteSpace($ip)) {
    Write-Host "[!] Địa chỉ IP không được để trống!" -ForegroundColor Red
    exit
}

Write-Host "`n[*] Đang kết nối ADB tới loa tại $ip:5555..." -ForegroundColor Green
adb connect "${ip}:5555"

Write-Host "`n[*] Đang cài đặt PhicommGemini.apk..." -ForegroundColor Green
adb -s "${ip}:5555" install -r -g PhicommGemini.apk

Write-Host "`n[*] Đang cấp quyền hệ thống..." -ForegroundColor Green
adb -s "${ip}:5555" shell pm grant com.phicomm.gemini android.permission.RECORD_AUDIO
adb -s "${ip}:5555" shell pm grant com.phicomm.gemini android.permission.WRITE_EXTERNAL_STORAGE
adb -s "${ip}:5555" shell pm grant com.phicomm.gemini android.permission.READ_EXTERNAL_STORAGE

Write-Host "`n[*] Khởi chạy Dịch vụ Gemini trên loa R1..." -ForegroundColor Green
adb -s "${ip}:5555" shell am startservice -n com.phicomm.gemini/.service.PhicommGeminiService
adb -s "${ip}:5555" shell am start -n com.phicomm.gemini/.MainActivity

Write-Host "`n===================================================================" -ForegroundColor Cyan
Write-Host "  🎉 THÀNH CÔNG! Mở trình duyệt truy cập: http://${ip}:8080" -ForegroundColor Green
Write-Host "===================================================================" -ForegroundColor Cyan
