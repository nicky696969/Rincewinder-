package com.nicky.rincewinder.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Lokální úložiště RAWů na Pixelu. Rozhraní, protože v :app je to SAF (DocumentFile), v testech obyčejné soubory.
 * Cesty jsou neprůhledné řetězce pro volajícího; [targetPath]/[partPath] je vytváří.
 */
interface LocalFiles {
    fun targetPath(name: String): String
    fun partPath(name: String): String = targetPath(name) + ".part"
    fun exists(path: String): Boolean
    fun size(path: String): Long?
    @Throws(IOException::class) fun md5(path: String): String
    /** Připíše data ze streamu na konec souboru (vytvoří ho, když chybí). Vrací počet připsaných bajtů. */
    @Throws(IOException::class) fun appendFrom(path: String, input: InputStream, onProgress: (Long) -> Unit = {}): Long
    @Throws(IOException::class) fun finish(partPath: String, finalPath: String)
    fun delete(path: String): Boolean
    fun freeBytes(): Long
}

class JvmLocalFiles(private val root: File) : LocalFiles {
    init { root.mkdirs() }

    override fun targetPath(name: String) = File(root, name).path
    override fun exists(path: String) = File(path).isFile
    override fun size(path: String) = File(path).takeIf { it.isFile }?.length()

    override fun md5(path: String): String {
        val md = MessageDigest.getInstance("MD5")
        File(path).inputStream().use { inp ->
            val buf = ByteArray(65536)
            while (true) {
                val n = inp.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    override fun appendFrom(path: String, input: InputStream, onProgress: (Long) -> Unit): Long {
        var total = 0L
        FileOutputStream(File(path), true).use { out ->
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
        return total
    }

    override fun finish(partPath: String, finalPath: String) {
        val f = File(finalPath)
        if (f.exists() && !f.delete()) throw IOException("nejde přepsat $finalPath")
        if (!File(partPath).renameTo(f)) throw IOException("nejde přejmenovat $partPath")
    }

    override fun delete(path: String) = File(path).delete()
    override fun freeBytes() = root.usableSpace
}
