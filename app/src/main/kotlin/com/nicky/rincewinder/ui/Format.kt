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
            else -> String.format(Locale.ROOT, "~%.1f h", s / 3600).replace('.', ',')
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

    fun plural(n: Int, one: String, few: String, many: String) = when {
        n == 1 -> "$n $one"
        n in 2..4 -> "$n $few"
        else -> "$n $many"
    }
}
