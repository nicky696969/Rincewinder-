package com.nicky.rincewinder.core

/**
 * Orientace snímku z IFD0 hlavičky ARW (TIFF tag 0x0112). Vložený JPEG z ARW otočení nenese,
 * takže snímky na výšku by v mřížce ležely na boku (doloženo na těle 8. 11. 2026: NCK01548 = 8,
 * NCK01570 = 6, snímky na šířku = 1; IFD0 je na offsetu 8, 18 položek, stačí prvních 4 KB).
 */
object ArwOrientation {
    /** Kolik bajtů ze začátku souboru stačí na IFD0. */
    const val HEADER_BYTES = 4096

    /** Hodnota tagu Orientation (1, 3, 6, 8 …), null = není TIFF nebo tag chybí. */
    fun parse(b: ByteArray): Int? {
        if (b.size < 8) return null
        val le = b[0] == 'I'.code.toByte() && b[1] == 'I'.code.toByte()
        val be = b[0] == 'M'.code.toByte() && b[1] == 'M'.code.toByte()
        if (!le && !be) return null
        fun u8(o: Int) = b[o].toInt() and 0xff
        fun u16(o: Int) = if (le) u8(o) or (u8(o + 1) shl 8) else (u8(o) shl 8) or u8(o + 1)
        fun u32(o: Int): Long = if (le) u16(o).toLong() or (u16(o + 2).toLong() shl 16) else (u16(o).toLong() shl 16) or u16(o + 2).toLong()
        if (u16(2) != 42) return null
        val ifd = u32(4)
        if (ifd < 8 || ifd + 2 > b.size) return null
        val n = u16(ifd.toInt())
        for (i in 0 until n) {
            val e = ifd.toInt() + 2 + i * 12
            if (e + 12 > b.size) break
            if (u16(e) == 0x0112) return u16(e + 8)
        }
        return null
    }

    /** O kolik stupňů ve směru hodinových ručiček otočit náhled, aby stál správně. */
    fun degrees(orientation: Int?): Int = when (orientation) {
        3 -> 180
        6 -> 90
        8 -> 270
        else -> 0
    }
}
