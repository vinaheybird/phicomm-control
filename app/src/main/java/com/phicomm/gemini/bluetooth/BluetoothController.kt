package com.phicomm.gemini.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.SharedPreferences
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
    private var context: Context? = null
    private var prefs: SharedPreferences? = null

    // Theo dõi thiết bị đang kết nối qua BroadcastReceiver thay vì profile proxy
    private var connectedDeviceAddress: String? = null
    private var connectedDeviceName: String? = null

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        Log.d(TAG, "Bluetooth vừa bật, đảm bảo SCAN_MODE_CONNECTABLE...")
                        ensureConnectable()
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        connectedDeviceAddress = null
                        connectedDeviceName = null
                        Log.d(TAG, "Bluetooth đã tắt.")
                    }
                }
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> {
                    val mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1)
                    val prevMode = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE, -1)
                    Log.d(TAG, "Scan mode thay đổi: $prevMode → $mode")
                    // FXSystemModeImpl reset về SCAN_MODE_NONE (20) → ta set lại CONNECTABLE ngay
                    if (mode == BluetoothAdapter.SCAN_MODE_NONE && bluetoothAdapter?.isEnabled == true) {
                        Log.d(TAG, "Scan mode bị reset về NONE. Đang khôi phục CONNECTABLE...")
                        ensureConnectable()
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    connectedDeviceAddress = device?.address
                    connectedDeviceName = device?.name
                    Log.d(TAG, "ACL Connected: ${device?.name} (${device?.address})")
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device?.address == connectedDeviceAddress) {
                        Log.d(TAG, "ACL Disconnected: ${device?.name}")
                        connectedDeviceAddress = null
                        connectedDeviceName = null
                    }
                }
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val type = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
                    try {
                        Log.d(TAG, "Yêu cầu ghép đôi từ ${device?.name} (type=$type). Tự động chấp nhận...")
                        val confirmMethod = device?.javaClass?.getMethod("setPairingConfirmation", Boolean::class.javaPrimitiveType)
                        confirmMethod?.invoke(device, true)
                        if (type == 0) {
                            val pinMethod = device?.javaClass?.getMethod("setPin", ByteArray::class.java)
                            pinMethod?.invoke(device, "0000".toByteArray())
                        }
                        abortBroadcast()
                        Log.d(TAG, "Đã chấp nhận ghép đôi với ${device?.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi chấp nhận ghép đôi: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun ensureConnectable() {
        try {
            val adapter = bluetoothAdapter ?: return
            try {
                val method = adapter.javaClass.getMethod("setScanMode", Int::class.javaPrimitiveType)
                method.invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE)
            } catch (e: Exception) {
                val method = adapter.javaClass.getMethod("setScanMode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                method.invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE, 0)
            }
            Log.d(TAG, "Đã set SCAN_MODE_CONNECTABLE")
        } catch (e: Exception) {
            Log.e(TAG, "Không thể set SCAN_MODE_CONNECTABLE: ${e.message}")
        }
    }

    fun init(ctx: Context) {
        context = ctx.applicationContext
        prefs = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1
        }
        context?.registerReceiver(btStateReceiver, filter)

        if (bluetoothAdapter?.isEnabled == true) {
            ensureConnectable()
        }
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
        if (!adapter.isEnabled) return adapter.enable()
        return true
    }

    fun disableBluetooth(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (adapter.isEnabled) return adapter.disable()
        return true
    }

    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        val bonded = adapter.bondedDevices ?: return emptyList()
        val connectedAddr = connectedDeviceAddress

        return bonded.map { device ->
            BluetoothDeviceInfo(
                name = device.name ?: "Thiết bị không tên",
                address = device.address,
                isPaired = true,
                isConnected = (device.address == connectedAddr)
            )
        }
    }

    fun getConnectedDevice(): BluetoothDevice? {
        val addr = connectedDeviceAddress ?: return null
        return try {
            bluetoothAdapter?.getRemoteDevice(addr)
        } catch (e: Exception) {
            null
        }
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

        if (device.bondState == BluetoothDevice.BOND_NONE) {
            Log.d(TAG, "Thiết bị chưa ghép đôi, bắt đầu ghép đôi...")
            return device.createBond()
        }

        // Dùng BluetoothDevice.connect() qua reflection — không cần giữ profile proxy
        try {
            val connectMethod: Method = device.javaClass.getMethod("connect")
            connectMethod.isAccessible = true
            connectMethod.invoke(device)
            Log.d(TAG, "Đã gọi device.connect() cho $address")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi device.connect(): ${e.message}", e)
        }
        return false
    }

    fun disconnectCurrentDevice(): Boolean {
        val device = getConnectedDevice() ?: return true
        try {
            val disconnectMethod: Method = device.javaClass.getMethod("disconnect")
            disconnectMethod.isAccessible = true
            disconnectMethod.invoke(device)
            Log.d(TAG, "Đã gọi device.disconnect() cho ${device.address}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi device.disconnect(): ${e.message}", e)
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
            Log.d(TAG, "Đã bật dò tìm Bluetooth trong $durationSeconds giây")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi setScanMode discoverable: ${e.message}", e)
        }
        return false
    }

    fun isAutoReconnectEnabled(): Boolean = prefs?.getBoolean(KEY_AUTO_RECONNECT, true) ?: true

    fun setAutoReconnectEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_RECONNECT, enabled)?.apply()
    }

    private fun saveLastDevice(address: String) {
        prefs?.edit()?.putString(KEY_LAST_DEVICE, address)?.apply()
    }

    fun getLastConnectedDeviceAddress(): String? = prefs?.getString(KEY_LAST_DEVICE, null)

    fun reconnectLastDevice(): Boolean {
        val lastAddress = getLastConnectedDeviceAddress() ?: return false
        return connectDevice(lastAddress)
    }
}

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val isPaired: Boolean,
    val isConnected: Boolean
)
