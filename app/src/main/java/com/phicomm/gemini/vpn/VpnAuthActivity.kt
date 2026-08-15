package com.phicomm.gemini.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log

class VpnAuthActivity : Activity() {
    companion object {
        private const val TAG = "VpnAuthActivity"
        private const val VPN_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "VpnAuthActivity onCreate: Requesting VPN permission...")
        
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        } else {
            Log.d(TAG, "VPN already authorized. Starting service...")
            startVpnService()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "VPN permission GRANTED by user!")
                startVpnService()
            } else {
                Log.e(TAG, "VPN permission DENIED by user!")
            }
            finish()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, DnsVpnService::class.java)
        intent.action = DnsVpnService.ACTION_START
        startService(intent)
    }
}
