package com.nicky.rincewinder.core

import java.io.IOException

data class DeleteOutcome(val deleted: List<FileKey>, val changedOnCard: List<FileKey>, val skipped: List<Pair<FileRecord, String>>)

/**
 * Mazání z karty (UX_NAVRH, pravidla; ZADANI_F1 §5.6). Volá se až po potvrzení podržením v UI.
 * Maže jen OVERENO s existující lokální kopií; pro každý soubor token → POST /rm; 409 = soubor se změnil, nemaže se.
 */
class DeleteFlow(
    private val client: AgentClient,
    private val store: FileStore,
    private val local: LocalFiles,
    private val cardDir: String = "DCIM/100MSDCF",
) {
    /** Co by se smazalo: pro dialog (počet, velikost, vynechané s důvodem). Nic nemaže. */
    fun preview(selected: Collection<FileRecord>) = LocalCheck(store, local).deletable(selected)

    @Throws(IOException::class)
    fun delete(selected: Collection<FileRecord>): DeleteOutcome {
        val (ok, skipped) = preview(selected)
        val deleted = ArrayList<FileKey>()
        val changed = ArrayList<FileKey>()
        for (rec in ok) {
            val path = "$cardDir/${rec.key.name}"
            val token = client.rmToken(path, rec.key.size, rec.key.mtime)
            if (token == null || !client.rm(path, token)) {
                changed += rec.key
                store.update(rec.key) { it.copy(errorReason = "soubor na kartě se změnil, nesmazán") }
                continue
            }
            store.update(rec.key) { it.copy(state = FileState.SMAZANO_Z_KARTY, errorReason = null) }
            deleted += rec.key
        }
        return DeleteOutcome(deleted, changed, skipped)
    }
}
