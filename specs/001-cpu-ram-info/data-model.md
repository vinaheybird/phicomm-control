# Data Model: CPU & RAM Info

## SystemMetrics
Đại diện cho trạng thái phần cứng của thiết bị tại một thời điểm nhất định.

### Fields
- `cpuUsagePercent` (Integer): Tỷ lệ % CPU đang được sử dụng (0 - 100).
- `ramTotal` (Long): Tổng dung lượng RAM của thiết bị (bytes).
- `ramUsed` (Long): Dung lượng RAM đang được sử dụng (bytes).
- `ramFree` (Long): Dung lượng RAM còn trống (bytes).

### Validation Rules
- Các giá trị RAM không được âm. `ramUsed` + `ramFree` có thể xấp xỉ `ramTotal` do cách hệ điều hành cấp phát bộ nhớ.
- `cpuUsagePercent` luôn nằm trong khoảng 0 đến 100. Nếu lỗi đọc thông tin, trả về -1.

## API Contracts

### Endpoint: `/api/status` (hoặc mở rộng endpoint hiện tại)
**Method**: `GET` (nanoHTTPD map GET/POST thành `session.parameters`)

**Response Payload**:
```json
{
  "success": true,
  "cpuUsagePercent": 45,
  "ramTotal": 1073741824,
  "ramUsed": 805306368,
  "ramFree": 268435456,
  "...": "các trường hiện có như btEnabled, volume, v.v."
}
```
