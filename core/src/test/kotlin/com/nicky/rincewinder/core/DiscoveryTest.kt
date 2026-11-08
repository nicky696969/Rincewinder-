package com.nicky.rincewinder.core

import com.nicky.rincewinder.testing.FakeAgent
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoveryTest {
    private val card = Files.createTempDirectory("card").toFile()
    private lateinit var agent: FakeAgent

    @BeforeTest fun up() { agent = FakeAgent(card) }
    @AfterTest fun down() { agent.close(); card.deleteRecursively() }

    @Test fun `sken najde Agenta a pozna ho podle mac`() = runBlocking {
        val found = Discovery.scan("127.0.0.", port = agent.port, timeoutMs = 300)
        assertTrue(found.any { it.host == "127.0.0.1" }, "nalezeno: $found")
        assertEquals("000000000000", found.first { it.host == "127.0.0.1" }.status.mac)
        assertTrue(Discovery.scan("127.0.0.", agent.port, mac = "000000000000", timeoutMs = 300).isNotEmpty())
        assertTrue(Discovery.scan("127.0.0.", agent.port, mac = "aabbccddeeff", timeoutMs = 300).isEmpty(), "cizí MAC se nevrátí")
    }

    @Test fun `pet stavu Domu podle portu`() {
        assertEquals(BodyLink.NIC_NEODPOVIDA, Discovery.decide(false, false, null))
        assertEquals(BodyLink.AGENT_NEBEZI, Discovery.decide(false, true, true))
        assertEquals(BodyLink.AGENT_NEBEZI, Discovery.decide(false, true, null))
        assertEquals(BodyLink.AGENT_CHYBI, Discovery.decide(false, true, false))
        assertEquals(BodyLink.AGENT_OK_ADB_VYPNUTE, Discovery.decide(true, false, null))
        assertEquals(BodyLink.VSE_OK, Discovery.decide(true, true, true))
    }

    @Test fun `QR z displeje tela se precte vcetne klice`() {
        val q = QrPairing.parse("http://192.168.1.103:8808/?m=a0c9a0ce95ad&v=3&k=1a2b3c4d")!!
        assertEquals("192.168.1.103", q.host); assertEquals(8808, q.port)
        assertEquals("a0c9a0ce95ad", q.mac); assertEquals(3, q.versionCode); assertEquals("1a2b3c4d", q.key)
        val old = QrPairing.parse("http://192.168.1.103:8808/?m=a0c9a0ce95ad&v=0.1.1-probe")!!
        assertNull(old.versionCode); assertNull(old.key)
        assertNotNull(QrPairing.parse("  http://10.0.0.5:8808/  "))
        assertNull(QrPairing.parse("https://evil.example/?m=1"))
        assertNull(QrPairing.parse("neco jineho"))
    }

    @Test fun `predpona podsite`() {
        assertEquals("192.168.1.", Discovery.prefixOf("192.168.1.101"))
        assertNull(Discovery.prefixOf("fe80::1"))
    }
}
