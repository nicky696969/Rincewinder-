package com.nicky.rincewinder.data

import com.nicky.rincewinder.core.DEFAULT_CARD_DIR
import com.nicky.rincewinder.core.FileKey
import com.nicky.rincewinder.core.FileRecord
import com.nicky.rincewinder.core.FileState
import com.nicky.rincewinder.core.FileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Záznamy o souborech v jednom JSON souboru (atomický zápis přes .tmp + rename).
 * Vědomá odchylka od ZADANI_F1 §5.1 (Room): stovky záznamů, žádné dotazy — JSON je jednodušší a bez KSP.
 * Změny publikuje přes [records], aby UI nemuselo nic pollovat.
 */
class JsonFileStore(private val file: File) : FileStore {
    private val map = LinkedHashMap<FileKey, FileRecord>()
    private val _records = MutableStateFlow<List<FileRecord>>(emptyList())
    val records: StateFlow<List<FileRecord>> get() = _records

    init { load() }

    @Synchronized override fun get(key: FileKey) = map[key]
    @Synchronized override fun all() = map.values.toList()
    @Synchronized override fun upsert(record: FileRecord) { map[record.key] = record; persist() }
    @Synchronized override fun remove(key: FileKey) { map.remove(key); persist() }
    @Synchronized override fun update(key: FileKey, change: (FileRecord) -> FileRecord): FileRecord? {
        val cur = map[key] ?: return null
        val next = change(cur)
        map[key] = next
        persist()
        return next
    }

    private fun persist() {
        _records.value = map.values.toList()
        val arr = JSONArray()
        for (r in map.values) arr.put(JSONObject().apply {
            put("name", r.key.name); put("size", r.key.size); put("mtime", r.key.mtime)
            put("state", r.state.name)
            put("localPath", r.localPath); put("md5Agent", r.md5Agent); put("md5Local", r.md5Local)
            put("bytesDone", r.bytesDone); put("errorReason", r.errorReason)
            put("lastSeenOnCard", r.lastSeenOnCard); put("firstSeen", r.firstSeen); put("cardDir", r.cardDir)
        })
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun load() {
        if (!file.isFile) return
        val arr = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val key = FileKey(o.getString("name"), o.getLong("size"), o.getLong("mtime"))
            map[key] = FileRecord(
                key = key,
                state = runCatching { FileState.valueOf(o.getString("state")) }.getOrDefault(FileState.NA_KARTE),
                localPath = o.optString("localPath", "").ifEmpty { null },
                md5Agent = o.optString("md5Agent", "").ifEmpty { null },
                md5Local = o.optString("md5Local", "").ifEmpty { null },
                bytesDone = o.optLong("bytesDone", 0),
                errorReason = o.optString("errorReason", "").ifEmpty { null },
                lastSeenOnCard = o.optLong("lastSeenOnCard", 0),
                firstSeen = o.optLong("firstSeen", 0),
                cardDir = o.optString("cardDir", DEFAULT_CARD_DIR),
            )
        }
        _records.value = map.values.toList()
    }
}
