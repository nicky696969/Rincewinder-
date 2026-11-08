package com.nicky.rincewinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nicky.rincewinder.core.BodyLink
import com.nicky.rincewinder.core.FirstPairing
import com.nicky.rincewinder.core.Thresholds
import com.nicky.rincewinder.core.newSummary
import java.util.Calendar

/** Domů — „Dvě světla · beton“: 01 Tělo (modrá hodina), 02 Nové snímky (sodík), konzola jako hlavní akce. */
@Composable
fun HomeScreen(vm: AppViewModel, modifier: Modifier, onPickManually: () -> Unit, onScanQr: () -> Unit, onOpenSettings: () -> Unit) {
    val body by vm.body.collectAsState()
    val records by vm.records.collectAsState()
    val transfer by vm.transfer.collectAsState()
    val ask by vm.firstPairingAsk.collectAsState()
    val st = body.status
    val agentUp = st != null
    val summary = records.newSummary()
    val todayStart = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val todayNew = records.count { it.isNew && it.key.mtime >= todayStart }

    ask?.let { (n, bytes) ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = Palette.Flat, shape = Chamfer,
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

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header()

        // 01 · TĚLO (studené světlo)
        val (lamp, sentence) = when {
            body.permissionMissing -> Palette.Red to "Bez místní sítě tělo nenajdu."
            body.searching && body.host == null -> Palette.Sodium to "Hledám tělo…"
            else -> when (body.link) {
                BodyLink.NIC_NEODPOVIDA -> Palette.Red to "Tělo spí."
                BodyLink.AGENT_NEBEZI -> Palette.Sodium to "Tělo je vzhůru, Agent spí."
                BodyLink.AGENT_CHYBI -> Palette.Sodium to "Agent v těle chybí."
                BodyLink.AGENT_OK_ADB_VYPNUTE, BodyLink.VSE_OK -> Palette.Teal to "A7 II je blízko."
            }
        }
        BetonCard("01", Palette.ColdText, ties = true) {
            Label("Tělo")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Lamp(lamp); Spacer(Modifier.width(8.dp))
                Text(sentence, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Light)
            }
            if (st != null) {
                Text(
                    "Agent ${st.agentVersion ?: "?"} · ${st.wifiDbm?.let { "$it dBm" } ?: "—"} · ${st.batteryPct ?: "—"} % · ${st.cardFree?.let { Format.bytes(it) } ?: "—"} volných",
                    color = Palette.Mute, fontSize = 12.sp,
                )
                if ((st.raw["clockSynced"] as? Boolean) == false) Text("Hodiny těla nejsou nastavené", color = Palette.Sodium, fontSize = 12.sp)
            } else when {
                body.permissionMissing -> {
                    Text("Android zablokoval přístup k místní síti.", color = Palette.Mute, fontSize = 12.sp)
                    GhostButton("Otevřít nastavení", onOpenSettings, color = Palette.ColdText)
                }
                body.link == BodyLink.NIC_NEODPOVIDA -> {
                    Text("Probuď ho (spoušť napůl), pak MENU → Aplikace → Rincewinder Agent.", color = Palette.Mute, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(if (body.searching) "Hledám…" else "Hledat znovu", { vm.refresh() }, Modifier.weight(1f), enabled = !body.searching, color = Palette.ColdText)
                        GhostButton("Skenovat QR", onScanQr, Modifier.weight(1f), color = Palette.ColdText)
                    }
                }
                body.link == BodyLink.AGENT_NEBEZI -> {
                    Text("Na těle MENU → Aplikace → Rincewinder Agent, nebo:", color = Palette.Mute, fontSize = 12.sp)
                    GhostButton("Spustit z telefonu", { vm.startAgentFromPhone() }, color = Palette.ColdText)
                }
                body.link == BodyLink.AGENT_CHYBI -> {
                    Text("ADB běží, Agent v těle není.", color = Palette.Mute, fontSize = 12.sp)
                    GhostButton("Nainstalovat Agent", { vm.installBundledAgent() }, enabled = vm.hasBundledAgent, color = Palette.ColdText)
                    if (!vm.hasBundledAgent) Text("APK Agenta není přibalené v tomhle buildu.", color = Palette.Sodium, fontSize = 12.sp)
                }
                else -> {}
            }
        }

        Joint()

        // 02 · NOVÉ SNÍMKY (sodík)
        BetonCard("02", Palette.SodiumLight) {
            Label("Nové snímky", Palette.WarmLabel)
            when {
                !agentUp -> Text("Zjistím, až poběží Agent.", color = Palette.Mute, fontSize = 13.sp)
                st?.cardPresent == false -> Text("Karta v těle chybí.", color = Palette.Text)
                ask != null -> Text("Čekám na volbu prvního spárování…", color = Palette.Mute, fontSize = 13.sp)
                records.isEmpty() && !vm.prefs.firstPairingDone -> Text("Načítám kartu…", color = Palette.Mute, fontSize = 13.sp)
                records.isEmpty() -> Text("Na kartě nejsou žádné ARW.", color = Palette.Text)
                summary.count == 0 -> Text("Vše staženo ✓", color = Palette.Teal, fontSize = 17.sp)
                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        LedNumber(summary.count.toString())
                        Spacer(Modifier.width(8.dp))
                        Text(Format.word(summary.count, "nový ARW", "nové ARW", "nových ARW"), color = Palette.Mute, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Text(
                        "${Format.bytes(summary.bytes)} · ${Format.eta(summary.bytes, vm.prefs.lastMbps)}" +
                            (if (vm.prefs.lastMbps > 0) " při ${Format.mbps(vm.prefs.lastMbps)}" else ""),
                        color = Palette.Mute, fontSize = 12.sp,
                    )
                }
            }
            st?.cardFree?.let { if (it < 1L shl 30) Text("Karta téměř plná — smaž ověřené (${records.count { r -> r.isDeletable }})", color = Palette.Sodium, fontSize = 12.sp) }
        }

        if (transfer.running) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ColumnProgress(transfer.doneFiles, transfer.totalFiles, active = transfer.waitingForBody == null)
                Text(
                    transfer.waitingForBody
                        ?: "${transfer.doneFiles + 1} z ${transfer.totalFiles} · ${transfer.currentName ?: ""} · ${Format.mbps(transfer.mbps)} · zbývá ${Format.eta(transfer.bytesTotal - transfer.bytesDone, transfer.mbps)}",
                    color = if (transfer.waitingForBody != null) Palette.Sodium else Palette.Mute, fontSize = 11.sp,
                )
            }
            GhostButton("POZASTAVIT", { vm.pauseTransfer() })
        } else if (agentUp && summary.count > 0) {
            if (Thresholds.tooMuchForWifi(summary)) {
                Text(
                    "Moc na Wi‑Fi (${Format.eta(summary.bytes, vm.prefs.lastMbps.takeIf { it > 0 } ?: 2f)}). Přenos kabelem se připravuje.",
                    color = Palette.Sodium, fontSize = 12.sp,
                )
                CantileverButton(if (todayNew > 0) "STÁHNOUT DNEŠNÍ ($todayNew)" else "DNES NIC NOVÉHO", { vm.downloadToday() }, enabled = todayNew > 0)
                GhostButton("Přesto stáhnout vše (${summary.count})", { vm.downloadNew() })
            } else {
                CantileverButton("STÁHNOUT NOVÉ (${summary.count})", { vm.downloadNew() })
            }
            TextButton(onClick = onPickManually, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Vybrat ručně…", color = Palette.ColdText, letterSpacing = 0.1.em)
            }
        }

        vm.prefs.lastTransferSummary?.let {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label("Poslední přenos", Palette.MuteWarm)
                Text(it, color = Palette.Mute, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** RINCE (LED) · WINDER (sodík) — dvě světla už v názvu. */
@Composable
fun Header() {
    Row(Modifier.padding(top = 6.dp, bottom = 2.dp)) {
        Text("RINCE", style = TextStyle(color = Palette.ColdText, fontSize = 14.sp, fontWeight = FontWeight.Light, letterSpacing = 0.3.em, shadow = Shadow(Palette.LedBlue.copy(alpha = 0.7f), blurRadius = 14f)))
        Text("WINDER", style = TextStyle(color = Palette.SodiumLight, fontSize = 14.sp, fontWeight = FontWeight.Light, letterSpacing = 0.3.em, shadow = Shadow(Palette.Sodium.copy(alpha = 0.7f), blurRadius = 14f)))
    }
}
