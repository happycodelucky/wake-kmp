package com.happycodelucky.wake.internal

import com.happycodelucky.wake.DEFAULT_BROADCAST_ADDRESS
import com.happycodelucky.wake.DEFAULT_WAKE_PORT
import com.happycodelucky.wake.WakeResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * White-box test of [DefaultWake]'s orchestration. Uses an in-test recording
 * [UdpBroadcaster] (visible because it is `internal` to `:wake` and this is the
 * `:wake` test source set) to assert the parse → build → broadcast → map chain
 * without opening a real socket. The consumer-facing black-box fake is
 * `FakeWake` in `:wake-testing`.
 */
class DefaultWakeTest {
    /** Records the last send and returns a programmable outcome. */
    private class RecordingBroadcaster(
        private val outcome: WakeSendOutcome = WakeSendOutcome.Sent,
    ) : UdpBroadcaster {
        var sentPacket: ByteArray? = null
        var sentAddress: String? = null
        var sentPort: Int? = null
        var sendCount: Int = 0

        override suspend fun send(
            packet: ByteArray,
            broadcastAddress: String,
            port: Int,
        ): WakeSendOutcome {
            sendCount++
            sentPacket = packet
            sentAddress = broadcastAddress
            sentPort = port
            return outcome
        }
    }

    @Test
    fun valid_mac_builds_packet_and_returns_Success() =
        runTest {
            val broadcaster = RecordingBroadcaster()
            val wake = DefaultWake(broadcaster)

            val result = wake.wake("AA:BB:CC:DD:EE:FF")

            assertIs<WakeResult.Success>(result)
            assertEquals(1, broadcaster.sendCount)
            assertEquals(102, broadcaster.sentPacket?.size)
            // The packet is the real magic packet for the parsed MAC.
            val expectedPacket =
                buildMagicPacket(
                    byteArrayOf(
                        0xAA.toByte(),
                        0xBB.toByte(),
                        0xCC.toByte(),
                        0xDD.toByte(),
                        0xEE.toByte(),
                        0xFF.toByte(),
                    ),
                )
            assertContentEquals(expectedPacket, broadcaster.sentPacket)
        }

    @Test
    fun defaults_pass_through_to_the_broadcaster() =
        runTest {
            val broadcaster = RecordingBroadcaster()

            DefaultWake(broadcaster).wake("AABBCCDDEEFF")

            assertEquals(DEFAULT_BROADCAST_ADDRESS, broadcaster.sentAddress)
            assertEquals(DEFAULT_WAKE_PORT, broadcaster.sentPort)
        }

    @Test
    fun explicit_address_and_port_pass_through() =
        runTest {
            val broadcaster = RecordingBroadcaster()

            DefaultWake(broadcaster).wake("AABBCCDDEEFF", broadcastAddress = "192.168.1.255", port = 7)

            assertEquals("192.168.1.255", broadcaster.sentAddress)
            assertEquals(7, broadcaster.sentPort)
        }

    @Test
    fun invalid_mac_returns_InvalidMacAddress_and_never_sends() =
        runTest {
            val broadcaster = RecordingBroadcaster()

            val result = DefaultWake(broadcaster).wake("not-a-mac")

            assertIs<WakeResult.InvalidMacAddress>(result)
            assertEquals(0, broadcaster.sendCount)
            assertNull(broadcaster.sentPacket)
        }

    @Test
    fun broadcaster_failure_maps_to_NetworkError() =
        runTest {
            val broadcaster = RecordingBroadcaster(WakeSendOutcome.Failed("sendto errno=49"))

            val result = DefaultWake(broadcaster).wake("AA:BB:CC:DD:EE:FF")

            val error = assertIs<WakeResult.NetworkError>(result)
            assertEquals("sendto errno=49", error.message)
        }
}
