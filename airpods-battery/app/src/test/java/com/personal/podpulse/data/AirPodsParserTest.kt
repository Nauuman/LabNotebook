package com.personal.podpulse.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPodsParserTest {

    private fun packet(model: Int, status: Int, pods: Int, flags: Int): ByteArray =
        ByteArray(27).also {
            it[0] = 0x07
            it[1] = 0x19
            it[2] = 0x01
            it[3] = (model shr 8).toByte()
            it[4] = (model and 0xFF).toByte()
            it[5] = status.toByte()
            it[6] = pods.toByte()
            it[7] = flags.toByte()
        }

    @Test
    fun parsesAirPodsPro2WithPrimaryBit() {
        // status 0x2B: primary bit set (not flipped), in-ear flags 0x02 and 0x08
        val s = AirPodsParser.parse(packet(0x1420, 0x2B, 0x87, 0x45), -55, 1L)!!
        assertEquals(PodModel.AIRPODS_PRO_2, s.model)
        assertEquals(70, s.left)
        assertEquals(80, s.right)
        assertEquals(50, s.case)
        assertTrue(s.caseCharging)
        assertFalse(s.leftCharging)
        assertFalse(s.rightCharging)
        assertTrue(s.leftInEar)
        assertTrue(s.rightInEar)
        assertEquals(-55, s.rssi)
    }

    @Test
    fun swapsBudsWhenFlipped() {
        // status 0x08: flipped (no 0x20 bit); only the 0x08 in-ear flag is set
        val s = AirPodsParser.parse(packet(0x0E20, 0x08, 0x87, 0x1A), -60, 1L)!!
        assertEquals(PodModel.AIRPODS_PRO, s.model)
        assertEquals(80, s.left)
        assertEquals(70, s.right)
        assertEquals(100, s.case)
        assertTrue(s.rightCharging) // flipped: 0x01 means right
        assertFalse(s.leftCharging)
        assertTrue(s.leftInEar) // flipped: 0x08 means left
        assertFalse(s.rightInEar)
    }

    @Test
    fun unavailableNibbleIsNull() {
        val s = AirPodsParser.parse(packet(0x0F20, 0x20, 0xF5, 0x0F), -50, 1L)!!
        assertEquals(50, s.left)
        assertNull(s.right)
        assertNull(s.case)
    }

    @Test
    fun parsesSingleBatteryHeadphones() {
        val s = AirPodsParser.parse(packet(0x0A20, 0x20, 0x06, 0x10), -50, 1L)!!
        assertEquals(PodModel.AIRPODS_MAX, s.model)
        assertEquals(60, s.left)
        assertTrue(s.leftCharging)
        assertNull(s.right)
    }

    @Test
    fun rejectsOtherMessages() {
        val other = packet(0x1420, 0x2B, 0x87, 0x45).also { it[0] = 0x10 }
        assertNull(AirPodsParser.parse(other, -50, 1L))
        assertNull(AirPodsParser.parse(ByteArray(4), -50, 1L))
        assertNull(AirPodsParser.parse(null, -50, 1L))
        assertNull(AirPodsParser.parse(packet(0x1420, 0x20, 0xFF, 0x0F), -50, 1L))
    }

    @Test
    fun chargingBudIsNeverInEar() {
        // not flipped; in-ear flags set for both, but left (0x01) and right (0x02) charging
        val s = AirPodsParser.parse(packet(0x1420, 0x2B, 0x99, 0x35), -50, 1L)!!
        assertTrue(s.leftCharging)
        assertTrue(s.rightCharging)
        assertFalse(s.leftInEar)
        assertFalse(s.rightInEar)
    }

    @Test
    fun bothInCaseFlag() {
        // 0x24: this bud is left, both buds in case; not charging (e.g. full)
        val s = AirPodsParser.parse(packet(0x1420, 0x24, 0xAA, 0x08), -50, 1L)!!
        assertTrue(s.leftInCase)
        assertTrue(s.rightInCase)
        assertFalse(s.leftInEar)
    }

    @Test
    fun oneInCaseFlagPicksTheRightBud() {
        // 0x50: flipped, so this bud is the right one; one bud in case and it's this one
        val s = AirPodsParser.parse(packet(0x1420, 0x50, 0x88, 0x08), -50, 1L)!!
        assertTrue(s.rightInCase)
        assertFalse(s.leftInCase)
        val other = AirPodsParser.parse(packet(0x1420, 0x30, 0x88, 0x08), -50, 1L)!!
        // 0x30: this bud is left and not in the case, one bud in case -> right is in case
        assertTrue(other.rightInCase)
        assertFalse(other.leftInCase)
    }

    @Test
    fun chargingImpliesInCase() {
        val s = AirPodsParser.parse(packet(0x1420, 0x20, 0x88, 0x18), -50, 1L)!!
        assertTrue(s.leftCharging)
        assertTrue(s.leftInCase)
        assertFalse(s.rightInCase)
    }
}
