package com.personal.podpulse.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsensusTest {
    private var t = 0L
    private fun s(left: Int?, right: Int?, case: Int?, inEar: Boolean = false) = PodStatus(
        modelId = 0x1420,
        left = left,
        right = right,
        case = case,
        leftInEar = inEar,
        rightInEar = inEar,
        timestamp = t++,
    )

    @Test
    fun oddBogusPacketIsIgnored() {
        val r = Consensus.of(listOf(s(90, 90, 70), s(90, 90, 70), s(0, 0, 0), s(90, 90, 70)))
        assertEquals(90, r.left)
        assertEquals(90, r.right)
        assertEquals(70, r.case)
    }

    @Test
    fun unknownCaseDoesNotHideKnownLevel() {
        val r = Consensus.of(listOf(s(80, 80, 50), s(80, 80, null), s(80, 80, null)))
        assertEquals(50, r.case)
    }

    @Test
    fun realChangeWinsOnceItDominates() {
        val r = Consensus.of(listOf(s(90, 90, 70), s(80, 90, 70), s(80, 90, 70)))
        assertEquals(80, r.left)
    }

    @Test
    fun inEarFlagIsMajorityVote() {
        assertFalse(Consensus.of(listOf(s(90, 90, 70, false), s(90, 90, 70, true), s(90, 90, 70, false))).leftInEar)
        assertTrue(Consensus.of(listOf(s(90, 90, 70, true), s(90, 90, 70, false), s(90, 90, 70, true))).leftInEar)
    }

    @Test
    fun keepsLatestSignalAndTime() {
        val r = Consensus.of(listOf(s(90, 90, 70), s(90, 90, 70)))
        assertEquals(1L, r.timestamp)
    }
}
