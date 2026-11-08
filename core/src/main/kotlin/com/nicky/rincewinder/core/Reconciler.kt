package com.nicky.rincewinder.core

/** Volba při prvním spárování (zamčeno Nickym: výchozí [OD_DNESKA]). */
enum class FirstPairing { OD_DNESKA, VSE, NIC }

data class ReconcileResult(val added: Int, val markedOld: Int, val gone: Int, val total: Int)

/**
 * Sladí výpis karty (`/ls`) se záznamy. Neznámý klíč → NA_KARTE (při prvním spárování podle [FirstPairing]).
 * Soubor, který z karty zmizel: s lokální kopií → NEZNAMY, bez ní se záznam zahodí. SMAZANO_Z_KARTY zůstává.
 */
class Reconciler(private val store: FileStore) {

    fun reconcile(
        entries: List<LsEntry>,
        nowMs: Long,
        firstPairing: FirstPairing = FirstPairing.OD_DNESKA,
        todayStartMs: Long = 0,
    ): ReconcileResult {
        val first = store.isEmpty()
        val onCard = entries.filter { it.isArw }.associateBy { it.toKey() }
        var added = 0
        var old = 0
        for ((key, entry) in onCard) {
            val cur = store.get(key)
            if (cur != null) {
                store.upsert(cur.copy(lastSeenOnCard = nowMs, cardDir = entry.dir))
                continue
            }
            val state = if (first) when (firstPairing) {
                FirstPairing.VSE -> FileState.NA_KARTE
                FirstPairing.NIC -> FileState.STARE
                FirstPairing.OD_DNESKA -> if (key.mtime >= todayStartMs) FileState.NA_KARTE else FileState.STARE
            } else FileState.NA_KARTE
            if (state == FileState.STARE) old++ else added++
            store.upsert(FileRecord(key, state, lastSeenOnCard = nowMs, firstSeen = nowMs, cardDir = entry.dir))
        }
        var gone = 0
        for (rec in store.all()) {
            if (rec.key in onCard || rec.state == FileState.SMAZANO_Z_KARTY || rec.state == FileState.NEZNAMY) continue
            gone++
            val hasLocal = rec.state == FileState.STAZENO || rec.state == FileState.OVERENO || rec.state == FileState.LOKALNE_CHYBI
            if (hasLocal) store.upsert(rec.copy(state = FileState.NEZNAMY)) else store.remove(rec.key)
        }
        return ReconcileResult(added, old, gone, store.all().size)
    }
}

/** Kontrola lokální kopie těsně před dialogem mazání (UX_NAVRH, pravidla). */
class LocalCheck(private val store: FileStore, private val local: LocalFiles) {
    /** Vrátí záznamy, které jde opravdu smazat z karty; ostatní vybrané převede na LOKALNE_CHYBI nebo vynechá. */
    fun deletable(selected: Collection<FileRecord>): Pair<List<FileRecord>, List<Pair<FileRecord, String>>> {
        val ok = ArrayList<FileRecord>()
        val skipped = ArrayList<Pair<FileRecord, String>>()
        for (r in selected) {
            val fresh = store.get(r.key) ?: r
            if (!fresh.isDeletable) { skipped += fresh to "není ověřeno (${fresh.state})"; continue }
            val path = fresh.localPath
            val size = path?.let { local.size(it) }
            if (path == null || size == null) {
                store.upsert(fresh.copy(state = FileState.LOKALNE_CHYBI, errorReason = "lokální kopie chybí"))
                skipped += fresh to "lokální kopie chybí"
            } else if (size != fresh.key.size) {
                store.upsert(fresh.copy(state = FileState.LOKALNE_CHYBI, errorReason = "lokální kopie má jinou velikost"))
                skipped += fresh to "jiná velikost lokální kopie"
            } else ok += fresh
        }
        return ok to skipped
    }
}
