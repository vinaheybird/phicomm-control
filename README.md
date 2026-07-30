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

1. **Kết nối Wi-Fi**: Kết nối điện thoại vào mạng Wi-Fi loa phát ra (tên **`Phicomm R1`** hoặc **`Phicomm_R1_xxxx`**).
2. **Mở ứng dụng Terminal**:
   - Trên **iPhone/iPad**: Mở app **iSH Shell** (tải miễn phí trên App Store).
   - Trên **Android**: Mở app **Termux** (tải trên F-Droid / Play Store).
3. **Chạy 1 dòng lệnh tự động duy nhất**:
   ```bash
   curl -sSL https://raw.githubusercontent.com/vinaheybird/phicomm-control/main/install_tools/termux_install.sh | sh
   ```
   *(Script sẽ tự động cài `adb`, tự tải file APK `PhicommGemini.apk` từ GitHub Release, nạp lên loa R1, tắt app rác và hỏi tên/mật khẩu Wi-Fi nhà bạn để gửi sang loa!)*

4. Sau khi hoàn tất, kết nối lại Wi-Fi nhà và mở trình duyệt truy cập:
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
