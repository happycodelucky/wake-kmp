/*
 * Wake — the platform seam.
 *
 * `UdpBroadcaster` is the single point of platform divergence: it sends an
 * already-built packet as a UDP broadcast datagram. Implemented over POSIX
 * sockets on Apple (`PosixUdpBroadcaster`) and `java.net.DatagramSocket` on
 * Android (`JvmUdpBroadcaster`). Keeping this an interface (CLAUDE.md §8,
 * favouring interface + factory over top-level `expect`/`actual`) lets
 * commonTest substitute a recording fake and unit-test `DefaultWake`'s
 * orchestration with no real sockets.
 */
package com.happycodelucky.wake.internal

/**
 * Sends a UDP broadcast datagram. The only platform-specific behaviour in the
 * library.
 */
internal interface UdpBroadcaster {
    /**
     * Send [packet] as a single UDP broadcast datagram to [broadcastAddress] on
     * [port].
     *
     * Implementations enable the broadcast socket option, perform the send off
     * the caller's thread (the platform send can block), and never throw —
     * failures are returned as [WakeSendOutcome.Failed].
     */
    suspend fun send(
        packet: ByteArray,
        broadcastAddress: String,
        port: Int,
    ): WakeSendOutcome
}

/**
 * The narrow internal outcome of a [UdpBroadcaster.send].
 * [com.happycodelucky.wake.internal.DefaultWake] maps this onto the public
 * [com.happycodelucky.wake.WakeResult].
 */
internal sealed interface WakeSendOutcome {
    /** The datagram was handed to the OS. */
    data object Sent : WakeSendOutcome

    /** The send failed; [message] describes the platform error. */
    data class Failed(
        val message: String,
    ) : WakeSendOutcome
}
