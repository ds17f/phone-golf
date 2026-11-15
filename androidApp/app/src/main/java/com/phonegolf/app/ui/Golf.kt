package com.phonegolf.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonegolf.app.HoleResult
import com.phonegolf.app.Leaderboard
import com.phonegolf.app.PlayerCard
import com.phonegolf.app.formatToPar
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val Serif = FontFamily.Serif
private val Mono = FontFamily.Monospace

/** Tournament positions with ties: 1, T2, T2, 4. Players with no holes get none. */
fun positions(cards: List<PlayerCard>): List<String> = cards.map { p ->
    if (p.thru == 0) return@map ""
    val first = cards.indexOfFirst { it.thru > 0 && it.totalToPar == p.totalToPar }
    val tied = cards.count { it.thru > 0 && it.totalToPar == p.totalToPar } > 1
    (if (tied) "T" else "") + (first + 1)
}

private fun toParColor(n: Int?, base: Color) = when {
    n == null -> Color(0xFF8A918C)
    n < 0 -> Golf.Red
    n == 0 -> Golf.Green
    else -> base
}

// ---- tournament leaderboard -----------------------------------------------------

@Composable
fun TournamentBoard(b: Leaderboard, meId: String?, today: String) {
    val pos = positions(b.cards)
    val sub = when (b.course.status) {
        "active" -> "${b.course.name} · Hole ${b.course.currentHole} of 18"
        "upcoming" -> "${b.course.name} · Starts ${b.course.startDate}"
        else -> "${b.course.name} · Final"
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().background(Golf.Board, shape).border(3.dp, Golf.BoardLine, shape).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("LEADERBOARD", color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 5.sp)
        Text(sub, color = Golf.BoardText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            BoardHeader("POS", Modifier.width(40.dp))
            BoardHeader("PLAYER", Modifier.weight(1f), TextAlign.Start)
            BoardHeader("TO PAR", Modifier.width(62.dp))
            BoardHeader("TODAY", Modifier.width(54.dp))
            BoardHeader("THRU", Modifier.width(44.dp))
        }
        b.cards.forEachIndexed { i, p ->
            val todayToPar = p.holes.firstOrNull { it.date == today }?.toPar
            val thru = when {
                p.thru == 0 -> "–"
                b.course.status == "finished" || p.holes.lastOrNull()?.gross != null -> "F"
                else -> "${p.thru}"
            }
            val me = p.playerId == meId
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).height(40.dp)
                    .background(Golf.Plaque, RoundedCornerShape(4.dp))
                    .then(if (me) Modifier.border(2.dp, Golf.Gold, RoundedCornerShape(4.dp)) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaqueText(pos[i], Modifier.width(40.dp), size = 15)
                Box(Modifier.width(2.dp).height(40.dp).background(Golf.Board))
                Text(
                    p.name.uppercase(), Modifier.weight(1f).padding(horizontal = 8.dp),
                    color = Golf.PlaqueInk, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.width(2.dp).height(40.dp).background(Golf.Board))
                PlaqueText(if (p.thru > 0) formatToPar(p.totalToPar) else "–", Modifier.width(60.dp), toParColor(if (p.thru > 0) p.totalToPar else null, Golf.PlaqueInk), 19)
                PlaqueText(formatToPar(todayToPar), Modifier.width(54.dp), toParColor(todayToPar, Golf.PlaqueInk))
                PlaqueText(thru, Modifier.width(44.dp))
            }
        }
    }
}

@Composable
private fun BoardHeader(text: String, modifier: Modifier, align: TextAlign = TextAlign.Center) {
    Text(text, modifier, color = Golf.BoardText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, textAlign = align)
}

@Composable
private fun PlaqueText(text: String, modifier: Modifier, color: Color = Golf.PlaqueInk, size: Int = 16) {
    Text(text, modifier, color = color, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = size.sp, textAlign = TextAlign.Center)
}

// ---- scoreboard strip ---------------------------------------------------------------

class Plaque(val label: String, val value: String, val valueColor: Color, val sub: String)

