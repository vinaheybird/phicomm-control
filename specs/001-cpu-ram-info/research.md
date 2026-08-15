# Research: CPU & RAM Info

## Decision 1: Lấy thông tin RAM
- **Decision**: Sử dụng `ActivityManager.MemoryInfo` của Android.
- **Rationale**: Đây là API chuẩn xác và được hỗ trợ trên mọi phiên bản Android, cho phép lấy `totalMem`, `availMem` một cách dễ dàng và đáng tin cậy mà không cần quyền đặc biệt.
- **Alternatives considered**: Đọc file `/proc/meminfo` (phức tạp hơn trong việc parse text, không cần thiết khi đã có API chuẩn).

## Decision 2: Lấy thông tin CPU
- **Decision**: Đọc file `/proc/stat` để tính toán tỷ lệ sử dụng CPU.
- **Rationale**: Phicomm R1 chạy Android 5.1, nơi việc đọc `/proc/stat` vẫn hoạt động tốt và không bị hạn chế quyền nghiêm ngặt như các bản Android mới. Cần đọc 2 lần cách nhau một khoảng thời gian nhỏ để tính ra Delta (tổng thời gian CPU và thời gian rảnh), từ đó suy ra %.
- **Alternatives considered**: Chạy lệnh `top -n 1` (chậm và tốn tài nguyên parse), sử dụng `HardwarePropertiesManager` (yêu cầu Android 7.0+ và app là DeviceOwner, không khả thi trên Phicomm R1).

## Decision 3: Giao thức truyền dữ liệu
- **Decision**: Thêm endpoint `/api/status/system` trên `WebConfigServer` trả về JSON và dùng polling (mỗi 3 giây) từ `app.js` tương tự như cách đang làm với trạng thái Bluetooth.
- **Rationale**: Giữ tính nhất quán với kiến trúc hiện tại của `app.js` (`setInterval(refreshStatus, 3000)`). Tránh phức tạp hóa hệ thống bằng WebSocket khi không thực sự cần realtime quá khắt khe.
- **Alternatives considered**: WebSocket (nhanh hơn nhưng NanoHTTPD cần module riêng `nanohttpd-websocket`, tăng dung lượng app và độ phức tạp frontend).

## Decision 4: Hiển thị trên Web UI
- **Decision**: Thêm một mục "Hệ thống & Phần cứng" trên giao diện `index.html`, sử dụng các thanh progress bar cơ bản bằng CSS để trực quan hóa %.
- **Rationale**: Đơn giản, nhẹ, không cần thư viện biểu đồ bên ngoài (như Chart.js) gây nặng web.
- **Alternatives considered**: Thư viện biểu đồ (quá phức tạp cho tính năng nhỏ).
