package com.moni.freeglobalvpn.data

import com.moni.freeglobalvpn.data.provider.ServerProvider
import com.moni.freeglobalvpn.data.provider.VerifiedListProvider
import com.moni.freeglobalvpn.data.provider.VpnGateParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Aggregates every [ServerProvider], keeps a disk cache, runs health checks and exposes the
 * live list. Only servers that were actually published by a provider are ever shown.
 */
class ServerRepository(
    private val providers: List<ServerProvider>,
    private val cache: ServerCache,
    private val scope: CoroutineScope,
    initialProbesPaused: Boolean = false,
) {
    data class State(
        val servers: List<VpnServer> = emptyList(),
        val health: Map<String, ServerHealth> = emptyMap(),
        val fetchedAt: Long = 0,
        val loadingFromCache: Boolean = true,
        val refreshing: Boolean = false,
        val checkingHealth: Boolean = false,
        val error: String? = null,
        /** providerId -> error for partial failures */
        val providerErrors: Map<String, String> = emptyMap(),
        /** When the verified-good list was produced (0 = not using it). */
        val verifiedAt: Long = 0,
        /** True when the verified list was unavailable and the raw live list is shown instead. */
        val unverifiedFallback: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val refreshLock = Mutex()
    private var healthJob: Job? = null
    private val liveConfigs = HashMap<String, String>()

    /** When true (VPN connected) probes would travel through the tunnel and be meaningless. */
    @Volatile var probesPaused = initialProbesPaused


    init {
        scope.launch(Dispatchers.IO) {
            // Older app versions cached the untested live list – keep only tested/imported servers.
            val snap = cache.loadSnapshot()?.let { sn -> sn.copy(servers = sn.servers.filter { it.providerId != VpnGateParser.PROVIDER_ID }) }
            _state.update {
                if (snap != null) it.copy(servers = snap.servers, health = snap.health, fetchedAt = snap.fetchedAt, loadingFromCache = false,
                    verifiedAt = snap.verifiedAt, unverifiedFallback = snap.verifiedAt == 0L && snap.servers.any { s -> s.providerId == VpnGateParser.PROVIDER_ID })
                else it.copy(loadingFromCache = false)
            }
            if (snap == null || System.currentTimeMillis() - snap.fetchedAt > STALE_MS) refresh()
            else if (!probesPaused) runHealthChecks()
        }
    }

    fun provider(id: String) = providers.firstOrNull { it.id == id }
    fun providers() = providers

    fun refreshAsync() { scope.launch { refresh() } }

    suspend fun refresh() = refreshLock.withLock {
        _state.update { it.copy(refreshing = true, error = null) }
        val now = System.currentTimeMillis()
        // 1) Everything except the raw live VPN Gate list (verified list + imported configs).
        val results = withContext(Dispatchers.IO) {
            providers.filter { it.id != VpnGateParser.PROVIDER_ID }
                .map { p -> async { p.id to runCatching { p.fetch() } } }.awaitAll()
        }.toMutableList()
        val verified = results.firstOrNull { it.first == VerifiedListProvider.ID }?.second?.getOrNull()
        val verifiedFresh = verified != null && verified.servers.isNotEmpty()
        // 2) Only good, tested servers are shown. The unverified live list is a fallback for when the
        //    verified list can't be downloaded or the verification job has stopped.
        if (!verifiedFresh) {
            providers.firstOrNull { it.id == VpnGateParser.PROVIDER_ID }?.let { p ->
                results += p.id to withContext(Dispatchers.IO) { runCatching { p.fetch() } }
            }
        }
        val servers = ArrayList<VpnServer>()
        val configs = HashMap<String, String>()
        val errors = HashMap<String, String>()
        for ((id, r) in results) {
            if (id == VerifiedListProvider.ID && !verifiedFresh) {
                r.exceptionOrNull()?.let { errors[id] = it.message ?: it.javaClass.simpleName }
                    ?: run { errors[id] = "verified list is out of date" }
                continue
            }
            r.onSuccess { servers += it.servers; configs += it.configs }
                .onFailure { errors[id] = it.message ?: it.javaClass.simpleName }
        }
        if (servers.isEmpty()) {
            // Keep the previous (cached) list, but tell the user it could not be updated.
            _state.update {
                it.copy(refreshing = false, providerErrors = errors,
                    error = "Could not update server list. Check your internet connection." +
                        if (it.servers.isNotEmpty()) " Showing the last known list." else "")
            }
            return@withLock
        }
        synchronized(liveConfigs) { liveConfigs.clear(); liveConfigs.putAll(configs) }
        val ids = servers.mapTo(HashSet()) { it.id }
        val prevHealth = _state.value.health.filterKeys { it in ids }
        val verifiedAt = if (verifiedFresh) verified!!.generatedAt ?: 0L else 0L
        withContext(Dispatchers.IO) {
            cache.saveConfigs(configs)
            cache.saveSnapshot(ServerCache.Snapshot(now, servers, prevHealth, verifiedAt))
        }
        _state.update {
            it.copy(servers = servers, health = prevHealth, fetchedAt = now, refreshing = false,
                providerErrors = errors, verifiedAt = verifiedAt, unverifiedFallback = !verifiedFresh)
        }
        if (!probesPaused) runHealthChecks()
    }

    fun runHealthChecks() {
        if (healthJob?.isActive == true) return
        healthJob = scope.launch {
            _state.update { it.copy(checkingHealth = true) }
            val s = _state.value
            val fresh = HealthChecker.checkAll(s.servers, s.health)
            if (probesPaused) { _state.update { it.copy(checkingHealth = false) }; return@launch }
            _state.update { it.copy(health = it.health + fresh, checkingHealth = false) }
            persist()
        }
    }

    /** Periodic background health checks while the app is visible and the VPN is off. */
    fun startAutoHealth(visible: () -> Boolean): Job = scope.launch {
        while (true) {
            delay(HEALTH_INTERVAL_MS)
            if (visible() && !probesPaused) {
                if (System.currentTimeMillis() - _state.value.fetchedAt > STALE_MS) refresh() else runHealthChecks()
            }
        }
    }

    /** Real latency probe of [servers] from this phone right now (used by Smart Connect). */
    suspend fun probeNow(servers: List<VpnServer>) {
        val fresh = HealthChecker.checkAll(servers, _state.value.health, parallelism = 16)
        _state.update { it.copy(health = it.health + fresh) }
        persist()
    }

    /** Forget all measurements and failures (Developer options). */
    fun resetHealth() {
        _state.update { it.copy(health = emptyMap()) }
        scope.launch { persist(); if (!probesPaused) runHealthChecks() }
    }

    fun recordFailure(serverId: String) {
        _state.update { st ->
            val h = st.health[serverId] ?: ServerHealth(reachable = true)
            st.copy(health = st.health + (serverId to h.copy(failures = h.failures + 1, lastFailureAt = System.currentTimeMillis())))
        }
        scope.launch(Dispatchers.IO) { persist() }
    }

    fun recordSuccess(serverId: String) {
        _state.update { st ->
            val h = st.health[serverId] ?: return@update st
            st.copy(health = st.health + (serverId to h.copy(failures = 0)))
        }
    }

    suspend fun configFor(serverId: String): String? = withContext(Dispatchers.IO) {
        synchronized(liveConfigs) { liveConfigs[serverId] } ?: cache.loadConfig(serverId)
            ?: providers.firstOrNull { serverId.startsWith(it.id + ":") }?.let { p ->
                runCatching { p.fetch().configs[serverId] }.getOrNull()
            }
    }

    fun server(id: String) = _state.value.servers.firstOrNull { it.id == id }

    fun ranked(countryCode: String? = null, exclude: Set<String> = emptySet()): List<VpnServer> {
        val s = _state.value
        val pool = s.servers.filter { (countryCode == null || it.countryCode == countryCode) && it.id !in exclude }
        return ServerRanking.rank(pool, s.health)
    }

    fun countries(): List<CountryGroup> = countriesOf(_state.value)

    private suspend fun persist() = withContext(Dispatchers.IO) {
        val s = _state.value
        cache.saveSnapshot(ServerCache.Snapshot(s.fetchedAt, s.servers, s.health, s.verifiedAt))
    }

    companion object {
        const val STALE_MS = 2 * 60 * 60_000L     // refresh list every 2 h
        const val HEALTH_INTERVAL_MS = 10 * 60_000L

        fun countriesOf(s: State): List<CountryGroup> {
            val byCountry = s.servers.groupBy { it.countryCode }
            val groups = byCountry.filterValues { list -> ServerRanking.rank(list, s.health).isNotEmpty() }.map { (cc, list) ->
                val ranked = ServerRanking.rank(list, s.health)
                CountryGroup(
                    code = cc,
                    name = list.first().countryName,
                    // Only servers that still answer from this phone; dead/unreachable ones are hidden.
                    servers = ranked,
                    bestLatencyMs = ranked.firstNotNullOfOrNull { s.health[it.id]?.latencyMs }
                        ?: ranked.firstOrNull()?.reportedPingMs,
                    availability = ServerRanking.availability(list, s.health),
                )
            }.toMutableList()
            // Requested countries with no live server: listed honestly as unavailable.
            val shown = groups.map { it.code }.toSet()
            for (cc in Countries.requested) if (cc !in shown) {
                groups += CountryGroup(cc, Countries.name(cc), emptyList(), null, Availability.UNAVAILABLE)
            }
            return groups.sortedWith(compareBy<CountryGroup> { !it.isAvailable }.thenBy { it.name })
        }
    }
}
