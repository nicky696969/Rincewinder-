package com.nicky.rincewinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nicky.rincewinder.core.BodyLink
import com.nicky.rincewinder.data.SafLocalFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Diagnostika jako karty: vše z `GET /status`; logy z karty přes Agent. Plochý styl. */
@Composable
fun DiagScreen(vm: AppViewModel, modifier: Modifier) {
    val body by vm.body.collectAsState()
    val st = body.status
    val raw = st?.raw ?: emptyMap()
    val scope = rememberCoroutineScope()
    val adb = body.link == BodyLink.VSE_OK || body.link == BodyLink.AGENT_NEBEZI || body.link == BodyLink.AGENT_CHYBI
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header()
        Text("Diagnostika", color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Light)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Baterie těla", "${st?.batteryPct ?: "—"} %", "${raw["batteryMv"]?.let { "${(it as Number).toInt() / 1000f} V" } ?: "—"} · ${raw["batteryC"] ?: "—"} °C", Palette.SodiumLight, Modifier.weight(1f))
            Tile("Karta", st?.cardFree?.let { Format.bytes(it) } ?: "—", "volných z ${st?.cardTotal?.let { Format.bytes(it) } ?: "—"}", Palette.SodiumLight, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Wi‑Fi", "${st?.wifiDbm ?: "—"} dBm", "${raw["wifiLinkMbps"] ?: "—"} Mb/s · ${body.host ?: ""}", Palette.ColdText, Modifier.weight(1f))
            Tile("Tweak / Agent", "${(raw["tweakVersion"] as? String)?.substringAfterLast(' ') ?: "—"} / ${st?.agentVersion ?: "—"}",
                "ADB ${if (adb) "✓" else "✗"} · HTTP ${if (st != null) "✓" else "✗"}", Palette.ColdText, Modifier.weight(1f))
        }
        Section("Rychlost") { Text("poslední ${if (vm.prefs.lastMbps > 0) Format.mbps(vm.prefs.lastMbps) else "neměřeno"}", color = Palette.Text) }
        Section("Logy z karty") {
            Text("Uloží se do složky přenosu na Pixelu.", color = Palette.Mute, fontSize = 12.sp)
            listOf("RINCEAGT.LOG", "TRAPFOC.LOG", "SHARP.CSV").forEach { name ->
                GhostButton("↓ $name", {
                    scope.launch {
                        val c = vm.client() ?: return@launch vm.say("Agent neběží")
                        val tree = vm.prefs.treeUri ?: return@launch vm.say("Není vybraná složka")
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
                }, enabled = st != null, color = Palette.ColdText)
            }
        }
        if (body.link == BodyLink.AGENT_OK_ADB_VYPNUTE) Section("ADB vypnuté") { Text("Pro logcat zapni v Tweaku Developer → ADB.", color = Palette.Text) }
        Section("Logcat", dim = true) { Text("Přes ADB s předvolbami filtru — připravuje se.", color = Palette.Mute, fontSize = 13.sp) }
    }
}

@Composable
private fun Tile(title: String, big: String, sub: String, accent: Color, modifier: Modifier) {
    Column(modifier.background(Palette.Flat).border(1.dp, Palette.FlatBorder).padding(12.dp)) {
        Label(title, accent.copy(alpha = 0.8f))
        Text(big, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Light)
        Text(sub, color = Palette.Mute, fontSize = 11.sp)
    }
}
