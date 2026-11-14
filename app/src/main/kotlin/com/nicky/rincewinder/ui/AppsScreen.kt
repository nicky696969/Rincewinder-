package com.nicky.rincewinder.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nicky.rincewinder.body.AdbOps
import com.nicky.rincewinder.body.ApkStage
import com.nicky.rincewinder.body.GhRelease
import com.nicky.rincewinder.body.StagedApk
import com.nicky.rincewinder.core.BodyApp
import com.nicky.rincewinder.core.BodyLink

private const val APK_MIME = "application/vnd.android.package-archive"

/**
 * Apky na těle — plochý styl (beton jen na Domů a v mřížce). Seznam, detail a akce přes ADB;
 * instalace ze souboru, Sdílet, odkazu a GitHub Releases. Bez ADB jde APK jen připravit.
 */
@Composable
fun AppsScreen(vm: AppViewModel, modifier: Modifier) {
    val body by vm.body.collectAsState()
    val busy by vm.busy.collectAsState()
    val staged by vm.staged.collectAsState()
    val download by vm.download.collectAsState()
    val version by vm.appsVersion.collectAsState()
    val adb = body.link == BodyLink.VSE_OK || body.link == BodyLink.AGENT_NEBEZI || body.link == BodyLink.AGENT_CHYBI
    val appsRes by produceState<Result<List<Pair<BodyApp, Boolean>>>?>(null, body.host, adb, version) { value = if (adb) vm.bodyApps() else null }
    val apps = appsRes?.getOrNull()
    val urlOffer by vm.urlOffer.collectAsState()

    var detailPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var installAsk by remember { mutableStateOf<StagedApk?>(null) }
    var urlAsk by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.stageUris(it) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header()
        Text("Apky na těle", color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Light)
        busy?.let { Text("$it…", color = Palette.SodiumLight, fontSize = 13.sp) }

        if (!adb) {
            Section("ADB vypnuté") {
                Text("Pro instalaci a správu aplikací zapni v Tweaku Developer → ADB. Přenos RAWů to nepotřebuje.", color = Palette.Text, fontSize = 14.sp)
            }
        } else {
            Section("Nainstalované") {
                when {
                    appsRes == null -> Text("Načítám přes ADB…", color = Palette.Mute)
                    apps == null -> {
                        Text("ADB neodpovídá: ${appsRes?.exceptionOrNull()?.message ?: "chyba"}", color = Palette.Sodium, fontSize = 13.sp)
                        GhostButton("Zkusit znovu", { vm.reloadApps() }, color = Palette.ColdText)
                    }
                }
                apps?.forEach { (app, running) ->
                    Column(Modifier.fillMaxWidth().clickable { detailPkg = app.pkg }.padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(Format.appName(app.pkg), color = Palette.Text, fontSize = 15.sp)
                            Text(listOfNotNull(app.versionName, if (running) "běží" else null).joinToString(" · "), color = if (running) Palette.Teal else Palette.SodiumLight, fontSize = 13.sp)
                        }
                        Text(app.pkg, color = Palette.Mute, fontSize = 11.sp)
                    }
                }
            }
            Section("Rincewinder Agent") {
                val running = body.status != null
                Text(if (running) "běží ${body.status?.agentVersion}" else "neběží", color = if (running) Palette.Teal else Palette.Sodium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Spustit z telefonu", { vm.startAgentFromPhone() }, Modifier.weight(1f), enabled = !running, color = Palette.ColdText)
                    GhostButton(if (body.agentInstalled == false) "Nainstalovat" else "Přeinstalovat přibalený", { vm.installBundledAgent() }, Modifier.weight(1f), enabled = vm.hasBundledAgent && busy == null)
                }
                if (running) Text("Přeinstalace běžícího Agenta přeruší přenos.", color = Palette.Sodium, fontSize = 12.sp)
            }
        }

        Section("Instalovat APK") {
            Text("Soubor z Pixelu, odkaz, nebo Sdílet / Otevřít v z jiné aplikace. Jakékoli APK pro Android 4.1 (API 16) a starší; tělo nemá dotyk.", color = Palette.Mute, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Ze souboru", { picker.launch(arrayOf(APK_MIME, "application/octet-stream")) }, Modifier.weight(1f))
                GhostButton("Z odkazu", { urlAsk = true }, Modifier.weight(1f), enabled = download == null, color = Palette.ColdText)
            }
            download?.let { p -> Text(if (p >= 0f) "Stahuji… ${(p * 100).toInt()} %" else "Stahuji…", color = Palette.SodiumLight, fontSize = 13.sp) }
            staged.forEach { a ->
                val inst = apps?.firstOrNull { it.first.pkg == a.pkg }?.first
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${a.label} ${a.versionName ?: ""}", color = Palette.Text, fontSize = 15.sp)
                    Text("${a.pkg} · ${Format.bytes(a.file.length())} · ${a.source}", color = Palette.Mute, fontSize = 11.sp)
                    if (adb && apps != null) Text(if (inst != null) "v těle je ${inst.versionName ?: "?"} → nahradí se" else "v těle zatím není", color = Palette.ColdText, fontSize = 12.sp)
                    if (a.tooNew) Text("Chce Android API ${a.minSdk}, tělo má ${StagedApk.BODY_SDK}. Instalace nejspíš selže.", color = Palette.Red, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Instalovat", { installAsk = a }, Modifier.weight(1f), enabled = adb && busy == null)
                        GhostButton("Zahodit", { vm.dropStaged(a) }, Modifier.weight(1f), color = Palette.ColdText)
                    }
                }
            }
        }

        GitHubSection(vm, download == null)
    }

    val detail = apps?.firstOrNull { it.first.pkg == detailPkg }
    if (detail != null) AppDetailSheet(vm, detail.first, detail.second, busy == null) { detailPkg = null }

    installAsk?.let { a ->
        val inst = apps?.firstOrNull { it.first.pkg == a.pkg }?.first
        InstallDialog(a, inst, agentRunning = body.status != null, onClose = { installAsk = null }) { backup -> vm.installStaged(a, inst, backup); installAsk = null }
    }

    if (urlAsk || urlOffer != null) {
        var url by remember(urlOffer) { mutableStateOf(urlOffer ?: "") }
        val close = { urlAsk = false; vm.urlOfferShown() }
        AlertDialog(
            onDismissRequest = close,
            containerColor = Palette.Flat, shape = Chamfer,
            title = { Text("APK z odkazu", fontWeight = FontWeight.Light) },
            text = { OutlinedTextField(url, { url = it }, label = { Text("https://…") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.stageUrl(url); close() }, enabled = url.trim().startsWith("https://") && download == null) { Text("Stáhnout") } },
            dismissButton = { TextButton(onClick = close) { Text("Zrušit", color = Palette.ColdText) } },
        )
    }
}

