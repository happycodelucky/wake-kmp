package com.happycodelucky.wake.internal

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MagicPacketTest {
    private val mac =
        byteArrayOf(
            0xAA.toByte(),
            0xBB.toByte(),
            0xCC.toByte(),
            0xDD.toByte(),
            0xEE.toByte(),
            0xFF.toByte(),
        )

    @Test
    fun packet_is_102_bytes() {
        assertEquals(102, buildMagicPacket(mac).size)
        assertEquals(102, MAGIC_PACKET_LENGTH)
    }

    @Test
    fun packet_starts_with_six_0xFF_sync_bytes() {
        val packet = buildMagicPacket(mac)
        for (i in 0 until MAC_LENGTH) {
            assertEquals(0xFF.toByte(), packet[i], "byte $i should be 0xFF")
        }
    }

    @Test
    fun packet_repeats_the_mac_sixteen_times_after_the_sync_stream() {
        val packet = buildMagicPacket(mac)
        for (repetition in 0 until MAC_REPETITIONS) {
            val offset = MAC_LENGTH + repetition * MAC_LENGTH
            val slice = packet.copyOfRange(offset, offset + MAC_LENGTH)
            assertContentEquals(mac, slice, "repetition $repetition should equal the MAC")
        }
    }

    @Test
    fun all_zero_mac_still_produces_a_well_formed_packet() {
        val packet = buildMagicPacket(ByteArray(MAC_LENGTH))
        assertEquals(102, packet.size)
        for (i in 0 until MAC_LENGTH) {
            assertEquals(0xFF.toByte(), packet[i])
        }
        // Everything after the sync stream is 0x00.
        for (i in MAC_LENGTH until MAGIC_PACKET_LENGTH) {
            assertEquals(0x00.toByte(), packet[i])
        }
    }

    @Test
    fun wrong_length_mac_is_rejected() {
        assertFailsWith<IllegalArgumentException> { buildMagicPacket(ByteArray(5)) }
        assertFailsWith<IllegalArgumentException> { buildMagicPacket(ByteArray(7)) }
        assertFailsWith<IllegalArgumentException> { buildMagicPacket(ByteArray(0)) }
    }
}
