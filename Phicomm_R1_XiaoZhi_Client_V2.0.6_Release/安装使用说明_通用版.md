# Loa Phicomm R1 Chạy XiaoZhi —— Hướng Dẫn Cài Đặt & Sử Dụng Toàn Diện

> Áp dụng cho bản phát hành **V2.0.6** (`XiaoZhi_Unisound_V2.0.6` / `XiaoZhi_EchoService_V2.0.6` / `XiaoZhi_SoHost_V2.0.1`),
> Phía server tương thích với **MyXiaoZhi_MediaServer ≥ V1.7.55**.
> Trong tài liệu này: `<IP_Loa>` là địa chỉ IP mạng nội bộ của loa R1 (ví dụ `192.168.2.5`), `<IP_NAS>` là địa chỉ máy chủ chạy Docker (ví dụ `192.168.2.10`).

**Địa Chỉ Dự Án**

| Thành phần | Đường dẫn |
|---|---|
| Client XiaoZhi cho loa R1 (Gói cài đặt & Mã nguồn đầy đủ) | https://github.com/shj4588/Phicomm_R1_XiaoZhi_Client |
| **MyXiaoZhi_MediaServer** (Server Kho nhạc / Sách nói / Lịch sử phát) | https://github.com/shj4588/MyXiaoZhi_MediaServer |

> Server kho nhạc ban đầu có tên là `R1XiaoZhi_MediaServer`, **từ v1.6.8 đổi tên thành `MyXiaoZhi_MediaServer`**.
> Các container đã triển khai theo tên cũ vẫn chạy bình thường, dữ liệu trong `files/` không bị ảnh hưởng. Khi cài mới vui lòng dùng tên mới.

**Bảng Đối Chiếu Phiên Bản**

| Gói | Tên file | Phiên bản bên trong | Baseline |
|---|---|---|---|
| device (XiaoZhi gốc, chạy trên Pandora) | `XiaoZhi_Unisound_V2.0.6.apk` | `2.0.6` | `1.3.360` |
| player (Web Console, chạy trên EchoService) | `XiaoZhi_EchoService_V2.0.6.apk` | `pl-2.0.6` | `pl-1.6.163` |
| so host (Thư viện Opus SO, cài qua hệ thống) | `XiaoZhi_SoHost_V2.0.1.apk` | `2.0.1` (dùng tiếp) | — |

**Tổng Quan: Toàn Bộ Hệ Thống Gồm 4 Thành Phần**

| Thành phần | Nơi chạy | Cổng | Chức năng |
|---|---|---|---|
| Client XiaoZhi R1 (3 file APK) | Trên loa R1 | 8080 (Web Console) | Thay thế trợ lý giọng nói đám mây của hãng, kết nối server XiaoZhi, giữ nguyên phần cứng 4 mic khử ồn AEC |
| **MyXiaoZhi_MediaServer** (Docker) | NAS / Máy chủ Docker | **8100** | Cổng cấp nhạc tổng hợp: Nhạc nội bộ + Nhạc online + Radio mạng + Sách nói + Lưu tiến độ/Phát tiếp |
| **LxServer (Lạc Tuyết)** (Docker) | NAS / Máy chủ Docker | 9527 | Kênh tìm kiếm và lấy link phát nhạc online từ các nền tảng |
| Server XiaoZhi | Server chính thức hoặc tự dựng | 8003(OTA) / 8000(WS) | Bộ não AI xử lý giọng nói; chính thức là `api.tenclass.net`, tự dựng là `xiaozhi-esp32-server` |

```
Loa Phicomm R1 ── Cổng 8080 Web Console ── Trình duyệt máy tính / điện thoại
   │
   ├─ Giao thức WS giọng nói ── Server XiaoZhi (Chính thức / Tự dựng)
   │
   └─ Nguồn nhạc http://<IP_NAS>:8100/index.json
              └── MyXiaoZhi_MediaServer ── LxServer:9527 ── Các nền tảng âm nhạc
```

---

## §1 Điều Kiện Tiên Quyết

1. **Firmware của loa R1 bắt buộc phải là 3448**. Không cài trên các phiên bản khác (script cài đặt sẽ tự động kiểm tra và từ chối nếu sai bản).
   Cách kiểm tra: chạy lệnh `adb shell getprop ro.build.version.incremental`. Nếu không phải 3448, vui lòng dùng công cụ flash ROM gốc của R1 để nâng cấp trước.
