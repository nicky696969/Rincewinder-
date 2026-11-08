package com.nicky.rincewinder.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nicky.rincewinder.core.FileRecord
import com.nicky.rincewinder.core.FileState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Filter(val label: String) { NOVE("Nové"), STAZENE("Stažené"), VSE("Vše") }

/** Soubory — mřížka 16:9 (Nicky fotí 1920×1080) se snímky jako zapuštěnými okny v betonu. */
@Composable
fun FilesScreen(vm: AppViewModel, modifier: Modifier) {
    val records by vm.records.collectAsState()
    val body by vm.body.collectAsState()
    val transfer by vm.transfer.collectAsState()
    var filter by rememberSaveable { mutableStateOf(Filter.NOVE) }
    var byName by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<String>()) }
    var preview by remember { mutableStateOf<FileRecord?>(null) }
    var deleteDialog by remember { mutableStateOf(false) }

    val shown = records.filter {
        when (filter) { Filter.NOVE -> it.isNew; Filter.STAZENE -> it.isDownloaded; Filter.VSE -> it.state != FileState.NEZNAMY }
    }.let { l -> if (byName) l.sortedBy { it.key.name } else l.sortedByDescending { it.key.mtime } }
    val sel = shown.filter { it.key.name in selected.value }
    val total = records.filter { it.state != FileState.NEZNAMY }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Header()
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Soubory na kartě", color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.padding(start = 8.dp))
                Text("${total.size} · ${Format.bytes(total.sumOf { it.key.size })}", color = Palette.Mute, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Filter.entries.forEach { f ->
                    val n = if (f == Filter.NOVE) records.count { it.isNew } else null
                    Tab(f.label + (n?.let { " $it" } ?: ""), filter == f) { filter = f; selected.value = emptySet() }
                }
                Tab(if (byName) "⇅ název" else "⇅ čas", false) { byName = !byName }
            }
            Text("○ na kartě · ◔ stahuje se · ✓ staženo · ✓✓ ověřeno · ! chyba", color = Palette.Mute, fontSize = 10.5.sp)
        }

        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.weight(1f).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.key.name + it.key.size }) { rec ->
                val isSel = rec.key.name in selected.value
                val bmp by produceState<Bitmap?>(vm.thumbs.cached(rec.key), rec.key) {
                    if (value == null) value = vm.thumbs.load(rec.key, rec.cardPath) { vm.client() }
                }
                Tile(rec, bmp, isSel, transfer.currentName == rec.key.name,
                    onToggle = { selected.value = if (isSel) selected.value - rec.key.name else selected.value + rec.key.name },
                    onOpen = { preview = rec })
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (shown.isNotEmpty()) Text("Klepnutí = náhled · podržení nebo kroužek = výběr", color = Palette.Mute, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 6.dp))
            if (filter == Filter.STAZENE) {
                val n = sel.count { it.isDeletable }
                CantileverButton(
                    "SMAZAT Z KARTY ($n ${Format.word(n, "ověřený", "ověřené", "ověřených")})", { deleteDialog = true },
                    enabled = n > 0 && body.status != null, color = Palette.Red, onColor = Color(0xFF2A0804), under = Color(0xFF5A1A12),
                )
            } else {
                val newSel = sel.filter { it.isNew }
                CantileverButton(
                    "STÁHNOUT VYBRANÉ (${newSel.size} · ${Format.bytes(newSel.sumOf { it.key.size - it.bytesDone })})",
                    { vm.downloadSelected(newSel.map { it.key }); selected.value = emptySet() },
                    enabled = newSel.isNotEmpty() && body.status != null && !transfer.running,
                )
            }
        }
    }

    preview?.let { rec -> PreviewDialog(vm, rec, onClose = { preview = null }) }
    if (deleteDialog) DeleteDialog(vm, sel, onClose = { ok -> deleteDialog = false; if (ok) selected.value = emptySet() })
}

/** Filtr jako cedule: hranatý obrys, vybraný = sodíková výplň. */
@Composable
private fun Tab(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.background(if (on) Palette.Sodium else Color.Transparent)
            .border(1.dp, if (on) Palette.Sodium else Color(0xFF3A4652))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(text.uppercase(), color = if (on) Palette.OnSodium else Palette.Text, fontSize = 11.sp, letterSpacing = 0.1.em, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun Tile(rec: FileRecord, bmp: Bitmap?, selected: Boolean, active: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val dim = rec.state == FileState.SMAZANO_Z_KARTY
    Box(
        Modifier.aspectRatio(16f / 9f)
            .border(if (selected) 3.dp else 0.dp, if (selected) Palette.Sodium else Color.Transparent)
            .recessed().padding(5.dp)
            .pointerInput(rec.key) { detectTapGestures(onTap = { onOpen() }, onLongPress = { onToggle() }) }
            .alpha(if (dim) 0.45f else 1f),
    ) {
        Box(Modifier.fillMaxSize().background(Palette.ConcreteDark).innerShade()) {
            if (bmp != null) {
                // na výšku focené (po otočení) se nevejdou do 16:9 — celé, s tmavými pruhy po stranách
                val portrait = bmp.height > bmp.width
                Image(bmp.asImageBitmap(), rec.key.name, Modifier.fillMaxSize(), contentScale = if (portrait) ContentScale.Fit else ContentScale.Crop)
            }
        }
        SelectDot(selected, Modifier.align(Alignment.TopStart).padding(5.dp).clickable(onClick = onToggle))
        val (badge, color) = when (rec.state) {
            FileState.NA_KARTE, FileState.STARE -> "" to Palette.Text
            FileState.STAHUJE_SE -> (if (active) "◔ ${(rec.bytesDone * 100 / maxOf(1, rec.key.size))} %" else "◔") to Palette.SodiumLight
            FileState.STAZENO -> "✓" to Palette.Text
            FileState.OVERENO -> "✓✓" to Palette.Teal
            FileState.SMAZANO_Z_KARTY -> "✓" to Palette.Mute
            FileState.CHYBA, FileState.LOKALNE_CHYBI -> "!" to Palette.Red
            FileState.NEZNAMY -> "?" to Palette.Mute
        }
        if (badge.isNotEmpty()) Text(
            badge, Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 5.dp, vertical = 1.dp),
            color = color, fontWeight = FontWeight.Black, fontSize = 12.sp,
        )
        Text(
            "${Format.shortName(rec.key.name)} · ${Format.tileTime(rec.key.mtime)}",
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 3.dp),
            color = Palette.Text, fontSize = 10.sp, letterSpacing = 0.06.em,
        )
    }
}

