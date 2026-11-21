package com.moni.freeglobalvpn.data.provider

/** Small helpers for inspecting and hardening OpenVPN config text before it reaches the engine. */
object OvpnConfig {
    data class Remote(val host: String, val port: Int, val proto: String)

    /**
     * Directives that execute programs, write files or open control channels. Untrusted public
     * configs must never be able to use them, so they are stripped (defence in depth – the
     * ics-openvpn parser already ignores most of them).
     */
    private val forbidden = setOf(
        "up", "down", "route-up", "route-pre-down", "ipchange", "tls-verify", "client-connect",
        "client-disconnect", "learn-address", "auth-user-pass-verify", "script-security", "plugin",
        "management", "management-client", "log", "log-append", "status", "writepid", "cd",
        "chroot", "setenv", "setenv-safe", "daemon", "tmp-dir", "iproute", "dhcp-option-script",
    )

    fun firstRemote(config: String): Remote? {
        var proto = "udp"
        var remote: Remote? = null
        for (raw in config.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#") || line.startsWith(";")) continue
            val parts = line.split(Regex("\\s+"))
            when (parts.firstOrNull()?.lowercase()) {
                "proto" -> if (parts.size > 1) proto = normalizeProto(parts[1])
                "remote" -> if (remote == null && parts.size > 1) {
                    val port = parts.getOrNull(2)?.toIntOrNull() ?: 1194
                    val p = parts.getOrNull(3)?.let { normalizeProto(it) }
                    remote = Remote(parts[1], port, p ?: "")
                }
            }
        }
        val r = remote ?: return null
        return r.copy(proto = r.proto.ifEmpty { proto })
    }

    private fun normalizeProto(p: String) = if (p.lowercase().startsWith("tcp")) "tcp" else "udp"

    /** Removes dangerous directives, keeping inline blocks (<ca>, <cert>, ...) intact. */
    fun sanitize(config: String): String {
        val out = StringBuilder(config.length)
        var inBlock = false
        for (raw in config.lineSequence()) {
            val line = raw.trimEnd('\r')
            val t = line.trim()
            if (t.startsWith("<") && !t.startsWith("</")) inBlock = true
            if (t.startsWith("</")) { inBlock = false; out.append(line).append('\n'); continue }
            if (!inBlock) {
                val key = t.split(Regex("\\s+")).firstOrNull()?.lowercase().orEmpty()
                if (key in forbidden) continue
            }
            out.append(line).append('\n')
        }
        return out.toString()
    }

    fun needsUserPass(config: String): Boolean =
        config.lineSequence().any { it.trim().startsWith("auth-user-pass") } &&
            !config.contains("<auth-user-pass>")
}