2. Loa và máy tính / NAS nằm trong **cùng mạng LAN**, tính năng WiFi ADB của loa đã được bật (địa chỉ dạng `192.168.2.5:5555`).
3. Một thiết bị chạy Docker (Synology Container Manager, fnOS, QNAP, Linux trên PVE, mini PC x86, Raspberry Pi ARM...).
4. (Tùy chọn) Server XiaoZhi tự dựng: `xiaozhi-esp32-server`...; nếu không muốn tự dựng có thể kết nối thẳng server chính thức.

---

## §2 Cài Đặt APK Vào Loa R1

### 2.1 Cài đặt tự động bằng script (Khuyến nghị)

Giải nén thư mục cài đặt trên máy tính Windows, **nhấp đúp chuột vào `一键安装.bat` hoặc `Cai_Dat_Nhanh.bat`** (mở bằng CMD/Explorer, không dùng Git Bash):

1. Khi được hỏi IP, nhấn Enter nếu dùng IP mặc định `192.168.2.5:5555` (hoặc nhập IP thực tế của loa; dùng cáp USB thì gõ `usb`).
2. Sau khi kết nối thành công, chọn **`[1] Cài đặt nhanh toàn bộ`**: script sẽ tự động tải lên 3 file APK → đối chiếu dung lượng byte → cài đặt → kích hoạt dịch vụ → đợi cổng 8080 sẵn sàng.
3. Khi thấy 3 lần báo `Success` và dòng chữ `CÀI ĐẶT HOÀN TẤT` là xong, toàn bộ quá trình mất khoảng 2 phút.

### 2.2 Cài đặt thủ công qua dòng lệnh

Mở CMD hoặc PowerShell:

```bat
adb connect 192.168.2.5:5555

adb push apps\XiaoZhi_SoHost_V2.0.1.apk     /data/local/tmp/sohost.apk
adb push apps\XiaoZhi_Unisound_V2.0.6.apk   /data/local/tmp/jp2.apk
adb push apps\XiaoZhi_EchoService_V2.0.6.apk /data/local/tmp/echo.apk

adb shell "pm install -r -d /data/local/tmp/sohost.apk"
adb shell "pm install -r -d /data/local/tmp/jp2.apk"
adb shell "pm install -r -d /data/local/tmp/echo.apk"

adb shell am startservice -n com.phicomm.speaker.device/.ui.service.WindowsService
adb shell am startservice -n com.phicomm.speaker.player/.EchoService
```

**Bắt buộc phải cài đủ cả 3 gói**, thiếu SoHost thì mic sẽ không mã hóa được âm thanh gửi lên server. Cài xong hãy đợi 15~40 giây rồi mới mở cổng `:8080`.

> **Lưu ý**: Lệnh cài đặt bắt buộc phải bao trong dấu ngoặc kép (`adb shell "pm install ..."`). Nếu bỏ ngoặc kép, lệnh có thể không được thực thi đúng.

### 2.3 Kiểm tra xác thực

- Dùng trình duyệt mở `http://<IP_Loa>:8080/`, dưới chân trang sẽ thấy phiên bản (`2.0.6` / `pl-2.0.6`).
- Gọi câu lệnh đánh thức: **"XiaoZhi XiaoZhi"** (Tiểu Trí) hoặc "XiaoXun XiaoXun" (Tiểu Tấn - tên gốc của hãng).

---

## §3 Cài Đặt LxServer (Docker) —— Kênh Lấy Nhạc Trực Tuyến

> Nhạc trực tuyến không do loa tự cào mà được trung gian qua LxServer. Nếu không cài LxServer thì không nghe được nhạc online (nhạc nội bộ trong NAS, sách nói, radio vẫn hoạt động bình thường).

### 3.1 Khởi chạy container

Tạo file `docker-compose.yml`:

```yaml
services:
  lxserver:
    image: xcq0607/lxserver:latest
    container_name: lxserver
    restart: unless-stopped
    ports:
      - "9527:9527"
    volumes:
      - ./data:/server/data
      - ./logs:/server/logs
      - ./cache:/server/cache
    environment:
      - TZ=Asia/Ho_Chi_Minh
```

Chạy lệnh:

```bash
docker compose up -d
```

### 3.2 Việc cần làm sau khi cài

1. Mở trình duyệt vào `http://<IP_NAS>:9527/admin` → Cấu hình hệ thống → **Đổi mật khẩu admin mặc định (`123456`)**.
2. Nếu có nguồn nhạc tùy chỉnh (nguồn script), cấu hình tương tự như bản LxMusic desktop.
3. Nếu dùng tài khoản người dùng có định danh, tạo một API Token lưu lại để điền vào trang cấu hình MediaServer.

---

