package com.nicky.rincewinder.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import com.nicky.rincewinder.core.AgentClient
import com.nicky.rincewinder.core.ArwOrientation
import com.nicky.rincewinder.core.FileKey
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Náhledy z vloženého JPEG v ARW (`GET /thumb?min=400`): paměťová LRU + disková cache v cacheDir
 * podle klíče `název+velikost`, takže se k tělu chodí jen jednou.
 *
 * K tělu jdou nejvýš [PARALLEL] dotazy najednou a při chybě se opakují: karta (FUSE FuFsys) vrací
 * `open failed: EBUSY`, když má otevřeno moc souborů naráz (doloženo v RINCEAGT.LOG 8. 11. 2026,
 * mřížka pálila ~15 dotazů paralelně během stahování).
 */
class Thumbs(ctx: Context) {
    private val dir = File(ctx.cacheDir, "thumbs").apply { mkdirs() }
    private val mem = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val gate = Semaphore(PARALLEL)

    private fun id(key: FileKey) = "${key.name}_${key.size}"

    fun cached(key: FileKey): Bitmap? = mem.get(id(key))

    private suspend fun fetch(cardPath: String, client: () -> AgentClient?): ByteArray? {
        for (attempt in 0 until ATTEMPTS) {
            val c = client() ?: return null
            val b = gate.withPermit { runCatching { c.thumb(cardPath, 400) }.getOrNull() }
            if (b != null && b.isNotEmpty()) return b
            delay(BACKOFF_MS[attempt])
        }
        return null
    }

    suspend fun load(key: FileKey, cardPath: String, client: () -> AgentClient?): Bitmap? = withContext(Dispatchers.IO) {
        val id = id(key)
        mem.get(id)?.let { return@withContext it }
        val f = File(dir, "$id.jpg")
        val bytes = if (f.isFile) f.readBytes() else {
            val b = fetch(cardPath, client) ?: return@withContext null
            runCatching { f.writeBytes(b) }
            b
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val sample = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / 480)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        val upright = rotate(bmp, degrees(key, cardPath, client))
        mem.put(id, upright)
        upright
    }

    /** Celý vložený JPEG pro velký náhled (největší z ARW), otočený podle orientace. */
    suspend fun full(key: FileKey, cardPath: String, client: AgentClient): Bitmap? = withContext(Dispatchers.IO) {
        val b = gate.withPermit { runCatching { client.thumb(cardPath, 1600) }.getOrNull() } ?: return@withContext null
        val bmp = BitmapFactory.decodeByteArray(b, 0, b.size) ?: return@withContext null
        rotate(bmp, degrees(key, cardPath) { client })
    }

    /**
     * Otočení z hlavičky ARW (tag 0x0112, prvních 4 KB přes Range). Vložený JPEG orientaci nenese,
     * snímky na výšku by jinak ležely na boku. Výsledek se pamatuje na disku vedle náhledu.
     */
    private suspend fun degrees(key: FileKey, cardPath: String, client: () -> AgentClient?): Int {
        val id = id(key)
        orient[id]?.let { return it }
        val f = File(dir, "$id.o")
        if (f.isFile) f.readText().trim().toIntOrNull()?.let { orient[id] = it; return it }
        val c = client() ?: return 0
        val head = gate.withPermit { runCatching { c.readRange(cardPath, 0, ArwOrientation.HEADER_BYTES) }.getOrNull() } ?: return 0
        val deg = ArwOrientation.degrees(ArwOrientation.parse(head))
        runCatching { f.writeText(deg.toString()) }
        orient[id] = deg
        return deg
    }

    private fun rotate(bmp: Bitmap, deg: Int): Bitmap =
        if (deg == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg.toFloat()) }, true)

    private val orient = ConcurrentHashMap<String, Int>()

    companion object {
        const val PARALLEL = 2
        const val ATTEMPTS = 3
        val BACKOFF_MS = longArrayOf(400, 1200, 0)
    }
}
