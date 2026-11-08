package com.nicky.rincewinder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nicky.rincewinder.App
import com.nicky.rincewinder.body.AdbOps
import com.nicky.rincewinder.body.BodyState
import com.nicky.rincewinder.core.AgentClient
import com.nicky.rincewinder.core.BodyLink
import com.nicky.rincewinder.core.DeleteFlow
import com.nicky.rincewinder.core.DeleteOutcome
import com.nicky.rincewinder.core.FileKey
import com.nicky.rincewinder.core.FileRecord
import com.nicky.rincewinder.core.FirstPairing
import com.nicky.rincewinder.core.QrPairing
import com.nicky.rincewinder.core.Reconciler
import com.nicky.rincewinder.data.SafLocalFiles
import com.nicky.rincewinder.sync.SyncService
import com.nicky.rincewinder.sync.TransferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

const val CARD_DIR = "DCIM/100MSDCF"

/** Jeden držák stavu pro všechny obrazovky; vše síťové mimo hlavní vlákno. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val a = App.of(app)
    val prefs get() = a.prefs
    val store get() = a.store
    val thumbs = Thumbs(app)

    private val _body = MutableStateFlow(BodyState())
    val body: StateFlow<BodyState> get() = _body
    val records: StateFlow<List<FileRecord>> get() = store.records
    val transfer: StateFlow<TransferState> get() = SyncService.state

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> get() = _toast
    fun toastShown() { _toast.value = null }
    fun say(msg: String) { _toast.value = msg }

    /** Volba při prvním spárování čeká na uživatele (UX_WIREFRAMES §10). */
    private val _firstPairingAsk = MutableStateFlow<Pair<Int, Long>?>(null)
    val firstPairingAsk: StateFlow<Pair<Int, Long>?> get() = _firstPairingAsk
    private var pendingLs: List<com.nicky.rincewinder.core.LsEntry>? = null

    fun client(): AgentClient? = _body.value.host?.takeIf { _body.value.status != null }?.let { a.finder.client(it) }

    fun refresh() {
        if (_body.value.searching) return
        _body.value = _body.value.copy(searching = true)
        viewModelScope.launch {
            val s = a.finder.find()
            _body.value = s.copy(searching = false)
            if (s.status != null) listCard(s.host!!)
        }
    }

    /** Spáruje z QR: uloží host, mac a klíč, pak rovnou zjistí stav. */
    fun pairFromQr(text: String): Boolean {
        val q = QrPairing.parse(text) ?: return false
        prefs.lastIp = q.host
        q.mac?.let { prefs.bodyMac = it }
        q.key?.let { prefs.agentKey = it }
        refresh()
        return true
    }

    fun setManualIp(ip: String) { prefs.lastIp = ip.trim(); refresh() }

    private suspend fun listCard(host: String) {
        val ls = withContext(Dispatchers.IO) { runCatching { a.finder.client(host).ls(CARD_DIR) }.getOrNull() } ?: return
        if (!prefs.firstPairingDone && store.isEmpty()) {
            val arw = ls.filter { it.isArw }
            pendingLs = ls
            _firstPairingAsk.value = arw.size to arw.sumOf { it.size }
            return
        }
        withContext(Dispatchers.IO) { Reconciler(store).reconcile(ls, System.currentTimeMillis()) }
    }

    fun answerFirstPairing(choice: FirstPairing) {
        val ls = pendingLs ?: return
        _firstPairingAsk.value = null
        prefs.firstPairingDone = true
        viewModelScope.launch(Dispatchers.IO) {
            val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            Reconciler(store).reconcile(ls, System.currentTimeMillis(), choice, today)
        }
    }

    fun downloadNew() = SyncService.start(getApplication())
    fun downloadSelected(keys: Collection<FileKey>) = SyncService.start(getApplication(), keys)
    fun pauseTransfer() = SyncService.pause(getApplication())

    /** Jen soubory od dneška (UX_WIREFRAMES §10, velký počet). */
    fun downloadToday() {
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        SyncService.start(getApplication(), store.all().filter { it.isNew && it.key.mtime >= today }.map { it.key })
    }

    private fun localFiles(): SafLocalFiles? = prefs.treeUri?.let { runCatching { SafLocalFiles(getApplication(), android.net.Uri.parse(it)) }.getOrNull() }

    /** Náhled mazání pro dialog: (smazatelné, vynechané s důvodem). */
    suspend fun deletePreview(selected: Collection<FileRecord>): Pair<List<FileRecord>, List<Pair<FileRecord, String>>> =
        withContext(Dispatchers.IO) {
            val c = client() ?: return@withContext emptyList<FileRecord>() to selected.map { it to "tělo nedostupné" }
            val l = localFiles() ?: return@withContext emptyList<FileRecord>() to selected.map { it to "složka nedostupná" }
            DeleteFlow(c, store, l, CARD_DIR).preview(selected)
        }

    suspend fun deleteFromCard(selected: Collection<FileRecord>): DeleteOutcome? = withContext(Dispatchers.IO) {
        val c = client() ?: return@withContext null
        val l = localFiles() ?: return@withContext null
        runCatching { DeleteFlow(c, store, l, CARD_DIR).delete(selected) }.getOrElse { e -> say("Mazání selhalo: ${e.message}"); null }
    }

    suspend fun verifyMd5(rec: FileRecord) = withContext(Dispatchers.IO) {
        val c = client() ?: return@withContext
        val l = localFiles() ?: return@withContext
        val path = rec.localPath ?: return@withContext
        runCatching {
            val agentMd5 = c.md5("$CARD_DIR/${rec.key.name}"); val localMd5 = l.md5(path)
            val same = agentMd5 == localMd5
            store.update(rec.key) { it.copy(state = if (same) com.nicky.rincewinder.core.FileState.OVERENO else com.nicky.rincewinder.core.FileState.CHYBA, md5Agent = agentMd5, md5Local = localMd5, errorReason = if (same) null else "MD5 nesedí") }
        }.onFailure { say("Ověření selhalo: ${it.message}") }
    }

    // --- Apky (ADB) ---
    fun startAgentFromPhone() = viewModelScope.launch(Dispatchers.IO) {
        val h = _body.value.host ?: return@launch
        runCatching { AdbOps.startAgent(h, withUi = _body.value.link == BodyLink.AGENT_NEBEZI) }
            .onSuccess { say("Agent spuštěn z telefonu"); kotlinx.coroutines.delay(2500); refresh() }
            .onFailure { say("Spuštění selhalo: ${it.message}") }
    }

    fun installBundledAgent() = viewModelScope.launch(Dispatchers.IO) {
        val h = _body.value.host ?: return@launch
        val apk = File(getApplication<Application>().cacheDir, "agent.apk")
        runCatching {
            getApplication<Application>().assets.open("agent.apk").use { i -> apk.outputStream().use { i.copyTo(it) } }
            AdbOps.install(h, apk)
        }.onSuccess { (ok, txt) -> say(if (ok) "Agent nainstalován" else "Instalace: $txt"); if (ok) { AdbOps.startAgent(h, true); kotlinx.coroutines.delay(2500); refresh() } }
            .onFailure { say("Instalace selhala: ${it.message}") }
    }

    val hasBundledAgent: Boolean by lazy { runCatching { getApplication<Application>().assets.open("agent.apk").close(); true }.getOrDefault(false) }

    suspend fun listApps(): List<Pair<String, String?>> = withContext(Dispatchers.IO) {
        val h = _body.value.host ?: return@withContext emptyList()
        runCatching { AdbOps.thirdPartyPackages(h).map { it to AdbOps.versionName(h, it) } }.getOrDefault(emptyList())
    }
}
