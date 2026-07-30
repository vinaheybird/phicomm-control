package com.phicomm.gemini.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.lang.reflect.Method

object BluetoothController {
    private const val TAG = "BluetoothController"
    private const val PREF_NAME = "phicomm_bt_prefs"
    private const val KEY_LAST_DEVICE = "last_connected_device_mac"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect_enabled"

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var a2dpProfile: BluetoothProfile? = null
    private var context: Context? = null
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
        prefs = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.A2DP) {
                    a2dpProfile = proxy
                    Log.d(TAG, "Dịch vụ A2DP Bluetooth đã sẵn sàng.")
                    if (isAutoReconnectEnabled()) {
                        reconnectLastDevice()
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.A2DP) {
                    a2dpProfile = null
                    Log.d(TAG, "Dịch vụ A2DP Bluetooth đã ngắt.")
                }
            }
        }, BluetoothProfile.A2DP)
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun toggleBluetooth(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return if (adapter.isEnabled) {
            adapter.disable()
        } else {
            adapter.enable()
        }
    }

    fun enableBluetooth(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!adapter.isEnabled) {
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

        val paired = adapter.bondedDevices ?: return emptyList()
        val connectedDevice = getConnectedDevice()

        return paired.map { device ->
            BluetoothDeviceInfo(
                name = device.name ?: "Thiết bị không tên",
                address = device.address,
                isPaired = true,
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
        }

        // Fallback: Thử ghép đôi nếu chưa paired
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            device.createBond()
        }
        return true
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
