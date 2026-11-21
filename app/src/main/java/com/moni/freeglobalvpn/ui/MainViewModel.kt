package com.moni.freeglobalvpn.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moni.freeglobalvpn.FreeVpnApp
import com.moni.freeglobalvpn.data.CountryGroup
import com.moni.freeglobalvpn.data.DnsChoice
import com.moni.freeglobalvpn.data.ServerRepository
import com.moni.freeglobalvpn.data.Settings
import com.moni.freeglobalvpn.data.ThemeMode
import com.moni.freeglobalvpn.data.VpnServer
import com.moni.freeglobalvpn.vpn.BankingApps
import com.moni.freeglobalvpn.vpn.ConnectionState
import com.moni.freeglobalvpn.vpn.NetworkProbe
import com.moni.freeglobalvpn.vpn.SpeedTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as FreeVpnApp).container
    val repository = c.repository

    val connection: StateFlow<ConnectionState> = c.vpn.state
    val traffic = c.vpn.traffic
    val engineMessage = c.vpn.engineMessage
    val servers: StateFlow<ServerRepository.State> = repository.state

    val settings: StateFlow<Settings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val countries: StateFlow<List<CountryGroup>> = repository.state
        .map { ServerRepository.countriesOf(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val selectedOverride = MutableStateFlow<String?>(null)

    /** The server the big button will use: the one picked, else last used (if still listed). */
    val selected: StateFlow<VpnServer?> = combine(repository.state, settings, connection, selectedOverride) { st, s, conn, pick ->
        when (conn) {
            is ConnectionState.Connected -> conn.server
            is ConnectionState.Connecting -> conn.server
            is ConnectionState.Reconnecting -> conn.server
            else -> pick?.let { id -> st.servers.firstOrNull { it.id == id } }
                ?: s.lastServerId?.let { id -> st.servers.firstOrNull { it.id == id } }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    data class NetInfo(
        val ip: NetworkProbe.PublicIp? = null,
        val checking: Boolean = false,
        val dns: List<String> = emptyList(),
        val tunnelLatencyMs: Int? = null,
        /** Country of the real (non-VPN) IP, used for the "you are here" marker. */
        val homeCountry: String? = null,
    )
    private val _net = MutableStateFlow(NetInfo())
    val net: StateFlow<NetInfo> = _net.asStateFlow()

    sealed interface SpeedState {
        data object Idle : SpeedState
        data class Running(val step: String) : SpeedState
        data class Done(val result: SpeedTest.Result) : SpeedState
        data class Error(val message: String) : SpeedState
    }
    private val _speed = MutableStateFlow<SpeedState>(SpeedState.Idle)
    val speed: StateFlow<SpeedState> = _speed.asStateFlow()
    private var speedJob: Job? = null
    private var latencyJob: Job? = null

    init {
        // Re-check the public IP whenever the tunnel goes up or down: proves the IP really changed.
        viewModelScope.launch {
            connection.map { Pair(it is ConnectionState.Connected, it is ConnectionState.Disconnected || it is ConnectionState.Failed) }
                .distinctUntilChanged()
                .collect { (connected, off) ->
                    if (connected || off) {
                        delay(if (connected) 1_500 else 800)
                        refreshNetInfo()
                    }
                    latencyJob?.cancel()
                    if (connected) latencyJob = launch {
                        while (isActive) {
                            if (c.appVisible.value) _net.value = _net.value.copy(tunnelLatencyMs = NetworkProbe.tunnelLatencyMs())
                            delay(5_000)
                        }
                    } else _net.value = _net.value.copy(tunnelLatencyMs = null)
                }
        }
    }

    fun refreshNetInfo() {
        viewModelScope.launch {
            _net.value = _net.value.copy(checking = true)
            val ip = NetworkProbe.publicIp()
            val dns = withContext(Dispatchers.IO) { NetworkProbe.vpnDnsServers(getApplication()) }
            val home = if (connection.value !is ConnectionState.Connected) ip?.countryCode ?: _net.value.homeCountry else _net.value.homeCountry
            _net.value = _net.value.copy(ip = ip, dns = dns, checking = false, homeCountry = home)
        }
    }

    // ---- actions
    fun quickConnect() = c.vpn.connect(null, null)
    fun connectSelectedOrQuick() {
        val s = selected.value
        if (s != null) c.vpn.connect(s) else quickConnect()
    }
    fun connectServer(server: VpnServer) { selectedOverride.value = server.id; c.vpn.connect(server) }
    fun connectCountry(code: String) { selectedOverride.value = null; c.vpn.connect(null, code) }
    fun disconnect() = c.vpn.disconnect()
    fun refreshServers() = repository.refreshAsync()

    fun toggleFavorite(key: String) = viewModelScope.launch { c.settings.toggleFavorite(key) }
    fun setTheme(m: ThemeMode) = viewModelScope.launch { c.settings.setTheme(m) }
    fun setAutoReconnect(v: Boolean) = viewModelScope.launch { c.settings.setAutoReconnect(v) }
    fun setSmartFailover(v: Boolean) = viewModelScope.launch { c.settings.setSmartFailover(v) }
    fun setBlockWhileReconnecting(v: Boolean) = viewModelScope.launch { c.settings.setBlockWhileReconnecting(v) }
    fun setDns(v: DnsChoice) = viewModelScope.launch { c.settings.setDns(v) }
    fun setAllowLan(v: Boolean) = viewModelScope.launch { c.settings.setAllowLan(v) }
    fun acceptDisclosure() = viewModelScope.launch { c.settings.setDisclosureAccepted() }
    fun clearRecents() = viewModelScope.launch { c.settings.clearRecents() }
    fun setBypass(pkg: String, bypass: Boolean) = viewModelScope.launch {
        val cur = settings.value.bypassApps
        c.settings.setBypassApps(if (bypass) cur + pkg else cur - pkg)
    }

    suspend fun installedApps(): List<BankingApps.AppInfo> =
        withContext(Dispatchers.IO) { BankingApps.installedApps(getApplication()) }

    fun runSpeedTest() {
        if (speedJob?.isActive == true) return
        speedJob = viewModelScope.launch {
            _speed.value = SpeedState.Running("Starting…")
            _speed.value = runCatching {
                SpeedTest.run { step -> _speed.value = SpeedState.Running(step) }
            }.fold({ SpeedState.Done(it) }, { SpeedState.Error(it.message ?: "Speed test failed") })
        }
    }

    // ---- imported configs
    fun importedEntries() = c.importedProvider.entries()

    fun importConfig(uri: Uri, name: String, countryCode: String, user: String?, pass: String?, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { ins ->
                        val bytes = ins.readBytes()
                        require(bytes.size < 512 * 1024) { "File is too large for an OpenVPN config" }
                        String(bytes, Charsets.UTF_8)
                    }
                }
            }
            val config = text.getOrNull()
            if (config == null) { onResult("Could not read the file: ${text.exceptionOrNull()?.message}"); return@launch }
            val r = withContext(Dispatchers.IO) { c.importedProvider.add(name, countryCode, config, user, pass) }
            r.onSuccess { repository.refresh(); onResult("Imported \"${it.name}\"") }
                .onFailure { onResult(it.message ?: "Import failed") }
        }
    }

    fun removeImported(id: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) { c.importedProvider.remove(id) }
        repository.refresh()
    }
}
