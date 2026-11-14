package com.nicky.rincewinder.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.random.Random

/*
 * Brutalistické detaily „Dvě světla · beton“ — každý vzatý z Nickyho snímků na kartě:
 * beton s otiskem bednění (sila), zkosené rohy a spínací otvory (kolonáda), čísla úrovní jako „18“
 * u vchodu, fotky jako zapuštěná okna (vystupující rámy 1570), postup jako sloupořadí (kolonáda 1548),
 * dilatační spáry (desky parkoviště 1546), hlavní tlačítko jako konzola (lamely 1542).
 */

val Chamfer = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)

/** Lité betonové desky: prkna bednění po 18 dp, kresba dřeva, zrno; [lit] = nahoře modrá, dole sodík. */
fun Modifier.concrete(lit: Boolean = true, ties: Boolean = false): Modifier = drawWithCache {
    val plank = 18.dp.toPx()
    val rnd = Random(7)
    val light = List(140) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
    val dark = List(220) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
    val grainX = generateSequence(rnd.nextFloat() * 20f) { it + 11.dp.toPx() + rnd.nextFloat() * 26.dp.toPx() }.takeWhile { it < size.width }.toList()
    val litBrush = Brush.verticalGradient(0f to Color(0x2E5FB8FF), 0.45f to Color.Transparent, 1f to Color(0x2AFFB34A))
    val tieR = 1.6.dp.toPx()
    val tieStep = 14.5.dp.toPx()
    onDrawBehind {
        drawRect(Palette.Concrete)
        var y = plank
        while (y < size.height) { drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), 1f); y += plank }
        grainX.forEach { x -> drawLine(Color.Black.copy(alpha = 0.07f), Offset(x, 0f), Offset(x, size.height), 1f) }
        drawPoints(light, PointMode.Points, Color.White.copy(alpha = 0.06f), strokeWidth = 1.5f)
        drawPoints(dark, PointMode.Points, Color.Black.copy(alpha = 0.16f), strokeWidth = 1.8f)
        if (lit) drawRect(litBrush)
        if (ties) {
            val cy = 12.dp.toPx()
            for (i in 0 until 4) {
                val c = Offset(size.width - 14.dp.toPx() - i * tieStep, cy)
                drawCircle(Color.White.copy(alpha = 0.10f), tieR + 1.2f, c + Offset(0.6f, 0.6f))
                drawCircle(Color(0xFF0B0C0D), tieR, c)
            }
        }
    }
}

/** Jemné filmové zrno přes celou obrazovku. */
fun Modifier.grain(): Modifier = drawWithCache {
    val rnd = Random(11)
    val pts = List(900) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
    val pts2 = List(900) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
    onDrawWithContent {
        drawContent()
        drawPoints(pts, PointMode.Points, Color.White.copy(alpha = 0.035f), strokeWidth = 2f)
        drawPoints(pts2, PointMode.Points, Color.Black.copy(alpha = 0.06f), strokeWidth = 2f)
    }
}

/** Betonová karta se zkoseným rohem a číslem úrovně vlevo. */
@Composable
fun BetonCard(level: String, levelColor: Color, ties: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Chamfer).concrete(ties = ties).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LevelNumber(level, levelColor)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
    }
}

/** Plochá karta pro Apky a Diagnostiku (beton jen tam, kde je detailem, ne ornamentem). */
@Composable
fun Section(title: String, dim: Boolean = false, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(Palette.Flat).border(1.dp, Palette.FlatBorder, RoundedCornerShape(2.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Label(title, if (dim) Palette.Mute else Palette.ColdLabel)
        content()
    }
}

@Composable
fun Label(text: String, color: Color = Palette.ColdLabel) {
    Text(text.uppercase(), color = color, fontSize = 10.sp, letterSpacing = 0.3.em, fontWeight = FontWeight.Medium)
}

/** Čísla úrovní jako v garážích: těžký grotesk se šablonovými můstky (svislé mezery přes znaky). */
@Composable
fun LevelNumber(text: String, color: Color, size: TextUnit = 32.sp, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
            drawContent()
            val gap = 2.dp.toPx()
            val step = 10.dp.toPx()
            var x = step - gap
            while (x < this.size.width) {
                drawRect(Color.Black, Offset(x, 0f), Size(gap, this.size.height), blendMode = BlendMode.Clear)
                x += step
            }
        },
        color = color, fontSize = size, fontWeight = FontWeight.Black, letterSpacing = (-0.04).em, lineHeight = size,
    )
}

