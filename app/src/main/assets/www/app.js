// --- Utilities ---
function showToast(message, isError = false) {
    const container = document.getElementById('toast-container');
    if (!container) return;
    
    const toast = document.createElement('div');
    toast.className = 'toast ' + (isError ? 'error' : '');
    toast.innerText = message;
    
    container.appendChild(toast);
    
    // Animate in
    setTimeout(() => toast.classList.add('show'), 10);
    
    // Remove after 3s
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 400);
    }, 3000);
}

async function postApi(url, btnElement = null, loaderId = null) {
    if (btnElement) {
        btnElement.classList.add('loading');
        btnElement.disabled = true;
        btnElement.dataset.locked = "true";
    }
    const loader = loaderId ? document.getElementById(loaderId) : null;
    if (loader) loader.style.display = 'inline-block';
    
    try {
        const res = await fetch(url, { method: 'POST' });
        const data = await res.json();
        
        if (data.message) {
            showToast(data.message, !data.success);
        }
        
        if (window.refreshStatus) {
            await window.refreshStatus();
        }
        return data;
    } catch (err) {
        showToast("Không thể kết nối với loa", true);
        console.error(err);
        return { success: false };
    } finally {
        // Phicomm R1 Android takes ~2-3 seconds to fully transition BT state
        setTimeout(() => {
            if (btnElement) {
                btnElement.classList.remove('loading');
                btnElement.disabled = false;
                btnElement.dataset.locked = "false";
            }
            if (loader) loader.style.display = 'none';
            // Force final refresh to sync UI after delay
            if (window.refreshStatus) window.refreshStatus();
        }, 3000);
    }
}

// --- Dashboard Logic ---
async function refreshStatus() {
    try {
        const res = await fetch('/api/status');
        const d = await res.json();
        
        const btStatusText = document.getElementById('btStatusText');
        if (btStatusText) {
            if (d.btEnabled) {
                btStatusText.innerText = d.connectedDevice ? d.connectedDevice.name : 'Đang tìm kiếm...';
            } else {
                btStatusText.innerText = 'Tắt';
            }
        }
        
        const btToggleEl = document.getElementById('btToggle');
        if (btToggleEl && btToggleEl.dataset.locked !== "true" && btToggleEl.checked !== d.btEnabled) {
            btToggleEl.checked = d.btEnabled;
        }
        
        // Hiện/Ẩn row ghép đôi
        const discoverRow = document.getElementById('discoverRow');
        if (discoverRow) {
            discoverRow.style.display = d.btEnabled ? 'flex' : 'none';
        }
        
        const autoReEl = document.getElementById('btAuto');
        if (autoReEl && !autoReEl.disabled && autoReEl.checked !== d.autoReconnect) autoReEl.checked = d.autoReconnect;
        
        // Cập nhật CPU và RAM
        const cpuText = document.getElementById('cpuText');
        const cpuBar = document.getElementById('cpuBar');
        if (cpuText && cpuBar && d.cpuUsagePercent !== undefined) {
            const cpu = d.cpuUsagePercent;
            cpuText.innerText = cpu >= 0 ? `${cpu}%` : 'Lỗi';
            if (cpu >= 0) {
                cpuBar.style.width = `${cpu}%`;
                cpuBar.className = 'progress-bar-fill' + (cpu > 85 ? ' danger' : (cpu > 60 ? ' warning' : ''));
            } else {
                cpuBar.style.width = `0%`;
            }
        }
        
        const ramText = document.getElementById('ramText');
        const ramBar = document.getElementById('ramBar');
        if (ramText && ramBar && d.ramTotal > 0) {
            const usedMB = Math.round(d.ramUsed / (1024 * 1024));
            const totalMB = Math.round(d.ramTotal / (1024 * 1024));
            const percent = (d.ramUsed / d.ramTotal) * 100;
            
            ramText.innerText = `${usedMB} / ${totalMB} MB`;
            ramBar.style.width = `${percent}%`;
            ramBar.className = 'progress-bar-fill' + (percent > 85 ? ' danger' : (percent > 70 ? ' warning' : ''));
        }

        const volRangeEl = document.getElementById('volRange');
        if (volRangeEl && document.activeElement !== volRangeEl) {
            volRangeEl.value = d.volume;
        }
        
        const pairedList = document.getElementById('pairedList');
        if (pairedList) {
            if (!d.pairedDevices || d.pairedDevices.length === 0) {
                pairedList.innerHTML = '<div class="list-item"><div class="item-content"><div class="item-title" style="color:var(--text-muted)">Không có thiết bị</div></div></div>';
            } else {
                pairedList.innerHTML = d.pairedDevices.map(dev => `
                    <div class="list-item">
                        <div class="item-content">
                            <div class="item-title">${dev.name}</div>
                            <div class="item-subtitle">${dev.address}</div>
                        </div>
                        <div class="item-action">
                            ${dev.isConnected 
                                ? '<div class="badge connected">Đã kết nối</div>' 
                                : `<button class="btn" onclick="postApi('/api/bluetooth/connect?address=${dev.address}', this)">
                                     <span class="loader"></span> Kết nối
                                   </button>`
                            }
                        </div>
                    </div>
                `).join('');
            }
        }
    } catch (e) {
        console.error("Lỗi lấy trạng thái:", e);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    if (document.getElementById('btToggle')) {
        window.refreshStatus = refreshStatus;
        refreshStatus();
        setInterval(refreshStatus, 1500);
    }
});
