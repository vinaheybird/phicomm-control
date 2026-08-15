# Quickstart: Kiểm tra thông tin CPU & RAM

Hướng dẫn này giúp bạn xác nhận tính năng hiển thị CPU/RAM hoạt động đúng end-to-end từ lúc cài đặt app đến khi lên giao diện Web.

## Prerequisites
- Đã cài đặt ứng dụng lên thiết bị Phicomm R1.
- Máy tính và thiết bị ở cùng một mạng nội bộ (có thể ping được tới IP của thiết bị).

## 1. Kiểm tra API Endpoint trực tiếp
1. Lấy địa chỉ IP của loa Phicomm R1 (ví dụ `192.168.1.100`).
2. Mở terminal hoặc trình duyệt trên máy tính, gọi API:
   ```bash
   curl http://192.168.1.100:8080/api/status
   ```
3. **Kết quả mong muốn**: JSON trả về có chứa các trường `cpuUsagePercent`, `ramTotal`, `ramUsed`, `ramFree`.
   ```json
   {
     "success": true,
     "cpuUsagePercent": 25,
     "ramTotal": 1024000000,
     "ramUsed": 500000000,
     "ramFree": 524000000,
     ...
   }
   ```

## 2. Kiểm tra Giao diện Web (Dashboard)
1. Mở trình duyệt web, truy cập `http://192.168.1.100:8080`.
2. Quan sát phần **Hệ thống & Phần cứng** mới được thêm vào.
3. **Kết quả mong muốn**:
   - Thấy 2 thanh thông số: "Sử dụng CPU" và "Sử dụng RAM".
   - Các chỉ số % hiển thị rõ ràng và có thanh tiến trình (progress bar) minh họa.
   - Khoảng 3 giây một lần, các con số này tự động thay đổi nhẹ (do mức độ sử dụng CPU/RAM thực tế luôn biến động) mà trang không bị load lại.

## 3. Kiểm tra Edge Cases
- **Load test CPU**: Mở một video nặng hoặc copy file lớn trên loa, quay lại web dashboard, % CPU phải tăng vọt và hiển thị chính xác theo thời gian thực (độ trễ < 3s).
