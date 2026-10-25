package com.personal.podpulse.data

import com.personal.podpulse.features.EarDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class EarDetectorTest {
    private var playing = true
    private val events = mutableListOf<String>()
    private val detector = EarDetector(
        isPlaying = { playing },
        pause = { events += "pause"; playing = false },
        resume = { events += "resume"; playing = true },
    )

    private fun feed(vararg counts: Int) = counts.forEach(detector::update)

    @Test
    fun pausesWhenBudRemovedAndResumesWhenPutBack() {
        feed(2, 2, 1, 1, 2, 2)
        assertEquals(listOf("pause", "resume"), events)
    }

    @Test
    fun ignoresSingleGlitches() {
        feed(2, 2, 1, 2, 2, 0, 2, 2)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun doesNotPauseWhenNothingPlays() {
        playing = false
        feed(2, 2, 1, 1, 2, 2)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun resumesOnlyWhenBothBudsAreBack() {
        feed(2, 2, 0, 0, 1, 1)
        assertEquals(listOf("pause"), events)
        feed(2, 2)
        assertEquals(listOf("pause", "resume"), events)
    }

    @Test
    fun doesNotResumeMusicTheUserPausedThemselves() {
        feed(2, 2, 1, 1)
        playing = false // still paused, user did nothing
        feed(2, 2)
        assertEquals(listOf("pause", "resume"), events)
        events.clear()
        playing = false // user paused manually
        feed(1, 1, 2, 2)
        assertEquals(emptyList<String>(), events)
    }
}
