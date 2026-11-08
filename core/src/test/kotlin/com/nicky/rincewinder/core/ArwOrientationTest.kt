package com.nicky.rincewinder.core

import com.nicky.rincewinder.testing.FakeAgent
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArwOrientationTest {

    /** Minimální TIFF: hlavička + IFD0 se dvěma položkami (Make, Orientation), jako IFD0 v ARW. */
    private fun tiff(order: ByteOrder, orientation: Int): ByteArray {
        val bb = ByteBuffer.allocate(64).order(order)
        bb.put(if (order == ByteOrder.LITTLE_ENDIAN) "II".toByteArray() else "MM".toByteArray())
        bb.putShort(42); bb.putInt(8)
        bb.putShort(2)
        bb.putShort(0x010F); bb.putShort(2); bb.putInt(5); bb.putInt(40)      // Make, ASCII
        bb.putShort(0x0112); bb.putShort(3); bb.putInt(1); bb.putShort(orientation.toShort()); bb.putShort(0)
        bb.putInt(0)
        return bb.array()
    }

    @Test fun `little endian jako ARW z A7 II - orientace 8`() {
        assertEquals(8, ArwOrientation.parse(tiff(ByteOrder.LITTLE_ENDIAN, 8)))
        assertEquals(270, ArwOrientation.degrees(8))
    }

    @Test fun `big endian a ostatni hodnoty`() {
        assertEquals(6, ArwOrientation.parse(tiff(ByteOrder.BIG_ENDIAN, 6)))
        assertEquals(90, ArwOrientation.degrees(6))
        assertEquals(0, ArwOrientation.degrees(1))
        assertEquals(0, ArwOrientation.degrees(null))
    }

    @Test fun `smeti a kratka data nejsou orientace`() {
        assertNull(ArwOrientation.parse(ByteArray(3)))
        assertNull(ArwOrientation.parse("JFIF....".toByteArray()))
        assertNull(ArwOrientation.parse(tiff(ByteOrder.LITTLE_ENDIAN, 6).copyOf(20)))
    }

    @Test fun `readRange pres Agenta vrati jen zacatek souboru`() {
        val card = Files.createTempDirectory("card").toFile()
        try {
            File(card, "DCIM/10361107").mkdirs()
            val data = tiff(ByteOrder.LITTLE_ENDIAN, 8) + ByteArray(10_000) { it.toByte() }
            File(card, "DCIM/10361107/V.ARW").writeBytes(data)
            FakeAgent(card).use { agent ->
                val head = HttpAgentClient("127.0.0.1", agent.port).readRange("DCIM/10361107/V.ARW", 0, ArwOrientation.HEADER_BYTES)
                assertEquals(ArwOrientation.HEADER_BYTES, head.size)
                assertContentEquals(data.copyOf(ArwOrientation.HEADER_BYTES), head)
                assertEquals(8, ArwOrientation.parse(head))
            }
        } finally {
            card.deleteRecursively()
        }
    }
}
