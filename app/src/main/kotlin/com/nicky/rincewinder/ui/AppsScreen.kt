package com.nicky.rincewinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nicky.rincewinder.core.BodyLink

/** Apky na těle — plochý styl (beton jen na Domů a v mřížce). Seznam přes ADB, Agent instalace/spuštění. */
@Composable
fun AppsScreen(vm: AppViewModel, modifier: Modifier) {
    val body by vm.body.collectAsState()
    val adb = body.link == BodyLink.VSE_OK || body.link == BodyLink.AGENT_NEBEZI || body.link == BodyLink.AGENT_CHYBI
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header()
        Text("Apky na těle", color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Light)
        if (!adb) {
            Section("ADB vypnuté") {
                Text("Pro instalaci a správu aplikací zapni v Tweaku Developer → ADB. Přenos RAWů to nepotřebuje.", color = Palette.Text, fontSize = 14.sp)
            }
            return@Column
        }
        val apps by produceState<List<Pair<String, String?>>?>(null, body.host, body.status?.agentVersion) { value = vm.listApps() }
        Section("Nainstalované") {
            if (apps == null) Text("Načítám přes ADB…", color = Palette.Mute)
            apps?.forEach { (pkg, ver) ->
                Column(Modifier.padding(vertical = 3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Format.appName(pkg), color = Palette.Text, fontSize = 15.sp)
                        Text(ver ?: "", color = Palette.SodiumLight, fontSize = 13.sp)
                    }
                    Text(pkg, color = Palette.Mute, fontSize = 11.sp)
                }
            }
        }
        Section("Rincewinder Agent") {
            val running = body.status != null
            Text(if (running) "běží ${body.status?.agentVersion}" else "neběží", color = if (running) Palette.Teal else Palette.Sodium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Spustit z telefonu", { vm.startAgentFromPhone() }, Modifier.weight(1f), enabled = !running, color = Palette.ColdText)
                GhostButton(if (body.agentInstalled == false) "Nainstalovat" else "Přeinstalovat 0.2.0", { vm.installBundledAgent() }, Modifier.weight(1f), enabled = vm.hasBundledAgent)
            }
            if (running) Text("Přeinstalace běžícího Agenta přeruší přenos.", color = Palette.Sodium, fontSize = 12.sp)
        }
        Section("Instalovat vlastní APK", dim = true) {
            Text("Z úložiště Pixelu a z GitHub Releases — připravuje se.", color = Palette.Mute, fontSize = 13.sp)
        }
    }
}
