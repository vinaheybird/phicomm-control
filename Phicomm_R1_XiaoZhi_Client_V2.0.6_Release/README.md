# Phicomm R1 Chuyên Dụng XiaoZhi —— Bản Phát Hành V2.0.6 (Bộ Cài Đặt)

Gói phát hành này **chỉ chứa các thành phần để cài đặt vào loa**: 3 file APK, script cài đặt tự động, bộ công cụ adb và tài liệu hướng dẫn.
**Không bao gồm mã nguồn** (xem tại GitHub repository) và **không bao gồm Docker server** (xem tại repository MyXiaoZhi_MediaServer).

| File | Mô tả |
|---|---|
| `apps/XiaoZhi_Unisound_V2.0.6.apk` | Gói chính (XiaoZhi gốc, chạy ký sinh trên Pandora nguyên bản của loa) |
| `apps/XiaoZhi_EchoService_V2.0.6.apk` | Gói điều khiển (Console Web, ký sinh trên EchoService nguyên bản, giao diện Web cổng `:8080`) |
| `apps/XiaoZhi_SoHost_V2.0.1.apk` | Gói SO Host (Thư viện giải mã/mã hóa âm thanh Opus; trên hệ thống không root chỉ có trình cài đặt hệ thống mới giải nén được file .so này) |
| `一键安装.bat` / `Cai_Dat_Nhanh.bat` | Script cài đặt tự động (kèm bộ 3 file `adb.exe`, không chạy bằng Git Bash) |
| `说明.txt` / `Huong_Dan_Cai_Dat.txt` | Hướng dẫn tóm tắt: Đọc trước khi cài + Điểm mới + Lưu ý |
| `安装使用说明_通用版.md` | Hướng dẫn sử dụng chi tiết: 6 trang giao diện Web, 37 công cụ điều khiển giọng nói MCP, FAQ giải đáp thắc mắc |
| `md5.txt` | Mã kiểm tra băm MD5 của 3 file APK |

**Bảng đối chiếu phiên bản**:

| Gói | Tên file | Phiên bản bên trong | Baseline (Thử nghiệm) |
|---|---|---|---|
| device (XiaoZhi gốc) | `XiaoZhi_Unisound_V2.0.6.apk` | `XzBootstrap.VERSION = 2.0.6` | `v1.3.360` |
| player (Web Console) | `XiaoZhi_EchoService_V2.0.6.apk` | `Boot.VER = pl-2.0.6` | `pl-1.6.163` |
| so host | `XiaoZhi_SoHost_V2.0.1.apk` | Dùng tiếp V2.0.1 | — |

> Khi nâng cấp, hãy **đối chiếu mã MD5**, không chỉ nhìn vào chuỗi phiên bản.

---

## Hướng Dẫn Cài Đặt / Nâng Cấp

1. **Firmware của loa R1 bắt buộc phải là 3448** (script sẽ tự động kiểm tra, nếu không phải 3448 sẽ khóa menu cài đặt để tránh lỗi câm loa/mất wake word).
2. Loa và máy tính cùng chung một mạng WiFi/LAN, loa đã bật tính năng gỡ lỗi mạng ADB (port 5555).
3. Nhấp đúp chuột vào `一键安装.bat` hoặc `Cai_Dat_Nhanh.bat` (chạy trên CMD hoặc PowerShell của Windows, **KHÔNG dùng Git Bash** vì Git Bash sẽ nuốt mất dấu ngoặc kép trong lệnh).
   Nhập IP của loa (ví dụ `192.168.2.5:5555`, nếu nối bằng cáp USB thì nhập `usb`).
4. Chọn menu **`[1] Cài đặt nhanh toàn bộ`** để cài cả 3 gói APK và tự động khởi động dịch vụ. Nâng cấp từ bản cũ cũng chọn mục này (cài đè trực tiếp, danh sách Yêu thích, Báo thức, Chương trình của tôi và mọi cài đặt đều được giữ nguyên).
5. Mở trình duyệt web truy cập `http://<IP_Loa>:8080/` → Xem thẻ **"Kích hoạt"** ở trên đầu trang để lấy mã 6 chữ số → Nhập vào server XiaoZhi để ghép nối thiết bị.
6. Nếu bạn dùng server tự dựng (local server), sau khi cài đặt xong hãy dùng menu **`[6]`** để điền lại địa chỉ server một lần nữa (việc cài mới sẽ làm trống địa chỉ OTA).

Các tùy chọn thông dụng khác trong Menu:

