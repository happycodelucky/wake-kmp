package com.happycodelucky.wake.internal

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MacAddressParsingTest {
    private val expected =
        byteArrayOf(
            0xAA.toByte(),
            0xBB.toByte(),
            0xCC.toByte(),
            0xDD.toByte(),
            0xEE.toByte(),
            0xFF.toByte(),
        )

    @Test
    fun parses_colon_separated() {
        assertContentEquals(expected, parseMacBytes("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun parses_hyphen_separated() {
        assertContentEquals(expected, parseMacBytes("AA-BB-CC-DD-EE-FF"))
    }

    @Test
    fun parses_bare_hex() {
        assertContentEquals(expected, parseMacBytes("AABBCCDDEEFF"))
    }

    @Test
    fun parsing_is_case_insensitive() {
        assertContentEquals(expected, parseMacBytes("aa:bb:cc:dd:ee:ff"))
        assertContentEquals(expected, parseMacBytes("aAbBcCdDeEfF"))
    }

    @Test
    fun trims_surrounding_whitespace() {
        assertContentEquals(expected, parseMacBytes("  AA:BB:CC:DD:EE:FF  "))
    }

    @Test
    fun parses_lowest_and_highest_values() {
        assertContentEquals(ByteArray(6), parseMacBytes("00:00:00:00:00:00"))
        assertContentEquals(
            ByteArray(6) { 0xFF.toByte() },
            parseMacBytes("FF:FF:FF:FF:FF:FF"),
        )
    }

    @Test
    fun rejects_too_few_octets() {
        assertNull(parseMacBytes("AA:BB:CC:DD:EE"))
        assertNull(parseMacBytes("AABBCCDDEE"))
    }

    @Test
    fun rejects_too_many_octets() {
        assertNull(parseMacBytes("AA:BB:CC:DD:EE:FF:00"))
        assertNull(parseMacBytes("AABBCCDDEEFF00"))
    }

    @Test
    fun rejects_non_hex_characters() {
        assertNull(parseMacBytes("AA:BB:CC:DD:EE:GG"))
        assertNull(parseMacBytes("ZZBBCCDDEEFF"))
    }

    @Test
    fun rejects_unsupported_separators() {
        assertNull(parseMacBytes("AA.BB.CC.DD.EE.FF"))
        assertNull(parseMacBytes("AA BB CC DD EE FF"))
    }

    @Test
    fun rejects_empty_and_blank() {
        assertNull(parseMacBytes(""))
        assertNull(parseMacBytes("   "))
    }

    @Test
    fun mac_address_of_round_trips_through_value_class() {
        val bytes = assertNotNull(parseMacBytes("AA:BB:CC:DD:EE:FF"))
        val address = MacAddress.of(bytes)
        assertContentEquals(expected, address.bytes)
    }
}