## §4 Cài Đặt MyXiaoZhi_MediaServer (Docker) —— Kho Nhạc & Sách Nói

Chỉ một container trên cổng **8100** giải quyết toàn bộ: Kho nhạc nội bộ + Nhạc online + Radio mạng + Sách nói + Lưu tiến độ/nghe tiếp.
Địa chỉ xuất kho duy nhất cho loa: `http://<IP_NAS>:8100/index.json`.

Tải về tại: https://github.com/shj4588/MyXiaoZhi_MediaServer

### 4.1 Cấu hình thư mục gắn kết (Mount)

Trong file `docker-compose.yml`, sửa phần bên trái dấu hai chấm thành đường dẫn thực tế trên máy bạn:

```yaml
- /volume1/music:/music              # Thư mục nhạc nội bộ
- /volume1/audiobook:/audiobook      # Thư mục sách nói (nếu không có thì xóa dòng này)
```

Khởi chạy container:

```bash
cd MyXiaoZhi_MediaServer
docker compose up -d --build
```

### 4.2 Các trang quản lý trên Web (`http://<IP_NAS>:8100`)

| Trang | Chức năng |
|---|---|
| **Kho nhạc** | Xem riêng 3 phần: Nhạc nội bộ / Nhạc online / Radio; có thể nghe thử trực tiếp; nút quét lại kho nhạc |
| **Radio** | Thêm, xóa, sửa, sắp xếp các kênh radio trực tuyến |
| **Chương trình của tôi** | Kệ sách nói online lưu vĩnh viễn: bấm nghe ngay, kéo thanh tiến độ, cứu tập thiếu |
| **Nguồn nhạc** | Cấu hình địa chỉ LxServer và Token, các công tắc bật/tắt nguồn, chọn chất lượng âm thanh mặc định, sao chép URL nguồn nhạc cho R1, danh sách chuyển mã theo thiết bị |
| **Lịch sử phát** | Xem lịch sử nghe theo thiết bị hoặc nhóm (nội dung, tập, số giây, thời gian), xóa lịch sử |
| **Nhóm thiết bị** | Tạo nhóm các loa để chia sẻ tiến độ nghe (nghe dở trên loa phòng khách có thể vào phòng ngủ nghe tiếp) |

### 4.3 Ba mục cần cấu hình ở trang "Nguồn nhạc"

1. **Địa chỉ LxServer**: Điền `http://<IP_NAS>:9527` → Bấm Lưu (có nút "Kiểm tra" để tự động test kết nối).
2. **Token**: Điền Token nếu dùng tài khoản định danh trên LxServer; nếu để mở công khai thì để trống.
3. **Chất lượng âm thanh mặc định**: `128k` (mặc định, ổn định và nhanh nhất) / `192k` / `320k`. Nếu mạng chậm nghe hay bị khựng thì để `128k`.

---

## §5 Cấu Hình Trên Web Console Của Loa (`http://<IP_Loa>:8080`)

| Vị trí | Mục cần cấu hình | Giải thích |
|---|---|---|
| Cài đặt → **Server XiaoZhi** | Địa chỉ OTA | **Để trống = Dùng server chính thức**; Tự dựng thì điền `http://<IP_Server>:8003/xiaozhi/ota/` |
| | Địa chỉ WS | **Để trống = Tự động lấy từ OTA** (khuyên dùng); chỉ điền tay khi có nhu cầu đặc biệt `ws://...:8000/xiaozhi/v1/` |
| | Token | **Để trống = Tự động cấp từ OTA**; chỉ điền khi server tự dựng có mật khẩu bảo vệ |
| Cài đặt → **Nguồn nhạc** | URL nguồn nhạc | Chỉ để duy nhất một dòng `http://<IP_NAS>:8100/index.json` → Bấm **"Lưu và tải lại"** |
| Cài đặt → **Giới hạn số lượng bài** | `0` = **Không giới hạn** | Đặt 0 để tải toàn bộ bài hát trong kho |
| Nhạc → Yêu thích | Quản lý danh sách yêu thích | Tải lên danh sách bài hát tùy chỉnh (file .txt) |

---

## §6 Kích Hoạt Thiết Bị

1. Mở trình duyệt vào `http://<IP_Loa>:8080/`.
2. Ở thẻ **"Kích hoạt"** ngay đầu trang, bạn sẽ thấy **mã kích hoạt 6 chữ số**.
3. Đăng nhập vào trang quản trị thiết bị của server XiaoZhi (trang web chính thức hoặc server tự dựng) → Thêm thiết bị mới và nhập mã 6 số này vào.
4. Sau khi ghép nối thành công, trạng thái trên thẻ sẽ chuyển sang màu xanh lá. Loa đã sẵn sàng trò chuyện.
5. **Lưu ý**: Mỗi khi cài đặt lại APK, địa chỉ OTA sẽ bị làm trống, bạn chỉ cần điền lại và kích hoạt lại nếu cần.

