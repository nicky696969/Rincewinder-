package com.nicky.rincewinder.core

/** Stavy souboru podle docs/UX_NAVRH.md („Datový model stavů souboru“). Názvy jsou úmyslně české, 1:1 s dokumentem. */
enum class FileState {
    NA_KARTE,
    STAHUJE_SE,
    STAZENO,
    OVERENO,
    SMAZANO_Z_KARTY,
    CHYBA,
    STARE,
    LOKALNE_CHYBI,
    NEZNAMY,
}

/** Klíč souboru na kartě. Mezi cestami (Wi‑Fi × OTG) se párují jen [name] + [size]; [mtime] je informativní. */
data class FileKey(val name: String, val size: Long, val mtime: Long) {
    /** Klíč pro párování mezi cestami. */
    val crossPathKey: String get() = "$name:$size"
}

data class FileRecord(
    val key: FileKey,
    val state: FileState,
    val localPath: String? = null,
    val md5Agent: String? = null,
    val md5Local: String? = null,
    val bytesDone: Long = 0,
    val errorReason: String? = null,
    val lastSeenOnCard: Long = 0,
    val firstSeen: Long = 0,
) {
    /** Jedna definice „nové“ pro tlačítko, počet na Domů i filtr (UX_NAVRH, pravidla). */
    val isNew: Boolean
        get() = state == FileState.NA_KARTE || state == FileState.CHYBA ||
            state == FileState.STAHUJE_SE || state == FileState.LOKALNE_CHYBI

    /** Filtr *Stažené*. */
    val isDownloaded: Boolean
        get() = state == FileState.STAZENO || state == FileState.OVERENO || state == FileState.SMAZANO_Z_KARTY

    /** Mazat z karty jde jen ověřené; existenci lokální kopie znovu ověří [LocalCheck] těsně před dialogem. */
    val isDeletable: Boolean get() = state == FileState.OVERENO
}

/** Souhrn pro Domů: „23 nových ARW · 1,1 GB“. */
data class NewSummary(val count: Int, val bytes: Long)

fun Collection<FileRecord>.newSummary(): NewSummary {
    val new = filter { it.isNew }
    return NewSummary(new.size, new.sumOf { it.key.size - it.bytesDone })
}

/** Prahy z UX_WIREFRAMES §10: nad 50 souborů nebo 2 GB Domů nabídne kabel místo Wi‑Fi. */
object Thresholds {
    const val TOO_MANY_FILES = 50
    const val TOO_MANY_BYTES = 2L * 1024 * 1024 * 1024
    fun tooMuchForWifi(s: NewSummary) = s.count > TOO_MANY_FILES || s.bytes > TOO_MANY_BYTES
}
