package com.nicky.rincewinder.core

import com.nicky.rincewinder.testing.FakeAgent
import com.nicky.rincewinder.testing.FlakyProxy
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncEngineTest {
    private lateinit var card: File
    private lateinit var pixel: File
    private lateinit var agent: FakeAgent
    private lateinit var proxy: FlakyProxy
    private lateinit var store: InMemoryFileStore
    private lateinit var local: JvmLocalFiles
    private val events = ArrayList<SyncEvent>()
    private val dir = "DCIM/100MSDCF"

    @BeforeTest fun up() {
        card = Files.createTempDirectory("card").toFile()
        pixel = Files.createTempDirectory("pixel").toFile()
        File(card, dir).mkdirs()
        agent = FakeAgent(card)
        proxy = FlakyProxy(agent.port)
        store = InMemoryFileStore()
        local = JvmLocalFiles(pixel)
    }

    @AfterTest fun down() { proxy.close(); agent.close(); card.deleteRecursively(); pixel.deleteRecursively() }

    private fun arw(name: String, size: Int, seed: Int): File {
        val f = File(card, "$dir/$name")
        f.writeBytes(Random(seed).nextBytes(size))
        return f
    }

    private fun client() = HttpAgentClient("127.0.0.1", proxy.port)
    private fun engine(c: AgentClient = client(), parallel: Int = 2) = SyncEngine(c, store, local, dir, parallel, onEvent = { synchronized(events) { events += it } })
    private fun reconcile() = Reconciler(store).reconcile(client().ls(dir), nowMs = 1_000, firstPairing = FirstPairing.VSE)

    @Test fun `stazeni a overeni tri souboru paralelne`() = runBlocking {
        val files = listOf(arw("A.ARW", 300_000, 1), arw("B.ARW", 200_000, 2), arw("C.ARW", 150_000, 3))
        reconcile()
        assertEquals(3, store.all().count { it.isNew })
        val fin = engine().run()
        assertEquals(3, fin.verified); assertEquals(0, fin.failed); assertFalse(fin.paused)
        for (f in files) {
            val rec = store.all().single { it.key.name == f.name }
            assertEquals(FileState.OVERENO, rec.state)
            assertEquals(local.md5(rec.localPath!!), rec.md5Agent)
            assertTrue(File(rec.localPath!!).readBytes().contentEquals(f.readBytes()))
        }
        assertEquals(0, store.all().count { it.isNew })
        assertTrue(pixel.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test fun `vypadek uprostred souboru - pauza, bytesDone, pokracovani od offsetu`() = runBlocking {
        val f = arw("BIG.ARW", 1_000_000, 7)
        reconcile()
        proxy.cutAfter.set(400_000)
        val first = engine().run()
        assertTrue(first.paused); assertEquals(0, first.verified)
        assertEquals(1, proxy.cuts)
        val rec = store.get(FileKey(f.name, f.length(), f.lastModified()))!!
        assertEquals(FileState.STAHUJE_SE, rec.state)
        assertTrue(rec.bytesDone in 1 until f.length(), "bytesDone=${rec.bytesDone}")
        assertTrue(rec.isNew)
        assertTrue(events.any { it is SyncEvent.Paused })
        // tělo „se probudilo“: druhé spuštění pokračuje od offsetu, ne od nuly
        events.clear()
        val second = engine().run()
        assertEquals(1, second.verified); assertFalse(second.paused)
        val started = events.filterIsInstance<SyncEvent.Started>().single()
        assertEquals(rec.bytesDone, started.from)
        val done = store.get(rec.key)!!
        assertEquals(FileState.OVERENO, done.state)
        assertTrue(File(done.localPath!!).readBytes().contentEquals(f.readBytes()))
    }

    @Test fun `telo nedostupne - pauza bez CHYBA`() = runBlocking {
        arw("X.ARW", 10_000, 9)
        reconcile()
        val dead = HttpAgentClient("127.0.0.1", 1, connectTimeoutMs = 300)
        val fin = engine(dead).run()
        assertTrue(fin.paused); assertEquals(0, fin.failed)
        assertEquals(FileState.STAHUJE_SE, store.all().single().state)
        assertTrue(store.all().single().isNew)
    }

    @Test fun `MD5 neshoda - jedno opakovani, pak CHYBA`() = runBlocking {
        arw("M.ARW", 50_000, 11)
        reconcile()
        val real = client()
        var calls = 0
        val lying = object : AgentClient by real {
            override fun md5(path: String): String { calls++; return "0".repeat(32) }
        }
        val fin = engine(lying).run()
        assertEquals(1, fin.failed); assertEquals(0, fin.verified)
        assertEquals(2, calls, "opakuje se právě jednou")
        val rec = store.all().single()
        assertEquals(FileState.CHYBA, rec.state)
        assertNotNull(rec.errorReason)
        assertNull(local.size(local.targetPath("M.ARW")), "po neshodě se lokální kopie smaže")
        assertTrue(rec.isNew, "CHYBA patří do „nových“, příští Stáhnout nové ji vezme")
    }

    @Test fun `mazani jen overenych a kontrola lokalni kopie`() = runBlocking {
        arw("D1.ARW", 20_000, 21); arw("D2.ARW", 20_000, 22)
        reconcile()
        engine().run()
        val all = store.all()
        assertTrue(all.all { it.state == FileState.OVERENO })
        // uživatel smazal lokální kopii D2 v Souborech
        File(all.single { it.key.name == "D2.ARW" }.localPath!!).delete()
        val (ok, skipped) = LocalCheck(store, local).deletable(all)
        assertEquals(listOf("D1.ARW"), ok.map { it.key.name })
        assertEquals(1, skipped.size)
        assertEquals(FileState.LOKALNE_CHYBI, store.all().single { it.key.name == "D2.ARW" }.state)
        assertTrue(store.all().single { it.key.name == "D2.ARW" }.isNew)
    }
}
