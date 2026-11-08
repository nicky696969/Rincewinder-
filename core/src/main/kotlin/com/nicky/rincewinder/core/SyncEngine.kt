package com.nicky.rincewinder.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

sealed class SyncEvent {
    data class Started(val key: FileKey, val from: Long) : SyncEvent()
    data class Progress(val key: FileKey, val bytesDone: Long, val total: Long) : SyncEvent()
    data class Verified(val key: FileKey) : SyncEvent()
    data class Failed(val key: FileKey, val reason: String) : SyncEvent()
    /** Síť nebo tělo vypadlo: fronta se zastavila, [bytesDone] zůstávají, pokračuje se voláním [SyncEngine.run] znovu. */
    data class Paused(val key: FileKey?, val reason: String) : SyncEvent()
    data class Finished(val verified: Int, val failed: Int, val paused: Boolean) : SyncEvent()
}

/**
 * Stažení a ověření souborů z karty přes Agenta (docs/ZADANI_F1.md §5.5).
 * Max [parallel] spojení; MD5 souboru n běží při stahování n+1 (každé stažení+ověření je jedna úloha).
 * I/O chyba = pauza celé fronty (tělo spí / Wi‑Fi pryč), ne CHYBA souboru.
 */
class SyncEngine(
    private val client: AgentClient,
    private val store: FileStore,
    private val local: LocalFiles,
    private val parallel: Int = 2,
    private val verifyMd5: Boolean = true,
    private val onEvent: (SyncEvent) -> Unit = {},
) {
    private val paused = AtomicBoolean(false)

    /** Stáhne a ověří všechny záznamy, které jsou „nové“ (nebo jen [only]). Vrací souhrn. */
    suspend fun run(only: Collection<FileKey>? = null): SyncEvent.Finished {
        paused.set(false)
        val todo = store.all().filter { it.isNew && (only == null || it.key in only) }
        val sem = Semaphore(parallel)
        var verified = 0
        var failed = 0
        coroutineScope {
            todo.map { rec ->
                async(Dispatchers.IO) {
                    if (paused.get()) return@async null
                    sem.withPermit {
                        if (paused.get()) null else processOne(rec)
                    }
                }
            }.awaitAll().forEach { r ->
                when (r) { true -> verified++; false -> failed++; null -> {} }
            }
        }
        val fin = SyncEvent.Finished(verified, failed, paused.get())
        onEvent(fin)
        return fin
    }

    /** true = OVERENO (nebo STAZENO bez ověřování), false = CHYBA, null = pauza. */
    private suspend fun processOne(start: FileRecord): Boolean? = withContext(Dispatchers.IO) {
        var attempt = 0
        var rec = start
        while (true) {
            attempt++
            val path = rec.cardPath
            val part = local.partPath(rec.key.name)
            val target = local.targetPath(rec.key.name)
            try {
                // 1) stáhnout (s pokračováním od velikosti .part)
                var done = local.size(part) ?: 0L
                if (done > rec.key.size) { local.delete(part); done = 0 }
                rec = store.update(rec.key) { it.copy(state = FileState.STAHUJE_SE, bytesDone = done, errorReason = null) } ?: return@withContext false
                onEvent(SyncEvent.Started(rec.key, done))
                if (done < rec.key.size) {
                    client.openFile(path, done).use { fs ->
                        if (fs.total >= 0 && fs.total != rec.key.size) {
                            fail(rec, "soubor na kartě má jinou velikost (${fs.total})"); return@withContext false
                        }
                        var acc = done
                        local.appendFrom(part, fs.input) { n ->
                            acc += n
                            onEvent(SyncEvent.Progress(rec.key, acc, rec.key.size))
                        }
                        done = acc
                    }
                }
                val got = local.size(part) ?: 0L
                if (got != rec.key.size) {
                    // stream skončil dřív bez výjimky: považovat za výpadek, ne za CHYBA
                    store.update(rec.key) { it.copy(bytesDone = got) }
                    pause(rec.key, "přenos skončil předčasně ($got z ${rec.key.size} B)")
                    return@withContext null
                }
                local.finish(part, target)
                rec = store.update(rec.key) { it.copy(state = FileState.STAZENO, localPath = target, bytesDone = got) }!!

                // 2) ověřit
                if (!verifyMd5) return@withContext true
                val md5Local = local.md5(target)
                val md5Agent = client.md5(path)
                if (md5Local == md5Agent) {
                    store.update(rec.key) { it.copy(state = FileState.OVERENO, md5Local = md5Local, md5Agent = md5Agent) }
                    onEvent(SyncEvent.Verified(rec.key))
                    return@withContext true
                }
                local.delete(target)
                if (attempt >= 2) { fail(rec, "MD5 nesedí ani po opakování ($md5Local ≠ $md5Agent)"); return@withContext false }
                store.update(rec.key) { it.copy(state = FileState.NA_KARTE, bytesDone = 0, localPath = null) }
            } catch (e: IOException) {
                store.update(rec.key) { it.copy(state = FileState.STAHUJE_SE, bytesDone = local.size(part) ?: 0L) }
                pause(rec.key, e.message ?: e.toString())
                return@withContext null
            }
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    private fun fail(rec: FileRecord, reason: String) {
        store.update(rec.key) { it.copy(state = FileState.CHYBA, errorReason = reason, bytesDone = 0) }
        onEvent(SyncEvent.Failed(rec.key, reason))
    }

    private fun pause(key: FileKey?, reason: String) {
        if (paused.compareAndSet(false, true)) onEvent(SyncEvent.Paused(key, reason))
    }
}
