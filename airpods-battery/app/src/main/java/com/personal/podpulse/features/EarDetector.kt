package com.personal.podpulse.features

/**
 * Turns the noisy in-ear flags into pause/resume decisions, like an iPhone:
 * taking a bud out pauses playback, putting it back resumes (only if we paused).
 * A new in-ear count must be seen twice in a row before it counts.
 */
class EarDetector(
    private val isPlaying: () -> Boolean,
    private val pause: () -> Unit,
    private val resume: () -> Unit,
) {
    private var stable: Int? = null
    private var pending: Int? = null
    private var pendingCount = 0
    private var pausedFrom: Int? = null

    fun update(inEarCount: Int) {
        if (inEarCount == pending) pendingCount++ else {
            pending = inEarCount
            pendingCount = 1
        }
        if (pendingCount < 2 || inEarCount == stable) return

        val previous = stable
        stable = inEarCount
        if (previous == null) return

        if (inEarCount < previous) {
            if (pausedFrom == null && isPlaying()) {
                pause()
                pausedFrom = previous
            }
        } else {
            val target = pausedFrom ?: return
            if (inEarCount >= target) {
                pausedFrom = null
                if (!isPlaying()) resume()
            }
        }
    }
}
