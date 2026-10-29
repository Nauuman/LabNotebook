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

    fun parse(data: ByteArray?, rssi: Int, timestamp: Long): PodStatus? {
        if (data == null || data.size < 10) return null
        // The length byte (data[1]) varies between models and firmware, so it isn't checked.
        if (data[0].u() != PROXIMITY_PAIRING) return null

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
        val leftLevel = level(if (flipped) pods shr 4 else pods and 0x0F)
        val rightLevel = level(if (flipped) pods and 0x0F else pods shr 4)
        val case = level(flags and 0x0F)
        if (leftLevel == null && rightLevel == null && case == null) return null

        val thisIsLeft = !flipped
        val thisInCase = (status and 0x40) != 0
        val bothInCase = (status and 0x04) != 0
        val oneInCase = (status and 0x10) != 0
        val left = BudState.of(
            caseFlag = bothInCase || (oneInCase && thisInCase == thisIsLeft),
            earFlag = (status and (if (flipped) 0x08 else 0x02)) != 0,
            chargeFlag = (charging and (if (flipped) 0x02 else 0x01)) != 0,
        )
        val right = BudState.of(
            caseFlag = bothInCase || (oneInCase && thisInCase != thisIsLeft),
            earFlag = (status and (if (flipped) 0x02 else 0x08)) != 0,
            chargeFlag = (charging and (if (flipped) 0x01 else 0x02)) != 0,
        )
        return PodStatus(
            modelId = modelId,
            left = leftLevel,
            right = rightLevel,
            case = case,
            leftCharging = left.charging,
            rightCharging = right.charging,
            caseCharging = (charging and 0x04) != 0,
            leftInEar = left.inEar,
            rightInEar = right.inEar,
            leftInCase = left.inCase,
            rightInCase = right.inCase,
            rssi = rssi,
            timestamp = timestamp,
        )
    }

    /**
     * Resolves contradicting flags for one bud, most trustworthy first:
     * the in-case flag, then the in-ear flag (a bud in your ear can't be
     * charging), then charging (a charging bud is in the case).
     */
    private data class BudState(val inCase: Boolean, val inEar: Boolean, val charging: Boolean) {
        companion object {
            fun of(caseFlag: Boolean, earFlag: Boolean, chargeFlag: Boolean) = when {
                caseFlag -> BudState(inCase = true, inEar = false, charging = chargeFlag)
                earFlag -> BudState(inCase = false, inEar = true, charging = false)
                else -> BudState(inCase = chargeFlag, inEar = false, charging = chargeFlag)
            }
        }
    }

    private fun level(raw: Int): Int? = if (raw in 0..10) raw * 10 else null

    private fun Byte.u(): Int = toInt() and 0xFF
}
