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

## 📱 HƯỚNG DẪN CÀI ĐẶT APK & GỬI WI-FI TỪ IPHONE (APP iSH) VÀ ANDROID (TERMUX)

Nếu bạn dùng iPhone hoặc Android, bạn có thể **cài file APK Web Controller và gửi Wi-Fi trực tiếp từ điện thoại sang loa Phicomm R1**:

---

### 🍏 Dành Cho iPhone / iPad (App iSH Shell) & 🤖 Android (Termux):

1. **Bật Internet (4G hoặc Wi-Fi nhà)** và mở ứng dụng Terminal:
   - Trên **iPhone/iPad**: Mở app **iSH Shell** (tải miễn phí trên App Store).
   - Trên **Android**: Mở app **Termux** (tải trên F-Droid / Play Store).
2. **Chạy 1 dòng lệnh tự động duy nhất**:

   **Cách A — Dùng `curl` (khuyên dùng cho cả iSH & Termux):**
   ```bash
   apk add curl 2>/dev/null; curl -sSL https://raw.githubusercontent.com/vinaheybird/phicomm-control/main/install_tools/termux_install.sh | sh
   ```

   **Cách B — Dùng `wget` 2 bước (nếu không có curl):**
   ```bash
   wget -O install.sh https://raw.githubusercontent.com/vinaheybird/phicomm-control/main/install_tools/termux_install.sh && sh install.sh
   ```

   > ⚠️ **Lưu ý:** `wget -qO- URL | sh` (dạng pipe trực tiếp) **không hoạt động** trên iSH vì busybox wget không follow redirect HTTPS đúng cách khi piped. Hãy dùng Cách A hoặc Cách B ở trên.

3. **Quy trình tự động 2 bước của Script**:
   - **Bước 1**: Script tải `adb` và file `PhicommGemini.apk` từ GitHub Release về điện thoại (cần 4G/Internet).
   - **Bước 2**: Khi màn hình báo tạm dừng, bạn chuyển Wi-Fi điện thoại sang mạng của loa (**`Phicomm R1`**) rồi nhấn **[ENTER]**.
   - Script sẽ tự kết nối ADB `192.168.43.1:5555`, đẩy APK lên loa, vô hiệu hóa toàn bộ app rác và hỏi Wi-Fi nhà để gửi sang loa!
4. Kết nối lại điện thoại vào Wi-Fi nhà và mở trình duyệt truy cập:
   ```http
   http://phicomm.local:8080
   ```

---


## 💻 HƯỚNG DẪN CÀI ĐẶT BẰNG MÁY TÍNH (WINDOWS / MAC)

### CÁCH 1: QUA WI-FI PHÁT RA TỪ LOA (Khuyên Dùng)
1. Kết nối Wi-Fi máy tính vào mạng `Phicomm R1`.
2. Mở thư mục `install_tools` trên máy tính và click đúp vào file **`install_direct_to_r1.bat`**.
3. File tự động nạp APK `PhicommGemini.apk` lên loa, tắt app rác và gửi Wi-Fi nhà.
4. Mở trình duyệt truy cập `http://phicomm.local:8080`.

### CÁCH 2: QUA CÁP USB (MICRO-USB)
1. Nối cáp sạc Micro-USB từ đằng sau loa R1 vào cổng USB máy tính PC.
2. Chạy file `install_tools/install_direct_to_r1.bat`.

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

