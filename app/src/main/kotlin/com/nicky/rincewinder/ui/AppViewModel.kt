package com.nicky.rincewinder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nicky.rincewinder.App
import com.nicky.rincewinder.body.AdbOps
import com.nicky.rincewinder.body.ApkStage
import com.nicky.rincewinder.body.GhRelease
import com.nicky.rincewinder.body.StagedApk
import com.nicky.rincewinder.body.BodyState
import com.nicky.rincewinder.core.AgentClient
import com.nicky.rincewinder.core.BodyApp
import com.nicky.rincewinder.core.BodyLink
import com.nicky.rincewinder.core.DeleteFlow
import com.nicky.rincewinder.core.DeleteOutcome
import com.nicky.rincewinder.core.FileKey
import com.nicky.rincewinder.core.FileRecord
import com.nicky.rincewinder.core.FirstPairing
import com.nicky.rincewinder.core.PmParse
import com.nicky.rincewinder.core.QrPairing
import com.nicky.rincewinder.core.lsCard
import com.nicky.rincewinder.core.Reconciler
import com.nicky.rincewinder.data.SafLocalFiles
import com.nicky.rincewinder.sync.SyncService
import com.nicky.rincewinder.sync.TransferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

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
        val ls = withContext(Dispatchers.IO) { runCatching { a.finder.client(host).lsCard() }.getOrNull() } ?: return
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
            DeleteFlow(c, store, l).preview(selected)
        }

    suspend fun deleteFromCard(selected: Collection<FileRecord>): DeleteOutcome? = withContext(Dispatchers.IO) {
        val c = client() ?: return@withContext null
        val l = localFiles() ?: return@withContext null
        runCatching { DeleteFlow(c, store, l).delete(selected) }.getOrElse { e -> say("Mazání selhalo: ${e.message}"); null }
    }

    suspend fun verifyMd5(rec: FileRecord) = withContext(Dispatchers.IO) {
        val c = client() ?: return@withContext
        val l = localFiles() ?: return@withContext
        val path = rec.localPath ?: return@withContext
        runCatching {
            val agentMd5 = c.md5(rec.cardPath); val localMd5 = l.md5(path)
            val same = agentMd5 == localMd5
            store.update(rec.key) { it.copy(state = if (same) com.nicky.rincewinder.core.FileState.OVERENO else com.nicky.rincewinder.core.FileState.CHYBA, md5Agent = agentMd5, md5Local = localMd5, errorReason = if (same) null else "MD5 nesedí") }
        }.onFailure { say("Ověření selhalo: ${it.message}") }
    }

    // --- Apky (ADB) ---
    fun startAgentFromPhone() = bodyOp("Spouštění Agenta") { h ->
        AdbOps.startAgent(h, withUi = _body.value.link == BodyLink.AGENT_NEBEZI)
        kotlinx.coroutines.delay(2500); refresh()
        "Agent spuštěn z telefonu"
    }

    fun installBundledAgent() = bodyOp("Instalace Agenta") { h ->
        val apk = File(getApplication<Application>().cacheDir, "agent.apk")
        try {
            getApplication<Application>().assets.open("agent.apk").use { i -> apk.outputStream().use { i.copyTo(it) } }
            val (ok, txt) = AdbOps.install(h, apk)
            if (!ok) return@bodyOp "Instalace Agenta: $txt"
        } finally {
            apk.delete()
        }
        AdbOps.startAgent(h, true); kotlinx.coroutines.delay(2500); refresh()
        "Agent nainstalován"
    }

    val hasBundledAgent: Boolean by lazy { runCatching { getApplication<Application>().assets.open("agent.apk").close(); true }.getOrDefault(false) }

    /** Aplikace 3. stran v těle s příznakem „běží“; null = ADB neodpovídá. */
    suspend fun bodyApps(): Result<List<Pair<BodyApp, Boolean>>> = withContext(Dispatchers.IO) {
        runCatching { AdbOps.apps(_body.value.host ?: throw java.io.IOException("tělo není připojené")) }
    }

    fun reloadApps() = _appsVersion.update { it + 1 }

    suspend fun apkSize(codePath: String): Long? = withContext(Dispatchers.IO) {
        val h = _body.value.host ?: return@withContext null
        runCatching { AdbOps.apkSize(h, codePath) }.getOrNull()
    }

    /** Po každé změně v těle se zvýší → seznam aplikací se načte znovu. */
    private val _appsVersion = MutableStateFlow(0)
    val appsVersion: StateFlow<Int> get() = _appsVersion

    /** Jedna operace nad tělem najednou; text běžící operace pro UI (null = klid). */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> get() = _busy

    private fun bodyOp(label: String, block: suspend (host: String) -> String?) {
        if (_busy.value != null) { say("Počkej, běží: ${_busy.value}"); return }
        val h = _body.value.host ?: run { say("Tělo není připojené"); return }
        _busy.value = label
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runCatching { block(h) }.onSuccess { it?.let(::say) }.onFailure { say("$label selhalo: ${it.message}") }
            } finally {
                _busy.value = null
                _appsVersion.update { it + 1 }
            }
        }
    }

    fun launchApp(pkg: String) = bodyOp("Spouštění") { h -> AdbOps.launch(h, pkg) }
    fun stopApp(pkg: String) = bodyOp("Ukončování") { h -> AdbOps.forceStop(h, pkg); "Ukončeno" }
    fun clearApp(pkg: String) = bodyOp("Mazání dat") { h -> if (AdbOps.clearData(h, pkg)) "Data aplikace smazána" else "Data se nepodařilo smazat" }
    fun uninstallApp(pkg: String) = bodyOp("Odinstalace") { h ->
        val (ok, txt) = AdbOps.uninstall(h, pkg)
        if (ok) { if (pkg == AdbOps.AGENT_PKG) refresh(); "Odinstalováno" } else "Odinstalace: $txt"
    }

    /** Záloha APK z těla do složky `APK-zalohy` ve stromu SAF. */
    fun backupApp(app: BodyApp) = bodyOp("Záloha APK") { h -> backupApk(h, app) }

    private fun backupApk(host: String, app: BodyApp): String {
        val codePath = app.codePath ?: return "Cesta k APK v těle není známá"
        val uri = prefs.treeUri ?: return "Není vybraná složka v Pixelu"
        val tmp = File(getApplication<Application>().cacheDir, "backup.apk")
        try {
            if (!AdbOps.pullApk(host, codePath, tmp)) return "Tělo APK nevydalo (shell ho nepřečte)"
            val l = SafLocalFiles(getApplication(), android.net.Uri.parse(uri), "APK-zalohy")
            val path = l.targetPath(PmParse.safeApkName("${app.pkg}_${app.versionName ?: "x"}"))
            // appendFrom připisuje na konec: starý soubor musí být prokazatelně pryč
            if (l.exists(path) && !l.delete(path)) return "Starou zálohu nejde přepsat"
            tmp.inputStream().use { l.appendFrom(path, it) }
            if (l.size(path) != tmp.length()) { l.delete(path); return "Záloha se nezapsala celá" }
            return "Záloha uložena: ${l.dirName}/${path.substringAfterLast('#')}"
        } finally {
            tmp.delete()
        }
    }

    /** Soubory, které aplikace v těle píše do kořene karty (názvy 8.3). */
    fun cardFilesOf(pkg: String): List<String> = when (pkg) {
        "com.nicky.focusproject" -> listOf("TRAPFOC.LOG", "SHARP.CSV")
        AdbOps.AGENT_PKG -> listOf("RINCEAGT.LOG")
        AdbOps.TWEAK_PKG -> listOf("TWEAKLOG.TXT")
        else -> emptyList()
    }

    /** Stáhne soubory aplikace z karty přes Agenta do složky `logy`; název dostane časovou značku. */
    fun fetchCardFiles(pkg: String) = bodyOp("Stahování logů") { _ ->
        val c = client() ?: return@bodyOp "Agent neběží, logy z karty nejdou stáhnout"
        val uri = prefs.treeUri ?: return@bodyOp "Není vybraná složka v Pixelu"
        val l = SafLocalFiles(getApplication(), android.net.Uri.parse(uri), "logy")
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(java.util.Date())
        var n = 0
        for (name in cardFilesOf(pkg)) {
            val got = runCatching {
                c.openFile(name).use { fs ->
                    val path = l.targetPath("${stamp}_$name")
                    if (l.exists(path) && !l.delete(path)) throw java.io.IOException("starý soubor nejde přepsat")
                    l.appendFrom(path, fs.input)
                }
            }.isSuccess
            if (got) n++
        }
        if (n == 0) "Na kartě žádný z těch souborů není" else "Staženo do ${l.dirName}: ${Format.plural(n, "soubor", "soubory", "souborů")}"
    }

    // --- Instalace APK: soubor / Sdílet / odkaz / GitHub ---
    private val _staged = MutableStateFlow<List<StagedApk>>(emptyList())
    val staged: StateFlow<List<StagedApk>> get() = _staged

    /** Průběh stahování z odkazu: 0..1, -1 = délka neznámá, null = nestahuje se. */
    private val _download = MutableStateFlow<Float?>(null)
    val download: StateFlow<Float?> get() = _download

    /** Požadavek na přepnutí karty (index) z intentu Sdílet / Otevřít v. */
    private val _goToTab = MutableStateFlow<Int?>(null)
    val goToTab: StateFlow<Int?> get() = _goToTab
    fun tabShown() { _goToTab.value = null }

    private fun addStaged(a: StagedApk) = _staged.update { l -> l.filter { it.file != a.file } + a }
    fun dropStaged(a: StagedApk) { a.file.delete(); _staged.update { it - a } }

    /** Odkaz ze Sdílet čeká na potvrzení v dialogu „APK z odkazu“ (nic se nestahuje bez klepnutí). */
    private val _urlOffer = MutableStateFlow<String?>(null)
    val urlOffer: StateFlow<String?> get() = _urlOffer
    fun urlOfferShown() { _urlOffer.value = null }

    /** Jen `content://` (SAF, Sdílet): cizí intent nesmí přes `file://` číst soukromé soubory appky. */
    fun stageUris(all: List<android.net.Uri>) {
        val uris = all.filter { it.scheme == "content" }
        if (uris.isEmpty()) return
        _goToTab.value = Tab.APKY.ordinal
        viewModelScope.launch(Dispatchers.IO) {
            for (u in uris) runCatching { ApkStage.fromUri(getApplication(), u) }
                .onSuccess(::addStaged).onFailure { say("${it.message}") }
        }
    }

    fun stageUrl(url: String) {
        if (_download.value != null) { say("Počkej, jedno stahování už běží"); return }
        _goToTab.value = Tab.APKY.ordinal
        _download.value = -1f
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ApkStage.fromUrl(getApplication(), url) { _download.value = it } }
                .onSuccess(::addStaged).onFailure { say("Stažení selhalo: ${it.message}") }
            _download.value = null
        }
    }

    /** Sdílet / Otevřít v: APK jako soubor, nebo text s odkazem https. */
    fun handleIntent(intent: android.content.Intent?) {
        intent ?: return
        when (intent.action) {
            android.content.Intent.ACTION_VIEW -> intent.data?.let { stageUris(listOf(it)) }
            android.content.Intent.ACTION_SEND -> {
                val stream = intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                val text = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
                when {
                    stream != null -> stageUris(listOf(stream))
                    text != null -> {
                        val url = PmParse.firstHttpsUrl(text)
                        if (url == null) say("Ve sdíleném textu není odkaz https")
                        else { _urlOffer.value = url; _goToTab.value = Tab.APKY.ordinal }
                    }
                }
            }
            android.content.Intent.ACTION_SEND_MULTIPLE ->
                stageUris(intent.getParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java) ?: emptyList())
        }
    }

    /** Instalace připraveného APK do těla; volitelně nejdřív záloha verze, která v těle je. */
    fun installStaged(a: StagedApk, installed: BodyApp?, backupFirst: Boolean) = bodyOp("Instalace ${a.label}") { h ->
        if (backupFirst && installed != null) {
            val b = backupApk(h, installed)
            if (!b.startsWith("Záloha uložena")) return@bodyOp "Instalace zrušena, záloha se nepovedla: $b"
        }
        val (ok, txt) = AdbOps.install(h, a.file)
        if (!ok) return@bodyOp "Instalace ${a.label}: $txt"
        dropStaged(a)
        if (a.pkg == AdbOps.AGENT_PKG) { AdbOps.startAgent(h, true); kotlinx.coroutines.delay(2500); refresh() }
        "${a.label} ${a.versionName ?: ""} nainstalováno"
    }

    private val _repos = MutableStateFlow(prefs.ghRepos)
    val repos: StateFlow<List<String>> get() = _repos

    fun addRepo(input: String): Boolean {
        val r = PmParse.githubRepo(input) ?: return false
        prefs.ghRepos = prefs.ghRepos + r
        _repos.value = prefs.ghRepos
        return true
    }

    fun removeRepo(repo: String) { prefs.ghRepos = prefs.ghRepos - repo; _repos.value = prefs.ghRepos }

    /** Vydání s APK; chyba se vrací jako text pro řádek repa. */
    suspend fun releases(repo: String): Result<List<GhRelease>> = withContext(Dispatchers.IO) { runCatching { ApkStage.releases(repo) } }
}
