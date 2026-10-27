package com.personal.podpulse.data

/**
 * Decodes the "proximity pairing" message that AirPods broadcast over
 * Bluetooth LE (Apple manufacturer data, type 0x07). This is the same data an
 * iPhone uses for its battery pop-up, and it needs no pairing or Apple account.
 *
 * Layout (bytes after the 0x004C company ID):
 *   0     message type (0x07)
 *   1     remaining length
 *   2     prefix
 *   3-4   model ID (e.g. 0x0E20 = AirPods Pro)
 *   5     status, about the bud that's broadcasting ("this" bud):
 *           0x02 this bud in ear, 0x08 other bud in ear, 0x04 both buds in case,
 *           0x10 one bud in case, 0x20 this bud is the left one, 0x40 this bud in case
 *   6     bud batteries, one nibble each (0-10 = 0-100 %, 15 = unavailable)
 *   7     high nibble = charging flags, low nibble = case battery
 */
object AirPodsParser {
    const val APPLE_COMPANY_ID = 0x004C
    const val PROXIMITY_PAIRING = 0x07

    /** The battery message's length byte. Other 0x07 messages use a different layout. */
    private const val BATTERY_MESSAGE_LENGTH = 0x19

    fun parse(data: ByteArray?, rssi: Int, timestamp: Long): PodStatus? {
        if (data == null || data.size < 10) return null
        if (data[0].u() != PROXIMITY_PAIRING || data[1].u() != BATTERY_MESSAGE_LENGTH) return null

        val modelId = (data[3].u() shl 8) or data[4].u()
        val status = data[5].u()
        val pods = data[6].u()
        val flags = data[7].u()
        val charging = flags shr 4

        if (PodModel.fromId(modelId).single) {
            val level = level(pods and 0x0F) ?: return null
            return PodStatus(
                modelId = modelId,
                left = level,
                right = null,
                case = null,
                leftCharging = (charging and 0x01) != 0,
                rssi = rssi,
                timestamp = timestamp,
            )
        }

        // The nibble order of left/right swaps depending on which bud is primary.
        val flipped = (status and 0x20) == 0
        val left = level(if (flipped) pods shr 4 else pods and 0x0F)
        val right = level(if (flipped) pods and 0x0F else pods shr 4)
        val case = level(flags and 0x0F)
        if (left == null && right == null && case == null) return null

        val leftInEar = (status and (if (flipped) 0x08 else 0x02)) != 0
        val rightInEar = (status and (if (flipped) 0x02 else 0x08)) != 0

        // A bud in your ear can't be charging or in the case: that flag wins.
        // Otherwise a charging bud is always in the case.
        val leftCharging = !leftInEar && (charging and (if (flipped) 0x02 else 0x01)) != 0
        val rightCharging = !rightInEar && (charging and (if (flipped) 0x01 else 0x02)) != 0
        val thisIsLeft = !flipped
        val thisInCase = (status and 0x40) != 0
        val bothInCase = (status and 0x04) != 0
        val oneInCase = (status and 0x10) != 0
        val leftInCase = !leftInEar &&
            (leftCharging || bothInCase || (oneInCase && thisInCase == thisIsLeft))
        val rightInCase = !rightInEar &&
            (rightCharging || bothInCase || (oneInCase && thisInCase != thisIsLeft))
        return PodStatus(
            modelId = modelId,
            left = left,
            right = right,
            case = case,
            leftCharging = leftCharging,
            rightCharging = rightCharging,
            caseCharging = (charging and 0x04) != 0,
            leftInEar = leftInEar,
            rightInEar = rightInEar,
            leftInCase = leftInCase,
            rightInCase = rightInCase,
            rssi = rssi,
            timestamp = timestamp,
        )
    }

    private fun level(raw: Int): Int? = if (raw in 0..10) raw * 10 else null

    private fun Byte.u(): Int = toInt() and 0xFF
}
