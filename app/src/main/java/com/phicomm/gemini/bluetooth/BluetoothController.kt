package com.phicomm.gemini.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import java.lang.reflect.Method

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter

object BluetoothController {
    private const val TAG = "BluetoothController"
    private const val PREF_NAME = "phicomm_bt_prefs"
    private const val KEY_LAST_DEVICE = "last_connected_device_mac"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect_enabled"

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var a2dpProfile: BluetoothProfile? = null
    private var context: Context? = null
    private var prefs: SharedPreferences? = null

    private val PROFILE_A2DP_SINK = 11

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) {
                    Log.d(TAG, "Bluetooth vừa được bật, khởi tạo lại A2DP_SINK Profile và set connectable...")
                    ensureConnectable()
                    initA2dpProfile()
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    Log.d(TAG, "Bluetooth đã tắt, xóa A2DP Profile.")
                    a2dpProfile = null
                }
            } else if (action == BluetoothDevice.ACTION_PAIRING_REQUEST) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val type = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
                
                try {
                    Log.d(TAG, "Nhận yêu cầu ghép đôi từ ${device?.name} (Kiểu: $type). Đang tự động chấp nhận...")
                    
                    val confirmMethod = device?.javaClass?.getMethod("setPairingConfirmation", Boolean::class.javaPrimitiveType)
                    confirmMethod?.invoke(device, true)

                    if (type == 0 /* PAIRING_VARIANT_PIN */) {
                        val pinMethod = device?.javaClass?.getMethod("setPin", ByteArray::class.java)
                        pinMethod?.invoke(device, "0000".toByteArray())
                    }

                    abortBroadcast()
                    Log.d(TAG, "Đã tự động chấp nhận ghép đôi với ${device?.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi tự động chấp nhận ghép đôi: ${e.message}", e)
                }
            }
        }
    }

    private fun ensureConnectable() {
        try {
            val adapter = bluetoothAdapter ?: return
            // Thử gọi setScanMode(int)
            try {
                val method = adapter.javaClass.getMethod("setScanMode", Int::class.javaPrimitiveType)
                method.invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE)
            } catch (e: Exception) {
                // Thử gọi setScanMode(int, int)
                val method = adapter.javaClass.getMethod("setScanMode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                method.invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE, 0)
            }
            Log.d(TAG, "Đã set SCAN_MODE_CONNECTABLE")
        } catch (e: Exception) {
            Log.e(TAG, "Không thể set SCAN_MODE_CONNECTABLE qua reflection", e)
        }
    }

    fun init(ctx: Context) {
        context = ctx.applicationContext
        prefs = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1 
        }
        context?.registerReceiver(btStateReceiver, filter)

        // Set FXSystemMode=bluetooth ngay khi khởi động để EchoService không can thiệp
        setSystemMode("bluetooth")

        if (bluetoothAdapter?.isEnabled == true) {
            ensureConnectable()
            initA2dpProfile()
        }
    }

    private fun initA2dpProfile() {
        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == PROFILE_A2DP_SINK) {
                    a2dpProfile = proxy
                    Log.d(TAG, "Dịch vụ A2DP_SINK Bluetooth đã sẵn sàng.")
                    if (isAutoReconnectEnabled()) {
                        reconnectLastDevice()
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == PROFILE_A2DP_SINK) {
                    a2dpProfile = null
                    Log.d(TAG, "Dịch vụ A2DP_SINK Bluetooth đã ngắt.")
                }
            }
        }, PROFILE_A2DP_SINK)
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /**
     * Đặt FXSystemMode để EchoService (app gốc Phicomm) không tự ngắt kết nối Bluetooth.
     * mode="bluetooth": EchoService cho phép kết nối BT.
     * mode="normal": EchoService nghĩ loa đang ở chế độ Wifi/AI, sẽ ngắt kết nối BT.
     */
    private fun setSystemMode(mode: String) {
        try {
            val cr = context?.contentResolver ?: return
            Settings.System.putString(cr, "FXSystemMode", mode)
            Log.d(TAG, "Đã set FXSystemMode=$mode")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi set FXSystemMode: ${e.message}")
        }
    }

    fun toggleBluetooth(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return if (adapter.isEnabled) {
            setSystemMode("normal")
            adapter.disable()
        } else {
            setSystemMode("bluetooth")
            adapter.enable()
        }
    }

    fun enableBluetooth(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!adapter.isEnabled) {
            setSystemMode("bluetooth")
            return adapter.enable()
        }
        return true
    }

    fun disableBluetooth(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (adapter.isEnabled) {
            return adapter.disable()
        }
        return true
    }

    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        val pairedSet = adapter.bondedDevices?.toMutableSet() ?: mutableSetOf()
        val connectedDevice = getConnectedDevice()

        // Phải đảm bảo thiết bị đang kết nối cũng hiển thị trong danh sách
        if (connectedDevice != null && !pairedSet.contains(connectedDevice)) {
            pairedSet.add(connectedDevice)
        }

        return pairedSet.map { device ->
            BluetoothDeviceInfo(
                name = device.name ?: "Thiết bị không tên",
                address = device.address,
                isPaired = adapter.bondedDevices?.contains(device) == true,
                isConnected = (device.address == connectedDevice?.address)
            )
        }
    }

    fun getConnectedDevice(): BluetoothDevice? {
        val a2dp = a2dpProfile ?: return null
        val connectedDevices = a2dp.connectedDevices
        return if (connectedDevices.isNotEmpty()) connectedDevices[0] else null
    }

    fun connectDevice(address: String): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!adapter.isEnabled) adapter.enable()

        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            Log.e(TAG, "Địa chỉ MAC không hợp lệ: $address", e)
            return false
        }

        saveLastDevice(address)

        // Phải ghép đôi trước khi kết nối A2DP
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            Log.d(TAG, "Thiết bị chưa ghép đôi, bắt đầu ghép đôi...")
            return device.createBond()
        }

        val a2dp = a2dpProfile
        if (a2dp != null) {
            try {
                val connectMethod: Method = a2dp.javaClass.getMethod("connect", BluetoothDevice::class.java)
                connectMethod.isAccessible = true
                val result = connectMethod.invoke(a2dp, device) as Boolean
                Log.d(TAG, "Kết nối A2DP đến ${device.name} ($address): $result")
                return result
            } catch (e: Exception) {
                Log.e(TAG, "Không thể gọi hàm connect A2DP qua reflection: ${e.message}", e)
            }
        } else {
            Log.e(TAG, "a2dpProfile đang null, không thể kết nối ngay lúc này!")
        }

        return false
    }

    fun disconnectCurrentDevice(): Boolean {
        val a2dp = a2dpProfile ?: return false
        val currentDevice = getConnectedDevice() ?: return true

        try {
            val disconnectMethod: Method = a2dp.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
            disconnectMethod.isAccessible = true
            val result = disconnectMethod.invoke(a2dp, currentDevice) as Boolean
            Log.d(TAG, "Ngắt kết nối A2DP khỏi ${currentDevice.name}: $result")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Không thể ngắt kết nối A2DP qua reflection: ${e.message}", e)
        }
        return false
    }

    fun startDiscovery(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!adapter.isEnabled) adapter.enable()
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        return adapter.startDiscovery()
    }

    fun makeDiscoverable(durationSeconds: Int = 300): Boolean {
        val adapter = bluetoothAdapter ?: return false
        try {
            val method: Method = adapter.javaClass.getMethod("setScanMode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            method.invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, durationSeconds)
            Log.d(TAG, "Đã bật chế độ dò tìm Bluetooth trong $durationSeconds giây")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi bật setScanMode discoverable: ${e.message}", e)
        }
        return false
    }

    fun isAutoReconnectEnabled(): Boolean {
        return prefs?.getBoolean(KEY_AUTO_RECONNECT, true) ?: true
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_RECONNECT, enabled)?.apply()
    }

    private fun saveLastDevice(address: String) {
        prefs?.edit()?.putString(KEY_LAST_DEVICE, address)?.apply()
    }

    fun getLastConnectedDeviceAddress(): String? {
        return prefs?.getString(KEY_LAST_DEVICE, null)
    }

    fun reconnectLastDevice(): Boolean {
        val lastAddress = getLastConnectedDeviceAddress() ?: return false
        Log.d(TAG, "Tự động kết nối lại thiết bị gần nhất: $lastAddress")
        return connectDevice(lastAddress)
    }
}

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val isPaired: Boolean,
    val isConnected: Boolean
)
