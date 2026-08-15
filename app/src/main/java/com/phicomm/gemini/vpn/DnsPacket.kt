package com.phicomm.gemini.vpn

import java.nio.ByteBuffer

/**
 * Minimal DNS/UDP/IPv4 parser and crafter for local DNS spoofing.
 */
object DnsPacket {

    // IP header constants
    private const val IP_HEADER_LENGTH = 20
    private const val UDP_HEADER_LENGTH = 8

    fun isDnsQuery(packet: ByteArray, packetLength: Int): Boolean {
        if (packetLength < IP_HEADER_LENGTH + UDP_HEADER_LENGTH) return false

        val buffer = ByteBuffer.wrap(packet, 0, packetLength)
        val versionAndIHL = buffer.get().toInt() and 0xFF
        val ihl = (versionAndIHL and 0x0F) * 4
        if (ihl < IP_HEADER_LENGTH || packetLength < ihl + UDP_HEADER_LENGTH) return false

        val protocol = buffer.get(9).toInt() and 0xFF
        if (protocol != 17) return false // Not UDP

        // Read destination port
        val dstPort = buffer.getShort(ihl + 2).toInt() and 0xFFFF
        return dstPort == 53
    }

    fun parseDnsQueryDomain(packet: ByteArray, packetLength: Int): String? {
        if (!isDnsQuery(packet, packetLength)) return null

        val buffer = ByteBuffer.wrap(packet, 0, packetLength)
        val ihl = ((buffer.get(0).toInt() and 0xFF) and 0x0F) * 4
        val dnsOffset = ihl + UDP_HEADER_LENGTH

        if (packetLength <= dnsOffset + 12) return null // DNS header is 12 bytes

        buffer.position(dnsOffset)
        // Skip DNS header (ID, Flags, QDCOUNT, ANCOUNT, NSCOUNT, ARCOUNT)
        buffer.position(dnsOffset + 12)

        return readDomainName(buffer)
    }

    private fun readDomainName(buffer: ByteBuffer): String? {
        val sb = java.lang.StringBuilder()
        var len = buffer.get().toInt() and 0xFF
        while (len > 0) {
            if ((len and 0xC0) == 0xC0) {
                // Pointer, we don't handle pointers in simple query parsing
                return null
            }
            val part = ByteArray(len)
            buffer.get(part)
            sb.append(String(part)).append(".")
            len = buffer.get().toInt() and 0xFF
        }
        if (sb.isNotEmpty()) {
            sb.deleteCharAt(sb.length - 1)
        }
        return sb.toString()
    }

    fun createDnsResponse(queryPacket: ByteArray, queryLength: Int, fakeIp: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(queryPacket, 0, queryLength)
        val ihl = ((buffer.get(0).toInt() and 0xFF) and 0x0F) * 4
        
        val srcIp = ByteArray(4)
        buffer.position(12)
        buffer.get(srcIp)
        
        val dstIp = ByteArray(4)
        buffer.get(dstIp)

        val srcPort = buffer.getShort(ihl).toInt() and 0xFFFF
        val dstPort = buffer.getShort(ihl + 2).toInt() and 0xFFFF

        val dnsOffset = ihl + UDP_HEADER_LENGTH
        
        // Find end of query
        buffer.position(dnsOffset + 12)
        readDomainName(buffer)
        buffer.getShort() // QTYPE
        buffer.getShort() // QCLASS
        val queryEnd = buffer.position()

        val dnsPayloadSize = (queryEnd - dnsOffset) + 16 // 16 bytes for answer
        val udpLength = UDP_HEADER_LENGTH + dnsPayloadSize
        val totalLength = IP_HEADER_LENGTH + udpLength

        val response = ByteArray(totalLength)
        val outBuffer = ByteBuffer.wrap(response)

        // Write IPv4 Header
        outBuffer.put(0x45.toByte()) // Version + IHL
        outBuffer.put(0.toByte()) // TOS
        outBuffer.putShort(totalLength.toShort()) // Total Length
        outBuffer.putShort(0.toShort()) // ID
        outBuffer.putShort(0.toShort()) // Flags & Fragment Offset
        outBuffer.put(64.toByte()) // TTL
        outBuffer.put(17.toByte()) // Protocol (UDP)
        outBuffer.putShort(0.toShort()) // Checksum placeholder
        outBuffer.put(dstIp) // Src IP (swapped)
        outBuffer.put(srcIp) // Dst IP (swapped)

        // Write UDP Header
        outBuffer.putShort(dstPort.toShort()) // Src Port (swapped)
        outBuffer.putShort(srcPort.toShort()) // Dst Port (swapped)
        outBuffer.putShort(udpLength.toShort()) // Length
        outBuffer.putShort(0.toShort()) // Checksum

        // Copy DNS Query Payload
        val dnsPayload = ByteArray(queryEnd - dnsOffset)
        System.arraycopy(queryPacket, dnsOffset, dnsPayload, 0, dnsPayload.size)
        
        // Modify DNS Header for Response
        dnsPayload[2] = 0x81.toByte() // Flags: Standard query response, No error
        dnsPayload[3] = 0x80.toByte()
        dnsPayload[7] = 1.toByte() // ANCOUNT = 1

        outBuffer.put(dnsPayload)

        // Write DNS Answer (A record)
        outBuffer.putShort(0xC00C.toShort()) // Name pointer to offset 12 in DNS payload
        outBuffer.putShort(1.toShort()) // Type A
        outBuffer.putShort(1.toShort()) // Class IN
        outBuffer.putInt(300) // TTL 300s
        outBuffer.putShort(4.toShort()) // Data length 4
        outBuffer.put(fakeIp) // Fake IP (e.g. 127.0.0.1)

        // Compute IP checksum
        outBuffer.position(10)
        outBuffer.putShort(computeChecksum(response, 0, IP_HEADER_LENGTH))

        return response
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }
}
