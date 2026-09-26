/*
 * Wake — the injectable send seam for Kotlin consumers.
 *
 * `Wake.up(...)` is a static call on a stateless `object`: zero ceremony, but
 * nothing to substitute in a unit test. [WakeSender] is the sanctioned
 * alternative — a one-method interface a feature can depend on and have injected
 * by constructor. Production passes [Wake.asSender]; tests pass `FakeWake` from
 * `:wake-testing`. Callers who don't need a test seam keep calling [Wake.up]
 * directly.
 *
 * Kotlin-only (`@HiddenFromObjC`) for now. Swift consumers wanting a seam
 * declare their own protocol over `Wake.up(mac:)`. (Swift can construct an
 * `Outcome` via `init(value:)` / `init(failure:)`, so exporting this for Swift
 * fakes is a possible follow-up.)
 */
@file:OptIn(ExperimentalObjCRefinement::class)

package com.happycodelucky.wake

import com.happycodelucky.outcome.Outcome
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * An injectable abstraction over [Wake.up].
 *
 * Depend on this in a feature you want to unit-test without sending a real
 * packet, and have it supplied by constructor:
 *
 * ```kotlin
 * class WakeMyDesktop(private val wake: WakeSender = Wake.asSender()) {
 *     suspend fun run(): Outcome<Unit> = wake.up("AA:BB:CC:DD:EE:FF")
 * }
 * ```
 *
 * In production the default argument wires the real [Wake.asSender]; in tests
 * pass a `FakeWake` (from `:wake-testing`). Code that has no need to substitute
 * the sender can ignore this type entirely and call [Wake.up] directly.
 *
 * The single method mirrors [Wake.up] exactly — same parameters, same defaults,
 * same [Outcome] — so swapping a direct `Wake.up(...)` call for an injected
 * `sender.up(...)` is a drop-in change.
 */
@HiddenFromObjC
public interface WakeSender {
    /**
     * Build the magic packet for [mac] and broadcast it over UDP.
     *
     * Behaves identically to [Wake.up]: never throws (other than cancellation);
     * a failure holds [WakeException.InvalidMacAddress] for unparseable input or
     * [WakeException.NetworkError] for a failed send.
     *
     * @param mac the target device's hardware (MAC) address.
     * @param broadcastAddress the IPv4 broadcast target. Defaults to
     *   [DEFAULT_BROADCAST_ADDRESS].
     * @param port the destination UDP port. Defaults to [DEFAULT_WAKE_PORT].
     * @return success, or a failure holding a [WakeException].
     */
    public suspend fun up(
        mac: String,
        broadcastAddress: String = DEFAULT_BROADCAST_ADDRESS,
        port: Int = DEFAULT_WAKE_PORT,
    ): Outcome<Unit>
}

/**
 * A [WakeSender] backed by the real [Wake.up] platform send.
 *
 * This is the production wiring: `Wake.asSender().up(...)` is exactly
 * `Wake.up(...)`. Use it as the default constructor argument for a feature that
 * depends on a [WakeSender].
 */
@HiddenFromObjC
@Suppress("UnusedReceiverParameter") // Scoped to Wake for discoverability (Wake.asSender()).
public fun Wake.asSender(): WakeSender = RealWakeSender

/** The single [WakeSender] that delegates straight to [Wake.up]. */
private object RealWakeSender : WakeSender {
    override suspend fun up(
        mac: String,
        broadcastAddress: String,
        port: Int,
    ): Outcome<Unit> = Wake.up(mac, broadcastAddress, port)
}
