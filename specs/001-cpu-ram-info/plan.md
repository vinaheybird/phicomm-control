# Implementation Plan: Hiển thị thông tin CPU RAM

**Branch**: `[001-cpu-ram-info]` | **Date**: 2026-08-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-cpu-ram-info/spec.md`

## Summary

Hiển thị thông tin phần trăm sử dụng CPU và dung lượng RAM hiện tại (tổng số, đã sử dụng, còn trống) của thiết bị Phicomm R1 (chạy Android) lên giao diện Web Dashboard bằng cách mở rộng endpoint `/api/status` và sử dụng CSS styling để làm progress bar trực quan.

## Technical Context

**Language/Version**: Kotlin (Android 5.1 / SDK 22), HTML/CSS/JS (Vanilla)

**Primary Dependencies**: NanoHTTPD (backend), Android `ActivityManager` (lấy RAM), đọc `File("/proc/stat")` (lấy CPU).

**Storage**: N/A

**Testing**: Kiểm tra thủ công (end-to-end) bằng Dashboard.

**Target Platform**: Android (Phicomm R1) / Trình duyệt web.

**Project Type**: Android App tích hợp local Web Server (Dashboard điều khiển).

**Performance Goals**: Việc lấy thông số không được vượt quá 5% tải CPU.

**Constraints**: Yêu cầu độ trễ cập nhật < 3s, tận dụng polling có sẵn trong `app.js`.

**Scale/Scope**: Tối đa 1-2 thiết bị client theo dõi dashboard cùng lúc.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Project không có constitution hiện hành cho việc này, các best practices về Android và NanoHTTPD được áp dụng. (Pass)

## Project Structure

### Documentation (this feature)

```text
specs/001-cpu-ram-info/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/
├── java/com/phicomm/gemini/
│   ├── utils/
│   │   └── SystemMonitor.kt       # [NEW] Helper lấy CPU & RAM
│   └── web/
│       └── WebConfigServer.kt     # [MODIFY] Thêm trường cpu/ram vào /api/status
└── assets/www/
    ├── index.html                 # [MODIFY] Thêm DOM hiển thị CPU/RAM
    ├── style.css                  # [MODIFY] Thêm style progress bar
    └── app.js                     # [MODIFY] Update logic parse CPU/RAM vào DOM
```

**Structure Decision**: Tính năng tích hợp trực tiếp vào dự án Android hiện tại. Sử dụng `SystemMonitor.kt` để tránh phình to `WebConfigServer.kt`, đồng thời tách bạch logic lấy thông số hệ thống khỏi logic web API.
