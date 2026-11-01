package com.nicky.rincewinder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReconcilerTest {
    private val day = 86_400_000L
    private val today = 100 * day
    private fun e(name: String, size: Long, mtime: Long) = LsEntry(name, size, mtime, false)

    private val card = listOf(
        e("OLD1.ARW", 100, today - 5 * day),
        e("OLD2.ARW", 100, today - day),
        e("NEW1.ARW", 100, today + 3600_000),
        e("MOVIE.MP4", 500, today),
        LsEntry("SUB", 0, today, true),
    )

    @Test fun `prvni sparovani od dneska - stare dostanou STARE`() {
        val s = InMemoryFileStore()
        val r = Reconciler(s).reconcile(card, nowMs = today, firstPairing = FirstPairing.OD_DNESKA, todayStartMs = today)
        assertEquals(ReconcileResult(added = 1, markedOld = 2, gone = 0, total = 3), r)
        assertEquals(1, s.all().count { it.isNew })
        assertEquals(FileState.NA_KARTE, s.all().single { it.key.name == "NEW1.ARW" }.state)
        assertNull(s.all().find { it.key.name == "MOVIE.MP4" }, "jen ARW")
    }

    @Test fun `prvni sparovani vse a nic`() {
        val a = InMemoryFileStore()
        Reconciler(a).reconcile(card, today, FirstPairing.VSE, today)
        assertEquals(3, a.all().count { it.isNew })
        val n = InMemoryFileStore()
        Reconciler(n).reconcile(card, today, FirstPairing.NIC, today)
        assertEquals(0, n.all().count { it.isNew })
        assertEquals(3, n.all().count { it.state == FileState.STARE })
    }

    @Test fun `dalsi vypis - nove soubory jsou NA_KARTE, zmizele se vyridi`() {
        val s = InMemoryFileStore()
        Reconciler(s).reconcile(card, today, FirstPairing.OD_DNESKA, today)
        // NEW1 stažen a ověřen, OLD1 smazán v těle, přibyl NEW2 (i se starým mtime: po restartu jsou hodiny těla na 1970)
        s.update(FileKey("NEW1.ARW", 100, today + 3600_000)) { it.copy(state = FileState.OVERENO, localPath = "x") }
        val next = listOf(e("OLD2.ARW", 100, today - day), e("NEW1.ARW", 100, today + 3600_000), e("NEW2.ARW", 100, 0))
        val r = Reconciler(s).reconcile(next, today + day, FirstPairing.OD_DNESKA, today)
        assertEquals(1, r.added); assertEquals(1, r.gone)
        assertEquals(FileState.NA_KARTE, s.all().single { it.key.name == "NEW2.ARW" }.state)
        assertNull(s.all().find { it.key.name == "OLD1.ARW" }, "bez lokální kopie se záznam zahodí")
        // ověřený soubor, který z karty zmizí, zůstane jako NEZNAMY
        val r2 = Reconciler(s).reconcile(next.filter { it.name != "NEW1.ARW" }, today + 2 * day, FirstPairing.OD_DNESKA, today)
        assertEquals(1, r2.gone)
        assertEquals(FileState.NEZNAMY, s.all().single { it.key.name == "NEW1.ARW" }.state)
        assertEquals(NewSummary(1, 100), s.all().newSummary())
    }
}