---

## §7 Danh Sách Bài Hát Tùy Chỉnh (Yêu Thích)

File danh sách yêu thích lưu trực tiếp trong bộ nhớ của loa tại `files/r1_fav.json`, hỗ trợ tối đa 5000 bài.

### 7.1 Định dạng file txt để tải lên

Trong Web Console `http://<IP_Loa>:8080` → **Nhạc → Yêu thích** → Bấm "Tải lên danh sách", chọn file txt mã hóa UTF-8. Mỗi dòng một bài theo định dạng:

```
Tên bài hát - Ca sĩ
```

Hoặc định dạng đầy đủ (ngăn cách bằng 4 dấu gạch đứng `|`):

```
Tên bài hát|Ca sĩ|Đường dẫn phát|Tên album|Thời lượng
```

- Nhập không giới hạn số dòng (hàng nghìn bài chỉ mất vài chục giây), hệ thống lưu tên trước, khi nào phát đến bài nào mới phân giải link bài đó.
- Nhấn giữ tên bài hát trong 0.5 giây để đổi bản liên kết nếu hệ thống ghép sai bản nhạc mong muốn.

---

## §8 Danh Mục 37 Lệnh Giọng Nói Điều Khiển (MCP)

### 8.1 Phát nhạc (`self.r1player.*`)

| Mẫu câu tiếng Việt / Tương đương | Hành động thực hiện |
|---|---|
| "Bật bài Ngày Mai Em Đi", "Tôi muốn nghe bài Nắng Ấm Xa Dần của Sơn Tùng" | `play_song` (Nói cả tên ca sĩ + bài hát sẽ tìm chính xác hơn) |
| "Phát nhạc của Mỹ Tâm", "Bật mấy bài của Đan Trường" | `play_artist` (Phát danh sách nhạc của ca sĩ đó) |
| "Bật nhạc ngẫu nhiên", "Mở vài bài nghe chơi", "Phát ngẫu nhiên 5 bài" | `random` (Phát ngẫu nhiên theo số lượng) |
| "Phát nhạc online ngẫu nhiên", "Đổi một loạt bài mới", "Mở bài nào lạ chưa nghe" | `random_online` (Lấy ngẫu nhiên các bài mới trên mạng) |
| "Phát bài thứ 8", "Mở bài số 3 trong danh sách" | `play_index` / `play_at` |
| "Trong kho có nhạc Trịnh Công Sơn không?", "Có những ca sĩ nào?" | `search` / `list` (Chỉ tra cứu danh sách, không phát) |
| "Tạm dừng", "Tiếp tục phát", "Dừng lại", "Bài tiếp theo", "Bài trước đó" | `pause` / `play` / `stop` / `next` / `prev` |
| "Chỉnh âm lượng 50", "Bật to lên một chút", "Nhỏ tiếng lại" | `volume` (Mức từ 0 đến 100) |
| "Mở đài VOV Giao Thông", "Nghe kênh FM 91 Mhz", "Bật đài số 2" | `play_radio` |
| "Bài đang phát tên gì?", "Bài này dài bao lâu?" | `status` (Đọc tên bài, thời lượng đã phát và thời gian còn lại) |
| "Thêm bài này vào yêu thích", "Bỏ thích bài này", "Phát danh sách yêu thích" | `fav_add` / `fav_del` / `play_fav` |

### 8.2 Tra cứu thời lượng & Tua nhạc (Tính năng mới V2.0.6)

| Mẫu câu | Hành động |
|---|---|
| "Bài này còn bao lâu?", "Đã phát được bao lâu rồi?", "Tổng thời lượng bao nhiêu?" | `seek_play` (Chỉ đọc tiến độ thời gian) |
| "Tua nhanh 30 giây", "Tua lùi 2 phút" | `seek_play` với tham số `delta` (+30 / -120) |
| "Nhảy đến phút thứ 12", "Chuyển tới phút 3 giây 20" | `seek_play` với tham số `sec` (720 / 200) |

### 8.3 Sách nói / Kể chuyện

