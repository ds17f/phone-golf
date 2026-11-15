package com.phonegolf.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundLogicTest {
    private fun c(id: String, status: String, start: String, hole: Int? = null) =
        Course(id, id, start, start, status, hole)

    @Test
    fun picksActiveThenUpcomingThenFinished() {
        val fin = c("fin", "finished", "2026-01-01")
        val fin2 = c("fin2", "finished", "2026-03-01")
        val up1 = c("up1", "upcoming", "2026-12-10")
        val up2 = c("up2", "upcoming", "2026-12-01")
        val act = c("act", "active", "2026-11-10", 3)
        assertEquals("act", pickFeatured(listOf(fin, up1, act))?.id)
        assertEquals("up2", pickFeatured(listOf(fin, up1, up2))?.id)
        assertEquals("fin2", pickFeatured(listOf(fin, fin2))?.id)
        assertNull(pickFeatured(emptyList()))
    }

    @Test
    fun featuredFindsItsGroup() {
        val g1 = Group("g1", "A", "X", "o", emptyList(), listOf(c("old", "finished", "2026-01-01")))
        val g2 = Group("g2", "B", "Y", "o", emptyList(), listOf(c("now", "active", "2026-11-10", 2)))
        assertEquals("g2", featuredIn(listOf(g1, g2))?.first?.id)
        assertNull(featuredIn(listOf(Group("g3", "C", "Z", "o", emptyList(), emptyList()))))
    }

    @Test
    fun namesRounds() {
        assertEquals("Round 1", nextRoundName(0))
        assertEquals("Round 3", nextRoundName(2))
    }

    @Test
    fun plaqueValues() {
        assertEquals("4" to "of 18", holePlaque(c("a", "active", "2026-11-10", 4)))
        assertEquals("F" to "final", holePlaque(c("a", "finished", "2026-01-01")))
        val up = holePlaque(c("a", "upcoming", "2026-12-01"))
        assertEquals("–", up.first)
        assertTrue(up.second.startsWith("starts "))

        val h = HoleResult(1, "2026-11-14", 5, 30, 20, -15)
        assertEquals(35, target(h))
        assertEquals(-15, toParOf(20, h))
        val card = PlayerCard("p", "Me", 30, 0, 0, listOf(h))
        assertEquals("–" to "thru 0", roundPlaque(card, c("a", "active", "2026-11-10", 4)))
        val played = card.copy(thru = 2, totalToPar = -3)
        assertEquals("-3" to "final", roundPlaque(played, c("a", "finished", "2026-01-01")))
        assertEquals(h, todayHole(card, "2026-11-14"))
    }
}
