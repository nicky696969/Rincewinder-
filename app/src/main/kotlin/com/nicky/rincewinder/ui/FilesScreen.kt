package com.nicky.rincewinder.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.nicky.rincewinder.core.FileRecord
import com.nicky.rincewinder.core.FileState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Filter(val label: String) { NOVE("Nové"), STAZENE("Stažené"), VSE("Vše") }

/** Soubory — mřížka náhledů (UX_WIREFRAMES §3) + mazání s podržením (§4). */
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
    val scope = rememberCoroutineScope()

    val shown = records.filter {
        when (filter) { Filter.NOVE -> it.isNew; Filter.STAZENE -> it.isDownloaded; Filter.VSE -> it.state != FileState.NEZNAMY }
    }.let { l -> if (byName) l.sortedBy { it.key.name } else l.sortedByDescending { it.key.mtime } }
    val sel = shown.filter { it.key.name in selected.value }
    val total = records.filter { it.state != FileState.NEZNAMY }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Soubory na kartě  ${total.size} · ${Format.bytes(total.sumOf { it.key.size })}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Filter.entries.forEach { f ->
                    val n = if (f == Filter.NOVE) records.count { it.isNew } else null
                    FilterChip(selected = filter == f, onClick = { filter = f; selected.value = emptySet() }, label = { Text(f.label + (n?.let { " $it" } ?: "")) })
                }
                FilterChip(selected = byName, onClick = { byName = !byName }, label = { Text(if (byName) "⇅ název" else "⇅ čas") })
            }
            Text("legenda: ○ karta · ◔ stahuje · ✓ staženo · ✓✓ ověřeno · ! chyba", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }

        LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

        if (shown.isNotEmpty()) Text("Klepnutí = náhled · podržení = výběr", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Column(Modifier.padding(16.dp)) {
            if (filter == Filter.STAZENE) {
                val n = sel.count { it.isDeletable }
                Button(onClick = { deleteDialog = true }, Modifier.fillMaxWidth().height(64.dp), enabled = n > 0 && body.status != null) {
                    Text("SMAZAT Z KARTY ($n ověřených)")
                }
            } else {
                val n = sel.count { it.isNew }
                Button(onClick = { vm.downloadSelected(sel.filter { it.isNew }.map { it.key }); selected.value = emptySet() },
                    Modifier.fillMaxWidth().height(64.dp), enabled = n > 0 && body.status != null && !transfer.running) {
                    Text("STÁHNOUT VYBRANÉ ($n · ${Format.bytes(sel.filter { it.isNew }.sumOf { it.key.size - it.bytesDone })})")
                }
            }
        }
    }

    preview?.let { rec -> PreviewDialog(vm, rec, onClose = { preview = null }) }
    if (deleteDialog) DeleteDialog(vm, sel, onClose = { ok -> deleteDialog = false; if (ok) selected.value = emptySet() })
}

@Composable
private fun Tile(rec: FileRecord, bmp: Bitmap?, selected: Boolean, active: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val dim = rec.state == FileState.SMAZANO_Z_KARTY
    Box(
        Modifier.aspectRatio(1f)
            .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .background(Palette.Card)
            .pointerInput(rec.key) { detectTapGestures(onTap = { onOpen() }, onLongPress = { onToggle() }) }
            .alpha(if (dim) 0.45f else 1f),
    ) {
        if (bmp != null) Image(bmp.asImageBitmap(), rec.key.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // odznak vlevo nahoře = výběr; vpravo nahoře = stav (UX_NAVRH, tabulka stavů)
        Text(if (selected) "◉" else "○", Modifier.align(Alignment.TopStart).padding(4.dp).combinedClickable(onClick = onToggle), color = Color.White)
        val (badge, color) = when (rec.state) {
            FileState.NA_KARTE, FileState.STARE -> "" to Color.White
            FileState.STAHUJE_SE -> (if (active) "◔ ${(rec.bytesDone * 100 / maxOf(1, rec.key.size))}%" else "◔") to Palette.Orange
            FileState.STAZENO -> "✓" to Color.White
            FileState.OVERENO -> "✓✓" to Palette.Ok
            FileState.SMAZANO_Z_KARTY -> "✓" to Color.Gray
            FileState.CHYBA -> "!" to Palette.Err
            FileState.LOKALNE_CHYBI -> "!" to Palette.Err
            FileState.NEZNAMY -> "?" to Color.Gray
        }
        if (badge.isNotEmpty()) Text(badge, Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 4.dp), color = color)
        Text("${Format.shortName(rec.key.name)} · ${Format.time(rec.key.mtime)}", Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(4.dp),
            style = MaterialTheme.typography.labelSmall, color = Color.White)
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
        title = { Text(rec.key.name) },
        text = {
            Column {
                big?.let { Image(it.asImageBitmap(), rec.key.name, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                Spacer(Modifier.height(8.dp))
                Text("${Format.dateTime(rec.key.mtime)} · ${Format.bytes(rec.key.size)} · ${rec.state}")
                rec.errorReason?.let { Text(it, color = Palette.Err) }
                rec.md5Agent?.let { Text("MD5 $it", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
            }
        },
        confirmButton = {
            when (rec.state) {
                FileState.STAZENO -> TextButton(onClick = { scope.launch { vm.verifyMd5(rec); onClose() } }) { Text("Ověřit MD5") }
                FileState.OVERENO -> Text("ověřeno ✓✓", color = Palette.Ok)
                else -> if (rec.isNew) TextButton(onClick = { vm.downloadSelected(listOf(rec.key)); onClose() }) { Text("Stáhnout") }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Zavřít") } },
    )
}

/** Dialog mazání (§4): jen OVERENO s lokální kopií; potvrzení podržením 1,5 s; žádné Undo. */
@Composable
private fun DeleteDialog(vm: AppViewModel, selected: List<FileRecord>, onClose: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Pair<List<FileRecord>, List<Pair<FileRecord, String>>>?>(null) }
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
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
    AlertDialog(
        onDismissRequest = { if (!working) onClose(false) },
        title = { Text("Smazat z karty ${Format.plural(ok.size, "soubor", "soubory", "souborů")}?") },
        text = {
            Column {
                if (preview == null) Text("Kontroluji lokální kopie…")
                else {
                    Text("${Format.bytes(ok.sumOf { it.key.size })} · ${ok.size} staženo a ověřeno podle MD5 na Pixelu.")
                    skipped.forEach { (r, why) -> Text("${Format.shortName(r.key.name)}: $why — vynechán", color = Palette.Warn) }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(56.dp).background(Palette.Divider)
                        .pointerInput(ok) { detectTapGestures(onPress = { holding = true; tryAwaitRelease(); holding = false }) }) {
                        LinearProgressIndicator(progress = { progress }, Modifier.fillMaxSize(), color = Palette.Err)
                        Text(if (working) "Mažu…" else "Podrž 1,5 s", Modifier.align(Alignment.Center), color = Color.White)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { OutlinedButton(onClick = { onClose(false) }, enabled = !working) { Text("Zrušit") } },
    )
}