| Mẫu câu | Hành động |
|---|---|
| "Mở Tam Quốc Diễn Nghĩa", "Nghe Phàm Nhân Tu Tiên" | `ab_search` → `ab_play` (Không nói số tập = Tiếp tục nghe tập đã nghe dở lần trước) |
| "Phát từ tập 1", "Mở tập số 7" | `ab_play` kèm số tập cụ thể |
| "Mở Khu Rừng Đen" | `ab_play` (Phát đúng tập con trong bộ truyện dài tập) |
| "Tập tiếp theo", "Tập trước" | `ab_next` / `ab_prev` |
| "Tiếp tục nghe cuốn hôm trước" | `ab_resume` (Tìm kiếm theo thứ tự: Kho máy → Cache → Online) |
| "Lưu cuốn sách này lại" | `prog_addcur` (Thêm vào Chương trình của tôi) |

### 8.4 Đèn LED viền (`self.r1light.*`)

| Mẫu câu | Hành động |
|---|---|
| "Bật đèn hiệu ứng", "Chuyển đèn màu xanh dương", "Tăng độ sáng đèn", "Tắt đèn hiệu ứng" | `ambient` (Điều khiển bật/tắt, chế độ, độ sáng, màu sắc) |
| "Bật đèn ngủ", "Đổi đèn ngủ sang màu vàng ấm", "Tắt đèn ngủ" | `night` (Khi bật đèn ngủ, các đèn hiệu ứng nói chuyện sẽ tạm tắt cho tới khi tắt đèn ngủ) |

### 8.5 Báo thức (`self.r1alarm.*`)

| Mẫu câu | Hành động |
|---|---|
| "Đặt báo thức 7 giờ sáng mai", "Gọi tôi dậy lúc 6 rưỡi từ thứ Hai đến thứ Sáu" | `add` (Mặc định chọn ngẫu nhiên một bài hát làm chuông) |
| "Tôi có những báo thức nào?", "Ngày mai mấy giờ báo thức?" | `list` |
| "Tắt chuông đi" (khi chuông đang reo) | `dismiss` (Chỉ tắt lần reo này, ngày mai vẫn reo theo lịch) |
| "Hủy báo thức 7 giờ", "Xóa báo thức" | `toggle` (Tạm ngưng) / `del` (Xóa hẳn) |
| "Chỉnh âm lượng báo thức lên 60" | `vol` |

### 8.6 Lệnh dừng trò chuyện

| Mẫu câu | Hành vi |
|---|---|
| "Lui đi", "Cậu đi đi", "Được rồi im lặng đi", "Thôi không cần nữa" | `self.r1session.quit` (Dừng ngay phiên nói chuyện lập tức) |

---

## §9 Bảng Tra Cứu Lỗi & Cách Xử Lý

| Triệu chứng | Nguyên nhân & Cách khắc phục |
|---|---|
| Cài xong không mở được trang Web cổng 8080 | Hãy đợi 15~40 giây rồi F5 lại trang Web (lần đầu khởi động cần giải nén thư viện và nạp Hal nên lâu hơn). Không rút nguồn đột ngột. |
| Loa báo "Không tìm thấy lịch sử phát" | Nâng cấp lên V2.0.6; bản mới đã có cơ chế tự động chuyển sang bản Online nếu kho nội bộ thiếu tập. |
| Nhạc online không phát được / cứ 30 giây nhảy bài | Do LxServer chưa cài hoặc bị lỗi kết nối: kiểm tra địa chỉ LxServer trên trang 8100, chạy test tự kiểm tra `http://<IP_NAS>:8100/api/lx/selftest`. |
| Nhạc online bị giật, hay chờ nạp | Vào trang 8100 mục Nguồn nhạc, hạ chất lượng âm thanh mặc định xuống `128k`. |
| Nghe nhạc lossless (FLAC) không có tiếng | Vào trang quản lý server "Nguồn âm thanh → Chuyển mã âm thanh", thêm loa R1 vào danh sách chuyển mã để server nén lại định dạng tương thích trước khi truyền. |
| Không kết nối được WebSocket (Trang cài đặt báo WS đỏ/trống) | Kiểm tra địa chỉ OTA đã điền đúng chưa → Kiểm tra server AI XiaoZhi đã bật chưa → Kiểm tra mã kích hoạt đã ghép nối chưa. |
| Ra lệnh giọng nói không điều khiển được nhạc | Kiểm tra công tắc "Kho nhạc tùy chỉnh" trong trang Cài đặt đã bật chưa; sau khi bật hãy gọi "XiaoZhi XiaoZhi" một lần để loa cập nhật bảng công cụ mới. |
| Cả giọng trợ lý và nhạc đều mất tiếng | Đường ra âm thanh bị treo: Chạy lệnh khởi động lại loa qua menu [8] hoặc rút nguồn cắm lại. |
