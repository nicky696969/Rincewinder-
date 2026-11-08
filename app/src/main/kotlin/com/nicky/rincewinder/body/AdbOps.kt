package com.nicky.rincewinder.body

import dadb.Dadb
import java.io.File

/** Operace přes ADB (fork dadb) — jen install / pm / am / dumpsys. RAW nikdy (CLAUDE.md, stack). */
object AdbOps {
    const val AGENT_PKG = "com.nicky.rincewinder.agent"
    const val AGENT_SERVICE = "$AGENT_PKG/.AgentService"
    const val AGENT_ACTIVITY = "$AGENT_PKG/.MainActivity"

    private fun open(host: String) = Dadb.create(host, 5555, connectTimeout = 3000, socketTimeout = 20000)

    /** `pm list packages -3` → názvy balíčků. */
    fun thirdPartyPackages(host: String): List<String> = open(host).use { d ->
        d.shell("pm list packages -3").allOutput.lineSequence()
            .map { it.trim().removePrefix("package:") }.filter { it.isNotEmpty() }.toList()
    }

    fun agentInstalled(host: String): Boolean = AGENT_PKG in thirdPartyPackages(host)

    /** versionName balíčku z `dumpsys package`, null = nenainstalován. */
    fun versionName(host: String, pkg: String): String? = open(host).use { d ->
        Regex("versionName=(\\S+)").find(d.shell("dumpsys package $pkg").allOutput)?.groupValues?.get(1)
    }

    /** Běží proces balíčku? (kontrola `ps` před `am start`, ať nevznikne druhá kamerová instance) */
    fun running(host: String, pkg: String): Boolean = open(host).use { d ->
        d.shell("ps").allOutput.lineSequence().any { it.trimEnd().endsWith(pkg) }
    }

    /** „Spustit z telefonu“: služba Agenta (ověřeno na těle 8. 11. 2026), Activity jen když neběží. */
    fun startAgent(host: String, withUi: Boolean = false): String = open(host).use { d ->
        val r = d.shell("am startservice -n $AGENT_SERVICE")
        if (withUi && !running(host, AGENT_PKG)) d.shell("am start -n $AGENT_ACTIVITY")
        r.allOutput.trim()
    }

    fun install(host: String, apk: File): Pair<Boolean, String> = open(host).use { d ->
        val res = d.install(apk, "-r")
        val txt = res.toString()
        ("Success" in txt || res.javaClass.simpleName == "Success") to txt
    }

    fun uninstall(host: String, pkg: String): String = open(host).use { d -> d.uninstall(pkg).toString() }

    fun battery(host: String): Map<String, String> = open(host).use { d ->
        d.shell("dumpsys battery").allOutput.lineSequence().mapNotNull { l ->
            val p = l.trim().split(":", limit = 2); if (p.size == 2) p[0].trim() to p[1].trim() else null
        }.toMap()
    }
}
