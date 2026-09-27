/*
 * Wake — orchestration.
 *
 * [performWake] is the platform-agnostic core of the public [Wake.up] entry
 * point: parse → build → broadcast → map. It holds no platform code; the only
 * platform-specific dependency is the injected [UdpBroadcaster]. Production
 * calls it with the platform broadcaster (`Wake.up` supplies
 * `defaultBroadcaster()`), and commonTest calls it with a recording fake — so
 * the parse/build/map logic is exercised identically everywhere, without the
 * public [Wake] object needing a constructor seam.
 */
package com.happycodelucky.wake.internal

import com.happycodelucky.wake.WakeException

/**
 * Parse [mac], build its magic packet, broadcast it via [broadcaster], and map
 * the outcome onto a `Result` whose failure is a [WakeException].
 *
 * This is the testable seam behind [com.happycodelucky.wake.Wake.up]: it takes
 * the broadcaster as a parameter so unit tests can substitute a recording fake
 * for the real platform socket. It never throws — unparseable input and send
 * failures are returned as a failed `Result` holding
 * [WakeException.InvalidMacAddress] / [WakeException.NetworkError].
 *
 * @param broadcaster the UDP send seam (platform implementation in production,
 *   a fake in tests).
 * @param mac the target device's hardware (MAC) address.
 * @param broadcastAddress the IPv4 broadcast target.
 * @param port the destination UDP port.
 * @return success, or a failure holding a [WakeException].
 */
internal suspend fun performWake(
    broadcaster: UdpBroadcaster,
    mac: String,
    broadcastAddress: String,
    port: Int,
): Result<Unit> {
    val macBytes =
        parseMacBytes(mac)
            ?: return Result.failure(WakeException.InvalidMacAddress(mac))

    val packet = buildMagicPacket(macBytes)

    return when (val outcome = broadcaster.send(packet, broadcastAddress, port)) {
        is WakeSendOutcome.Sent -> Result.success(Unit)
        is WakeSendOutcome.Failed -> Result.failure(WakeException.NetworkError(outcome.message, outcome.cause))
    }
}
