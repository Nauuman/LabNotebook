package com.personal.podpulse.data

/**
 * One battery reading. Levels are percentages (0-100) in steps of 10, or null
 * when the device doesn't report that part (e.g. a bud that's out of the case
 * and disconnected). For single-battery headphones (AirPods Max) the level is
 * stored in [left].
 */
data class PodStatus(
    val modelId: Int,
    val left: Int?,
    val right: Int?,
    val case: Int?,
    val leftCharging: Boolean = false,
    val rightCharging: Boolean = false,
    val caseCharging: Boolean = false,
    val leftInEar: Boolean = false,
    val rightInEar: Boolean = false,
    val rssi: Int = 0,
    val timestamp: Long = 0L,
) {
    val model: PodModel get() = PodModel.fromId(modelId)

    /** Lowest bud level, used for the overall accent colour. */
    val lowest: Int? get() = listOfNotNull(left, right).minOrNull()

    /** True when everything the user sees is identical (ignores signal and time). */
    fun sameReadingAs(other: PodStatus): Boolean =
        copy(rssi = 0, timestamp = 0) == other.copy(rssi = 0, timestamp = 0)
}