| Phím | Chức năng |
|---|---|
| `[1]` | Cài đặt nhanh toàn bộ: so host + gói chính + console + khởi động + chờ cổng 8080 sẵn sàng |
| `[2]` `[3]` `[4]` | Cài đặt riêng từng gói (Mục `[3]` gói chính sẽ cài kèm gói console và khởi động lại dịch vụ) |
| `[5]` | Mở nhanh Web Console trên trình duyệt |
| `[6]` | Cài đặt địa chỉ Server (Chính thức / Server tự dựng nội bộ) |
| `[7]` `[8]` | Khởi động lại XiaoZhi / Khởi động lại toàn bộ loa (Reboot) |
| `[9]` | Xem trực tiếp log hoạt động của loa (`files/r1client.log`) |
| `[v]` | **Đối chiếu phiên bản**: Phiên bản file APK trong gói + Thời gian cài đặt thực tế của 2 gói trên loa (phải là thời gian vừa xong) |
| `[s]` | Mở giao diện dòng lệnh adb shell của loa |

Script cũng sẽ tự động kiểm tra dung lượng còn trống của bộ nhớ `/data` trước khi cài, và hiển thị lại phiên bản sau khi cài hoàn tất.

> **Tại sao số phiên bản đọc từ tên file chứ không đọc từ thiết bị?**
> Vì 2 file APK sử dụng kỹ thuật Janus để gắn mã tùy chỉnh vào gói gốc của nhà sản xuất, **file manifest vẫn là của hãng**, nên lệnh `dumpsys` sẽ luôn hiển thị số phiên bản gốc là `V3.2.5.2-` / `0.2.5`.
> Do đó, kiểm tra phiên bản thì xem tên file, còn kiểm tra xem "đã cài vào loa chưa" thì xem thời gian cập nhật `lastUpdateTime`.

**Lưu ý khi nâng cấp:**
- Sau khi cài bất kỳ gói APK nào, **tiến trình phát nhạc sẽ khởi động lại** → Danh sách phát tạm thời sẽ bị làm trống; danh sách bài hát Yêu thích, Báo thức, Chương trình của tôi không bị ảnh hưởng. Bạn chỉ cần nhấn lại "Nạp danh sách phát và nghe nhạc".
- Nâng cấp từ V2.0.5 hoặc cũ hơn: Trình duyệt có thể lưu cache trang cũ, bạn chỉ cần nhấn F5 (Ctrl + F5) để làm mới.
- Server cần phiên bản ≥ **V1.7.55** để có tính năng "Chuyển mã âm thanh theo danh sách thiết bị": Trong trang quản lý server "Nguồn âm thanh → Chuyển mã âm thanh", tích chọn R1 để xuất hiện nhãn "Chuyển mã"; nếu không tích thì truyền luồng trực tiếp.

---

## Các Điểm Mới Trên Bản V2.0.6 (So với V2.0.5)

Bản V2.0.5 xây dựng trên baseline: device `v1.3.329` + player `pl-1.6.148`.
Bản V2.0.6 nâng cấp baseline lên: device `v1.3.360` + player `pl-1.6.163`.

Trọng tâm chính là **Hệ thống ghi nhớ và báo cáo tiến độ phát nhạc / sách nói**: Đảm bảo chuỗi "Ai đang nghe, nghe đến đâu, đổi sang thiết bị khác có nghe tiếp được không" hoạt động chính xác.
Đồng thời bổ sung tính năng đọc thời lượng/tua bằng giọng nói, gọi tên phát tập con trong bộ sách, tìm kiếm 3 cấp (Nội bộ / Cache / Online), và sửa điểm bắt đầu nghe tiếp (không còn bị nhảy vào tập 0 giới thiệu).

### 1. Báo cáo tiến độ / Lịch sử phát nhạc (Trọng tâm chính)
- **Tạm dừng là chốt tiến độ ngay**: Tạm dừng trên Web, bấm nút cứng trên loa, ra lệnh giọng nói "dừng lại", hay tắt nguồn thiết bị đều lập tức lưu và gửi báo cáo tiến độ lên server.
- **Đồng bộ vị trí kèm định danh thiết bị**: Endpoint `/v1/pos` và URL lấy luồng nhạc đều kèm tham số `dev=` (địa chỉ MAC 12 ký tự), khi triển khai Docker sẽ không bị nhầm lẫn vào luồng của Web.
- **Bắt đầu phát là báo ngay**: Cứ mỗi 2 giây kiểm tra nhẹ trạng thái player, khi trạng thái đổi hoặc đủ 15 giây mới gửi nhịp tick; không còn phải tải toàn bộ danh sách phát chỉ để kiểm tra trạng thái.
- **Mở rộng ngưỡng vị trí**: Lúc vừa phát vị trí là 0 giây, bản cũ có điều kiện "vị trí < 8 giây không báo" làm mất nhịp báo cáo ban đầu, nay đã được sửa.
- **Đóng dấu tập vào hàng đợi**: Bên nào đưa sách vào hàng đợi thì ghi rõ thông tin `album`, tránh nhầm lẫn.
- **Chia sẻ tiến độ ở cấp độ bộ sách**: Khi nghe tiếp, hệ thống hỏi server `/v1/resume?q=&dev=`, nếu có dữ liệu sẽ ghi ngược về máy cục bộ. Đổi sang loa khác vẫn tiếp tục nghe được đoạn đang dở.

