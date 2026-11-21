package com.moni.freeglobalvpn.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * On-demand throughput test against Cloudflare's public speed-test endpoints
 * (the same ones speed.cloudflare.com uses). Runs through the tunnel when connected.
 */
object SpeedTest {
    data class Result(val downMbps: Double, val upMbps: Double?)

    suspend fun run(onProgress: (String) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        onProgress("Testing download…")
        val down = download(25_000_000, 10_000)
        onProgress("Testing upload…")
        val up = runCatching { upload(4_000_000) }.getOrNull()
        Result(down, up)
    }

    private suspend fun download(bytes: Long, maxMs: Long): Double {
        val c = URL("https://speed.cloudflare.com/__down?bytes=$bytes").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        val buf = ByteArray(64 * 1024)
        var total = 0L
        val t0 = System.nanoTime()
        try {
            c.inputStream.use { ins ->
                while (true) {
                    coroutineContext.ensureActive()
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if ((System.nanoTime() - t0) / 1_000_000 > maxMs) break
                }
            }
        } finally { c.disconnect() }
        val secs = (System.nanoTime() - t0) / 1e9
        return total * 8 / 1e6 / secs
    }

    private fun upload(bytes: Int): Double {
        val c = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.doOutput = true; c.requestMethod = "POST"
        c.setFixedLengthStreamingMode(bytes)
        val chunk = ByteArray(64 * 1024)
        val t0 = System.nanoTime()
        try {
            c.outputStream.use { os ->
                var left = bytes
                while (left > 0) { val n = minOf(left, chunk.size); os.write(chunk, 0, n); left -= n }
            }
            c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
        val secs = (System.nanoTime() - t0) / 1e9
        return bytes * 8 / 1e6 / secs
    }
}
