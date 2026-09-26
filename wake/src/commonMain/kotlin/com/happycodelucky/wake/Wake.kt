/*
 * Wake — public API surface (CLAUDE.md §8).
 *
 * [Wake.up] returns `Outcome<Unit>` (from `:outcome`), a Swift-friendly mirror of
 * `kotlin.Result`. A failure always holds a [WakeException] — a sealed hierarchy,
 * so a Kotlin `when` over it is exhaustive and Swift switches on it with SKIE's
 * `onEnum(of:)`.
 *
 * - **Kotlin** gets the whole `Result` API on it (`isSuccess`, `getOrThrow`,
 *   `fold`, `onFailure`, `map`…), and `toResult()` for the stdlib type.
 * - **Swift** unwraps it with `:outcome`'s bundled `get()`: success returns,
 *   failure throws the [WakeException] itself as a Swift `Error`.
 *
 * `up` never throws across the boundary; SKIE still renders the suspend call as
 * `async throws`, but that `throws` only carries cancellation.
 *
 * Wake is stateless and one-shot: there is no observer lifecycle and no
 * `AutoCloseable`. Each [Wake.up] call builds the magic packet, opens a UDP
 * broadcast socket, sends, and closes it. Tests inject a `FakeWake`
 * (in `:wake-testing`) by constructor; there is no global override.
 *
 * `Wake` is a Kotlin `object` so the call site reads as the `Wake.up(...)` pun
 * in both languages. SKIE renders the object's single instance as `Wake.shared`
 * in Swift; a hand-written Swift extension in `src/appleMain/swift/` re-exposes
 * it as the static `Wake.up(mac:)`.
 */
package com.happycodelucky.wake

import com.happycodelucky.outcome.Outcome
import com.happycodelucky.outcome.toOutcome
import com.happycodelucky.wake.internal.defaultBroadcaster
import com.happycodelucky.wake.internal.performWake
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

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
 * ```swift
 * do {
 *     try await Wake.up(mac: "AA:BB:CC:DD:EE:FF").get()
 * } catch let error as WakeException {
 *     switch onEnum(of: error) {
 *     case let .invalidMacAddress(e): print("bad MAC: \(e.mac)")
 *     case let .networkError(e): print("send failed: \(e.message)")
 *     }
 * }
 * ```
 */
public object Wake {
    /**
     * Build the magic packet for [mac] and broadcast it over UDP.
     *
     * The MAC may be formatted with colons (`AA:BB:CC:DD:EE:FF`), hyphens
     * (`aa-bb-cc-dd-ee-ff`), or no separators (`aabbccddeeff`); parsing is
     * case-insensitive.
     *
     * Never throws (other than coroutine cancellation): every failure is a
     * failed [Outcome] holding a [WakeException] —
     * [WakeException.InvalidMacAddress] for unparseable input, or
     * [WakeException.NetworkError] when the socket send fails.
     *
     * The send is fire-and-forget: success means the datagram was handed to the
     * OS, not that the target received it or powered on — on iOS, a missing
     * `NSLocalNetworkUsageDescription` is one way a success can still mean
     * nothing left the device (see [Wake]).
     *
     * @param mac the target device's hardware (MAC) address.
     * @param broadcastAddress the IPv4 broadcast target. Defaults to the
     *   limited broadcast [DEFAULT_BROADCAST_ADDRESS]; pass a subnet-directed
     *   broadcast to cross a router that forwards directed broadcasts.
     * @param port the destination UDP port. Defaults to [DEFAULT_WAKE_PORT].
     * @return success when the packet was sent, otherwise a failure holding a
     *   [WakeException].
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName(swiftName = "up")
    public suspend fun up(
        mac: String,
        broadcastAddress: String = DEFAULT_BROADCAST_ADDRESS,
        port: Int = DEFAULT_WAKE_PORT,
    ): Outcome<Unit> =
        performWake(
            broadcaster = defaultBroadcaster(),
            mac = mac,
            broadcastAddress = broadcastAddress,
            port = port,
        ).toOutcome()
}
