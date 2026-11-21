package com.moni.freeglobalvpn.vpn

import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom

/**
 * Proves that a real OpenVPN server is answering on ip:port – not just an open port.
 * Sends the protocol's first packet (P_CONTROL_HARD_RESET_CLIENT_V2) and expects the server's
 * P_CONTROL_HARD_RESET_SERVER_V2 reply. Works for TCP and UDP. Nothing else is exchanged.
 */
object OpenVpnProbe {
    private const val OP_CLIENT_RESET_V2 = 7
    private const val OP_SERVER_RESET_V2 = 8
    private val rnd = SecureRandom()

    /** OpenVPN control packet without tls-auth: opcode/key, session id, empty ack array, packet id 0. */
    fun clientReset(): ByteArray {
        val p = ByteArray(14)
        p[0] = (OP_CLIENT_RESET_V2 shl 3).toByte()
        val sid = ByteArray(8).also(rnd::nextBytes)
        System.arraycopy(sid, 0, p, 1, 8)
        p[9] = 0 // ack array length
        // bytes 10..13: message packet id 0
        return p
    }

    fun isServerReset(firstByte: Int) = (firstByte and 0xFF) ushr 3 == OP_SERVER_RESET_V2

    /** @return round-trip time in ms, or null when no OpenVPN server answered. */
    fun probe(ip: String, port: Int, proto: String, timeoutMs: Int = 3_000): Int? = try {
        val t0 = System.nanoTime()
        val ok = if (proto == "tcp") probeTcp(ip, port, timeoutMs) else probeUdp(ip, port, timeoutMs)
        if (ok) ((System.nanoTime() - t0) / 1_000_000).toInt().coerceAtLeast(1) else null
    } catch (_: Exception) {
        null
    }

    private fun probeTcp(ip: String, port: Int, timeoutMs: Int): Boolean = Socket().use { s ->
        s.connect(InetSocketAddress(ip, port), timeoutMs)
        s.soTimeout = timeoutMs
        val pkt = clientReset()
        val framed = ByteArray(pkt.size + 2)
        framed[0] = (pkt.size ushr 8).toByte(); framed[1] = pkt.size.toByte()
        System.arraycopy(pkt, 0, framed, 2, pkt.size)
        s.getOutputStream().apply { write(framed); flush() }
        val inp = DataInputStream(s.getInputStream())
        val len = inp.readUnsignedShort()
        len in 10..2048 && isServerReset(inp.readUnsignedByte())
    }

    private fun probeUdp(ip: String, port: Int, timeoutMs: Int): Boolean = DatagramSocket().use { s ->
        s.soTimeout = timeoutMs
        val pkt = clientReset()
        val addr = InetAddress.getByName(ip)
        val buf = ByteArray(2048)
        repeat(2) { // UDP can drop a packet: try twice within the timeout
            s.send(DatagramPacket(pkt, pkt.size, addr, port))
            try {
                val r = DatagramPacket(buf, buf.size)
                s.receive(r)
                if (r.length >= 10 && isServerReset(buf[0].toInt())) return@use true
            } catch (_: java.net.SocketTimeoutException) { }
        }
        false
    }
}
