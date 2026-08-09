// --- Utilities ---
function showToast(message, isError = false) {
    const container = document.getElementById('toast-container');
    if (!container) return;
    
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.style.backgroundColor = isError ? 'var(--danger)' : '#333';
    toast.innerText = message;
    
    container.appendChild(toast);
    
    // Animate in
    setTimeout(() => toast.classList.add('show'), 10);
    
    // Remove after 3s
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// Hàm fetch API bọc sẵn hiệu ứng Loading trên Button
async function postApi(url, btnElement = null) {
    if (btnElement) {
        btnElement.classList.add('loading');
        btnElement.disabled = true;
    }
    
    try {
        const res = await fetch(url, { method: 'POST' });
        const data = await res.json();
        
        if (data.message) {
            showToast(data.message, !data.success);
        }
        
        // Refresh ngay sau khi thao tác
        if (window.refreshStatus) {
            await window.refreshStatus();
        }
        return data;
    } catch (err) {
        showToast("Lỗi kết nối tới loa!", true);
        console.error(err);
        return { success: false };
    } finally {
        if (btnElement) {
            btnElement.classList.remove('loading');
            btnElement.disabled = false;
        }
    }
}

// --- Dashboard Logic ---
async function refreshStatus() {
    try {
        const res = await fetch('/api/status');
        const d = await res.json();
        
        // Cập nhật Bluetooth Status
        const btStatusEl = document.getElementById('btStatus');
        if (btStatusEl) {
            if (d.btEnabled) {
                btStatusEl.innerText = d.connectedDevice ? '🟢 Đã kết nối: ' + d.connectedDevice.name : '🟡 Đang chờ kết nối...';
            } else {
                btStatusEl.innerText = '🔴 Đã tắt';
            }
        }
        
        // Toggles
        const btToggleEl = document.getElementById('btToggle');
        if (btToggleEl && btToggleEl.checked !== d.btEnabled) btToggleEl.checked = d.btEnabled;
        
        const autoReEl = document.getElementById('btAuto');
        if (autoReEl && autoReEl.checked !== d.autoReconnect) autoReEl.checked = d.autoReconnect;
        
        const ledToggleEl = document.getElementById('ledToggle');
        if (ledToggleEl && ledToggleEl.checked !== d.ledEnabled) ledToggleEl.checked = d.ledEnabled;
        
        const ledModeEl = document.getElementById('ledMode');
        if (ledModeEl && ledModeEl.value !== d.ledMode) ledModeEl.value = d.ledMode;
        
        const muteToggleEl = document.getElementById('muteToggle');
        if (muteToggleEl && muteToggleEl.checked !== d.promptMute) muteToggleEl.checked = d.promptMute;
        
        // Volume
        const volRangeEl = document.getElementById('volRange');
        const volLabelEl = document.getElementById('volLabel');
        if (volRangeEl && document.activeElement !== volRangeEl) {
            volRangeEl.value = d.volume;
        }
        if (volLabelEl) volLabelEl.innerText = d.volume + '%';
        
        // Danh sách thiết bị
        const pairedList = document.getElementById('pairedList');
        if (pairedList) {
            if (!d.pairedDevices || d.pairedDevices.length === 0) {
                pairedList.innerHTML = '<p style="color: var(--text-muted); text-align: center; margin-top: 10px;">Chưa có thiết bị ghép đôi.</p>';
            } else {
                pairedList.innerHTML = d.pairedDevices.map(dev => `
                    <div class="device-item">
                        <div class="device-info">
                            <span class="device-name">${dev.name}</span>
                            <span class="device-mac">${dev.address}</span>
                        </div>
                        ${dev.isConnected 
                            ? '<span class="status-badge">Đang phát</span>' 
                            : `<button onclick="postApi('/api/bluetooth/connect?address=${dev.address}', this)">
                                 <span class="loader"></span>Kết Nối
                               </button>`
                        }
                    </div>
                `).join('');
            }
        }
    } catch (e) {
        console.error("Lỗi khi lấy trạng thái:", e);
    }
}

// Khởi chạy
document.addEventListener('DOMContentLoaded', () => {
    // Chỉ chạy refresh nếu đang ở trang có id btStatus
    if (document.getElementById('btStatus')) {
        window.refreshStatus = refreshStatus;
        refreshStatus();
        setInterval(refreshStatus, 4000);
    }
});
