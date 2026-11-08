package com.nicky.rincewinder.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import androidx.documentfile.provider.DocumentFile
import com.nicky.rincewinder.core.LocalFiles
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lokální úložiště přes SAF: strom `Pictures/Rincewinder` (onboarding 1/3), každý přenos podsložka s datem.
 * Cesty = URI dokumentů jako řetězce. `.part` se připisuje v režimu "wa" (append).
 */
class SafLocalFiles(private val ctx: Context, treeUri: Uri, dateDir: String = today()) : LocalFiles {
    private val resolver = ctx.contentResolver
    private val root = DocumentFile.fromTreeUri(ctx, treeUri) ?: throw IOException("strom SAF nedostupný")
    private val dir: DocumentFile by lazy {
        root.findFile(dateDir)?.takeIf { it.isDirectory } ?: root.createDirectory(dateDir)
            ?: throw IOException("nejde vytvořit složku $dateDir")
    }
    val dirName: String get() = dir.name ?: ""

    /** Cesta = "<uri složky>#<název souboru>"; dokument může ještě neexistovat. */
    override fun targetPath(name: String) = "${dir.uri}#$name"
    override fun partPath(name: String) = "${dir.uri}#$name.part"

    private fun find(path: String): DocumentFile? {
        val name = path.substringAfterLast('#')
        return dir.findFile(name)?.takeIf { it.isFile }
    }

    override fun exists(path: String) = find(path) != null
    override fun size(path: String): Long? = find(path)?.length()

    override fun md5(path: String): String {
        val doc = find(path) ?: throw IOException("chybí $path")
        val md = MessageDigest.getInstance("MD5")
        resolver.openInputStream(doc.uri)?.use { inp ->
            val buf = ByteArray(65536)
            while (true) { val n = inp.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
        } ?: throw IOException("nejde číst $path")
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    override fun appendFrom(path: String, input: InputStream, onProgress: (Long) -> Unit): Long {
        val name = path.substringAfterLast('#')
        val doc = find(path) ?: dir.createFile("application/octet-stream", name) ?: throw IOException("nejde vytvořit $name")
        // SAF někdy vytvoří "name.part" jako "name.part (1)" nebo doplní příponu; ověřit název
        if (doc.name != name) { doc.delete(); throw IOException("SAF přejmenoval $name na ${doc.name}") }
        var total = 0L
        val pfd: ParcelFileDescriptor = resolver.openFileDescriptor(doc.uri, "wa") ?: throw IOException("nejde otevřít $name")
        pfd.use { fd ->
            FileOutputStream(fd.fileDescriptor).use { out ->
                val buf = ByteArray(65536)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    total += n
                    onProgress(n.toLong())
                }
                out.fd.sync()
            }
        }
        return total
    }

    override fun finish(partPath: String, finalPath: String) {
        val finalName = finalPath.substringAfterLast('#')
        find(finalPath)?.delete()
        val part = find(partPath) ?: throw IOException("chybí $partPath")
        if (!part.renameTo(finalName)) throw IOException("nejde přejmenovat na $finalName")
    }

    override fun delete(path: String) = find(path)?.delete() ?: false

    override fun freeBytes(): Long = try {
        val sm = ctx.getSystemService(StorageManager::class.java)
        sm.getAllocatableBytes(sm.getUuidForPath(ctx.filesDir))
    } catch (_: Exception) {
        ctx.filesDir.usableSpace
    }

    companion object {
        fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
    }
}
