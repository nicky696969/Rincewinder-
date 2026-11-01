package com.nicky.rincewinder.core

/** Úložiště záznamů o souborech. V :app ho implementuje Room, v testech [InMemoryFileStore]. */
interface FileStore {
    fun get(key: FileKey): FileRecord?
    fun all(): List<FileRecord>
    fun upsert(record: FileRecord)
    fun remove(key: FileKey)
    fun isEmpty(): Boolean = all().isEmpty()

    /** Atomická změna jednoho záznamu; když neexistuje, nic se nestane a vrátí null. */
    fun update(key: FileKey, change: (FileRecord) -> FileRecord): FileRecord? {
        val cur = get(key) ?: return null
        val next = change(cur)
        upsert(next)
        return next
    }
}

class InMemoryFileStore : FileStore {
    private val map = LinkedHashMap<FileKey, FileRecord>()

    @Synchronized override fun get(key: FileKey) = map[key]
    @Synchronized override fun all() = map.values.toList()
    @Synchronized override fun upsert(record: FileRecord) { map[record.key] = record }
    @Synchronized override fun remove(key: FileKey) { map.remove(key) }
    @Synchronized override fun update(key: FileKey, change: (FileRecord) -> FileRecord): FileRecord? {
        val cur = map[key] ?: return null
        val next = change(cur)
        map[key] = next
        return next
    }
}
