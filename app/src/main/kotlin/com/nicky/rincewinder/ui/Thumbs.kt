package com.nicky.rincewinder.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.nicky.rincewinder.core.AgentClient
import com.nicky.rincewinder.core.FileKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Náhledy z vloženého JPEG v ARW (`GET /thumb?min=400`): paměťová LRU + disková cache v cacheDir
 * podle klíče `název+velikost`, takže se k tělu chodí jen jednou.
 */
class Thumbs(ctx: Context) {
    private val dir = File(ctx.cacheDir, "thumbs").apply { mkdirs() }
    private val mem = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun id(key: FileKey) = "${key.name}_${key.size}"

    fun cached(key: FileKey): Bitmap? = mem.get(id(key))

    suspend fun load(key: FileKey, cardPath: String, client: () -> AgentClient?): Bitmap? = withContext(Dispatchers.IO) {
        val id = id(key)
        mem.get(id)?.let { return@withContext it }
        val f = File(dir, "$id.jpg")
        val bytes = if (f.isFile) f.readBytes() else {
            val c = client() ?: return@withContext null
            val b = runCatching { c.thumb(cardPath, 400) }.getOrNull() ?: return@withContext null
            runCatching { f.writeBytes(b) }
            b
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val sample = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / 480)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        mem.put(id, bmp)
        bmp
    }

    /** Celý vložený JPEG pro velký náhled (největší z ARW, `min=0` vrací největší). */
    suspend fun full(key: FileKey, cardPath: String, client: AgentClient): Bitmap? = withContext(Dispatchers.IO) {
        val b = runCatching { client.thumb(cardPath, 1600) }.getOrNull() ?: return@withContext null
        BitmapFactory.decodeByteArray(b, 0, b.size)
    }
}
