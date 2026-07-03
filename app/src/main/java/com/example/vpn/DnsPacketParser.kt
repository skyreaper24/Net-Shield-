package com.example.vpn

import java.nio.ByteBuffer

object DnsPacketParser {

    fun parseDomainName(dnsPayload: ByteArray, dnsHeaderOffset: Int = 0): String? {
        val builder = StringBuilder()
        var offset = dnsHeaderOffset + 12 // Skip DNS Header (12 bytes)
        if (offset >= dnsPayload.size) return null

        try {
            while (offset < dnsPayload.size) {
                val len = dnsPayload[offset].toInt() and 0xFF
                if (len == 0) {
                    break
                }
                if ((len and 0xC0) == 0xC0) {
                    // Pointer compression is not expected in standard query questions, but return null if seen
                    return null
                }
                if (offset + 1 + len > dnsPayload.size) return null
                if (builder.isNotEmpty()) builder.append('.')
                builder.append(String(dnsPayload, offset + 1, len, Charsets.US_ASCII))
                offset += 1 + len
            }
        } catch (e: Exception) {
            return null
        }
        return builder.toString()
    }

    fun buildNxDomainResponse(queryPayload: ByteArray): ByteArray {
        val response = queryPayload.copyOf()
        if (response.size < 12) return response

        // Update Flags to 0x8183 (Standard response, Recursion available, NXDOMAIN status)
        response[2] = 0x81.toByte()
        response[3] = 0x83.toByte()

        // Clear Answer, Authority, and Additional RR counts to indicate 0 records
        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        response[10] = 0
        response[11] = 0

        return response
    }

    fun buildIpUdpResponse(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcPort: Int,
        destPort: Int,
        dnsPayload: ByteArray
    ): ByteArray {
        val ipLength = 20
        val udpLength = 8
        val totalLength = ipLength + udpLength + dnsPayload.size
        val packet = ByteArray(totalLength)

        // 1. IP Header
        packet[0] = 0x45.toByte() // Version 4, IHL 5 (20 bytes)
        packet[1] = 0x00.toByte() // Type of Service
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        packet[4] = 0x00.toByte() // Identification
        packet[5] = 0x00.toByte()
        packet[6] = 0x40.toByte() // Flags: Don't Fragment (0x4000)
        packet[7] = 0x00.toByte()
        packet[8] = 0x40.toByte() // TTL 64
        packet[9] = 0x11.toByte() // Protocol: UDP (17)
        packet[10] = 0x00.toByte() // IP Header Checksum placeholder
        packet[11] = 0x00.toByte()

        // Source and Destination IP
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(destIp, 0, packet, 16, 4)

        // Calculate and write the IP checksum
        val ipChecksum = calculateChecksum(packet, 0, 20)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // 2. UDP Header
        packet[20] = ((srcPort shr 8) and 0xFF).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = ((destPort shr 8) and 0xFF).toByte()
        packet[23] = (destPort and 0xFF).toByte()
        
        val udpTotalLength = udpLength + dnsPayload.size
        packet[24] = ((udpTotalLength shr 8) and 0xFF).toByte()
        packet[25] = (udpTotalLength and 0xFF).toByte()
        packet[26] = 0x00.toByte() // UDP checksum is optional in IPv4 (0 means disabled)
        packet[27] = 0x00.toByte()

        // 3. DNS Payload
        System.arraycopy(dnsPayload, 0, packet, 28, dnsPayload.size)

        return packet
    }

    private fun calculateChecksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length - 1
        while (i < end) {
            val high = buf[i].toInt() and 0xFF
            val low = buf[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }
        if (i == end) {
            val last = buf[end].toInt() and 0xFF
            sum += last shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()) and 0xFFFF
    }
}
