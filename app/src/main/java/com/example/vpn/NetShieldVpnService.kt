package com.example.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket

class NetShieldVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var readJob: Job? = null

    // A robust, standard set of test blocked domains for the initial milestone
    private val defaultBlockedDomains = setOf(
        "doubleclick.net",
        "testblocked.com",
        "ads.google.com",
        "telemetry.app",
        "analytics.google.com",
        "crashlytics.com",
        "facebook-telemetry.com"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return // Already running
        
        VpnStateTracker.setStatus(VpnStatus.STARTING)
        try {
            val builder = Builder()
                .setSession("NetShield")
                .setMtu(1500)
                .addAddress("10.0.0.2", 32)
                .addDnsServer("10.0.0.1")
                .addRoute("10.0.0.1", 32)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                VpnStateTracker.setStatus(VpnStatus.ERROR)
                return
            }

            VpnStateTracker.setStatus(VpnStatus.ACTIVE)
            startPacketReading()
        } catch (e: Exception) {
            VpnStateTracker.setStatus(VpnStatus.ERROR)
        }
    }

    private fun startPacketReading() {
        readJob = serviceScope.launch {
            val pfd = vpnInterface ?: return@launch
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val buffer = ByteArray(16384)

            try {
                while (isActive) {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        processPacket(buffer, length, outputStream)
                    }
                }
            } catch (e: Exception) {
                // Handle read exceptions or tunnel closure gracefully
            }
        }
    }

    private fun processPacket(buffer: ByteArray, length: Int, outputStream: FileOutputStream) {
        // Parse IPv4 packet and UDP
        val isIPv4 = (buffer[0].toInt() and 0xF0) == 0x40
        if (!isIPv4) return

        val ihl = (buffer[0].toInt() and 0x0F) * 4
        if (ihl + 8 > length) return // Not enough space for UDP header

        val protocol = buffer[9].toInt() and 0xFF
        if (protocol != 17) return // Protocol is not UDP

        // UDP Source and Destination Ports
        val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
        val destPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)

        if (destPort != 53) return // Not a DNS packet

        val udpLen = ((buffer[ihl + 4].toInt() and 0xFF) shl 8) or (buffer[ihl + 5].toInt() and 0xFF)
        val dnsPayloadSize = udpLen - 8
        if (ihl + 8 + dnsPayloadSize > length || dnsPayloadSize <= 0) return

        val dnsPayload = ByteArray(dnsPayloadSize)
        System.arraycopy(buffer, ihl + 8, dnsPayload, 0, dnsPayloadSize)

        // Parse DNS query domain
        val domain = DnsPacketParser.parseDomainName(dnsPayload) ?: return

        // Matching logic
        val isBlocked = shouldBlockDomain(domain)
        if (isBlocked) {
            VpnStateTracker.incrementBlocked()
            VpnStateTracker.addLog(domain, true, "Advertising / Tracking")

            // Build blocked DNS packet
            val blockedDnsPayload = DnsPacketParser.buildNxDomainResponse(dnsPayload)
            val responsePacket = DnsPacketParser.buildIpUdpResponse(
                srcIp = byteArrayOf(10, 0, 0, 1),
                destIp = byteArrayOf(10, 0, 0, 2),
                srcPort = 53,
                destPort = srcPort,
                dnsPayload = blockedDnsPayload
            )

            serviceScope.launch {
                try {
                    synchronized(outputStream) {
                        outputStream.write(responsePacket)
                    }
                } catch (ignored: Exception) {}
            }
        } else {
            VpnStateTracker.incrementAllowed()
            VpnStateTracker.addLog(domain, false, "Allowed System Query")

            // Resolve upstream
            resolveUpstream(dnsPayload, srcPort, outputStream)
        }
    }

    private fun shouldBlockDomain(domain: String): Boolean {
        // Suffix/exact matching against blocked list
        val normalizedDomain = domain.lowercase().trim()
        if (defaultBlockedDomains.contains(normalizedDomain)) return true
        
        // Match subdomain suffixes as well (e.g., ads.doubleclick.net)
        for (blocked in defaultBlockedDomains) {
            if (normalizedDomain.endsWith(".$blocked")) {
                return true
            }
        }
        return false
    }

    private fun resolveUpstream(
        dnsQueryPayload: ByteArray,
        clientSourcePort: Int,
        outputStream: FileOutputStream
    ) {
        serviceScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protect(socket) // Crucial to prevent loops
                socket.soTimeout = 2500

                val serverAddr = java.net.InetAddress.getByName("1.1.1.1") // Cloudflare DNS resolver
                val sendPacket = DatagramPacket(dnsQueryPayload, dnsQueryPayload.size, serverAddr, 53)
                socket.send(sendPacket)

                val receiveBuffer = ByteArray(4096)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                socket.receive(receivePacket)

                val dnsResponsePayload = ByteArray(receivePacket.length)
                System.arraycopy(receiveBuffer, 0, dnsResponsePayload, 0, receivePacket.length)

                // Build full response IP/UDP packet
                val ipResponse = DnsPacketParser.buildIpUdpResponse(
                    srcIp = byteArrayOf(10, 0, 0, 1),
                    destIp = byteArrayOf(10, 0, 0, 2),
                    srcPort = 53,
                    destPort = clientSourcePort,
                    dnsPayload = dnsResponsePayload
                )

                synchronized(outputStream) {
                    outputStream.write(ipResponse)
                }
            } catch (e: Exception) {
                // On resolution failure, reply gracefully with NXDOMAIN
                val nxResponse = DnsPacketParser.buildNxDomainResponse(dnsQueryPayload)
                val ipResponse = DnsPacketParser.buildIpUdpResponse(
                    srcIp = byteArrayOf(10, 0, 0, 1),
                    destIp = byteArrayOf(10, 0, 0, 2),
                    srcPort = 53,
                    destPort = clientSourcePort,
                    dnsPayload = nxResponse
                )
                try {
                    synchronized(outputStream) {
                        outputStream.write(ipResponse)
                    }
                } catch (ignored: Exception) {}
            } finally {
                socket?.close()
            }
        }
    }

    private fun stopVpn() {
        readJob?.cancel()
        readJob = null
        try {
            vpnInterface?.close()
        } catch (ignored: Exception) {}
        vpnInterface = null
        VpnStateTracker.setStatus(VpnStatus.STOPPED)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.vpn.STOP"
    }
}