/** Green board with three cream plaques: HOLE, TODAY, ROUND. */
@Composable
fun ScoreStrip(plaques: List<Plaque>) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().background(Golf.Board, shape).border(3.dp, Golf.BoardLine, shape).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        plaques.forEach { p ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p.label, Modifier.padding(top = 2.dp, bottom = 5.dp), color = Golf.BoardText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, maxLines = 1)
                Column(
                    Modifier.fillMaxWidth().background(Golf.Plaque, RoundedCornerShape(4.dp)).padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(p.value, color = p.valueColor, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 30.sp, maxLines = 1, softWrap = false)
                    Text(p.sub, color = Golf.PaperMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Colors a to-par number: red under par, green at par, ink over. */
fun toParInk(n: Int?): Color = toParColor(n, Golf.PlaqueInk)

// ---- paper scorecard --------------------------------------------------------------

private val LabelW = 52.dp
private val SumW = 38.dp
private val RowH = 30.dp
private val PlayerRowH = 42.dp
private val shortDate = java.time.format.DateTimeFormatter.ofPattern("MMM d")
private fun pretty(d: String) = LocalDate.parse(d).format(shortDate)
private val SumBg = Golf.PaperInk.copy(alpha = .05f)

@Composable
fun PaperScorecard(b: Leaderboard, meId: String?, today: String) {
    val shape = RoundedCornerShape(6.dp)
    Column(Modifier.fillMaxWidth().background(Golf.Paper, shape).border(1.dp, Golf.PaperLine, shape)) {
        Row(
            Modifier.fillMaxWidth().background(Golf.Green, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(b.course.name, color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("${pretty(b.course.startDate)} – ${pretty(b.course.endDate)}", color = Golf.BoardText, fontSize = 12.sp)
            }
            Text("⛳ SCORECARD", color = Golf.Gold, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.sp)
        }
        ScorecardHalf("OUT", 1..9, b.cards, meId, today)
        ScorecardHalf("IN", 10..18, b.cards, meId, today)
        Totals(b.cards, meId)
        Legend()
        Text(
            "Numbers are literal unlocks or pickups. Target = handicap + par.",
            color = Golf.PaperMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun ScorecardHalf(label: String, range: IntRange, cards: List<PlayerCard>, meId: String?, today: String) {
    val holes = cards.firstOrNull()?.holes.orEmpty().filter { it.hole in range }
    if (holes.isEmpty()) return
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp).fillMaxWidth().border(2.dp, Golf.PaperInk)) {
        Row {
            LabelCell(RowH, Golf.Green) { Label("HOLE", Color.White, 10) }
            holes.forEach { h ->
                val now = h.date == today
                HoleCell(RowH, if (now) Golf.Gold else Golf.Green) {
                    Text("${h.hole}", color = if (now) Golf.PaperInk else Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
            }
            SumCell(RowH, Golf.GreenDark) { Label(label, Color.White, 10) }
        }
        Row {
            LabelCell(22.dp) { Label("DAY", Golf.PaperMuted, 9) }
            holes.forEach { h ->
                val d = LocalDate.parse(h.date).dayOfWeek.getDisplayName(DateTextStyle.NARROW, Locale.getDefault())
                HoleCell(22.dp) { Text(d, color = Golf.PaperMuted, fontSize = 10.sp, maxLines = 1) }
            }
            SumCell(22.dp, SumBg) {}
        }
        val parBg = Golf.Green.copy(alpha = .10f)
        Row {
            LabelCell(RowH, parBg) { Label("PAR", Golf.PaperInk, 10) }
            holes.forEach { h -> HoleCell(RowH, parBg) { Num("${h.par}", size = 11) } }
            SumCell(RowH, parBg) { Num("${holes.sumOf { it.par }}", bold = true, size = 11) }
        }
        cards.forEach { p ->
            val ph = p.holes.filter { it.hole in range }
            Row {
                LabelCell(PlayerRowH, if (p.playerId == meId) Golf.Gold.copy(alpha = .28f) else Color.Transparent) { PlayerName(p) }
                ph.forEach { h ->
                    HoleCell(PlayerRowH, if (h.date == today) Golf.Gold.copy(alpha = .22f) else Color.Transparent) {
                        // A dash: a different player has no score for a finished hole.
                        if (p.playerId != meId && h.gross == null && h.date < today) Text("–", color = Golf.PaperMuted, fontSize = 12.sp)
                        else ScoreMark(h)
                    }
                }
                SumCell(PlayerRowH, SumBg) { Num(sumGross(ph), bold = true, size = 11) }
            }
        }
    }
}

@Composable
private fun PlayerName(p: PlayerCard) {
    Column(Modifier.padding(horizontal = 4.dp).fillMaxWidth()) {
        Text(p.name, color = Golf.PaperInk, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("hcp ${p.handicap}", color = Golf.PaperMuted, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1)
    }
}

/** OUT, IN, TOT and to-par for each player. Same label width as the halves. */
@Composable
private fun Totals(cards: List<PlayerCard>, meId: String?) {
    if (cards.firstOrNull()?.holes.isNullOrEmpty()) return
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp).fillMaxWidth().border(2.dp, Golf.PaperInk)) {
        Row {
            LabelCell(RowH, Golf.Green) { Label("", Color.White, 10) }
            listOf("OUT", "IN", "TOT", "±").forEach { t ->
                Cell(Modifier.weight(1f), RowH, Golf.Green) { Label(t, Color.White, 10) }
            }
        }
        cards.forEach { p ->
            val out = p.holes.filter { it.hole in 1..9 }
            val inn = p.holes.filter { it.hole in 10..18 }
            Row {
                LabelCell(PlayerRowH, if (p.playerId == meId) Golf.Gold.copy(alpha = .28f) else Color.Transparent) { PlayerName(p) }
                Cell(Modifier.weight(1f), PlayerRowH) { Num(sumGross(out), size = 12) }
                Cell(Modifier.weight(1f), PlayerRowH) { Num(sumGross(inn), size = 12) }
                Cell(Modifier.weight(1f), PlayerRowH, SumBg) { Num(sumGross(p.holes), bold = true, size = 12) }
                Cell(Modifier.weight(1f), PlayerRowH, SumBg) {
                    if (p.thru > 0) Num(formatToPar(p.totalToPar), bold = true, size = 12, color = toParColor(p.totalToPar, Golf.PaperInk))
                }
            }
        }
    }
}

private fun sumGross(hs: List<HoleResult>): String {
    val played = hs.mapNotNull { it.gross }
    return if (played.isEmpty()) "" else "${played.sum()}"
}

@Composable
private fun Cell(modifier: Modifier, h: Dp, bg: Color = Color.Transparent, align: Alignment = Alignment.Center, content: @Composable () -> Unit) {
    Box(modifier.height(h).background(bg).border(0.5.dp, Golf.PaperLine), contentAlignment = align) { content() }
}

@Composable
private fun LabelCell(h: Dp, bg: Color = Color.Transparent, content: @Composable () -> Unit) =
    Cell(Modifier.width(LabelW), h, bg, Alignment.CenterStart, content)

@Composable
private fun RowScope.HoleCell(h: Dp, bg: Color = Color.Transparent, content: @Composable () -> Unit) =
    Cell(Modifier.weight(1f), h, bg, content = content)

@Composable
private fun SumCell(h: Dp, bg: Color = Color.Transparent, content: @Composable () -> Unit) =
    Cell(Modifier.width(SumW), h, bg, content = content)

@Composable
private fun Label(text: String, color: Color, size: Int = 12) {
    Text(text, Modifier.padding(horizontal = 4.dp), color = color, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = size.sp, letterSpacing = 0.5.sp, maxLines = 1, softWrap = false)
}

@Composable
private fun Num(text: String, bold: Boolean = false, color: Color = Golf.PaperInk, size: Int = 13) {
    Text(text, color = color, fontFamily = Mono, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = size.sp, maxLines = 1, softWrap = false)
}

/** The count, circled (pill) when under the target, boxed when over. Doubled at 10 or more. */
@Composable
fun ScoreMark(h: HoleResult) {
    val gross = h.gross ?: return
    val t = h.toPar ?: 0
    val pill = RoundedCornerShape(50)
    val (color, shape: Shape?, double) = when {
        t <= -10 -> Triple(Golf.Red, pill, true)
        t < 0 -> Triple(Golf.Red, pill, false)
        t >= 10 -> Triple(Golf.PaperInk, RectangleShape, true)
        t > 0 -> Triple(Golf.PaperInk, RectangleShape, false)
        else -> Triple(Golf.PaperInk, null, false)
    }
    Mark("$gross", color, shape, double)
}

@Composable
private fun Mark(text: String, color: Color, shape: Shape?, double: Boolean) {
    var m = Modifier.wrapContentWidth(unbounded = true).heightIn(min = 20.dp).widthIn(min = 20.dp)
    if (shape != null && double) m = m.border(1.dp, color, shape).padding(1.5.dp)
    if (shape != null) m = m.border(1.dp, color, shape)
    Box(m.padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
            maxLines = 1, softWrap = false, style = TextStyle(textAlign = TextAlign.Center))
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mark("12", Golf.Red, RoundedCornerShape(50), false); LegendText("under")
        Mark("40", Golf.PaperInk, RectangleShape, false); LegendText("over")
        Mark("8", Golf.Red, RoundedCornerShape(50), true); LegendText("10+")
    }
}

@Composable
private fun LegendText(t: String) = Text(t, color = Golf.PaperInk, fontSize = 12.sp)
