package com.phonegolf.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class LocalBackendTest {
    private val today = LocalDate.parse("2026-11-14") // Saturday

    private fun backend(handicap: Int = 30): LocalBackend {
        val f = File.createTempFile("solo", ".json").apply { deleteOnExit() }
        LocalBackend.create(f, "Damian", handicap)
        return LocalBackend(f) { today }
    }

    @Test
    fun suggestMatchesServer() {
        assertNull(Scoring.suggest(listOf(10, 20)))
        assertEquals(20 to 3, Scoring.suggest(listOf(50, 10, 40, 20, 30)))
    }

    @Test
    fun playsASoloRound() = runBlocking {
        val b = backend(30)
        val c = b.createCourse(LocalBackend.GROUP_ID, "Solo Open", LocalDate.parse("2026-11-13"), 10, 5)
        assertEquals("active", c.status)
        assertEquals(2, c.currentHole)

        b.putScore(LocalDate.parse("2026-11-13"), 45, "unlocks", "auto") // 45-30-10 = +5
        b.putScore(LocalDate.parse("2026-11-14"), 30, "unlocks", "auto") // 30-30-5 = -5
        val card = b.leaderboard(c.id).cards.single()
        assertEquals(2, card.thru)
        assertEquals(0, card.totalToPar)
        assertEquals(5, card.holes[0].toPar)
        assertEquals(5, card.holes[1].par) // Saturday uses the weekend par
    }

    @Test
    fun manualScoreWinsOverAuto() = runBlocking {
        val b = backend()
        val d = LocalDate.parse("2026-11-13")
        b.putScore(d, 20, "unlocks", "manual")
        b.putScore(d, 50, "unlocks", "auto")
        assertEquals(20, b.scores().single().count)
    }

    @Test
    fun handicapChangeStartsTomorrow() = runBlocking {
        val b = backend(50)
        for ((d, n) in listOf("2026-11-10" to 30, "2026-11-11" to 40, "2026-11-12" to 20, "2026-11-13" to 60)) {
            b.putScore(LocalDate.parse(d), n, "unlocks", "auto")
        }
        val h = b.handicap(LocalBackend.GROUP_ID)
        assertEquals(25, h.suggestion?.value)
        b.setHandicap(LocalBackend.GROUP_ID, 25, "suggestion")
        assertEquals(25, b.handicap(LocalBackend.GROUP_ID).current)

        val c = b.createCourse(LocalBackend.GROUP_ID, "C", today, 0, 0)
        val holes = b.leaderboard(c.id).cards.single().holes
        assertEquals(50, holes[0].handicap) // today: old value
        assertEquals(25, holes[1].handicap) // tomorrow: new value
    }

    @Test
    fun movesAnUpcomingRoundToToday() = runBlocking {
        val b = backend()
        val c = b.createCourse(LocalBackend.GROUP_ID, "The Solo Open", today.plusDays(1), 0, 0)
        assertEquals("upcoming", c.status)
        b.moveCourse(c.id, today)
        val moved = b.group(LocalBackend.GROUP_ID).courses.single()
        assertEquals("active", moved.status)
        assertEquals(1, moved.currentHole)
    }

    @Test
    fun groupsNeedAServer() = runBlocking {
        val b = backend()
        assertEquals(listOf("solo"), b.groups().map { it.id })
        val e = runCatching { b.createGroup("X", 1) }.exceptionOrNull()
        assertTrue(e is ApiException)
    }

    @Test
    fun secondRoundIsFeaturedWhenFirstIsFinished() = runBlocking {
        val b = backend()
        b.createCourse(LocalBackend.GROUP_ID, nextRoundName(0), today.minusDays(30), 0, 0)
        var courses = b.groups().single().courses
        assertEquals("finished", courses.single().status)
        b.createCourse(LocalBackend.GROUP_ID, nextRoundName(courses.size), today, 0, 0)
        courses = b.groups().single().courses
        assertEquals(listOf("Round 1", "Round 2"), courses.map { it.name }.sorted())
        assertEquals("Round 2", pickFeatured(courses)?.name)
    }
}