/** Potvrzení instalace: co se nahradí, volitelná záloha stávající verze, varování u Agenta. */
@Composable
private fun InstallDialog(a: StagedApk, installed: BodyApp?, agentRunning: Boolean, onClose: () -> Unit, onConfirm: (backup: Boolean) -> Unit) {
    var backup by remember { mutableStateOf(installed != null) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Palette.Flat, shape = Chamfer,
        title = { Text("Instalovat do těla?", fontWeight = FontWeight.Light) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${a.label} ${a.versionName ?: ""}", color = Palette.Text)
                Text(a.pkg ?: "", color = Palette.Mute, fontSize = 12.sp)
                if (installed != null) {
                    Text("Nahradí verzi ${installed.versionName ?: "?"}; nastavení aplikace zůstane.", color = Palette.ColdText, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { backup = !backup }) {
                        Checkbox(backup, { backup = it })
                        Text("Nejdřív zálohovat verzi z těla do Pixelu", color = Palette.Text, fontSize = 13.sp)
                    }
                }
                if (a.pkg == AdbOps.AGENT_PKG && agentRunning) Text("Agent běží: instalace ho ukončí a přeruší přenos.", color = Palette.Sodium, fontSize = 12.sp)
                if (a.tooNew) Text("APK chce novější Android než tělo. Instalace nejspíš selže.", color = Palette.Red, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(backup && installed != null) }) { Text("Instalovat") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Zrušit", color = Palette.ColdText) } },
    )
}

/** Detail aplikace v těle jako spodní list: údaje z `dumpsys package` a akce. Nevratné akce podržením 1,5 s. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailSheet(vm: AppViewModel, app: BodyApp, running: Boolean, idle: Boolean, onClose: () -> Unit) {
    val size by produceState<Long?>(null, app.codePath) { value = app.codePath?.let { vm.apkSize(it) } }
    val cardFiles = remember(app.pkg) { vm.cardFilesOf(app.pkg) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Palette.Flat, shape = RectangleShape) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(Format.appName(app.pkg), color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.Light)
                Text(if (running) "běží" else "neběží", color = if (running) Palette.Teal else Palette.Mute, fontSize = 12.sp)
            }
            Info("balíček", app.pkg)
            Info("verze", listOfNotNull(app.versionName, app.versionCode?.let { "kód $it" }).joinToString(" · ").ifEmpty { "?" })
            size?.let { Info("velikost APK", Format.bytes(it)) }
            app.firstInstall?.let { Info("nainstalováno", it) }
            app.lastUpdate?.takeIf { it != app.firstInstall }?.let { Info("aktualizováno", it) }
            app.targetSdk?.let { Info("cílové API", it.toString()) }
            Text("Časy jsou z hodin těla.", color = Palette.Mute, fontSize = 11.sp)

            if (running) GhostButton("Ukončit", { vm.stopApp(app.pkg); onClose() }, enabled = idle, color = Palette.ColdText)
            else GhostButton("Spustit v těle", { vm.launchApp(app.pkg); onClose() }, enabled = idle)
            GhostButton("Zálohovat APK do Pixelu", { vm.backupApp(app); onClose() }, enabled = idle && app.codePath != null, color = Palette.ColdText)
            if (cardFiles.isNotEmpty()) GhostButton("Stáhnout ${cardFiles.joinToString(", ")}", { vm.fetchCardFiles(app.pkg); onClose() }, enabled = idle, color = Palette.ColdText)
            HoldButton("VYMAZAT DATA", { vm.clearApp(app.pkg); onClose() }, enabled = idle, color = Palette.SodiumDeep)
            HoldButton("ODINSTALOVAT", { vm.uninstallApp(app.pkg); onClose() }, enabled = idle)
            Text(
                when (app.pkg) {
                    AdbOps.TWEAK_PKG -> "Pozor: bez Tweaku se do těla přes Wi‑Fi ani ADB nedostaneš. Zpátky ho dostaneš jen z počítače přes USB."
                    AdbOps.AGENT_PKG -> "Bez Agenta nejde stahovat snímky. Znovu ho nainstaluješ z této obrazovky."
                    else -> "Vymazání dat i odinstalace jsou nevratné."
                },
                color = if (app.pkg == AdbOps.TWEAK_PKG) Palette.Red else Palette.Mute, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Palette.Mute, fontSize = 12.sp, modifier = Modifier.weight(0.38f))
        Text(value, color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(0.62f))
    }
}

/** Sledovaná veřejná repa: vydání s APK se načtou až po rozbalení (limit GitHubu 60 dotazů za hodinu). */
@Composable
private fun GitHubSection(vm: AppViewModel, canDownload: Boolean) {
    val repos by vm.repos.collectAsState()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    Section("GitHub · vydání s APK") {
        repos.forEach { repo ->
            Row(Modifier.fillMaxWidth().clickable { open = if (open == repo) null else repo }.padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(repo, color = Palette.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(if (open == repo) "skrýt" else "vydání", color = Palette.SodiumLight, fontSize = 12.sp)
            }
            if (open == repo) {
                val res by produceState<Result<List<GhRelease>>?>(null, repo) { value = vm.releases(repo) }
                val r = res
                when {
                    r == null -> Text("Načítám…", color = Palette.Mute, fontSize = 12.sp)
                    r.isFailure -> Text(r.exceptionOrNull()?.message ?: "chyba", color = Palette.Sodium, fontSize = 12.sp)
                    r.getOrThrow().isEmpty() -> Text("Žádné vydání s přílohou .apk.", color = Palette.Mute, fontSize = 12.sp)
                    else -> r.getOrThrow().forEach { rel ->
                        Text("${rel.title} · ${rel.published}" + if (rel.prerelease) " · předběžné" else "", color = Palette.ColdText, fontSize = 13.sp)
                        rel.assets.forEach { asset ->
                            GhostButton("${asset.name} · ${Format.bytes(asset.size)}", { vm.stageUrl(asset.url) }, enabled = canDownload)
                        }
                    }
                }
                TextButton(onClick = { vm.removeRepo(repo); open = null }) { Text("Odebrat repo ze seznamu", color = Palette.Mute, fontSize = 12.sp) }
            }
        }
        val known = ApkStage.CATALOG.filter { (repo, _) -> repos.none { it.equals(repo, ignoreCase = true) } }
        if (known.isNotEmpty()) {
            Label("Katalog · aplikace pro Sony", Palette.Mute)
            known.forEach { (repo, what) ->
                Row(Modifier.fillMaxWidth().clickable { vm.addRepo(repo) }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(repo, color = Palette.Text, fontSize = 13.sp)
                        Text(what, color = Palette.Mute, fontSize = 11.sp)
                    }
                    Text("přidat", color = Palette.SodiumLight, fontSize = 12.sp)
                }
            }
        }
        OutlinedTextField(input, { input = it }, label = { Text("owner/repo nebo adresa GitHubu") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        GhostButton("Přidat repo", { if (vm.addRepo(input)) input = "" else vm.say("Tohle není repo GitHubu") }, enabled = input.isNotBlank(), color = Palette.ColdText)
    }
}
