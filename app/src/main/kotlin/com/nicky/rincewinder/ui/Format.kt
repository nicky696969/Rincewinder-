package com.nicky.rincewinder.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** České formáty pro UI: velikosti, ETA, čas. */
object Format {
    fun bytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", b / (1024.0 * 1024 * 1024)).replace('.', ',')
        b >= 1L shl 20 -> "${b / (1 shl 20)} MB"
        b >= 1L shl 10 -> "${b / 1024} kB"
        else -> "$b B"
    }

    /** ETA z velikosti a rychlosti v MB/s; 0 = „~? min“. */
    fun eta(bytes: Long, mbps: Float): String {
        if (mbps <= 0f) return "~? min"
        val s = bytes / (mbps * 1024 * 1024)
        return when {
            s < 60 -> "~${s.toInt()} s"
            s < 3600 -> "~${(s / 60).toInt()} min"
            else -> "~${(s / 3600).toInt()} h ${((s % 3600) / 60).toInt()} min"
        }
    }

    fun mbps(v: Float) = String.format(Locale.ROOT, "%.1f MB/s", v).replace('.', ',')

    fun time(ms: Long): String = SimpleDateFormat("H:mm", Locale.ROOT).format(Date(ms))
    fun dateTime(ms: Long): String = SimpleDateFormat("d. M. H:mm", Locale.ROOT).format(Date(ms))

    /** `NCK00612.ARW` → `NCK0612` (popisek dlaždice, UX_WIREFRAMES §3). */
    fun shortName(name: String): String {
        val base = name.substringBeforeLast('.')
        return if (base.length > 7) base.take(3) + base.takeLast(4) else base
    }

    fun plural(n: Int, one: String, few: String, many: String) = "$n ${word(n, one, few, many)}"

    /** Jen tvar slova podle čísla (1 soubor · 2 soubory · 5 souborů). */
    fun word(n: Int, one: String, few: String, many: String) = when {
        n == 1 -> one
        n in 2..4 -> few
        else -> many
    }

    /** Popisek dlaždice: dnešní snímek jen čas, starší i datum (karta mívá snímky z více dnů). */
    fun tileTime(ms: Long): String {
        val d = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
        return if (d.format(Date(ms)) == d.format(Date())) time(ms) else dateTime(ms)
    }

    /** České názvy stavů místo názvů z kódu. */
    fun stateName(s: com.nicky.rincewinder.core.FileState): String = when (s) {
        com.nicky.rincewinder.core.FileState.NA_KARTE -> "na kartě"
        com.nicky.rincewinder.core.FileState.STAHUJE_SE -> "stahuje se"
        com.nicky.rincewinder.core.FileState.STAZENO -> "staženo, neověřeno"
        com.nicky.rincewinder.core.FileState.OVERENO -> "staženo a ověřeno"
        com.nicky.rincewinder.core.FileState.SMAZANO_Z_KARTY -> "smazáno z karty, kopie na Pixelu"
        com.nicky.rincewinder.core.FileState.CHYBA -> "chyba"
        com.nicky.rincewinder.core.FileState.STARE -> "starší snímek"
        com.nicky.rincewinder.core.FileState.LOKALNE_CHYBI -> "kopie na Pixelu chybí"
        com.nicky.rincewinder.core.FileState.NEZNAMY -> "jen na Pixelu"
    }

    /** Složka z cesty SAF (`…/document/primary%3A0000000000%2F2026-11-08#X.ARW` → `0000000000/2026-11-08`). */
    fun folderOf(localPath: String?): String? {
        val doc = localPath?.substringAfter("/document/", "")?.substringBefore('#')?.takeIf { it.isNotEmpty() } ?: return null
        return java.net.URLDecoder.decode(doc, "UTF-8").substringAfter(':')
    }

    /** Lidské názvy známých apek na těle. */
    fun appName(pkg: String): String = when (pkg) {
        "com.nicky.rincewinder.agent" -> "Rincewinder Agent"
        "com.nicky.focusproject" -> "TrapFocus"
        "com.github.ma1co.openmemories.tweak" -> "OpenMemories Tweak"
        "com.github.ma1co.openmemories.appstore" -> "OpenMemories App Store"
        "com.jonasjuffinger.timelapse" -> "Timelapse"
        "com.obsidium.focusbracket" -> "Focus Bracket"
        else -> pkg.substringAfterLast('.')
    }
}
