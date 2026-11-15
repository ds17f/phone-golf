package com.phonegolf.app

import org.json.JSONArray
import org.json.JSONObject

data class Player(val id: String, val name: String, val platform: String)

data class Member(val id: String, val name: String, val platform: String, val handicap: Int)

data class Course(
    val id: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val status: String, // upcoming | active | finished
    val currentHole: Int?,
)

data class Group(
    val id: String,
    val name: String,
    val inviteCode: String,
    val ownerId: String,
    val members: List<Member>,
    val courses: List<Course>,
)

data class Suggestion(val value: Int, val explanation: String)

data class HandicapInfo(val current: Int, val suggestion: Suggestion?, val needsDays: Int)

data class HoleResult(
    val hole: Int,
    val date: String,
    val par: Int,
    val handicap: Int,
    val gross: Int?,
    val toPar: Int?,
)

data class PlayerCard(
    val playerId: String,
    val name: String,
    val handicap: Int,
    val thru: Int,
    val totalToPar: Int,
    val holes: List<HoleResult>,
)

data class Leaderboard(val course: Course, val groupId: String, val cards: List<PlayerCard>)

data class DayScore(val date: String, val count: Int, val metric: String, val source: String)

// ---- JSON parsing ----------------------------------------------------------

private inline fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> =
    (0 until length()).map { f(getJSONObject(it)) }

private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else getInt(key)

fun parsePlayer(o: JSONObject) = Player(o.getString("id"), o.getString("name"), o.getString("platform"))

fun parseCourse(o: JSONObject) = Course(
    id = o.getString("id"),
    name = o.getString("name"),
    startDate = o.getString("startDate"),
    endDate = o.getString("endDate"),
    status = o.getString("status"),
    currentHole = o.optIntOrNull("currentHole"),
)

fun parseGroup(o: JSONObject) = Group(
    id = o.getString("id"),
    name = o.getString("name"),
    inviteCode = o.getString("inviteCode"),
    ownerId = o.getString("ownerId"),
    members = o.getJSONArray("members").map {
        Member(it.getString("id"), it.getString("name"), it.getString("platform"), it.getInt("handicap"))
    },
    courses = o.getJSONArray("courses").map(::parseCourse),
)

fun parseHandicap(o: JSONObject): HandicapInfo {
    val s = o.optJSONObject("suggestion")
    return HandicapInfo(
        current = o.getInt("current"),
        suggestion = s?.let { Suggestion(it.getInt("value"), it.getString("explanation")) },
        needsDays = o.optInt("suggestionNeedsDays", 0),
    )
}

fun parseLeaderboard(o: JSONObject): Leaderboard {
    val c = o.getJSONObject("course")
    return Leaderboard(
        course = parseCourse(c),
        groupId = c.getString("groupId"),
        cards = o.getJSONArray("leaderboard").map { p ->
            PlayerCard(
                playerId = p.getString("playerId"),
                name = p.getString("name"),
                handicap = p.optInt("handicap", 0),
                thru = p.getInt("thru"),
                totalToPar = p.getInt("totalToPar"),
                holes = p.getJSONArray("holes").map { h ->
                    HoleResult(
                        hole = h.getInt("hole"),
                        date = h.getString("date"),
                        par = h.getInt("par"),
                        handicap = h.getInt("handicap"),
                        gross = h.optIntOrNull("gross"),
                        toPar = h.optIntOrNull("toPar"),
                    )
                },
            )
        },
    )
}

fun parseScores(o: JSONObject): List<DayScore> = o.getJSONArray("scores").map {
    DayScore(it.getString("date"), it.getInt("count"), it.getString("metric"), it.getString("source"))
}

/** "E" for even, "-3" under, "+2" over. */
fun formatToPar(n: Int?): String = when {
    n == null -> "–"
    n == 0 -> "E"
    n > 0 -> "+$n"
    else -> "$n"
}
