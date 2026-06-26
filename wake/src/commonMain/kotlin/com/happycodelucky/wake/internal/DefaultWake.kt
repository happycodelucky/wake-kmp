/*
 * Wake — orchestration.
 *
 * `DefaultWake` is the platform-agnostic implementation of the public [Wake]
 * interface: parse → build → broadcast → map. It holds no platform code; the
 * only platform-specific dependency is the injected [UdpBroadcaster]. Both
 * platform factories (`Wake()` in appleMain / androidMain) construct a
 * `DefaultWake` with their platform broadcaster, and commonTest constructs one
 * with a fake — so the parse/build/map logic is exercised identically
 * everywhere.
 */
package com.happycodelucky.wake.internal

import com.happycodelucky.wake.Wake
import com.happycodelucky.wake.WakeResult

internal class DefaultWake(
    private val broadcaster: UdpBroadcaster,
) : Wake {
    override suspend fun wake(
        mac: String,
        broadcastAddress: String,
        port: Int,
    ): WakeResult {
        val macBytes =
            parseMacBytes(mac)
                ?: return WakeResult.InvalidMacAddress("could not parse MAC address: \"$mac\"")

        val packet = buildMagicPacket(macBytes)

        return when (val outcome = broadcaster.send(packet, broadcastAddress, port)) {
            is WakeSendOutcome.Sent -> WakeResult.Success
            is WakeSendOutcome.Failed -> WakeResult.NetworkError(outcome.message)
        }
    }
}
