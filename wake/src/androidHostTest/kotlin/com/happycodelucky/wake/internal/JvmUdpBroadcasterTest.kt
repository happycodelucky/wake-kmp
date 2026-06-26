package com.happycodelucky.wake.internal

import com.happycodelucky.wake.WakeResult
import kotlinx.coroutines.test.runTest
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Exercises the real `java.net` send path on the local JVM (androidHostTest
 * runs on the host, so sockets work without a device). Sends to loopback and
 * asserts the exact bytes arrive — the only broadcaster we can verify
 * end-to-end against a live socket.
 */
class JvmUdpBroadcasterTest {
    @Test
    fun send_delivers_the_exact_packet_to_a_loopback_receiver() =
        runTest {
            // Bind a receiver on an ephemeral loopback port.
            DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { receiver ->
                receiver.soTimeout = 2_000
                val port = receiver.localPort

                val packet =
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

                val outcome = JvmUdpBroadcaster().send(packet, "127.0.0.1", port)
                assertEquals(WakeSendOutcome.Sent, outcome)

                val buffer = ByteArray(256)
                val received = DatagramPacket(buffer, buffer.size)
                receiver.receive(received)

                assertEquals(102, received.length)
                assertContentEquals(packet, buffer.copyOf(received.length))
            }
        }

    @Test
    fun unresolvable_host_returns_Failed() =
        runTest {
            val packet = buildMagicPacket(ByteArray(6) { 0xFF.toByte() })

            val outcome = JvmUdpBroadcaster().send(packet, "this.is.not.a.real.host.invalid", 9)

            assertIs<WakeSendOutcome.Failed>(outcome)
        }

    @Test
    fun full_wake_through_performWake_succeeds_over_loopback() =
        runTest {
            DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { receiver ->
                receiver.soTimeout = 2_000
                val port = receiver.localPort

                val result =
                    performWake(
                        broadcaster = JvmUdpBroadcaster(),
                        mac = "AABBCCDDEEFF",
                        broadcastAddress = "127.0.0.1",
                        port = port,
                    )

                assertIs<WakeResult.Success>(result)

                val buffer = ByteArray(256)
                receiver.receive(DatagramPacket(buffer, buffer.size))
                assertEquals(0xFF.toByte(), buffer[0])
            }
        }
}
