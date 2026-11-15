package com.phonegolf.app

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Pure logic for the Round screen. */

/** Name of the next solo round, when [existing] rounds exist. */
fun nextRoundName(existing: Int) = "Round ${existing + 1}"

/**
 * The one course that the Round screen shows: the first active course; else the
 * earliest upcoming; else the most recent finished.
 */
fun pickFeatured(courses: List<Course>): Course? =
    courses.firstOrNull { it.status == "active" }
        ?: courses.filter { it.status == "upcoming" }.minByOrNull { it.startDate }
        ?: courses.filter { it.status == "finished" }.maxByOrNull { it.startDate }

/** The featured course over all groups, with the group that owns it. */
fun featuredIn(groups: List<Group>): Pair<Group, Course>? {
    val pairs = groups.flatMap { g -> g.courses.map { g to it } }
    val c = pickFeatured(pairs.map { it.second }) ?: return null
    return pairs.first { it.second.id == c.id }
}

private val shortDate = DateTimeFormatter.ofPattern("MMM d")

/** Big value and small line for the HOLE plaque. */
fun holePlaque(c: Course): Pair<String, String> = when (c.status) {
    "active" -> "${c.currentHole ?: "–"}" to "of 18"
    "upcoming" -> "–" to "starts ${LocalDate.parse(c.startDate).format(shortDate)}"
    else -> "F" to "final"
}

fun todayHole(card: PlayerCard?, today: String): HoleResult? = card?.holes?.firstOrNull { it.date == today }

/** Handicap plus par. A count under this is under par. */
fun target(h: HoleResult) = h.handicap + h.par

/** Strokes to par for a live count on a hole. */
fun toParOf(count: Int, h: HoleResult) = count - h.handicap - h.par

/** Big value and small line for the ROUND plaque. */
fun roundPlaque(card: PlayerCard?, c: Course): Pair<String, String> {
    val value = if (card == null || card.thru == 0) "–" else formatToPar(card.totalToPar)
    val sub = if (c.status == "finished") "final" else "thru ${card?.thru ?: 0}"
    return value to sub
}
