package com.phonegolf.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {
    @Test
    fun formatsToPar() {
        assertEquals("E", formatToPar(0))
        assertEquals("+3", formatToPar(3))
        assertEquals("-2", formatToPar(-2))
        assertEquals("–", formatToPar(null))
    }

    @Test
    fun parsesLeaderboard() {
        val json = JSONObject(
            """
            {"course":{"id":"c","name":"Open","startDate":"2026-11-09","endDate":"2026-11-26","status":"active","currentHole":6,"createdBy":"p","groupId":"g"},
             "holes":[],
             "leaderboard":[{"playerId":"p","name":"A","handicap":30,"thru":1,"totalGross":40,"totalNet":10,"totalToPar":0,
               "holes":[{"hole":1,"date":"2026-11-09","par":10,"handicap":30,"gross":40,"net":10,"toPar":0,"metric":"unlocks","source":"auto"},
                        {"hole":2,"date":"2026-11-10","par":10,"handicap":30,"gross":null,"net":null,"toPar":null,"metric":null,"source":null}]}]}
            """.trimIndent(),
        )
        val lb = parseLeaderboard(json)
        assertEquals("g", lb.groupId)
        assertEquals(6, lb.course.currentHole)
        assertEquals(40, lb.cards[0].holes[0].gross)
        assertNull(lb.cards[0].holes[1].gross)
        assertNull(lb.cards[0].holes[1].toPar)
    }
}
