# Feature Specification: Hiển thị thông tin CPU RAM

**Feature Branch**: `[001-cpu-ram-info]`

**Created**: 2026-08-15

**Status**: Draft

**Input**: User description: "tính năng hiển thị thông tin cpu ram"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Xem trạng thái hệ thống theo thời gian thực (Priority: P1)

Người dùng (Quản trị viên) muốn xem thông tin trạng thái CPU và RAM của thiết bị (Android) trên giao diện web để theo dõi sức khỏe và tải của thiết bị.

**Why this priority**: Đây là giá trị cốt lõi của tính năng, cung cấp khả năng giám sát cơ bản.

**Independent Test**: Có thể kiểm tra độc lập bằng cách mở trang web dashboard và xác minh các con số hiển thị khớp hoặc phản ánh đúng trạng thái thực tế của thiết bị.

**Acceptance Scenarios**:

1. **Given** người dùng truy cập vào trang dashboard, **When** trang tải xong, **Then** người dùng nhìn thấy các thẻ thông tin hiển thị % mức sử dụng CPU và % mức sử dụng RAM hiện tại.
2. **Given** người dùng đang ở trên trang dashboard, **When** trạng thái CPU/RAM của thiết bị thay đổi theo thời gian, **Then** các thông số trên giao diện web tự động cập nhật liên tục (cụ thể: mỗi 1.5 giây) mà không cần tải lại trang.

### Edge Cases

- Hệ thống xử lý thế nào khi ứng dụng không có quyền truy cập thông tin phần cứng? -> Hiển thị "Không có quyền" hoặc số liệu trống thay vì crash hệ thống.
- Điều gì xảy ra khi mất kết nối giữa giao diện web và thiết bị? -> Giao diện hiển thị trạng thái "Mất kết nối" thay vì giữ số liệu cũ gây nhầm lẫn.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống MUST thu thập được thông tin mức độ sử dụng CPU hiện tại của thiết bị (tính bằng phần trăm).
- **FR-002**: Hệ thống MUST thu thập được thông tin dung lượng RAM (tổng số, đã sử dụng, còn trống) của thiết bị.
- **FR-003**: Hệ thống MUST truyền các dữ liệu CPU và RAM từ thiết bị lên giao diện web (dashboard) một cách định kỳ (cụ thể: mỗi 1.5 giây).
- **FR-004**: Giao diện web MUST hiển thị các thông tin này dưới dạng widget vuông vức, xếp cạnh nhau (side-by-side) với số liệu nổi bật và thanh tiến trình gradient.

### Key Entities

- **SystemMetrics**: Đại diện cho thông tin trạng thái hệ thống tại một thời điểm, bao gồm:
  - `cpuUsagePercent`: Tỷ lệ phần trăm CPU đang được sử dụng.
  - `ramTotal`: Tổng dung lượng RAM của thiết bị.
  - `ramUsed`: Dung lượng RAM đang được sử dụng.
  - `ramFree`: Dung lượng RAM còn trống.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Thông tin CPU và RAM hiển thị trên giao diện web có độ trễ không quá 2 giây so với trạng thái thực tế của thiết bị.
- **SC-002**: Việc thu thập và truyền dữ liệu CPU/RAM không làm tăng mức sử dụng CPU của chính thiết bị quá 5%.
- **SC-003**: Dữ liệu hiển thị theo dạng Widget tối ưu hóa diện tích (iOS/macOS style), người dùng có thể nắm bắt được tình trạng quá tải (ví dụ: >90% RAM hoặc CPU) ngay trong 3 giây đầu tiên nhờ màu sắc cảnh báo (vàng/đỏ) trên giao diện.

## Assumptions

- Ứng dụng đã có sẵn cơ chế giao tiếp giữa thiết bị (server) và web (client).
- Hệ điều hành thiết bị cung cấp các API tiêu chuẩn để truy xuất thông tin CPU và RAM.
- Giao diện người dùng sẽ được tích hợp vào một trang dashboard hiện có.
