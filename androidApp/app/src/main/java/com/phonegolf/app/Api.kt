package com.phonegolf.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class ApiException(val status: Int, message: String) : Exception(message)

/** Minimal JSON client for the Phone Golf API. No third-party HTTP library. */
class Api(private val baseUrl: String, private val token: String?) : Backend {
    override val isLocal = false


    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject? =
        withContext(Dispatchers.IO) {
            val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("X-Local-Date", LocalDate.now().toString())
                token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val msg = runCatching { JSONObject(text).getString("error") }.getOrNull() ?: "Request failed ($status)"
                    throw ApiException(status, msg)
                }
                if (text.isBlank()) null else JSONObject(text)
            } finally {
                conn.disconnect()
            }
        }

    private fun json(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k, v) -> put(k, v) } }

    /** Returns the new player and their secret player key. */
    suspend fun createPlayer(name: String): Pair<Player, String> {
        val r = request("POST", "/api/players", json("name" to name, "platform" to "android"))!!
        return parsePlayer(r.getJSONObject("player")) to r.getString("token")
    }

    override suspend fun me(): Player = parsePlayer(request("GET", "/api/me")!!.getJSONObject("player"))

    override suspend fun rename(name: String): Player =
        parsePlayer(request("PUT", "/api/me", json("name" to name))!!.getJSONObject("player"))

    override suspend fun putScore(date: LocalDate, count: Int, metric: String, source: String) {
        request("PUT", "/api/scores/$date", json("count" to count, "metric" to metric, "source" to source))
    }

    override suspend fun scores(): List<DayScore> = parseScores(request("GET", "/api/scores")!!)

    override suspend fun groups(): List<Group> {
        val arr = request("GET", "/api/groups")!!.getJSONArray("groups")
        return (0 until arr.length()).map { parseGroup(arr.getJSONObject(it)) }
    }

    override suspend fun group(id: String): Group = parseGroup(request("GET", "/api/groups/$id")!!.getJSONObject("group"))

    override suspend fun createGroup(name: String, handicap: Int): Group =
        parseGroup(request("POST", "/api/groups", json("name" to name, "handicap" to handicap))!!.getJSONObject("group"))

    override suspend fun joinGroup(code: String, handicap: Int): Group =
        parseGroup(request("POST", "/api/groups/join", json("code" to code, "handicap" to handicap))!!.getJSONObject("group"))

    override suspend fun leaveGroup(id: String) {
        request("DELETE", "/api/groups/$id/membership")
    }

    override suspend fun handicap(groupId: String): HandicapInfo = parseHandicap(request("GET", "/api/groups/$groupId/handicap")!!)

    override suspend fun setHandicap(groupId: String, value: Int, source: String) {
        request("PUT", "/api/groups/$groupId/handicap", json("handicap" to value, "source" to source))
    }

    override suspend fun createCourse(groupId: String, name: String, startDate: LocalDate, weekdayPar: Int, weekendPar: Int): Course =
        parseCourse(
            request(
                "POST", "/api/groups/$groupId/courses",
                json("name" to name, "startDate" to startDate.toString(), "weekdayPar" to weekdayPar, "weekendPar" to weekendPar),
            )!!.getJSONObject("course"),
        )

    override suspend fun leaderboard(courseId: String): Leaderboard =
        parseLeaderboard(request("GET", "/api/courses/$courseId/leaderboard")!!)
}
