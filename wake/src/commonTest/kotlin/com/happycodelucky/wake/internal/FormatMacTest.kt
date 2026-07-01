package com.happycodelucky.wake.internal

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Tests for [formatMac], the inverse of [parseMacBytes]. Pure byte→text
 * formatting; no platform mocking, mirrors [MacAddressParsingTest]'s style.
 */
class FormatMacTest {
    @Test
    fun formats_uppercase_colon_separated() {
        val bytes =
            byteArrayOf(
                0xAA.toByte(),
                0xBB.toByte(),
                0xCC.toByte(),
                0xDD.toByte(),
                0xEE.toByte(),
                0xFF.toByte(),
            )
        assertEquals("AA:BB:CC:DD:EE:FF", formatMac(bytes))
    }

    @Test
    fun zero_pads_single_digit_octets() {
        assertEquals("01:02:03:04:05:06", formatMac(byteArrayOf(1, 2, 3, 4, 5, 6)))
        assertEquals("00:0A:00:0B:00:0C", formatMac(byteArrayOf(0, 0x0A, 0, 0x0B, 0, 0x0C)))
    }

    @Test
    fun formats_all_zero_and_all_ff() {
        assertEquals("00:00:00:00:00:00", formatMac(ByteArray(MAC_LENGTH)))
        assertEquals("FF:FF:FF:FF:FF:FF", formatMac(ByteArray(MAC_LENGTH) { 0xFF.toByte() }))
    }

    @Test
    fun round_trips_with_parseMacBytes() {
        val original =
            byteArrayOf(
                0x12.toByte(),
                0x34.toByte(),
                0x56.toByte(),
                0x78.toByte(),
                0x9A.toByte(),
                0xBC.toByte(),
            )
        // bytes → text → bytes
        val reparsed = assertNotNull(parseMacBytes(formatMac(original)))
        assertContentEquals(original, reparsed)

        // text → bytes → text (case-normalized to uppercase)
        val bytes = assertNotNull(parseMacBytes("aa:bb:cc:dd:ee:ff"))
        assertEquals("AA:BB:CC:DD:EE:FF", formatMac(bytes))
    }

    @Test
    fun rejects_wrong_length() {
        assertFailsWith<IllegalArgumentException> { formatMac(ByteArray(MAC_LENGTH - 1)) }
        assertFailsWith<IllegalArgumentException> { formatMac(ByteArray(MAC_LENGTH + 1)) }
        assertFailsWith<IllegalArgumentException> { formatMac(ByteArray(0)) }
    }
}