/** LED číslice z metra: zhasnuté segmenty „888“ za rozsvícenými. */
@Composable
fun LedNumber(text: String, color: Color = Palette.SodiumLight, size: TextUnit = 34.sp, modifier: Modifier = Modifier) {
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = size, lineHeight = size)
    Box(modifier) {
        Text(text.map { if (it.isDigit()) '8' else it }.joinToString(""), style = style, color = color.copy(alpha = 0.08f))
        Text(text, style = style.copy(shadow = Shadow(color.copy(alpha = 0.85f), blurRadius = 18f)), color = color)
    }
}

/** LED pásek z kolonády jako dělicí čára. */
@Composable
fun LedStrip(modifier: Modifier = Modifier, alpha: Float = 1f) {
    Box(
        modifier.fillMaxWidth().height(10.dp).drawBehind {
            val cy = size.height / 2
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Palette.LedBlue.copy(alpha = 0.22f * alpha), Color.Transparent)))
            drawLine(Palette.LedWhite.copy(alpha = alpha), Offset(0f, cy), Offset(size.width, cy), 2.dp.toPx(), StrokeCap.Round)
        },
    )
}

/** Dilatační spára jako mezi deskami parkoviště. */
@Composable
fun Joint(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Palette.ConcreteDark))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
    }
}

/** Kontrolka (lampa) stavu: tyrkys = připojeno, jantar = čeká, červená = nic. */
@Composable
fun Lamp(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(16.dp).drawBehind {
            drawCircle(color.copy(alpha = 0.25f), size.minDimension / 2)
            drawCircle(color, 5.dp.toPx())
        },
    )
}

/**
 * Postup jako sloupořadí (kolonáda): hotové soubory = sloupy nasvícené sodíkem zespodu,
 * právě stahovaný = LED modrá, zbytek tmavý beton. Nad 24 souborů se sloupy slučují.
 */
@Composable
fun ColumnProgress(done: Int, total: Int, active: Boolean, modifier: Modifier = Modifier, height: Dp = 30.dp) {
    val cols = total.coerceIn(1, 24)
    val per = ceil(total.coerceAtLeast(1) / cols.toFloat())
    val lit = (done / per).toInt()
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(cols) { i ->
            val state = when { i < lit -> 2; i == lit && active -> 1; else -> 0 }
            Box(
                Modifier.weight(1f).fillMaxHeight().drawBehind {
                    when (state) {
                        2 -> drawRect(Brush.verticalGradient(listOf(Color(0xFF3A332A), Color(0xFFA87A3A), Palette.SodiumLight)))
                        1 -> drawRect(Brush.verticalGradient(listOf(Palette.Concrete, Color(0xFF4A6F80), Palette.LedBlue)))
                        else -> drawRect(Palette.Concrete)
                    }
                    drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, 0f), Offset(0f, size.height), 1f)
                },
            )
        }
    }
}

/** Hlavní tlačítko jako vyložená deska (konzola): ostré hrany, deska pod převisem. */
@Composable
fun CantileverButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Palette.Sodium,
    onColor: Color = Palette.OnSodium,
    under: Color = Palette.SodiumDeep,
) {
    Box(modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Box(Modifier.matchParentSize().offset(y = 6.dp).padding(horizontal = 6.dp).background(if (enabled) under else Palette.ConcreteDark))
        Box(
            Modifier.fillMaxWidth().height(60.dp).background(if (enabled) color else Palette.Concrete).clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = if (enabled) onColor else Palette.Mute, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.22.em, fontSize = 14.sp)
        }
    }
}

