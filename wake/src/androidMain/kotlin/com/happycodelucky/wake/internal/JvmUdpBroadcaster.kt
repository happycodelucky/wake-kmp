/*
 * Wake — Android UDP broadcaster (java.net).
 *
 * CLAUDE.md §5 Step 3: AndroidX / platform first-party. `java.net.DatagramSocket`
 * is the standard JVM/Android UDP primitive; the send blocks, so it runs on
 * Dispatchers.IO (available on Android, unlike Apple Kotlin/Native). Any send
 * failure is folded into WakeSendOutcome.Failed so the public API never throws.
 */
package com.happycodelucky.wake.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

internal class JvmUdpBroadcaster : UdpBroadcaster {
    override suspend fun send(
        packet: ByteArray,
        broadcastAddress: String,
        port: Int,
    ): WakeSendOutcome =
        withContext(Dispatchers.IO) {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val address = InetAddress.getByName(broadcastAddress)
                    socket.send(DatagramPacket(packet, packet.size, address, port))
                }
                WakeSendOutcome.Sent
            } catch (
                // Any send failure folds into NetworkError, so a broad catch is intentional.
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                WakeSendOutcome.Failed(e.message ?: e::class.simpleName ?: "UDP send failed")
            }
        }
}
