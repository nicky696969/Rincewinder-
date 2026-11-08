package com.nicky.rincewinder.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket

data class FoundBody(val host: String, val status: AgentStatus)

/** Pět stavů Domů z docs/UX_WIREFRAMES.md §2. */
enum class BodyLink {
    NIC_NEODPOVIDA,        // ani 8808 ani 5555
    AGENT_NEBEZI,          // 5555 ano, 8808 ne, balíček Agenta v těle je
    AGENT_CHYBI,           // 5555 ano, 8808 ne, balíček Agenta v těle není
    AGENT_OK_ADB_VYPNUTE,  // 8808 ano, 5555 ne: hlavní tok funguje, ADB potřebují jen Apky a Diagnostika
    VSE_OK,                // 8808 i 5555
}

object Discovery {

    /** Rozhodnutí o stavu podle portů; [agentInstalled] je null, když ho nejde zjistit (ADB neběží). */
    fun decide(agentUp: Boolean, adbUp: Boolean, agentInstalled: Boolean?): BodyLink = when {
        agentUp && adbUp -> BodyLink.VSE_OK
        agentUp -> BodyLink.AGENT_OK_ADB_VYPNUTE
        adbUp -> if (agentInstalled == false) BodyLink.AGENT_CHYBI else BodyLink.AGENT_NEBEZI
        else -> BodyLink.NIC_NEODPOVIDA
    }

    fun tcpOpen(host: String, port: Int, timeoutMs: Int = 600): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
    } catch (_: Exception) {
        false
    }

    /**
     * Sken /24 na portu Agenta. Tělo se pozná podle `mac` ve `/status` (ARP appka na Androidu 10+ nepřečte).
     * [prefix] je např. "192.168.1."; [mac] null = vrátí všechny Agenty.
     */
    suspend fun scan(
        prefix: String,
        port: Int = 8808,
        mac: String? = null,
        timeoutMs: Int = 500,
        parallel: Int = 48,
    ): List<FoundBody> = coroutineScope {
        val sem = Semaphore(parallel)
        (1..254).map { i ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val host = "$prefix$i"
                    if (!tcpOpen(host, port, timeoutMs)) return@withPermit null
                    try {
                        val st = HttpAgentClient(host, port, connectTimeoutMs = timeoutMs, readTimeoutMs = timeoutMs * 4).status()
                        if (mac != null && !mac.equals(st.mac, ignoreCase = true)) null else FoundBody(host, st)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    /** Z IPv4 adresy telefonu „192.168.1.101“ → „192.168.1.“ (sken /24). */
    fun prefixOf(ip: String): String? {
        val p = ip.split('.')
        return if (p.size == 4 && p.all { it.toIntOrNull() in 0..255 }) p.take(3).joinToString(".") + "." else null
    }
}

/** Údaje z QR kódu na displeji Agenta: `http://IP:8808/?m=<mac>&v=<verze>&k=<klíč>`. */
data class QrPairing(val host: String, val port: Int, val mac: String?, val versionCode: Int?, val key: String?) {
    companion object {
        fun parse(text: String): QrPairing? {
            val m = Regex("""^http://([0-9.]+):(\d+)/?(?:\?(.*))?$""").matchEntire(text.trim()) ?: return null
            val (host, port, q) = m.destructured
            val params = q.split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return QrPairing(host, port.toInt(), params["m"], params["v"]?.toIntOrNull(), params["k"])
        }
    }
}
