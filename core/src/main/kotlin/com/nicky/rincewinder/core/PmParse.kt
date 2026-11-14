package com.nicky.rincewinder.core

/** Aplikace v těle, jak ji vidí `dumpsys package` (Android 4.1.2). Co výpis neobsahuje, je null. */
data class BodyApp(
    val pkg: String,
    val versionName: String? = null,
    val versionCode: Int? = null,
    val codePath: String? = null,
    val targetSdk: Int? = null,
    val firstInstall: String? = null,
    val lastUpdate: String? = null,
)

/** Čisté parsery výstupů `pm` / `dumpsys` / `ls` z adb shellu těla. Bez Androidu, kvůli testům na PC. */
object PmParse {
    private fun field(out: String, name: String): String? =
        Regex("(?m)^\\s*$name=(.+?)\\s*$").find(out)?.groupValues?.get(1)

    /** Číselné pole kdekoli na řádku (novější Android tiskne `versionCode=15 targetSdk=10` vedle sebe). */
    private fun number(out: String, name: String): Int? =
        Regex("(?:^|\\s)$name=(\\d+)", RegexOption.MULTILINE).find(out)?.groupValues?.get(1)?.toIntOrNull()

    /** `dumpsys package <pkg>`; null = balíček v těle není. */
    fun appInfo(pkg: String, dumpsys: String): BodyApp? {
        if (!dumpsys.contains("Package [$pkg]")) return null
        return BodyApp(
            pkg = pkg,
            versionName = field(dumpsys, "versionName"),
            versionCode = number(dumpsys, "versionCode"),
            codePath = field(dumpsys, "codePath"),
            targetSdk = number(dumpsys, "targetSdk"),
            firstInstall = field(dumpsys, "firstInstallTime"),
            lastUpdate = field(dumpsys, "lastUpdateTime"),
        )
    }

    /**
     * Spouštěcí Activity balíčku z celého `dumpsys package` (tabulka „Activity Resolver Table“,
     * blok `android.intent.action.MAIN:`). Vrací `pkg/.Activity`, null = nenalezeno.
     */
    fun launcher(pkg: String, dumpsysAll: String): String? {
        var inMain = false
        val comp = Regex("\\s(" + Regex.escape(pkg) + "/\\S+)")
        for (line in dumpsysAll.lineSequence()) {
            val t = line.trim()
            if (t == "android.intent.action.MAIN:") { inMain = true; continue }
            if (inMain && t.endsWith(":") && !t.contains(' ')) { inMain = false; continue }
            if (inMain) comp.find(line)?.let { return it.groupValues[1] }
        }
        return null
    }

    /** Běžící balíčky z výstupu `ps` (poslední sloupec = název procesu). */
    fun running(ps: String, pkgs: Collection<String>): Set<String> {
        val names = ps.lineSequence().map { it.trimEnd().substringAfterLast(' ') }.toSet()
        return pkgs.filter { p -> names.any { it == p || it.startsWith("$p:") } }.toSet()
    }

    /** Velikost z `ls -l <soubor>` na toolboxu 4.1: `-rw-r--r-- system system 41234 2026-11-08 10:20 x.apk`. */
    fun lsSize(out: String): Long? =
        Regex("^\\S+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+\\d{4}-\\d{2}-\\d{2}").find(out.trim())?.groupValues?.get(1)?.toLongOrNull()

    /** Srozumitelná česká příčina z výstupu `pm install`; neznámý kód se vrací tak, jak přišel. */
    fun installError(out: String): String {
        val code = Regex("(INSTALL_[A-Z_]+)").find(out)?.groupValues?.get(1)
        return when (code) {
            "INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES", "INSTALL_FAILED_UPDATE_INCOMPATIBLE" ->
                "Jiný podpis než verze v těle. Nejdřív ji odinstaluj (přijdeš o její nastavení)."
            "INSTALL_FAILED_OLDER_SDK" -> "Aplikace chce novější Android, než má tělo (4.1.2, API 16)."
            "INSTALL_FAILED_INSUFFICIENT_STORAGE" -> "V těle není dost místa pro instalaci."
            "INSTALL_FAILED_ALREADY_EXISTS" -> "Aplikace v těle už je."
            "INSTALL_FAILED_INVALID_APK", "INSTALL_PARSE_FAILED_NOT_APK", "INSTALL_PARSE_FAILED_BAD_MANIFEST",
            "INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION" -> "Soubor není platné APK."
            "INSTALL_PARSE_FAILED_NO_CERTIFICATES" -> "APK není podepsané."
            "INSTALL_FAILED_DEXOPT" -> "Tělo aplikaci nezpracovalo (dexopt). Zkus to znovu po restartu těla."
            "INSTALL_FAILED_MISSING_SHARED_LIBRARY" -> "Aplikace potřebuje knihovnu, kterou tělo nemá."
            null -> out.trim().lineSequence().lastOrNull { it.isNotBlank() } ?: "neznámá chyba"
            else -> code
        }
    }

    /** První odkaz https ve sdíleném textu, bez koncové interpunkce; null = žádný. */
    fun firstHttpsUrl(text: String): String? =
        Regex("https://[^\\s)>\\]\"',]+").find(text)?.value?.trimEnd('.', ';', ':', '!', '?')

    /** Úspěch `am start` / `monkey` se pozná jen podle kladného výstupu (chybějící příkaz „Error“ netiskne). */
    fun launched(out: String): Boolean = out.contains("Starting:") || out.contains("Events injected")

    /** `owner/repo` z adresy GitHubu nebo z přímého zápisu; null = nerozpoznáno. */
    fun githubRepo(input: String): String? {
        val s = input.trim().removeSuffix("/").removeSuffix(".git")
        val m = Regex("^(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:/.*)?$").find(s)
            ?: Regex("^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$").find(s) ?: return null
        return m.groupValues[1] + "/" + m.groupValues[2].removeSuffix(".git")
    }

    /** Bezpečný název souboru pro dočasnou cestu v těle (`/data/local/tmp/…`), vždy s příponou `.apk`. */
    fun safeApkName(name: String): String {
        val base = name.substringAfterLast('/').substringBefore('?').replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_')
        val n = base.ifEmpty { "app" }
        return if (n.endsWith(".apk", ignoreCase = true)) n else "$n.apk"
    }
}
