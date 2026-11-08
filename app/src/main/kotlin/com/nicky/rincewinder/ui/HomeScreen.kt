package com.nicky.rincewinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nicky.rincewinder.core.BodyLink
import com.nicky.rincewinder.core.FirstPairing
import com.nicky.rincewinder.core.Thresholds
import com.nicky.rincewinder.core.newSummary

/** Domů (UX_WIREFRAMES §1, §2, §10): jedna obrazovka, jedno primární tlačítko. */
@Composable
fun HomeScreen(vm: AppViewModel, modifier: Modifier, onPickManually: () -> Unit, onScanQr: () -> Unit, onOpenSettings: () -> Unit) {
    val body by vm.body.collectAsState()
    val records by vm.records.collectAsState()
    val transfer by vm.transfer.collectAsState()
    val ask by vm.firstPairingAsk.collectAsState()
    val st = body.status
    val agentUp = st != null
    val summary = records.newSummary()

    ask?.let { (n, bytes) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("První spárování") },
            text = { Text("Na kartě je $n ARW (${Format.bytes(bytes)}). Brát jako nové:") },
            confirmButton = { Button(onClick = { vm.answerFirstPairing(FirstPairing.OD_DNESKA) }) { Text("snímky od dneška") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.answerFirstPairing(FirstPairing.VSE) }) { Text("všech $n") }
                    TextButton(onClick = { vm.answerFirstPairing(FirstPairing.NIC) }) { Text("žádné") }
                }
            },
        )
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Rincewinder", style = MaterialTheme.typography.headlineSmall)

        Section("TĚLO") {
            val (dot, title) = when {
                body.permissionMissing -> Palette.Err to "Bez přístupu k místní síti tělo nenajdu"
                body.searching && body.host == null -> Palette.Warn to "Hledám tělo…"
                else -> when (body.link) {
                    BodyLink.NIC_NEODPOVIDA -> Palette.Err to "Tělo nenalezeno"
                    BodyLink.AGENT_NEBEZI -> Palette.Warn to "Tělo nalezeno, Agent neběží"
                    BodyLink.AGENT_CHYBI -> Palette.Warn to "Agent není v těle"
                    BodyLink.AGENT_OK_ADB_VYPNUTE, BodyLink.VSE_OK -> Palette.Ok to "A7 II připojena"
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(12.dp).background(dot, CircleShape)); Spacer(Modifier.size(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (st != null) {
                Text("Agent běží · ${body.host} · ${st.agentVersion ?: "?"}")
                Text("${st.wifiDbm?.let { "$it dBm" } ?: "—"} · bat ${st.batteryPct ?: "—"} % · ${st.cardFree?.let { Format.bytes(it) } ?: "—"} volných",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if ((st.raw["clockSynced"] as? Boolean) == false) Text("Hodiny těla nejsou nastavené", color = Palette.Warn)
            } else when {
                body.permissionMissing -> {
                    Text("Android zablokoval místní síť. Povol ji v nastavení appky.")
                    OutlinedButton(onClick = onOpenSettings) { Text("Otevřít nastavení") }
                }
                body.link == BodyLink.NIC_NEODPOVIDA -> {
                    Text("Na těle: probuď ho (spoušť napůl), pak MENU → Aplikace → Rincewinder Agent.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.refresh() }, enabled = !body.searching) { Text(if (body.searching) "Hledám…" else "Hledat znovu") }
                        TextButton(onClick = onScanQr) { Text("Skenovat QR") }
                    }
                }
                body.link == BodyLink.AGENT_NEBEZI -> {
                    Text("Na těle: MENU → Aplikace → Rincewinder Agent — nebo:")
                    Button(onClick = { vm.startAgentFromPhone() }) { Text("Spustit z telefonu") }
                }
                body.link == BodyLink.AGENT_CHYBI -> {
                    Text("ADB běží, ale Rincewinder Agent v těle chybí.")
                    Button(onClick = { vm.installBundledAgent() }, enabled = vm.hasBundledAgent) { Text("Nainstalovat Agent") }
                    if (!vm.hasBundledAgent) Text("APK Agenta není přibalené v tomhle buildu.", color = Palette.Warn)
                }
                else -> {}
            }
        }

        if (transfer.running) {
            Section("PŘENOS") {
                Text("${transfer.doneFiles} z ${transfer.totalFiles} · ${transfer.currentName ?: ""}")
                val frac = if (transfer.bytesTotal > 0) transfer.bytesDone.toFloat() / transfer.bytesTotal else 0f
                LinearProgressIndicator(progress = { frac }, Modifier.fillMaxWidth())
                Text(transfer.waitingForBody ?: "${Format.mbps(transfer.mbps)} · zbývá ${Format.eta(transfer.bytesTotal - transfer.bytesDone, transfer.mbps)}")
                TextButton(onClick = { vm.pauseTransfer() }) { Text("Pozastavit") }
            }
        }

        Section("NOVÉ SNÍMKY", dim = !agentUp) {
            when {
                !agentUp -> Text("Zjistím, až poběží Agent.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                st?.cardPresent == false -> Text("Karta v těle chybí")
                ask != null -> Text("Čekám na volbu prvního spárování…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                records.isEmpty() && !vm.prefs.firstPairingDone -> Text("Načítám kartu…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                records.isEmpty() -> Text("Na kartě nejsou žádné ARW")
                summary.count == 0 -> Text("Vše staženo ✓")
                else -> {
                    Text("${Format.plural(summary.count, "nový ARW", "nové ARW", "nových ARW")}", style = MaterialTheme.typography.titleLarge)
                    Text("${Format.bytes(summary.bytes)} · ${Format.eta(summary.bytes, vm.prefs.lastMbps)}" +
                        (if (vm.prefs.lastMbps > 0) " při ${Format.mbps(vm.prefs.lastMbps)}" else ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            st?.cardFree?.let { if (it < 1L shl 30) Text("Karta téměř plná — smaž ověřené (${records.count { r -> r.isDeletable }})", color = Palette.Warn) }
        }

        vm.prefs.lastTransferSummary?.let { Section("POSLEDNÍ PŘENOS") { Text(it) } }

        if (agentUp && summary.count > 0 && !transfer.running) {
            if (Thresholds.tooMuchForWifi(summary)) {
                Text("Moc na Wi‑Fi (${Format.eta(summary.bytes, vm.prefs.lastMbps.takeIf { it > 0 } ?: 2f)}) — připoj kabel (zatím F3)", color = Palette.Warn)
                Button(onClick = { vm.downloadToday() }, Modifier.fillMaxWidth().height(64.dp)) { Text("STÁHNOUT JEN DNEŠNÍ") }
                OutlinedButton(onClick = { vm.downloadNew() }, Modifier.fillMaxWidth()) { Text("Přesto stáhnout vše (${summary.count})") }
            } else {
                Button(onClick = { vm.downloadNew() }, Modifier.fillMaxWidth().height(64.dp)) { Text("STÁHNOUT NOVÉ (${summary.count})") }
            }
            TextButton(onClick = onPickManually, Modifier.fillMaxWidth()) { Text("Vybrat ručně…") }
        }
    }
}

@Composable
fun Section(title: String, dim: Boolean = false, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = if (dim) Color.Gray else MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
