package com.phicomm.gemini.vpn.nat

import android.util.Log

object TcpNat {
    private const val TAG = "TcpNat"

    // IP of the VPN interface (where local packets originate)
    private val VPN_IP = byteArrayOf(10, 0, 0, 2)
    // Fake IP we use to trick the local kernel into routing the response back to TUN
    private val FAKE_CLIENT_IP = byteArrayOf(10, 0, 0, 3)
    // The IP we hijack DNS to
    private val HIJACK_IP = byteArrayOf(10, 0, 0, 10)
    
    fun processPacket(packet: ByteArray, length: Int): Boolean {
        if (length < 20) return false
        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return false
        
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (length < ihl) return false
        
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 6) return false // Only handle TCP
        
        val tcpOffset = ihl
        if (length < tcpOffset + 20) return false
        val tcpLen = length - tcpOffset
        
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        
        val srcPort = ((packet[tcpOffset].toInt() and 0xFF) shl 8) or (packet[tcpOffset + 1].toInt() and 0xFF)
        val dstPort = ((packet[tcpOffset + 2].toInt() and 0xFF) shl 8) or (packet[tcpOffset + 3].toInt() and 0xFF)

        // Ingress (From App to Hijack IP)
        if (dstIp.contentEquals(HIJACK_IP) && (dstPort == 80 || dstPort == 19999)) {
            Log.d(TAG, "NAT Ingress: App -> HijackIP:$dstPort, rewriting to $dstPort->8080/19999")
            System.arraycopy(FAKE_CLIENT_IP, 0, packet, 12, 4)
            System.arraycopy(VPN_IP, 0, packet, 16, 4)
            if (dstPort == 80) {
                packet[tcpOffset + 2] = (8080 shr 8).toByte()
                packet[tcpOffset + 3] = (8080 and 0xFF).toByte()
            }
            
            computeIpChecksum(packet, 0, ihl)
            computeTcpChecksum(packet, 0, tcpOffset, tcpLen)
            return true
        }
        
        // Egress (From Local WebServer to FAKE_CLIENT_IP)
        if (srcIp.contentEquals(VPN_IP) && (srcPort == 8080 || srcPort == 19999) && dstIp.contentEquals(FAKE_CLIENT_IP)) {
            Log.d(TAG, "NAT Egress: WebServer:$srcPort -> FAKE_CLIENT_IP, rewriting back to App")
            System.arraycopy(HIJACK_IP, 0, packet, 12, 4)
            if (srcPort == 8080) {
                packet[tcpOffset] = (80 shr 8).toByte()
                packet[tcpOffset + 1] = (80 and 0xFF).toByte()
            }
            System.arraycopy(VPN_IP, 0, packet, 16, 4)
            
            computeIpChecksum(packet, 0, ihl)
            computeTcpChecksum(packet, 0, tcpOffset, tcpLen)
            return true
        }
        
        return false
    }

    private fun computeIpChecksum(packet: ByteArray, ipOffset: Int, ihl: Int) {
        packet[ipOffset + 10] = 0
        packet[ipOffset + 11] = 0
        var sum = 0
        for (i in 0 until ihl step 2) {
            sum += ((packet[ipOffset + i].toInt() and 0xFF) shl 8) or (packet[ipOffset + i + 1].toInt() and 0xFF)
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val checksum = sum.inv() and 0xFFFF
        packet[ipOffset + 10] = (checksum shr 8).toByte()
        packet[ipOffset + 11] = (checksum and 0xFF).toByte()
    }

    private fun computeTcpChecksum(packet: ByteArray, ipOffset: Int, tcpOffset: Int, tcpLen: Int) {
        packet[tcpOffset + 16] = 0
        packet[tcpOffset + 17] = 0

        var sum = 0
        // Pseudo header
        for (i in 12..19 step 2) {
            sum += ((packet[ipOffset + i].toInt() and 0xFF) shl 8) or (packet[ipOffset + i + 1].toInt() and 0xFF)
        }
        sum += 6 // Protocol TCP
        sum += tcpLen

        // TCP segment
        var i = tcpOffset
        var len = tcpLen
        while (len > 1) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
            len -= 2
        }
        if (len > 0) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val checksum = sum.inv() and 0xFFFF
        packet[tcpOffset + 16] = (checksum shr 8).toByte()
        packet[tcpOffset + 17] = (checksum and 0xFF).toByte()
    }
}
