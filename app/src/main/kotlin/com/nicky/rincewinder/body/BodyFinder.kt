package com.nicky.rincewinder.body

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import androidx.core.content.ContextCompat
import com.nicky.rincewinder.core.AgentStatus
import com.nicky.rincewinder.core.BodyLink
import com.nicky.rincewinder.core.Discovery
import com.nicky.rincewinder.core.HttpAgentClient
import com.nicky.rincewinder.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address

/** Výsledek hledání těla: host (může být null) + pět stavů Domů + status Agenta, když běží. */
data class BodyState(
    val link: BodyLink = BodyLink.NIC_NEODPOVIDA,
    val host: String? = null,
    val status: AgentStatus? = null,
    val agentInstalled: Boolean? = null,
    val permissionMissing: Boolean = false,
    val searching: Boolean = false,
)

/**
 * Objevení těla (ZADANI_F1 §5.5): 1) poslední IP z profilu, 2) sken /24 na 8808 (+5555), 3) identita podle `mac`.
 * Bez `ACCESS_LOCAL_NETWORK` nic neskenuje a vrátí [BodyState.permissionMissing] — žádný tichý timeout.
 */
class BodyFinder(private val ctx: Context, private val prefs: Prefs) {

    fun hasLocalNetworkPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** IPv4 Pixelu na Wi‑Fi (pro prefix skenu); null bez sítě. */
    fun ownIpv4(): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val lp: LinkProperties = cm.getLinkProperties(cm.activeNetwork ?: return null) ?: return null
        return lp.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }?.hostAddress
    }

    fun client(host: String) = HttpAgentClient(host, 8808, key = prefs.agentKey)

    /** Zjistí stav pro známý host (bez skenu). */
    suspend fun probe(host: String): BodyState = withContext(Dispatchers.IO) {
        val agentUp = Discovery.tcpOpen(host, 8808, 800)
        val adbUp = Discovery.tcpOpen(host, 5555, 800)
        val status = if (agentUp) runCatching { client(host).status() }.getOrNull() else null
        val installed = if (!agentUp && adbUp) runCatching { AdbOps.agentInstalled(host) }.getOrNull() else if (agentUp) true else null
        if (status?.mac != null) { prefs.bodyMac = status.mac; prefs.lastIp = host }
        BodyState(Discovery.decide(status != null, adbUp, installed), host, status, installed)
    }

    /** Celé hledání: poslední IP → sken podsítě (Agent 8808, pak 5555 podle MAC nejde, bere se první ADB). */
    suspend fun find(): BodyState = withContext(Dispatchers.IO) {
        if (!hasLocalNetworkPermission()) return@withContext BodyState(permissionMissing = true)
        prefs.lastIp?.let { ip ->
            val s = probe(ip)
            if (s.link != BodyLink.NIC_NEODPOVIDA) return@withContext s
        }
        val prefix = ownIpv4()?.let { Discovery.prefixOf(it) } ?: return@withContext BodyState()
        val found = Discovery.scan(prefix, 8808, prefs.bodyMac)
        found.firstOrNull()?.let { return@withContext probe(it.host) }
        // Agent neběží nikde: hledat ADB (5555) — tělo bez Agenta
        val adbHosts = (1..254).map { "$prefix$it" }.chunked(32).flatMap { chunk ->
            chunk.filter { Discovery.tcpOpen(it, 5555, 400) }
        }
        val h = adbHosts.firstOrNull { runCatching { AdbOps.thirdPartyPackages(it).any { p -> p.contains("openmemories") } }.getOrDefault(false) }
            ?: return@withContext BodyState()
        prefs.lastIp = h
        probe(h)
    }

    companion object {
        const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
