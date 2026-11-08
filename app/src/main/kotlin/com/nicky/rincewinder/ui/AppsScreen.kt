package com.nicky.rincewinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nicky.rincewinder.body.AdbOps
import com.nicky.rincewinder.core.BodyLink

/** Apky na těle (UX_WIREFRAMES §6) — v M4 jen seznam přes ADB a Agent (instalace/spuštění). Vlastní APK a GitHub = F2. */
@Composable
fun AppsScreen(vm: AppViewModel, modifier: Modifier) {
    val body by vm.body.collectAsState()
    val adb = body.link == BodyLink.VSE_OK || body.link == BodyLink.AGENT_NEBEZI || body.link == BodyLink.AGENT_CHYBI
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Apky na těle", style = MaterialTheme.typography.headlineSmall)
        if (!adb) {
            Section("ADB VYPNUTÉ") {
                Text("Pro instalaci a správu aplikací zapni v Tweaku: Developer → ADB. Přenos RAWů to nepotřebuje.")
            }
            return@Column
        }
        val apps by produceState<List<Pair<String, String?>>?>(null, body.host, body.status?.agentVersion) { value = vm.listApps() }
        Section("NAINSTALOVANÉ") {
            if (apps == null) Text("Načítám přes ADB…")
            apps?.forEach { (pkg, ver) ->
                val agent = pkg == AdbOps.AGENT_PKG
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (agent) "Rincewinder Agent" else pkg.substringAfterLast('.'), style = MaterialTheme.typography.bodyLarge)
                    Text(ver ?: "", color = Color.Gray)
                }
                Text(pkg, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        Section("RINCEWINDER AGENT") {
            val running = body.status != null
            Text(if (running) "běží ${body.status?.agentVersion}" else "neběží")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.startAgentFromPhone() }, enabled = !running) { Text("Spustit z telefonu") }
                Button(onClick = { vm.installBundledAgent() }, enabled = vm.hasBundledAgent) { Text(if (body.agentInstalled == false) "Nainstalovat" else "Přeinstalovat 0.2.0") }
            }
            if (running) Text("Přeinstalace běžícího Agenta přeruší přenos.", color = Palette.Warn)
        }
        Section("INSTALOVAT VLASTNÍ APK") { Text("Z úložiště Pixelu a z GitHub Releases — přijde ve F2.", color = Color.Gray) }
    }
}
