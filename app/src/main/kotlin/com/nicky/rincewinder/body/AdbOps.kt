package com.nicky.rincewinder.body

import com.nicky.rincewinder.core.BodyApp
import com.nicky.rincewinder.core.PmParse
import dadb.Dadb
import dadb.InstallResult
import dadb.SyncResult
import dadb.UninstallResult
import java.io.File

/** Operace přes ADB (fork dadb) — jen install / pm / am / dumpsys. RAW nikdy (CLAUDE.md, stack). */
object AdbOps {
    const val AGENT_PKG = "com.nicky.rincewinder.agent"
    const val AGENT_SERVICE = "$AGENT_PKG/.AgentService"
    const val AGENT_ACTIVITY = "$AGENT_PKG/.MainActivity"
    const val TWEAK_PKG = "com.github.ma1co.openmemories.tweak"
    /** Aplikace bez kamery: jejich běh nebrání spuštění jiné aplikace. */
    private val NON_CAMERA = setOf(AGENT_PKG, TWEAK_PKG, "com.github.ma1co.openmemories.appstore")

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

    /** Instalace APK (`push` + `pm install -r`). Vrací (úspěch, česká hláška při chybě). */
    fun install(host: String, apk: File): Pair<Boolean, String> = open(host).use { d ->
        when (val res = d.install(apk, "-r")) {
            is InstallResult.Success -> true to "Success"
            is InstallResult.Failure -> false to PmParse.installError(res.reason)
        }
    }

    fun uninstall(host: String, pkg: String): Pair<Boolean, String> = open(host).use { d ->
        when (val res = d.uninstall(pkg)) {
            is UninstallResult.Success -> true to "Success"
            is UninstallResult.Failure -> false to res.reason.trim()
        }
    }

    /** Seznam aplikací 3. stran s údaji z `dumpsys package` a příznakem „běží“ — jedno spojení. */
    fun apps(host: String): List<Pair<BodyApp, Boolean>> = open(host).use { d ->
        val pkgs = d.shell("pm list packages -3").allOutput.lineSequence()
            .map { it.trim().removePrefix("package:") }.filter { it.isNotEmpty() }.toList()
        val running = PmParse.running(d.shell("ps").allOutput, pkgs)
        pkgs.map { p -> (PmParse.appInfo(p, d.shell("dumpsys package $p").allOutput) ?: BodyApp(p)) to (p in running) }
    }

    /** Velikost APK v těle (`ls -l` na codePath); null = nejde zjistit. */
    fun apkSize(host: String, codePath: String): Long? = open(host).use { d ->
        PmParse.lsSize(d.shell("ls -l $codePath").allOutput)
    }

    /**
     * Spuštění aplikace v těle. Druhá kamerová aplikace rozbije Sony cameraex (CLAUDE.md), proto se
     * nespustí, když už běží jiná aplikace 3. strany (mimo Agent, Tweak a App Store). Vrací hlášku pro UI.
     */
    fun launch(host: String, pkg: String): String = open(host).use { d ->
        val pkgs = d.shell("pm list packages -3").allOutput.lineSequence()
            .map { it.trim().removePrefix("package:") }.filter { it.isNotEmpty() }.toList()
        val running = PmParse.running(d.shell("ps").allOutput, pkgs)
        if (pkg in running) return@use "Už běží"
        val blockers = running - NON_CAMERA - pkg
        if (blockers.isNotEmpty()) return@use "Nejdřív ukonči: ${blockers.joinToString()}"
        // nejdřív krátký výpis jen pro balíček; celý `dumpsys package` (stovky kB po 4KB paketech) až jako záloha
        val comp = PmParse.launcher(pkg, d.shell("dumpsys package $pkg").allOutput)
            ?: PmParse.launcher(pkg, d.shell("dumpsys package").allOutput)
        val out = if (comp != null) d.shell("am start -n $comp").allOutput
        else d.shell("monkey -p $pkg -c android.intent.category.LAUNCHER 1").allOutput
        if (PmParse.launched(out) && !out.contains("Error")) "Spuštěno v těle"
        else "Spuštění selhalo: ${out.trim().lineSequence().lastOrNull { it.isNotBlank() } ?: "bez odpovědi"}"
    }

    fun forceStop(host: String, pkg: String): String = open(host).use { d -> d.shell("am force-stop $pkg").allOutput.trim() }

    /** `pm clear` — smaže data a nastavení aplikace v těle. */
    fun clearData(host: String, pkg: String): Boolean = open(host).use { d -> d.shell("pm clear $pkg").allOutput.contains("Success") }

    /** Záloha nainstalovaného APK z těla (`sync:` pull z codePath). Vrací false, když ho shell nepřečte. */
    fun pullApk(host: String, codePath: String, dst: File): Boolean = open(host).use { d ->
        d.pull(dst, codePath) is SyncResult.Success && dst.length() > 0
    }

    fun battery(host: String): Map<String, String> = open(host).use { d ->
        d.shell("dumpsys battery").allOutput.lineSequence().mapNotNull { l ->
            val p = l.trim().split(":", limit = 2); if (p.size == 2) p[0].trim() to p[1].trim() else null
        }.toMap()
    }
}
