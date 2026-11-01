package com.nicky.rincewinder.core

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Řádek z `GET /ls`: `name;size;mtimeMs;d|f`. */
data class LsEntry(val name: String, val size: Long, val mtime: Long, val isDir: Boolean) {
    val isArw: Boolean get() = !isDir && name.endsWith(".ARW", ignoreCase = true)
    fun toKey() = FileKey(name, size, mtime)
}

/** Vybraná pole z `GET /status` (kontrakt v docs/ZADANI_F1.md §4). Co Agent nezná, je null. */
data class AgentStatus(
    val agentVersion: String?,
    val mac: String?,
    val ip: String?,
    val cardPresent: Boolean?,
    val cardFree: Long?,
    val cardTotal: Long?,
    val batteryPct: Int?,
    val wifiDbm: Int?,
    val activeClients: Int?,
    val raw: Map<String, Any?>,
)

/** Otevřený stream souboru z Agenta. [length] = počet bajtů v tomto streamu (od [from] do konce). */
class FileStream(val input: InputStream, val from: Long, val length: Long, val total: Long) : AutoCloseable {
    override fun close() = input.close()
}

/** Klient Agenta na těle. Rozhraní kvůli testům (falešný klient pro MD5 neshodu apod.). */
interface AgentClient {
    @Throws(IOException::class) fun ping(): String
    @Throws(IOException::class) fun status(): AgentStatus
    @Throws(IOException::class) fun ls(path: String): List<LsEntry>
    @Throws(IOException::class) fun openFile(path: String, from: Long = 0): FileStream
    @Throws(IOException::class) fun md5(path: String): String
    @Throws(IOException::class) fun thumb(path: String, minLongSide: Int = 0): ByteArray
}

/** HTTP klient nad `java.net.HttpURLConnection` (funguje na JVM i na Androidu, bez závislostí). */
class HttpAgentClient(
    host: String,
    port: Int = 8808,
    private val connectTimeoutMs: Int = 4_000,
    private val readTimeoutMs: Int = 30_000,
) : AgentClient {
    val base = "http://$host:$port"

    private fun open(path: String, query: String = "", range: Long? = null): HttpURLConnection {
        val c = URL("$base$path$query").openConnection() as HttpURLConnection
        c.connectTimeout = connectTimeoutMs
        c.readTimeout = readTimeoutMs
        c.useCaches = false
        if (range != null && range > 0) c.setRequestProperty("Range", "bytes=$range-")
        return c
    }

    private fun q(path: String) = "?path=" + URLEncoder.encode(path, "UTF-8")

    private fun text(path: String, query: String = ""): String {
        val c = open(path, query)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code pro $path")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }

    override fun ping() = text("/ping").trim()

    override fun status(): AgentStatus {
        val m = MiniJson.parseObject(text("/status"))
        fun long(k: String) = (m[k] as? Number)?.toLong()
        fun int(k: String) = (m[k] as? Number)?.toInt()
        return AgentStatus(
            agentVersion = m["agentVersion"] as? String,
            mac = m["mac"] as? String,
            ip = m["ip"] as? String,
            cardPresent = m["cardPresent"] as? Boolean,
            cardFree = long("cardFree"),
            cardTotal = long("cardTotal"),
            batteryPct = int("batteryPct"),
            wifiDbm = int("wifiDbm"),
            activeClients = int("activeClients"),
            raw = m,
        )
    }

    override fun ls(path: String): List<LsEntry> =
        text("/ls", q(path)).lineSequence().filter { it.isNotBlank() }.map { line ->
            val p = line.split(';')
            if (p.size < 4) throw IOException("špatný řádek /ls: $line")
            LsEntry(p[0], p[1].toLong(), p[2].toLong(), p[3] == "d")
        }.toList()

    override fun openFile(path: String, from: Long): FileStream {
        val c = open("/file", q(path), range = from)
        val code = c.responseCode
        if (code == 416) { c.disconnect(); throw IOException("Range mimo soubor ($from) pro $path") }
        if (code !in 200..299) { c.disconnect(); throw IOException("HTTP $code pro $path") }
        val len = c.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
        val cr = c.getHeaderField("Content-Range") // bytes a-b/total
        val total = cr?.substringAfter('/')?.toLongOrNull() ?: if (code == 200) len else -1L
        val actualFrom = if (code == 206) cr?.substringAfter(' ')?.substringBefore('-')?.toLongOrNull() ?: from else 0L
        if (from > 0 && actualFrom != from) {
            c.disconnect(); throw IOException("Agent ignoroval Range (od $from, dostal $actualFrom)")
        }
        return FileStream(c.inputStream, actualFrom, len, total)
    }

    override fun md5(path: String) = text("/md5", q(path)).trim().lowercase()

    override fun thumb(path: String, minLongSide: Int): ByteArray {
        val c = open("/thumb", q(path) + "&min=$minLongSide")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode} pro thumb $path")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}

/** Nejmenší parser plochého JSON objektu (string, číslo, bool, null). Agent nic víc neposílá. */
object MiniJson {
    fun parseObject(s: String): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        var i = s.indexOf('{') + 1
        fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun readString(): String {
            val sb = StringBuilder()
            i++ // "
            while (i < s.length && s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (s[i]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                        else -> sb.append(s[i])
                    }
                } else sb.append(s[i])
                i++
            }
            i++ // "
            return sb.toString()
        }
        while (i < s.length) {
            skipWs()
            if (i >= s.length || s[i] == '}') break
            if (s[i] == ',') { i++; continue }
            val key = readString()
            skipWs(); i++ // :
            skipWs()
            val value: Any? = when {
                s[i] == '"' -> readString()
                s.startsWith("true", i) -> { i += 4; true }
                s.startsWith("false", i) -> { i += 5; false }
                s.startsWith("null", i) -> { i += 4; null }
                else -> {
                    val st = i
                    while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
                    val num = s.substring(st, i)
                    if (num.any { it in ".eE" }) num.toDouble() else num.toLong()
                }
            }
            out[key] = value
        }
        return out
    }
}
