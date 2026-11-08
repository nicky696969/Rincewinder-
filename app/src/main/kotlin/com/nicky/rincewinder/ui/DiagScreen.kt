package com.nicky.rincewinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nicky.rincewinder.core.BodyLink
import com.nicky.rincewinder.data.SafLocalFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Diagnostika jako karty (UX_WIREFRAMES §7): vše z `GET /status`; logy z karty přes Agent. Logcat = F2. */
@Composable
fun DiagScreen(vm: AppViewModel, modifier: Modifier) {
    val body by vm.body.collectAsState()
    val st = body.status
    val raw = st?.raw ?: emptyMap()
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Diagnostika", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("BATERIE TĚLA", "${st?.batteryPct ?: "—"} %", "${raw["batteryMv"]?.let { "${(it as Number).toInt() / 1000f} V" } ?: "—"} · ${raw["batteryC"] ?: "—"} °C", Modifier.weight(1f))
            Tile("KARTA", st?.cardFree?.let { Format.bytes(it) } ?: "—", "volných z ${st?.cardTotal?.let { Format.bytes(it) } ?: "—"}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("WI‑FI", "${st?.wifiDbm ?: "—"} dBm", "${raw["wifiLinkMbps"] ?: "—"} Mb/s · ${body.host ?: ""}", Modifier.weight(1f))
            val adb = body.link == BodyLink.VSE_OK || body.link == BodyLink.AGENT_NEBEZI || body.link == BodyLink.AGENT_CHYBI
            Tile("TWEAK / AGENT", "${(raw["tweakVersion"] as? String)?.substringAfterLast(' ') ?: "—"} / ${st?.agentVersion ?: "—"}",
                "ADB ${if (adb) "✓" else "✗"} · HTTP ${if (st != null) "✓" else "✗"}", Modifier.weight(1f))
        }
        Section("RYCHLOST") { Text("poslední ${if (vm.prefs.lastMbps > 0) Format.mbps(vm.prefs.lastMbps) else "neměřeno"}") }
        Section("LOGY Z KARTY") {
            Text("Uloží se do složky přenosu na Pixelu.", color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("RINCEAGT.LOG", "TRAPFOC.LOG", "SHARP.CSV").forEach { name ->
                    OutlinedButton(onClick = {
                        scope.launch {
                            val c = vm.client() ?: return@launch vm.say("Agent neběží")
                            val tree = vm.prefs.treeUri ?: return@launch vm.say("Není složka")
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    val l = SafLocalFiles(vm.getApplication<android.app.Application>(), android.net.Uri.parse(tree))
                                    val p = l.partPath(name)
                                    l.delete(p)
                                    c.openFile(name).use { fs -> l.appendFrom(p, fs.input) }
                                    l.finish(p, l.targetPath(name))
                                }.onSuccess { vm.say("$name staženo") }.onFailure { vm.say("$name: ${it.message}") }
                            }
                        }
                    }, enabled = st != null) { Text("↓ $name") }
                }
            }
        }
        if (body.link == BodyLink.AGENT_OK_ADB_VYPNUTE) Section("ADB VYPNUTÉ") { Text("Pro logcat zapni v Tweaku Developer → ADB.") }
        Section("LOGCAT") { Text("Přijde ve F2 (přes ADB, předvolby filtru).", color = Color.Gray) }
    }
}

@Composable
private fun Tile(title: String, big: String, sub: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(big, style = MaterialTheme.typography.titleLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
