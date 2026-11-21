package com.moni.freeglobalvpn.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.moni.freeglobalvpn.data.Countries
import com.moni.freeglobalvpn.data.ServerRepository
import com.moni.freeglobalvpn.data.Settings
import com.moni.freeglobalvpn.data.SettingsStore
import com.moni.freeglobalvpn.data.VpnServer
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.IOpenVPNServiceInternal
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.Preferences
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.VPNLaunchHelper
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the real OpenVPN engine (ics-openvpn, running in the ":openvpn" process) and turns its
 * raw state machine into [ConnectionState]. Nothing here is simulated: "Connected" is only shown
 * after the engine reports LEVEL_CONNECTED (tunnel up, routes installed).
 *
 * Stability features:
 *  - connect watchdog: a server that does not finish the handshake in time is marked failed
 *  - smart failover: automatically tries the next best server (same country, or global for Quick Connect)
 *  - reconnect watchdog: if an established tunnel cannot recover, switch server
 *  - the engine itself re-connects after network changes (WiFi <-> mobile) and pauses when offline
 */
class VpnController(
    private val app: Context,
    private val repo: ServerRepository,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) : VpnStatus.StateListener, VpnStatus.ByteCountListener {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficStats())
    val traffic: StateFlow<TrafficStats> = _traffic.asStateFlow()

    private val _engineMessage = MutableStateFlow("")
    val engineMessage: StateFlow<String> = _engineMessage.asStateFlow()

    private class Session(val countryCode: String?, val maxAttempts: Int) {
        val tried = HashSet<String>()
        var attempt = 0
    }

    private var current: VpnServer? = null
    private var session: Session? = null
    private var watchdog: Job? = null
    private var searchJob: Job? = null
    private var userStop = false
    private var connectStartedAt = 0L
    private var lastBytesAt = 0L

    init {
        VpnStatus.addStateListener(this)
        VpnStatus.addByteCountListener(this)
    }

    // ---------------------------------------------------------------- public API

    /**
     * Connect to [server], or – when null – to the best server in [countryCode]
     * (or the best server worldwide for Quick Connect when both are null).
     * The caller must already hold the VpnService permission (VpnService.prepare() == null).
     */
    fun connect(server: VpnServer?, countryCode: String? = server?.countryCode) {
        scope.launch {
            val settings = settingsStore.current()
            val target = server ?: repo.ranked(countryCode).firstOrNull()
            if (target == null) {
                _state.value = ConnectionState.Failed(
                    null,
                    if (countryCode != null) "No working server in ${Countries.name(countryCode)} right now. Try another country or refresh the list."
                    else "No working servers are available right now. Pull to refresh the server list."
                )
                return@launch
            }
            userStop = false
            session = Session(
                countryCode = if (server != null) server.countryCode else countryCode,
                maxAttempts = if (settings.smartFailover) MAX_ATTEMPTS else 1,
            )
            start(target, settings)
        }
    }

    /**
     * One-tap Smart Connect: measures the real latency from this phone to the best-ranked tested
     * servers (worldwide, or in [countryCode]), then connects to the fastest reachable one. If it does
     * not answer, smart failover moves on to the next best automatically.
     */
    fun smartConnect(countryCode: String? = null) {
        if (_state.value.isActive) return
        searchJob?.cancel()
        searchJob = scope.launch {
            val candidates = repo.ranked(countryCode).take(SMART_CANDIDATES)
            if (candidates.isEmpty()) {
                _state.value = ConnectionState.Failed(null,
                    if (countryCode != null) "No tested server in ${Countries.name(countryCode)} is reachable right now. Try Smart Connect for the best country."
                    else "No tested server is reachable right now. Tap Update servers, or check your internet connection.")
                return@launch
            }
            _state.value = ConnectionState.Searching(countryCode, candidates.size)
            repo.probeNow(candidates)
            if (_state.value !is ConnectionState.Searching) return@launch // cancelled by the user
            connect(null, countryCode)
        }
    }

    fun disconnect() {
        if (_state.value is ConnectionState.Searching) {
            searchJob?.cancel()
            _state.value = ConnectionState.Disconnected
            return
        }
        userStop = true
        session = null
        watchdog?.cancel()
        _state.value = ConnectionState.Disconnecting
        scope.launch {
            stopEngine()
            settingsStore.setConnectedSince(0)
            repo.probesPaused = false
            delay(3_000)
            if (_state.value is ConnectionState.Disconnecting) _state.value = ConnectionState.Disconnected
        }
    }

    // ---------------------------------------------------------------- engine control

    private suspend fun start(server: VpnServer, settings: Settings) {
        val s = session ?: Session(server.countryCode, MAX_ATTEMPTS).also { session = it }
        s.tried += server.id
        s.attempt++
        current = server
        _state.value = ConnectionState.Connecting(server, "Preparing secure tunnel…", s.attempt, s.maxAttempts)

        val config = repo.configFor(server.id)
        if (config == null) { onFailure("Server configuration is no longer available"); return }

        val profile = try {
            withContext(Dispatchers.IO) { buildProfile(server, config, settings) }
        } catch (e: Exception) {
            Log.w(TAG, "Profile build failed", e)
            onFailure("Invalid server configuration (${e.message})")
            return
        }
        val check = profile.checkProfile(app)
        if (check != de.blinkt.openvpn.R.string.no_error_found) {
            onFailure(app.getString(check))
            return
        }

        withContext(Dispatchers.IO) {
            val pm = ProfileManager.getInstance(app)
            // Only one profile is ever kept – the one in use.
            pm.profiles.toList().forEach { pm.removeProfile(app, it) }
            pm.addProfile(profile)
            ProfileManager.saveProfile(app, profile)
            pm.saveProfileList(app)
            // Lets Android "Always-on VPN" restart this tunnel after reboot / process death.
            Preferences.getDefaultSharedPreferences(app).edit().putString("alwaysOnVpn", profile.uuidString).apply()
        }
        settingsStore.addRecent(server)
        repo.probesPaused = true

        connectStartedAt = System.currentTimeMillis()
        VPNLaunchHelper.startOpenVpn(profile, app, "Free Global VPN", true)
        armWatchdog(CONNECT_TIMEOUT_MS) { "${server.hostName} did not respond" }
    }

    private fun buildProfile(server: VpnServer, rawConfig: String, settings: Settings): VpnProfile =
        ProfileFactory.build(
            server, rawConfig, settings,
            // Only packages that are still installed: the engine would otherwise drop them while iterating.
            bypassApps = settings.bypassApps.filter { isInstalled(it) }.toSet(),
            credentials = repo.provider(server.providerId)?.defaultCredentials(server),
        )

    private suspend fun stopEngine() = withContext(Dispatchers.Main) {
        ProfileManager.setConnectedVpnProfileDisconnected(app)
        val intent = Intent(app, OpenVPNService::class.java).setAction(OpenVPNService.START_SERVICE)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                runCatching { IOpenVPNServiceInternal.Stub.asInterface(binder).stopVPN(false) }
                    .onFailure { Log.w(TAG, "stopVPN failed", it) }
                runCatching { app.unbindService(this) }
            }
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        if (!app.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
            Log.w(TAG, "Could not bind OpenVPNService to stop it")
        }
    }

    private fun armWatchdog(timeoutMs: Long, reason: () -> String) {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(timeoutMs)
            if (_state.value !is ConnectionState.Connected) onFailure(reason())
        }
    }

    private fun onFailure(reason: String) {
        watchdog?.cancel()
        val failed = current
        failed?.let { repo.recordFailure(it.id) }
        val s = session
        if (!userStop && s != null && s.attempt < s.maxAttempts) {
            val next = repo.ranked(s.countryCode, exclude = s.tried).firstOrNull()
            if (next != null) {
                scope.launch {
                    _state.value = ConnectionState.Connecting(next, "$reason – trying ${next.hostName}…", s.attempt + 1, s.maxAttempts)
                    start(next, settingsStore.current())
                }
                return
            }
        }
        session = null
        scope.launch { stopEngine(); settingsStore.setConnectedSince(0) }
        repo.probesPaused = false
        _state.value = ConnectionState.Failed(
            failed,
            if (s != null && s.attempt > 1) "$reason. Tried ${s.attempt} servers – none answered. Free volunteer servers come and go; please try again or pick another country."
            else reason,
        )
    }

    // ---------------------------------------------------------------- engine callbacks

    override fun updateState(state: String?, logmessage: String?, localizedResId: Int, level: ConnectionStatus?, intent: Intent?) {
        scope.launch(Dispatchers.Main.immediate) {
            handleState(state.orEmpty(), logmessage, localizedResId, level ?: ConnectionStatus.UNKNOWN_LEVEL)
        }
    }

    override fun setConnectedVPN(uuid: String?) {}

    private suspend fun handleState(raw: String, log: String?, resId: Int, level: ConnectionStatus) {
        val detail = (if (resId != 0) runCatching { app.getString(resId) }.getOrNull() else null) ?: raw
        _engineMessage.value = listOfNotNull(detail, log?.takeIf { it.isNotBlank() }).joinToString(" – ")
        val prev = _state.value

        if (raw == "USER_VPN_PERMISSION_CANCELLED") {
            session = null
            _state.value = ConnectionState.Failed(current, "VPN permission was denied")
            return
        }

        when (level) {
            ConnectionStatus.LEVEL_CONNECTED -> {
                watchdog?.cancel()
                val server = current ?: restoreServer() ?: return
                current = server
                val settings = settingsStore.current()
                val since = if (prev is ConnectionState.Connected) prev.since
                    else if (prev is ConnectionState.Reconnecting && settings.connectedSince > 0) settings.connectedSince
                    else if (prev is ConnectionState.Disconnected && settings.connectedSince > 0) settings.connectedSince // app restarted
                    else System.currentTimeMillis()
                settingsStore.setConnectedSince(since)
                repo.recordSuccess(server.id)
                repo.probesPaused = true
                session?.let { it.attempt = 0; it.tried.clear(); it.tried += server.id }
                _state.value = ConnectionState.Connected(server, since)
            }

            ConnectionStatus.LEVEL_START,
            ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
            ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED -> {
                val server = current ?: restoreServer() ?: return
                current = server
                when (prev) {
                    is ConnectionState.Connected, is ConnectionState.Reconnecting,
                    is ConnectionState.WaitingForNetwork, is ConnectionState.Paused -> {
                        _state.value = ConnectionState.Reconnecting(server, detail)
                        if (prev !is ConnectionState.Reconnecting && settingsStore.current().smartFailover) {
                            if (session == null) session = Session(server.countryCode, MAX_ATTEMPTS).also { it.tried += server.id; it.attempt = 1 }
                            armWatchdog(RECONNECT_TIMEOUT_MS) { "Lost connection to ${server.hostName}" }
                        }
                    }
                    is ConnectionState.Connecting -> _state.value = prev.copy(detail = detail)
                    else -> if (!userStop) {
                        val s = session
                        _state.value = ConnectionState.Connecting(server, detail, s?.attempt ?: 1, s?.maxAttempts ?: 1)
                    }
                }
            }

            ConnectionStatus.LEVEL_NONETWORK -> {
                // No failover while offline – the engine resumes automatically when a network returns.
                watchdog?.cancel()
                if (!userStop && (prev.isActive)) _state.value = ConnectionState.WaitingForNetwork(current)
            }

            ConnectionStatus.LEVEL_VPNPAUSED -> {
                watchdog?.cancel()
                if (!userStop) _state.value = ConnectionState.Paused(current)
            }

            ConnectionStatus.LEVEL_AUTH_FAILED -> onFailure("Server rejected the login")

            ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT -> {
                session = null
                scope.launch { stopEngine() }
                _state.value = ConnectionState.Failed(current, "This server needs a username/password. Add them when importing the config.")
            }

            ConnectionStatus.LEVEL_NOTCONNECTED -> {
                val sinceStart = System.currentTimeMillis() - connectStartedAt
                when {
                    userStop || prev is ConnectionState.Disconnecting || prev is ConnectionState.Disconnected -> {
                        watchdog?.cancel()
                        _state.value = ConnectionState.Disconnected
                        _traffic.value = TrafficStats()
                        repo.probesPaused = false
                    }
                    // Ignore the transient NOPROCESS emitted while an old process is replaced.
                    sinceStart < 2_500 -> Unit
                    prev is ConnectionState.Connecting -> onFailure(detail.ifBlank { "Connection failed" })
                    prev is ConnectionState.Connected || prev is ConnectionState.Reconnecting -> {
                        val server = current
                        if (server != null && settingsStore.current().autoReconnect) {
                            if (session == null) session = Session(server.countryCode, MAX_ATTEMPTS)
                            start(server, settingsStore.current())
                        } else {
                            _state.value = ConnectionState.Failed(server, "The VPN connection ended unexpectedly")
                        }
                    }
                    else -> Unit
                }
            }

            else -> Unit
        }
    }

    /** After an app restart while the VPN kept running: find out which server it is using. */
    private suspend fun restoreServer(): VpnServer? {
        val settings = settingsStore.current()
        val id = settings.lastServerId ?: return null
        repo.state.first { !it.loadingFromCache }
        repo.server(id)?.let { return it }
        // No longer in the public list – rebuild what we know from the recent-connections record.
        val r = settings.recents.firstOrNull { it.serverId == id } ?: return null
        val parts = id.split(':')
        return VpnServer(
            id = id, providerId = parts.getOrElse(0) { "" }, hostName = r.hostName, ip = parts.getOrElse(1) { "" },
            port = parts.getOrNull(2)?.toIntOrNull() ?: 0, protocol = parts.getOrElse(3) { "" },
            countryCode = r.countryCode, countryName = Countries.name(r.countryCode),
        )
    }

    private fun isInstalled(pkg: String): Boolean = try {
        app.packageManager.getApplicationInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    override fun updateByteCount(inBytes: Long, outBytes: Long, diffIn: Long, diffOut: Long) {
        val now = System.currentTimeMillis()
        val dt = if (lastBytesAt == 0L) 2.0 else ((now - lastBytesAt) / 1000.0).coerceIn(0.5, 10.0)
        lastBytesAt = now
        _traffic.value = TrafficStats(
            downBps = (diffIn / dt).toLong(),
            upBps = (diffOut / dt).toLong(),
            totalDown = inBytes,
            totalUp = outBytes,
        )
    }

    private companion object {
        const val TAG = "VpnController"
        const val MAX_ATTEMPTS = 4
        const val SMART_CANDIDATES = 16
        const val CONNECT_TIMEOUT_MS = 25_000L
        const val RECONNECT_TIMEOUT_MS = 45_000L
    }
}
