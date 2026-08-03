# 🔊 Phicomm R1 Bluetooth Web Controller (Loa Bluetooth Điều Khiển Qua Trang Web)

Dự án chuyển đổi loa **Phicomm R1 (Feixun R1)** thành **Loa Bluetooth cao cấp điều khiển dễ dàng qua Trang Web (Web Controller)**. Hoạt động 100% bằng Dịch vụ Android ngầm chạy trực tiếp trên loa, cho phép bạn truy cập từ bất kỳ điện thoại hoặc máy tính nào cùng mạng Wi-Fi qua địa chỉ `http://phicomm.local:8080`.

---

## 🔥 TÍNH NĂNG NỔI BẬT

1. **Bật / Tắt Bluetooth Chủ Động**:
   - Công tắc (Toggle) bật/tắt phát sóng Bluetooth trực tiếp trên giao diện Web.

2. **Quản Lý Kết Nối & Chuyển Đổi Thiết Bị Nhanh (1-Click Switch)**:
   - Danh sách thiết bị Bluetooth đã ghép đôi hiển thị trực quan.
   - Nút bấm **"Kết nối" 1-click** cho phép chuyển đổi nguồn phát nhạc giữa điện thoại, máy tính, máy tính bảng cực kỳ mượt mà.
   - Nút **"Bật Dò Tìm" (Discoverable)** giúp thiết bị mới dễ dàng ghép đôi với loa trong 5 phút.
   - Tính năng **Tự động kết nối lại (Auto-reconnect)** thiết bị vừa phát nhạc gần nhất khi bật Bluetooth hoặc khởi động loa.

3. **Tắt Âm Thanh Thông Báo Bluetooth Quá To (Prompt Mute)**:
   - Tự động tắt tiếng (mute) các âm thanh nói/thông báo giọng nói mặc định quá to của loa Phicomm R1 khi bật/tắt hoặc ngắt/kết nối Bluetooth.

4. **Điều Khiển Đèn LED & Tiết Kiệm Điện**:
   - Công tắc Bật/Tắt dải đèn LED RGB (Tắt LED giúp tiết kiệm thêm 0.5W - 1W điện năng).
   - Tùy chỉnh màu sắc & hiệu ứng đèn LED (Xanh lam, Xanh nháy nhẹ, Vàng cam, Xanh lá, Tắt hẳn).

5. **Vô Hiệu Hóa App Rác Mặc Định (Phương Pháp Xiaozhi)**:
   - Cung cấp sẵn script ADB tự động tắt (`pm hide` / `pm disable`) toàn bộ các ứng dụng rác mặc định của Phicomm (`com.phicomm.speaker.*`), giúp loa khởi động nhanh, giải phóng RAM và CPU tải chỉ <2%.

---

## ⚡ MỨC TIÊU THỤ ĐIỆN NĂNG

- **Chế độ chờ (Idle - Cắm điện 24/7)**: ~2W - 4W (Tiền điện chỉ khoảng **4.000 - 5.000 VNĐ / tháng**).
- **Phát nhạc vừa phải**: ~5W - 8W.
- **Phát nhạc tối đa công suất**: ~15W - 20W.
- **Tắt dải đèn LED**: Giảm bớt 0.5W - 1W điện năng tiêu thụ.

---

## 📱 HƯỚNG DẪN CÀI ĐẶT QUA GITHUB (iSH SHELL / TERMUX / LINUX / MAC)

Dành cho **iPhone/iPad** (App **iSH Shell**) & **Android** (**Termux**).  
Do `curl` và pipe trực tiếp thường gặp lỗi trên iSH, hãy dùng lệnh `wget` 2 bước chuẩn dưới đây:

---

### 🚀 Lệnh Cài Đặt Tự Động (Dùng `wget` tương thích 100% trên iSH & Termux):

```bash
wget -O install.sh https://raw.githubusercontent.com/vinaheybird/phicomm-control/main/install.sh && sh install.sh
```

Hoặc nếu muốn truyền sẵn Tên Wi-Fi và Mật Khẩu nhà:
```bash
wget -O install.sh https://raw.githubusercontent.com/vinaheybird/phicomm-control/main/install.sh && sh install.sh "Tên_WiFi_Nhà" "Mật_Khẩu"
```

---

### 📋 Quy Trình Tự Động Của Script:
1. **Tải ADB & APK**: Tải file `PhicommGemini.apk` trực tiếp từ GitHub về máy.
2. **Nhập Wi-Fi**: Script sẽ hỏi Tên & Mật Khẩu Wi-Fi nhà bạn (nếu chưa truyền vào).
3. **Kết Nối Loa**: Bạn kết nối Wi-Fi máy/điện thoại vào Hotspot của loa (`Phicomm_R1_xxxx`) và nhấn **[ENTER]**.
4. **Cài Đặt & Nối Wi-Fi**: Script tự động cài APK, ẩn app rác Phicomm cũ và phát Intent kết nối Wi-Fi nhà cho loa tức thì!

Sau khi hoàn tất, kết nối điện thoại/máy tính về Wi-Fi nhà và truy cập:
```http
http://phicomm.local:8080
```
---

## 🌐 GIAO DIỆN WEB DASHBOARD (`http://phicomm.local:8080`)

Mở trình duyệt bất kỳ truy cập `http://phicomm.local:8080` (hoặc IP của loa) để điều khiển:
- **Công tắc Bật/Tắt Bluetooth**
- **Chọn & kết nối thiết bị Bluetooth 1-click**
- **Công tắc Bật/Tắt Đèn LED & chọn màu**
- **Công tắc Tắt âm thông báo Bluetooth to**
- **Thanh trượt chỉnh âm lượng loa**

---

## 🛠️ HƯỚNG DẪN BUILD APK BẰNG GRADLE (DÀNH CHO DEVELOPER)

### 1. Yêu Cầu Môi Trường
- **JDK 17** (Ví dụ: OpenJDK 17 tại `C:\Program Files\Microsoft\jdk-17...`)
- **Android SDK** (Thường nằm tại `%LOCALAPPDATA%\Android\Sdk` hoặc `C:\Users\<user>\AppData\Local\Android\Sdk`)

---

### 2. Các Lệnh Build APK Debug

#### 💻 Trên Windows Command Prompt (CMD):
```cmd
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot
set ANDROID_HOME=C:\Users\zkenz\AppData\Local\Android\Sdk
gradlew.bat assembleDebug
```

#### ⚡ Trên Windows PowerShell:
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

# Cách 1: Gọi gradlew.bat qua cmd
cmd /c "gradlew.bat assembleDebug"

# Cách 2: Gọi trực tiếp binary Gradle (nếu gradlew.bat gặp lỗi wrapper path)
$GRADLE_BIN = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.5-bin\5t9huq95ubn472n8rpzujfbqh\gradle-8.5\bin\gradle.bat"
& $GRADLE_BIN assembleDebug
```

#### 🐧 Trên Linux / macOS:
```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug
```

---

### 3. Vị Trí File APK Sau Khi Build
File APK tạo ra tại:
```
app/build/outputs/apk/debug/app-debug.apk
```

Để cập nhật bản APK dùng cho các script tự động cài đặt, copy file sang `install_tools`:
```powershell
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "install_tools\PhicommGemini.apk" -Force
```

