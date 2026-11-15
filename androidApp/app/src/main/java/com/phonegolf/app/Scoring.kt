package com.phonegolf.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The scoring rules, for solo mode. These must stay the same as api/src/scoring.ts.
 *
 *   net   = gross - handicap
 *   toPar = net - par
 */
object Scoring {
    const val HOLES = 18
    const val SUGGESTION_WINDOW_DAYS = 14L
    const val SUGGESTION_MIN_DAYS = 3

    data class HandicapEntry(val effectiveDate: String, val value: Int)
    data class HoleDef(val hole: Int, val date: String, val par: Int)

    /** History must be sorted by effectiveDate. */
    fun handicapOn(history: List<HandicapEntry>, date: String): Int {
        var value = 0
        for (h in history) {
            if (h.effectiveDate <= date) value = h.value else break
        }
        return value
    }

    fun buildCard(
        playerId: String,
        name: String,
        holes: List<HoleDef>,
        history: List<HandicapEntry>,
        scores: Map<String, Int>,
    ): PlayerCard {
        val results = holes.map { h ->
            val handicap = handicapOn(history, h.date)
            val gross = scores[h.date]
            HoleResult(h.hole, h.date, h.par, handicap, gross, gross?.let { it - handicap - h.par })
        }
        val played = results.filter { it.gross != null }
        return PlayerCard(playerId, name, history.lastOrNull()?.value ?: 0, played.size, played.sumOf { it.toPar ?: 0 }, results)
    }

    /** Average of the best (lowest) half of the counts. Null with fewer than 3 days. */
    fun suggest(counts: List<Int>): Pair<Int, Int>? {
        if (counts.size < SUGGESTION_MIN_DAYS) return null
        val used = (counts.size + 1) / 2
        val best = counts.sorted().take(used)
        return Math.round(best.sum().toDouble() / used).toInt() to used
    }

    fun isWeekend(date: LocalDate) = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    fun holes(start: LocalDate, weekdayPar: Int, weekendPar: Int): List<HoleDef> =
        (0 until HOLES).map { i ->
            val d = start.plusDays(i.toLong())
            HoleDef(i + 1, d.toString(), if (isWeekend(d)) weekendPar else weekdayPar)
        }

    fun courseInfo(id: String, name: String, start: LocalDate, today: LocalDate): Course {
        val end = start.plusDays((HOLES - 1).toLong())
        val status = when {
            today < start -> "upcoming"
            today > end -> "finished"
            else -> "active"
        }
        val hole = if (status == "active") ChronoUnit.DAYS.between(start, today).toInt() + 1 else null
        return Course(id, name, start.toString(), end.toString(), status, hole)
    }
}
