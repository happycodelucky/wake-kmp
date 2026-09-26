/*
 * Wake — public API surface (CLAUDE.md §8).
 *
 * Two audiences, each given its own native error idiom:
 *
 * - **Kotlin** gets the standard library's `kotlin.Result<Unit>` from [Wake.up],
 *   whose failure is always a [WakeException] (a sealed hierarchy, so a `when`
 *   over it is exhaustive). `isSuccess`, `getOrThrow`, `onFailure`, `fold`,
 *   `exceptionOrNull`… all work unchanged, and tests assert on it like any other
 *   `Result`.
 * - **Swift** never sees `kotlin.Result` — it is a value class, which ObjC export
 *   erases to an untyped `Any?`, so [Wake.up] is `@HiddenFromObjC`. Instead the
 *   `__up` bridge ([Wake.upOrThrow]) throws the [WakeException] across the
 *   boundary, and the bundled `src/appleMain/swift/Wake+Up.swift` re-exposes it as
 *   `static func up(mac:) async throws`, rethrowing a native Swift `WakeError`
 *   enum (`Error`, `Equatable`, `LocalizedError`).
 *
 * Wake is stateless and one-shot: there is no observer lifecycle and no
 * `AutoCloseable`. Each [Wake.up] call builds the magic packet, opens a UDP
 * broadcast socket, sends, and closes it. Tests inject a `FakeWake`
 * (in `:wake-testing`) by constructor; there is no global override.
 */
package com.happycodelucky.wake

import com.happycodelucky.wake.internal.defaultBroadcaster
import com.happycodelucky.wake.internal.performWake
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCName
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.native.ObjCName
import kotlin.native.ShouldRefineInSwift

/**
 * Default Wake-on-LAN UDP port.
 *
 * Port 9 (the "discard" service) is the most common WoL convention; 7 (echo)
 * and 0 are also seen in the wild. The port rarely matters for waking — the
 * target NIC matches on the magic-packet payload, not the destination port —
 * but 9 is the safe interoperable default.
 */
public const val DEFAULT_WAKE_PORT: Int = 9

/**
 * Default IPv4 broadcast address — the limited (local-segment) broadcast
 * `255.255.255.255`.
 *
 * This reaches every host on the sender's own link. To wake a host on a
 * different subnet across a router that forwards directed broadcasts, pass that
 * subnet's directed-broadcast address (e.g. `192.168.1.255`) explicitly.
 */
public const val DEFAULT_BROADCAST_ADDRESS: String = "255.255.255.255"

/**
 * Sends Wake-on-LAN / Wake-on-Wireless magic packets.
 *
 * `Wake` is a stateless, one-shot entry point — there is no instance to
 * construct and nothing to close. Each [up] call builds the 102-byte magic
 * packet for the target MAC (six `0xFF` bytes followed by the 6-byte hardware
 * address repeated 16×), opens a UDP broadcast socket, hands the datagram to
 * the OS, and closes the socket.
 *
 * The platform UDP send is selected automatically:
 *
 * - **Apple (iOS / macOS):** POSIX UDP broadcast sockets. On iOS the consuming
 *   app must declare `NSLocalNetworkUsageDescription` in its Info.plist — the
 *   first broadcast send triggers the Local Network privacy prompt, and without
 *   the string iOS silently drops the packet (macOS does not prompt). If
 *   broadcasts still don't leave the device, the app may additionally need the
 *   `com.apple.developer.networking.multicast` entitlement.
 * - **Android:** `java.net.DatagramSocket`. Requires the
 *   `android.permission.INTERNET` permission, which the library declares in
 *   its own manifest so it merges into the consuming app.
 *
 * ### Kotlin
 *
 * ```kotlin
 * Wake.up("AA:BB:CC:DD:EE:FF")
 *     .onSuccess { println("magic packet sent") }
 *     .onFailure { e ->
 *         when (e as WakeException) {
 *             is WakeException.InvalidMacAddress -> println("bad MAC: ${e.mac}")
 *             is WakeException.NetworkError -> println("send failed: ${e.message}")
 *         }
 *     }
 * ```
 *
 * ### Swift
 *
 * The bundled `Wake.up(mac:)` extension (in `src/appleMain/swift/`) throws a
 * native `WakeError` enum:
 *
 * ```swift
 * do {
 *     try await Wake.up(mac: "AA:BB:CC:DD:EE:FF")
 * } catch let error as WakeError {
 *     switch error {
 *     case .invalidMacAddress(let mac): print("bad MAC: \(mac)")
 *     case .networkError(let message): print("send failed: \(message)")
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalObjCName::class, ExperimentalObjCRefinement::class)
public object Wake {
    /**
     * Build the magic packet for [mac] and broadcast it over UDP.
     *
     * The MAC may be formatted with colons (`AA:BB:CC:DD:EE:FF`), hyphens
     * (`aa-bb-cc-dd-ee-ff`), or no separators (`aabbccddeeff`); parsing is
     * case-insensitive.
     *
     * Never throws (other than coroutine cancellation): every failure is a
     * [Result.failure] holding a [WakeException] —
     * [WakeException.InvalidMacAddress] for unparseable input, or
     * [WakeException.NetworkError] when the socket send fails.
     *
     * The send is fire-and-forget: success means the datagram was handed to the
     * OS, not that the target received it or powered on — on iOS, a missing
     * `NSLocalNetworkUsageDescription` is one way a success can still mean
     * nothing left the device (see [Wake]).
     *
     * Hidden from Swift, where `kotlin.Result` has no representation; Swift
     * calls the bundled `Wake.up(mac:)` instead (see [upOrThrow]).
     *
     * @param mac the target device's hardware (MAC) address.
     * @param broadcastAddress the IPv4 broadcast target. Defaults to the
     *   limited broadcast [DEFAULT_BROADCAST_ADDRESS]; pass a subnet-directed
     *   broadcast to cross a router that forwards directed broadcasts.
     * @param port the destination UDP port. Defaults to [DEFAULT_WAKE_PORT].
     * @return success when the packet was sent, otherwise a failure whose
     *   exception is a [WakeException].
     */
    @HiddenFromObjC
    public suspend fun up(
        mac: String,
        broadcastAddress: String = DEFAULT_BROADCAST_ADDRESS,
        port: Int = DEFAULT_WAKE_PORT,
    ): Result<Unit> =
        performWake(
            broadcaster = defaultBroadcaster(),
            mac = mac,
            broadcastAddress = broadcastAddress,
            port = port,
        )

    /**
     * Swift bridge for [up]: the same send, with a failure thrown rather than
     * returned.
     *
     * Exists only because `kotlin.Result` cannot cross ObjC export. It renders
     * in Swift as the refined `Wake.shared.__up(mac:…)`, which the bundled
     * `Wake.up(mac:)` wraps to rethrow the [WakeException] as a Swift `WakeError`.
     * Kotlin callers use [up] (it is opt-in-gated behind [InternalWakeSwiftApi]).
     *
     * Deliberately has no default arguments: SKIE does not carry `@Throws` onto
     * the overloads it generates for defaults (SKIE #151), so throwing through
     * one would abort the process. The Swift wrapper supplies the defaults.
     *
     * @throws WakeException when the MAC is unparseable or the send fails.
     */
    @InternalWakeSwiftApi
    @ShouldRefineInSwift
    @ObjCName(swiftName = "up")
    @Throws(WakeException::class, CancellationException::class)
    public suspend fun upOrThrow(
        mac: String,
        broadcastAddress: String,
        port: Int,
    ): Unit = up(mac, broadcastAddress, port).getOrThrow()
}
