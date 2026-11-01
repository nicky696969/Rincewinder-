package com.nicky.rincewinder.core

import com.nicky.rincewinder.testing.FakeAgent
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteFlowTest {
    private lateinit var card: File
    private lateinit var pixel: File
    private lateinit var agent: FakeAgent
    private val store = InMemoryFileStore()
    private lateinit var local: JvmLocalFiles
    private val dir = "DCIM/100MSDCF"

    @BeforeTest fun up() {
        card = Files.createTempDirectory("card").toFile(); pixel = Files.createTempDirectory("pixel").toFile()
        File(card, dir).mkdirs()
        agent = FakeAgent(card)
        local = JvmLocalFiles(pixel)
    }

    @AfterTest fun down() { agent.close(); card.deleteRecursively(); pixel.deleteRecursively() }

    private fun client(key: String? = agent.secret) = HttpAgentClient("127.0.0.1", agent.port, key)
    private fun arw(name: String, seed: Int) = File(card, "$dir/$name").apply { writeBytes(Random(seed).nextBytes(30_000)) }

    private fun downloadAll() = runBlocking {
        Reconciler(store).reconcile(client().ls(dir), 1, FirstPairing.VSE)
        SyncEngine(client(), store, local, dir).run()
    }

    @Test fun `smaze jen overene s platnym tokenem, zmeneny soubor vrati 409`() {
        val a = arw("A.ARW", 1); val b = arw("B.ARW", 2); arw("C.ARW", 3)
        downloadAll()
        assertTrue(store.all().all { it.state == FileState.OVERENO })
        // B byl mezitím v těle přepsán (jiná velikost), C není vybráno
        b.writeBytes(Random(9).nextBytes(31_000))
        val selected = store.all().filter { it.key.name != "C.ARW" }
        val out = DeleteFlow(client(), store, local, dir).delete(selected)
        assertEquals(listOf("A.ARW"), out.deleted.map { it.name })
        assertEquals(listOf("B.ARW"), out.changedOnCard.map { it.name })
        assertFalse(a.exists(), "A smazán z karty")
        assertTrue(b.exists(), "změněný B zůstal")
        assertTrue(File(card, "$dir/C.ARW").exists())
        assertEquals(FileState.SMAZANO_Z_KARTY, store.all().single { it.key.name == "A.ARW" }.state)
        assertEquals(FileState.OVERENO, store.all().single { it.key.name == "B.ARW" }.state)
        assertTrue(agent.log.any { it.startsWith("RM A.ARW") })
    }

    @Test fun `bez klice nebo mimo DCIM se nemaze`() {
        arw("A.ARW", 1)
        downloadAll()
        val rec = store.all().single()
        val e = assertFailsWith<AgentHttpException> { DeleteFlow(client(key = null), store, local, dir).delete(listOf(rec)) }
        assertEquals(403, e.code)
        assertTrue(File(card, "$dir/A.ARW").exists())
        val e2 = assertFailsWith<AgentHttpException> { client().rmToken("RINCEAGT.LOG", 0, 0) }
        assertEquals(403, e2.code)
    }

    @Test fun `token je jednorazovy a vazany na soubor`() {
        val a = arw("A.ARW", 1); arw("B.ARW", 2)
        downloadAll()
        val c = client()
        val tok = c.rmToken("$dir/A.ARW", a.length(), a.lastModified())!!
        assertFalse(c.rm("$dir/B.ARW", tok), "token A nesmí smazat B")
        assertTrue(c.rm("$dir/A.ARW", tok))
        assertFalse(a.exists())
        assertEquals(404, assertFailsWith<AgentHttpException> { c.rm("$dir/A.ARW", tok) }.code)
    }

    @Test fun `md5 cache a status s hodinami`() {
        val a = arw("A.ARW", 5)
        val c = client()
        val m1 = c.md5("$dir/A.ARW"); val m2 = c.md5("$dir/A.ARW")
        assertEquals(m1, m2)
        assertEquals(1, agent.log.count { it.startsWith("md5 A.ARW") }, "druhé volání jde z cache")
        a.writeBytes(Random(6).nextBytes(30_001))
        assertTrue(c.md5("$dir/A.ARW") != m1, "po změně souboru se cache nepoužije")
        val st = c.status()
        assertEquals(true, st.raw["clockSynced"]); assertEquals(true, st.raw["secured"])
        assertTrue((st.raw["deviceTimeMs"] as Long) > 1_700_000_000_000L)
    }
}