### 2. Định danh sách nói duy nhất
- Tên sách của tập con = Tên chính tập đó (ví dụ "Khu Rừng Đen II", "Tử Thần Vĩnh Sinh III"), tên bộ sách chỉ dùng để hiển thị.
- Trong lịch sử phát không ghép tiền tố tên cha, tránh việc cả bộ 3 tập bị ghi đè tiến độ của nhau.
- Nghiêm cấm lấy bừa "cuốn sách trước đó" để bù vào: Nếu không nhận diện được tên sách thì không gửi báo cáo, thà thiếu còn hơn sai.

### 3. Điều khiển bằng giọng nói cho bộ sưu tập sách (Collection)
- Cho phép phát trực tiếp tên tập con cụ thể: Nói "Phát Khu Rừng Đen" sẽ phát đúng tập đó, không bị nhảy ngược về tập đầu tiên của bộ sách.
- Tìm kiếm file cục bộ và bộ nhớ nghe tiếp theo tên tập con đồng nhất với sách đơn lẻ.

### 4. Đọc thời lượng / Tua nhanh / Tua lùi / Nhảy tới vị trí bằng giọng nói
- Công cụ MCP mới `self.r1player.seek_play` (Công cụ thứ 37).
- Tham số `sec`: số giây tuyệt đối (Ví dụ "Nhảy tới phút thứ 12" → 720); `delta`: số giây tương đối (Ví dụ "Tua nhanh 30 giây" → 30, "Tua lùi 2 phút" → -120); Không truyền tham số = **Chỉ tra cứu tiến độ**.
- Câu lệnh `status` ở cuối sẽ đọc thêm: "Đã phát đến X, tổng thời lượng Y, còn lại Z". Khi có tên sách sẽ đọc kèm "Tập N".
- Luồng phát thanh trực tiếp (Radio) không có tổng thời lượng sẽ từ chối tua và thông báo rõ ràng.
- Tua quá giới hạn sẽ tự động neo ở mốc trước khi hết bài 2 giây, tránh bị nhảy ngay sang bài kế tiếp.

### 5. Thứ tự tìm kiếm khi tiếp tục phát: Kho nội bộ → Cache → Online
- Nếu sách nội bộ bị thiếu tập (ví dụ trong máy chỉ có 3 tập nhưng online có 2113 tập), hệ thống sẽ tự động chuyển sang bản Online chứ không báo "Không tìm thấy lịch sử phát".
- Tự động sửa lại định danh albumId sang bản online để các lần sau phát mượt mà không gặp lại lỗi.

### 6. Khắc phục điểm phát khi tiếp tục nghe sách
- Lệnh `ab_play` không kèm số tập = Tiếp tục nghe đoạn dở lần trước: Lần lượt hỏi tiến độ từ server → Chương trình của tôi → Bộ nhớ trên loa; sau khi phát sẽ tự động lùi lại 10 giây để người nghe dễ bắt nhịp.
- Sửa lỗi nói tên sách bị phát nhầm vào **Tập 0 (Trailer / Giới thiệu)**.
- Khi đưa vào hàng đợi phát sẽ tự động lọc bỏ các file có tập ≤ 0 (nhạc dạo, nhạc kết phim, lời giới thiệu, trailer quảng cáo...).

### 7. Giao diện Web Console / Player
- Tạm dừng thủ công không bị XiaoZhi tự động phát tiếp sau khi trả lời câu hỏi.
- Khắc phục triệt để độ trễ nút bấm: `WebSrv` chuyển sang cơ chế xử lý đa luồng (mỗi kết nối một luồng riêng).
- Hỗ trợ cứu tập thiếu theo yêu cầu trực tiếp trên giao diện Web.

---

## Địa Chỉ Dự Án & Bản Quyền

- Mã nguồn R1 Client: https://github.com/shj4588/Phicomm_R1_XiaoZhi_Client
- Server Media: https://github.com/shj4588/MyXiaoZhi_MediaServer

Mã nguồn gốc thuộc bản quyền của Phicomm / Unisound. Mã nguồn tùy chỉnh phát hành theo giấy phép PolyForm Noncommercial 1.0.0 (Dùng cho mục đích cá nhân, học tập, phi thương mại).