@Composable
private fun PreviewDialog(vm: AppViewModel, rec: FileRecord, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val big by produceState<Bitmap?>(vm.thumbs.cached(rec.key), rec.key) {
        vm.client()?.let { c -> vm.thumbs.full(rec.key, rec.cardPath, c)?.let { value = it } }
    }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Palette.Flat, shape = Chamfer,
        title = { Text(rec.key.name, fontWeight = FontWeight.Light) },
        text = {
            Column {
                big?.let { Image(it.asImageBitmap(), rec.key.name, Modifier.fillMaxWidth().recessed().padding(4.dp), contentScale = ContentScale.FillWidth) }
                Spacer(Modifier.height(10.dp))
                Text("${Format.dateTime(rec.key.mtime)} · ${Format.bytes(rec.key.size)}", color = Palette.Text)
                Text(Format.stateName(rec.state), color = if (rec.state == FileState.OVERENO) Palette.Teal else Palette.Mute)
                rec.errorReason?.let { Text(it, color = Palette.Red) }
                rec.md5Agent?.let { Text("MD5 $it", fontSize = 10.sp, color = Palette.Mute) }
            }
        },
        confirmButton = {
            when (rec.state) {
                FileState.STAZENO -> TextButton(onClick = { scope.launch { vm.verifyMd5(rec); onClose() } }) { Text("Ověřit MD5") }
                FileState.OVERENO -> Text("ověřeno ✓✓", color = Palette.Teal, modifier = Modifier.padding(12.dp))
                else -> if (rec.isNew) TextButton(onClick = { vm.downloadSelected(listOf(rec.key)); onClose() }) { Text("Stáhnout") }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Zavřít", color = Palette.ColdText) } },
    )
}

/** Mazání: jen ověřené s lokální kopií; potvrzení podržením 1,5 s (červená výplň v betonové spáře); žádné Undo. */
@Composable
private fun DeleteDialog(vm: AppViewModel, selected: List<FileRecord>, onClose: (Boolean) -> Unit) {
    var preview by remember { mutableStateOf<Pair<List<FileRecord>, List<Pair<FileRecord, String>>>?>(null) }
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(selected) { preview = vm.deletePreview(selected) }
    LaunchedEffect(holding) {
        if (holding) {
            val start = System.currentTimeMillis()
            while (holding && progress < 1f) { progress = (System.currentTimeMillis() - start) / 1500f; delay(30) }
            if (holding && progress >= 1f && !working) {
                working = true
                val out = vm.deleteFromCard(preview?.first ?: emptyList())
                if (out != null) vm.say("${Format.plural(out.deleted.size, "soubor smazán", "soubory smazány", "souborů smazáno")} z karty" +
                    (if (out.changedOnCard.isNotEmpty()) ", ${out.changedOnCard.size} změněno na kartě (nesmazáno)" else ""))
                onClose(out != null)
            }
        } else progress = 0f
    }
    val ok = preview?.first ?: emptyList()
    val skipped = preview?.second ?: emptyList()
    val folder = ok.firstNotNullOfOrNull { Format.folderOf(it.localPath) }
    AlertDialog(
        onDismissRequest = { if (!working) onClose(false) },
        containerColor = Palette.Flat, shape = Chamfer,
        title = { Text("Smazat z karty ${Format.plural(ok.size, "soubor", "soubory", "souborů")}?", fontWeight = FontWeight.Light) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (preview == null) Text("Kontroluji kopie na Pixelu…", color = Palette.Mute)
                else {
                    Text("${Format.bytes(ok.sumOf { it.key.size })} · ${Format.word(ok.size, "stažen a ověřen", "staženy a ověřeny", "staženo a ověřeno")} podle MD5", color = Palette.Text)
                    folder?.let { Text("kopie na Pixelu: $it", color = Palette.Mute, fontSize = 12.sp) }
                    skipped.forEach { (r, why) -> Text("${Format.shortName(r.key.name)}: $why — vynechán", color = Palette.Sodium, fontSize = 12.sp) }
                    Text("Nevratné, z karty nejde vrátit.", color = Palette.Red, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).background(Palette.ConcreteDark)
                            .drawBehind { drawRect(Palette.Red, size = Size(size.width * progress.coerceIn(0f, 1f), size.height)) }
                            .border(1.dp, Palette.Red.copy(alpha = 0.6f))
                            .pointerInput(ok) { detectTapGestures(onPress = { holding = true; tryAwaitRelease(); holding = false }) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (working) "MAŽU…" else "PODRŽ 1,5 s", color = Palette.Text, letterSpacing = 0.2.em, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onClose(false) }, enabled = !working) { Text("Zrušit", color = Palette.ColdText) } },
    )
}
