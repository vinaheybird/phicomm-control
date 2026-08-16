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

async function loadGeminiKey() {
    try {
        const res = await fetch('/api/gemini/config');
        const d = await res.json();
        if (d.success) {
            if (d.hasKey) {
                const el = document.getElementById('geminiApiKey');
                if (el) el.value = "********";
            }
            if (d.modelName) {
                // Just keep it in a global variable for now, will populate when they click Check
                window.currentGeminiModel = d.modelName;
            }
        }
    } catch(e) {}
}

async function checkGeminiKey() {
    const key = document.getElementById('geminiApiKey').value;
    if (!key) {
        showToast("Vui lòng nhập API Key", true);
        return;
    }
    
    // If it's masked, we can't test it directly from browser, ask them to re-enter or we could test from backend
    if (key === "********") {
        showToast("Vui lòng nhập lại API Key thực tế để quét model", true);
        document.getElementById('geminiApiKey').value = "";
        return;
    }

    const btn = document.getElementById('btnCheckGemini');
    const loader = document.getElementById('loaderCheckGemini');
    btn.disabled = true;
    loader.style.display = 'inline-block';
    
    try {
        const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${key}`);
        const data = await response.json();
        
        if (!response.ok) {
            showToast("Lỗi API Key: " + (data.error?.message || "Unauthorized"), true);
            return;
        }

        const models = data.models || [];
        // Filter models that support generateContent and are Gemini models (exclude embedding, etc)
        const validModels = models.filter(m => 
            m.name.includes("gemini") && 
            m.supportedGenerationMethods && 
            m.supportedGenerationMethods.includes("generateContent")
        );
        
        if (validModels.length === 0) {
            showToast("API Key không có quyền truy cập model Gemini nào", true);
            return;
        }

        const select = document.getElementById('geminiModel');
        select.innerHTML = "";
        
        let foundCurrent = false;
        
        validModels.forEach(m => {
            const shortName = m.name.replace("models/", "");
            const option = document.createElement("option");
            option.value = shortName;
            option.text = `${m.displayName} (${shortName})`;
            
            if (window.currentGeminiModel === shortName) {
                option.selected = true;
                foundCurrent = true;
            } else if (!foundCurrent && shortName.includes("flash")) {
                // default to flash if possible
                option.selected = true;
            }
            
            select.appendChild(option);
        });

        document.getElementById('geminiModelContainer').style.display = 'block';
        showToast("Quét Model thành công!", false);
        
    } catch (e) {
        console.error(e);
        showToast("Lỗi kết nối tới Google API", true);
    } finally {
        btn.disabled = false;
        loader.style.display = 'none';
    }
}

async function saveGeminiConfig(btn) {
    const key = document.getElementById('geminiApiKey').value;
    const modelContainer = document.getElementById('geminiModelContainer');
    const model = document.getElementById('geminiModel').value;
    
    if (!key) {
        showToast("Vui lòng nhập API Key", true);
        return;
    }
    
    if (modelContainer.style.display !== 'none' && !model) {
        showToast("Vui lòng quét và chọn Model", true);
        return;
    }

    const loader = document.getElementById('loaderSaveGemini');
    btn.disabled = true;
    loader.style.display = 'inline-block';
    
    try {
        const formData = new URLSearchParams();
        // If it's masked, don't update the key, just update the model
        if (key !== "********") {
            formData.append('apiKey', key);
        }
        if (modelContainer.style.display !== 'none') {
            formData.append('modelName', model);
        }
        
        const res = await fetch('/api/gemini/config', { 
            method: 'POST',
            body: formData,
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        });
        const d = await res.json();
        showToast(d.message || "Đã lưu cấu hình", !d.success);
        
        if (d.success && key !== "********") {
            document.getElementById('geminiApiKey').value = "********";
            window.currentGeminiModel = model;
        }
    } catch(e) {
        showToast("Lỗi lưu cấu hình", true);
    } finally {
        btn.disabled = false;
        loader.style.display = 'none';
    }
}

document.addEventListener('DOMContentLoaded', () => {
    if (document.getElementById('btToggle')) {
        window.refreshStatus = refreshStatus;
        refreshStatus();
        setInterval(refreshStatus, 1500);
    }
    if (document.getElementById('geminiApiKey')) {
        loadGeminiKey();
    }
});

