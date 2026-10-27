package com.personal.podpulse.data

/**
 * AirPods packets are noisy: each bud broadcasts its own view, and the odd
 * packet carries a bogus value (e.g. 0 %). Instead of showing the latest
 * packet, show what most recent packets agree on.
 */
object Consensus {
    fun of(window: List<PodStatus>): PodStatus {
        require(window.isNotEmpty())
        val latest = window.last()

        /** Most common value; ties go to the most recent. */
        fun <T> mode(values: List<T>): T {
            val counts = values.groupingBy { it }.eachCount()
            val max = counts.values.max()
            return values.asReversed().first { counts[it] == max }
        }

        /** Levels ignore "unknown" packets as long as any packet knows the value. */
        fun level(select: (PodStatus) -> Int?): Int? =
            window.mapNotNull(select).takeIf { it.isNotEmpty() }?.let(::mode)

        fun flag(select: (PodStatus) -> Boolean): Boolean = mode(window.map(select))

        return latest.copy(
            left = level { it.left },
            right = level { it.right },
            case = level { it.case },
            leftCharging = flag { it.leftCharging },
            rightCharging = flag { it.rightCharging },
            caseCharging = flag { it.caseCharging },
            leftInEar = flag { it.leftInEar },
            rightInEar = flag { it.rightInEar },
            leftInCase = flag { it.leftInCase },
            rightInCase = flag { it.rightInCase },
        )
    }
}