/** Vedlejší akce: obrys jako LED pásek, bez výplně. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Palette.SodiumLight) {
    Box(
        modifier.fillMaxWidth().height(48.dp).border(1.dp, if (enabled) color.copy(alpha = 0.6f) else Palette.FlatBorder).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) color else Palette.Mute, letterSpacing = 0.12.em, fontSize = 13.sp)
    }
}

/** Fotka jako zapuštěné okno v betonu: rám se zkosenou hranou a stínem dovnitř (obsah se kreslí přes rám). */
fun Modifier.recessed(): Modifier = background(Palette.Concrete).drawWithContent {
    drawContent()
    val s = 2.dp.toPx()
    drawRect(Color.White.copy(alpha = 0.09f), Offset.Zero, Size(size.width, s))
    drawRect(Color.White.copy(alpha = 0.09f), Offset.Zero, Size(s, size.height))
    drawRect(Color.Black.copy(alpha = 0.55f), Offset(0f, size.height - s), Size(size.width, s))
    drawRect(Color.Black.copy(alpha = 0.55f), Offset(size.width - s, 0f), Size(s, size.height))
}

/** Stín ostění přes horní okraj fotky. */
fun Modifier.innerShade(): Modifier = drawWithContent {
    drawContent()
    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.6f), 0.28f to Color.Transparent, 1f to Color.Transparent))
}

/** Výběrový kroužek 24 dp: prázdný obrys, vybraný = plný jantar s ✓. */
@Composable
fun SelectDot(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(24.dp).clip(CircleShape)
            .background(if (selected) Palette.Sodium else Color.Black.copy(alpha = 0.45f))
            .border(if (selected) 0.dp else 1.5.dp, Palette.Text, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Text("✓", color = Palette.OnSodium, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

/** Stavový proužek těla nad všemi kartami: lampa, baterie, karta, Wi‑Fi. Bez Agenta jen stav spojení. */
@Composable
fun StatusStrip(body: com.nicky.rincewinder.body.BodyState, modifier: Modifier = Modifier) {
    val st = body.status
    val (lamp, word) = when {
        st != null -> Palette.Teal to "A7 II"
        body.searching -> Palette.Sodium to "hledám"
        body.link == com.nicky.rincewinder.core.BodyLink.NIC_NEODPOVIDA -> Palette.Red to "spí"
        else -> Palette.Sodium to "bez Agenta"
    }
    Row(
        modifier.fillMaxWidth().background(Palette.Flat).border(1.dp, Palette.FlatBorder).padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Lamp(lamp)
        StripCell("Tělo", word)
        StripCell("Baterie", st?.batteryPct?.let { "$it %" } ?: "—")
        StripCell("Karta", st?.cardFree?.let { Format.bytes(it) } ?: "—")
        StripCell("Wi‑Fi", st?.wifiDbm?.let { "$it dBm" } ?: "—")
    }
}

@Composable
private fun StripCell(label: String, value: String) {
    Column {
        Text(label.uppercase(), color = Palette.ColdLabel, fontSize = 9.sp, letterSpacing = 0.14.em)
        Text(value, color = Palette.Text, fontSize = 13.sp)
    }
}

/** Potvrzení všeho nevratného: podržení 1,5 s, výplň roste zleva. Puštění dřív akci zruší. */
@Composable
fun HoldButton(text: String, onConfirmed: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Palette.Red) {
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var fired by remember { mutableStateOf(false) }
    LaunchedEffect(holding) {
        if (holding) {
            val start = System.currentTimeMillis()
            while (holding && progress < 1f) { progress = (System.currentTimeMillis() - start) / 1500f; delay(30) }
            if (holding && progress >= 1f && !fired) { fired = true; onConfirmed() }
        } else if (!fired) progress = 0f
    }
    Box(
        modifier.fillMaxWidth().height(56.dp).background(Palette.ConcreteDark)
            .drawBehind { drawRect(color, size = Size(size.width * progress.coerceIn(0f, 1f), size.height)) }
            .border(1.dp, if (enabled) color.copy(alpha = 0.6f) else Palette.FlatBorder)
            .pointerInput(enabled) { if (enabled) detectTapGestures(onPress = { holding = true; tryAwaitRelease(); holding = false }) },
        contentAlignment = Alignment.Center,
    ) {
        Text("$text · PODRŽ 1,5 s", color = if (enabled) Palette.Text else Palette.Mute, letterSpacing = 0.14.em, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
