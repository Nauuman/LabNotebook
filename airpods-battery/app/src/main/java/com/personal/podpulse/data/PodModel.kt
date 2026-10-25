package com.personal.podpulse.data

/** Apple audio devices, keyed by the model ID found in their BLE advertisement. */
enum class PodModel(val id: Int, val displayName: String, val single: Boolean = false) {
    AIRPODS_1(0x0220, "AirPods"),
    AIRPODS_2(0x0F20, "AirPods (2nd gen)"),
    AIRPODS_3(0x1320, "AirPods (3rd gen)"),
    AIRPODS_4(0x1920, "AirPods 4"),
    AIRPODS_4_ANC(0x1B20, "AirPods 4 (ANC)"),
    AIRPODS_PRO(0x0E20, "AirPods Pro"),
    AIRPODS_PRO_2(0x1420, "AirPods Pro 2"),
    AIRPODS_PRO_2_USBC(0x2420, "AirPods Pro 2"),
    AIRPODS_MAX(0x0A20, "AirPods Max", single = true),
    AIRPODS_MAX_USBC(0x1F20, "AirPods Max", single = true),
    UNKNOWN(-1, "AirPods");

    companion object {
        fun fromId(id: Int): PodModel = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}
