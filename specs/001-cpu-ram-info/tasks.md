# Tasks: 001-cpu-ram-info

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure
*Since this feature integrates into an existing project, no new infrastructure is needed here.*

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented
*N/A for this feature, as it is a direct extension of existing systems.*

---

## Phase 3: User Story 1 - Xem trạng thái hệ thống theo thời gian thực (Priority: P1) ⭐ MVP

**Goal**: Hiển thị phần trăm sử dụng CPU và dung lượng RAM trên giao diện Web Dashboard.

**Independent Test**: Mở `http://[IP]:8080` và xem biểu đồ/chỉ số CPU, RAM được cập nhật mỗi 3s mà không tải lại trang. Kiểm tra endpoint API `/api/status`.

### Implementation for User Story 1

- [x] T001 [P] [US1] Create SystemMonitor.kt for CPU/RAM logic in app/src/main/java/com/phicomm/gemini/utils/SystemMonitor.kt
- [x] T002 [US1] Integrate SystemMonitor to `/api/status` endpoint in app/src/main/java/com/phicomm/gemini/web/WebConfigServer.kt
- [x] T003 [P] [US1] Add DOM structure for CPU/RAM to Web UI in app/src/main/assets/www/index.html
- [x] T004 [P] [US1] Add progress bar CSS styles in app/src/main/assets/www/style.css
- [x] T005 [US1] Update JS logic to parse API payload and update UI in app/src/main/assets/www/app.js

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T006 Run quickstart.md validation to ensure end-to-end functionality

---

## Dependencies & Execution Order

### Phase Dependencies

- **User Story 1 (P1)**: Can start immediately as Setup/Foundational are not needed.
- **Polish (Final Phase)**: Depends on US1 completion.

### User Story Dependencies

- **User Story 1 (P1)**: No dependencies on other stories.

### Parallel Opportunities

- T001, T003, T004 can all be worked on in parallel since they modify separate independent files.
- T002 depends on T001.
- T005 depends on T003 and T002 (API contract).

---

## Parallel Example: User Story 1

```bash
# Launch separate files together:
Task: "Create SystemMonitor.kt for CPU/RAM logic in app/src/main/java/com/phicomm/gemini/utils/SystemMonitor.kt"
Task: "Add DOM structure for CPU/RAM to Web UI in app/src/main/assets/www/index.html"
Task: "Add progress bar CSS styles in app/src/main/assets/www/style.css"
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Implement SystemMonitor and test.
2. Implement API endpoint and test via cURL.
3. Implement UI (HTML/CSS) and JS integration.
4. **STOP and VALIDATE**: Test User Story 1 independently via Dashboard.
