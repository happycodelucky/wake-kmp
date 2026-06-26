/*
 * Wake — JVM/Android UDP broadcaster (java.net).
 *
 * Shared by both the JVM desktop and Android targets — this file lives in the
 * `jvmShared` intermediate source set. `java.net.DatagramSocket` is the standard
 * JVM/Android UDP primitive (no Android API in sight); the send blocks, so it
 * runs on Dispatchers.IO (available on both, unlike Apple Kotlin/Native). Any
 * send failure is folded into WakeSendOutcome.Failed so the public API never
 * throws.
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
