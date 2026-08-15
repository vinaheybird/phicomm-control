package com.phicomm.gemini.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class DnsVpnService : VpnService() {

    companion object {
        private const val TAG = "DnsVpnService"
        const val ACTION_START = "com.phicomm.gemini.vpn.START"
        const val ACTION_STOP = "com.phicomm.gemini.vpn.STOP"
        
        // Target domains to hijack
        private val HIJACK_DOMAINS = setOf(
            "aios-home.hivoice.cn",
            "home.phicomm.com",
            "asrv3.hivoice.cn"
        )
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    
    // Real DNS server to forward non-hijacked queries
    private val realDnsServer = "8.8.8.8"
    private val forwardSocket = DatagramSocket()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START) {
            startVpn()
        } else if (action == ACTION_STOP) {
            stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .addAddress("10.0.0.2", 24)
                .addRoute("10.0.0.0", 24) // Route the entire virtual subnet to TUN
                .addDnsServer("10.0.0.1")
                .addRoute("10.0.0.1", 32) // System DNS
                .addRoute("114.114.114.114", 32) // Common China DNS
                .addRoute("8.8.8.8", 32) // Google DNS
                .addRoute("223.5.5.5", 32) // AliDNS
                .addRoute("1.2.4.8", 32) // CNNIC
                .setSession("Phicomm DNS Hijack")
                .setBlocking(true)

            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                Log.i(TAG, "VPN established successfully.")
                startPacketLoop()
            } else {
                Log.e(TAG, "Failed to establish VPN. App might not be prepared.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
        }
    }

    private fun startPacketLoop() {
        coroutineScope.launch {
            val fileDescriptor = vpnInterface?.fileDescriptor ?: return@launch
            val inputStream = FileInputStream(fileDescriptor)
            val outputStream = FileOutputStream(fileDescriptor)
            val buffer = ByteArray(32767)

            while (isActive) {
                try {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        handlePacket(buffer, length, outputStream)
                    }
                } catch (e: Exception) {
                    if (isActive) Log.e(TAG, "Error reading from TUN", e)
                    break
                }
            }
        }
    }

    private fun handlePacket(packet: ByteArray, length: Int, outputStream: FileOutputStream) {
        if (DnsPacket.isDnsQuery(packet, length)) {
            val domain = DnsPacket.parseDnsQueryDomain(packet, length)
            if (domain != null) {
                Log.d(TAG, "Intercepted DNS query for: $domain")
                if (HIJACK_DOMAINS.contains(domain)) {
                    Log.i(TAG, "Hijacking $domain -> 10.0.0.10 (Virtual NAT IP)")
                    val response = DnsPacket.createDnsResponse(packet, length, byteArrayOf(10, 0, 0, 10))
                    outputStream.write(response)
                    outputStream.flush()
                } else {
                    forwardToRealDns(packet, length, outputStream)
                }
            }
        } else {
            // Pass non-DNS packets to our stateless NAT
            if (com.phicomm.gemini.vpn.nat.TcpNat.processPacket(packet, length)) {
                outputStream.write(packet, 0, length)
                outputStream.flush()
            }
        }
    }

    private fun forwardToRealDns(packet: ByteArray, length: Int, outputStream: FileOutputStream) {
        // Extract the raw UDP DNS payload
        val buffer = java.nio.ByteBuffer.wrap(packet, 0, length)
        val ihl = ((buffer.get(0).toInt() and 0xFF) and 0x0F) * 4
        val dnsOffset = ihl + 8 // IP header + UDP header
        val dnsLen = length - dnsOffset
        
        val dnsPayload = ByteArray(dnsLen)
        System.arraycopy(packet, dnsOffset, dnsPayload, 0, dnsLen)

        coroutineScope.launch {
            try {
                val address = InetAddress.getByName(realDnsServer)
                val udpPacket = DatagramPacket(dnsPayload, dnsPayload.size, address, 53)
                forwardSocket.send(udpPacket)
                
                // For a robust implementation, we should listen for the response and map it back
                // For PoC, this simple non-blocking send ensures the request is sent,
                // but the response routing back to TUN requires a reverse mapping.
                // Since we only care about hijacking the two domains, losing other DNS responses 
                // temporarily is acceptable for a quick PoC test, but ideally we'd implement NAT here.
            } catch (e: Exception) {
                Log.e(TAG, "Failed to forward DNS", e)
            }
        }
    }

    private fun stopVpn() {
        coroutineScope.coroutineContext.cancelChildren()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
