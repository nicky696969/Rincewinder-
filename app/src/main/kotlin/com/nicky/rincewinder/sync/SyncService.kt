package com.nicky.rincewinder.sync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.nicky.rincewinder.App
import com.nicky.rincewinder.core.BodyLink
import com.nicky.rincewinder.core.FileKey
import com.nicky.rincewinder.core.SyncEngine
import com.nicky.rincewinder.core.SyncEvent
import com.nicky.rincewinder.data.SafLocalFiles
import com.nicky.rincewinder.ui.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Stav přenosu pro UI (Domů) i notifikaci. */
data class TransferState(
    val running: Boolean = false,
    val currentName: String? = null,
    val doneFiles: Int = 0,
    val totalFiles: Int = 0,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val mbps: Float = 0f,
    val waitingForBody: String? = null, // text, proč se čeká (tělo spí / Agent neběží)
    val lastMessage: String? = null,
)

/**
 * Přenos ve foreground service typu connectedDevice (bez 6h limitu). Po výpadku těla čeká a pokračuje sám
 * od `bytesDone`, bez dotazu (UX_NAVRH P0 #4). Pozastavit = zastavit službu, rozdělané soubory zůstávají
 * STAHUJE_SE a příští „Stáhnout nové“ je dokončí (UX_WIREFRAMES §5, Zrušení).
 */
class SyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_PAUSE, ACT_CANCEL -> {
                job?.cancel()
                finishWith(if (intent.action == ACT_CANCEL) "Přenos zrušen, rozdělané soubory se dokončí příště" else "Pozastaveno", vibrate = false)
                return START_NOT_STICKY
            }
        }
        if (job?.isActive == true) return START_STICKY
        val only = intent?.getStringArrayExtra(EXTRA_ONLY)?.map { s ->
            val p = s.split('|'); FileKey(p[0], p[1].toLong(), p[2].toLong())
        }
        ServiceCompat.startForeground(
            this, Notif.ID_TRANSFER,
            Notif.progress(this, "Rincewinder · přenos", "Hledám tělo…", 0, 0, false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        job = scope.launch { runLoop(only) }
        return START_STICKY
    }

    private suspend fun runLoop(only: List<FileKey>?) {
        val app = App.of(this)
        val tree = app.prefs.treeUri?.let { Uri.parse(it) }
        if (tree == null) { finishWith("Není vybraná složka pro RAWy", vibrate = true); return }
        val local = try { SafLocalFiles(this, tree) } catch (e: Exception) {
            finishWith("Ztratil jsem přístup ke složce — vyber ji znovu", vibrate = true); return
        }
        val total = app.store.all().filter { it.isNew && (only == null || it.key in only) }
        var doneFiles = 0
        val bytesTotal = total.sumOf { it.key.size }
        var bytesAcc = total.sumOf { it.bytesDone }
        var backoff = 5_000L
        _state.value = TransferState(running = true, totalFiles = total.size, bytesTotal = bytesTotal, bytesDone = bytesAcc)

        while (true) {
            val body = app.finder.find()
            val host = body.host
            if (host == null || body.status == null) {
                val why = when (body.link) {
                    BodyLink.AGENT_NEBEZI, BodyLink.AGENT_CHYBI, BodyLink.AGENT_OK_ADB_VYPNUTE ->
                        "Agent neběží: MENU → Aplikace → Rincewinder Agent"
                    else -> if (body.permissionMissing) "Chybí oprávnění k místní síti" else "Tělo spí: zmáčkni spoušť napůl"
                }
                if (_state.value.waitingForBody == null) Notif.event(this, "Tělo se odmlčelo", "$why. Fronta čeká, pokračuji po připojení.", vibrate = true)
                _state.value = _state.value.copy(waitingForBody = why)
                notify("Čekám na tělo", why, doneFiles, total.size, true)
                // Agent lze probudit z telefonu, když běží ADB
                if (body.link == BodyLink.AGENT_NEBEZI) runCatching { com.nicky.rincewinder.body.AdbOps.startAgent(host!!) }
                delay(backoff); backoff = minOf(backoff * 2, 60_000L)
                continue
            }
            backoff = 5_000L
            Notif.cancelEvent(this)
            _state.value = _state.value.copy(waitingForBody = null)

            var winStart = System.currentTimeMillis()
            var winBytes = 0L
            var mbps = app.prefs.lastMbps
            val engine = SyncEngine(app.finder.client(host), app.store, local, onEvent = { ev ->
                when (ev) {
                    is SyncEvent.Started -> _state.value = _state.value.copy(currentName = ev.key.name)
                    is SyncEvent.Progress -> {
                        val delta = ev.bytesDone - (lastSeen[ev.key] ?: ev.bytesDone)
                        lastSeen[ev.key] = ev.bytesDone
                        if (delta > 0) { bytesAcc += delta; winBytes += delta }
                        val now = System.currentTimeMillis()
                        if (now - winStart >= 3000) {
                            mbps = winBytes / 1048576f / ((now - winStart) / 1000f)
                            winStart = now; winBytes = 0
                            app.prefs.lastMbps = mbps
                        }
                        _state.value = _state.value.copy(bytesDone = bytesAcc, mbps = mbps, currentName = ev.key.name)
                        // notifikaci nejvýš 1× za sekundu: Android zahazuje víc než 5 aktualizací/s
                        // (8. 11. 2026 log: „Shedding notify … rate limit (5.0) exceeded“, appka posílala ~800/s)
                        if (now - lastNotifyMs >= 1000) {
                            lastNotifyMs = now
                            val left = Format.eta(bytesTotal - bytesAcc, mbps)
                            notify("Rincewinder · přenos", "${doneFiles + 1} z ${total.size} · ${ev.key.name}\n${Format.mbps(mbps)} · zbývá $left",
                                ((bytesAcc * 100) / maxOf(1, bytesTotal)).toInt(), 100, false)
                        }
                    }
                    is SyncEvent.Verified -> { doneFiles++; _state.value = _state.value.copy(doneFiles = doneFiles) }
                    is SyncEvent.Failed -> { doneFiles++; _state.value = _state.value.copy(doneFiles = doneFiles, lastMessage = "${ev.key.name}: ${ev.reason}") }
                    is SyncEvent.Paused -> _state.value = _state.value.copy(lastMessage = ev.reason)
                    is SyncEvent.Finished -> {}
                }
            })
            val fin = engine.run(only)
            if (fin.paused) { delay(3_000); continue }
            val verified = app.store.all().count { it.key in total.map { t -> t.key }.toSet() && it.state == com.nicky.rincewinder.core.FileState.OVERENO }
            val failed = fin.failed
            val summary = if (failed == 0) "$verified staženo, vše ověřeno ✓" else "$verified ověřeno, $failed ${Format.plural(failed, "chyba", "chyby", "chyb")}"
            app.prefs.lastTransferSummary = "${Format.dateTime(System.currentTimeMillis())} · ${Format.plural(total.size, "soubor", "soubory", "souborů")} · ${Format.bytes(bytesTotal)} · $summary"
            finishWith(summary, vibrate = true)
            return
        }
    }

    private val lastSeen = HashMap<FileKey, Long>()
    @Volatile private var lastNotifyMs = 0L

    private fun notify(title: String, text: String, done: Int, total: Int, waiting: Boolean) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(Notif.ID_TRANSFER, Notif.progress(this, title, text, done, total, waiting))
    }

    private fun finishWith(msg: String, vibrate: Boolean) {
        _state.value = TransferState(running = false, lastMessage = msg)
        Notif.event(this, "Rincewinder", msg, vibrate)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        if (_state.value.running) _state.value = TransferState(running = false, lastMessage = "Přenos přerušen")
        super.onDestroy()
    }

    companion object {
        const val ACT_PAUSE = "pause"
        const val ACT_CANCEL = "cancel"
        const val EXTRA_ONLY = "only"
        private val _state = MutableStateFlow(TransferState())
        val state: StateFlow<TransferState> get() = _state

        /** Spustí přenos nových (nebo jen [only]). */
        fun start(ctx: Context, only: Collection<FileKey>? = null) {
            val i = Intent(ctx, SyncService::class.java)
            if (only != null) i.putExtra(EXTRA_ONLY, only.map { "${it.name}|${it.size}|${it.mtime}" }.toTypedArray())
            ctx.startForegroundService(i)
        }

        fun pause(ctx: Context) = ctx.startService(Intent(ctx, SyncService::class.java).setAction(ACT_PAUSE))
    }
}
