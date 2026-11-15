package com.phonegolf.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Solo mode: one player, one built-in group ("Solo"), and all data in one JSON
 * file on this phone. No network. Rules are the same as on the server.
 */
class LocalBackend(
    private val file: File,
    private val today: () -> LocalDate = { LocalDate.now() },
) : Backend {
    override val isLocal = true

    companion object {
        const val GROUP_ID = "solo"
        private val lock = Mutex() // one lock for the file, shared by all instances

        /** Makes a new solo player. Replaces any old solo data in [file]. */
        fun create(file: File, name: String, handicap: Int) {
            val root = JSONObject()
                .put("player", JSONObject().put("id", UUID.randomUUID().toString()).put("name", name))
                .put("scores", JSONObject())
                .put("handicaps", JSONArray().put(entry("0000-01-01", handicap, "start")))
                .put("courses", JSONArray())
            file.parentFile?.mkdirs()
            file.writeText(root.toString())
        }

        private fun entry(date: String, value: Int, source: String) =
            JSONObject().put("effectiveDate", date).put("value", value).put("source", source)
    }

    private fun load(): JSONObject = JSONObject(file.readText())
    private fun save(root: JSONObject) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(file)
    }

    private suspend fun <T> read(f: (JSONObject) -> T): T = withContext(Dispatchers.IO) { lock.withLock { f(load()) } }
    private suspend fun <T> write(f: (JSONObject) -> T): T = withContext(Dispatchers.IO) {
        lock.withLock { val root = load(); val r = f(root); save(root); r }
    }

    private fun player(root: JSONObject): Player {
        val p = root.getJSONObject("player")
        return Player(p.getString("id"), p.getString("name"), "android")
    }

    private fun history(root: JSONObject): List<Scoring.HandicapEntry> {
        val a = root.getJSONArray("handicaps")
        return (0 until a.length()).map { a.getJSONObject(it) }
            .map { Scoring.HandicapEntry(it.getString("effectiveDate"), it.getInt("value")) }
            .sortedBy { it.effectiveDate }
    }

    private fun currentHandicap(root: JSONObject) = history(root).lastOrNull()?.value ?: 0

    private fun courses(root: JSONObject): List<JSONObject> {
        val a = root.getJSONArray("courses")
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun courseInfo(c: JSONObject) =
        Scoring.courseInfo(c.getString("id"), c.getString("name"), LocalDate.parse(c.getString("startDate")), today())

    private fun soloGroup(root: JSONObject): Group {
        val me = player(root)
        return Group(
            id = GROUP_ID,
            name = "Solo",
            inviteCode = "",
            ownerId = me.id,
            members = listOf(Member(me.id, me.name, "android", currentHandicap(root))),
            courses = courses(root).map(::courseInfo).sortedByDescending { it.startDate },
        )
    }

    private fun requireSolo(groupId: String) {
        if (groupId != GROUP_ID) throw ApiException(404, "Group not found")
    }

    override suspend fun me() = read(::player)

    override suspend fun rename(name: String) = write { root ->
        root.getJSONObject("player").put("name", name)
        player(root)
    }

    override suspend fun putScore(date: LocalDate, count: Int, metric: String, source: String) = write { root ->
        val scores = root.getJSONObject("scores")
        val old = scores.optJSONObject(date.toString())
        // A number the player typed in wins over a later automatic count.
        if (old?.optString("source") == "manual" && source == "auto") return@write
        scores.put(date.toString(), JSONObject().put("count", count).put("metric", metric).put("source", source))
    }

    override suspend fun scores() = read { root ->
        val s = root.getJSONObject("scores")
        val from = today().minusDays(30).toString()
        s.keys().asSequence().filter { it >= from }.sortedDescending().map { d ->
            val o = s.getJSONObject(d)
            DayScore(d, o.getInt("count"), o.getString("metric"), o.getString("source"))
        }.toList()
    }

    override suspend fun groups() = read { listOf(soloGroup(it)) }

    override suspend fun group(id: String) = read { requireSolo(id); soloGroup(it) }

    override suspend fun createGroup(name: String, handicap: Int): Group =
        throw ApiException(400, "Groups need a server. You play solo on this phone.")

    override suspend fun joinGroup(code: String, handicap: Int): Group =
        throw ApiException(400, "Groups need a server. You play solo on this phone.")

    override suspend fun leaveGroup(id: String) =
        throw ApiException(400, "You cannot leave the solo group.")

    override suspend fun handicap(groupId: String) = read { root ->
        requireSolo(groupId)
        val t = today()
        val s = root.getJSONObject("scores")
        val from = t.minusDays(Scoring.SUGGESTION_WINDOW_DAYS).toString()
        val to = t.minusDays(1).toString()
        val counts = s.keys().asSequence().filter { it in from..to }.map { s.getJSONObject(it).getInt("count") }.toList()
        val sug = Scoring.suggest(counts)
        HandicapInfo(
            current = currentHandicap(root),
            suggestion = sug?.let { (v, used) ->
                Suggestion(v, "Average of your best $used of the last ${counts.size} days you scored.")
            },
            needsDays = if (sug == null) Scoring.SUGGESTION_MIN_DAYS - counts.size else 0,
        )
    }

    override suspend fun setHandicap(groupId: String, value: Int, source: String) = write { root ->
        requireSolo(groupId)
        // Same rule as the server: a change starts tomorrow.
        val effective = today().plusDays(1).toString()
        val a = root.getJSONArray("handicaps")
        val keep = JSONArray()
        for (i in 0 until a.length()) {
            if (a.getJSONObject(i).getString("effectiveDate") != effective) keep.put(a.getJSONObject(i))
        }
        keep.put(entry(effective, value, source))
        root.put("handicaps", keep)
        Unit
    }

    override suspend fun createCourse(
        groupId: String, name: String, startDate: LocalDate, weekdayPar: Int, weekendPar: Int,
    ) = write { root ->
        requireSolo(groupId)
        val pars = JSONArray()
        Scoring.holes(startDate, weekdayPar, weekendPar).forEach { pars.put(it.par) }
        val c = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("name", name)
            .put("startDate", startDate.toString())
            .put("pars", pars)
        root.getJSONArray("courses").put(c)
        courseInfo(c)
    }

    /** Solo only: changes the first day of a course. Each par stays with its hole number. */
    suspend fun moveCourse(courseId: String, startDate: LocalDate) = write { root ->
        val c = courses(root).firstOrNull { it.getString("id") == courseId }
            ?: throw ApiException(404, "Course not found")
        c.put("startDate", startDate.toString())
        Unit
    }

    override suspend fun leaderboard(courseId: String) = read { root ->
        val c = courses(root).firstOrNull { it.getString("id") == courseId }
            ?: throw ApiException(404, "Course not found")
        val start = LocalDate.parse(c.getString("startDate"))
        val pars = c.getJSONArray("pars")
        val holes = (0 until pars.length()).map { i ->
            Scoring.HoleDef(i + 1, start.plusDays(i.toLong()).toString(), pars.getInt(i))
        }
        val s = root.getJSONObject("scores")
        val scores = holes.mapNotNull { h -> s.optJSONObject(h.date)?.let { h.date to it.getInt("count") } }.toMap()
        val me = player(root)
        Leaderboard(courseInfo(c), GROUP_ID, listOf(Scoring.buildCard(me.id, me.name, holes, history(root), scores)))
    }
}
