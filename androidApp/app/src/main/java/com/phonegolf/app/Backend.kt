package com.phonegolf.app

import java.time.LocalDate

/**
 * Where the game data lives. [Api] talks to the Phone Golf server.
 * [LocalBackend] keeps everything on this phone (solo mode, no server).
 */
interface Backend {
    val isLocal: Boolean
    suspend fun me(): Player
    suspend fun rename(name: String): Player
    suspend fun putScore(date: LocalDate, count: Int, metric: String, source: String)
    suspend fun scores(): List<DayScore>
    suspend fun groups(): List<Group>
    suspend fun group(id: String): Group
    suspend fun createGroup(name: String, handicap: Int): Group
    suspend fun joinGroup(code: String, handicap: Int): Group
    suspend fun leaveGroup(id: String)
    suspend fun handicap(groupId: String): HandicapInfo
    suspend fun setHandicap(groupId: String, value: Int, source: String)
    suspend fun createCourse(groupId: String, name: String, startDate: LocalDate, weekdayPar: Int, weekendPar: Int): Course
    suspend fun leaderboard(courseId: String): Leaderboard
}
