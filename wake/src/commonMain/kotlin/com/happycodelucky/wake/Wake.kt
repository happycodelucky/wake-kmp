/*
 * Wake — public API surface (CLAUDE.md §8).
 *
 * Designed for Swift consumers as much as Kotlin ones: [WakeResult] becomes an
 * exhaustive Swift enum via SKIE (`onEnum(of:)`), the `suspend fun wake`
 * bridges to Swift `async`, and the default-argument overloads surface as
 * natural Swift call sites (`wake(mac:)`, `wake(mac:broadcastAddress:)`, …).
 *
 * Wake is stateless and one-shot: there is no `.shared` singleton, no observer
 * lifecycle, and no `AutoCloseable`. Each [Wake.wake] call builds the magic
 * packet, opens a UDP broadcast socket, sends, and closes it. Tests inject a
 * `FakeWake` (in `:wake-testing`) by constructor; there is no global override.
 */
package com.happycodelucky.wake

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
 * The outcome of a [Wake.wake] attempt.
 *
 * A `sealed interface` rather than `kotlin.Result` (CLAUDE.md §8): SKIE renders
 * it as an exhaustive Swift enum, so Swift consumers get a compiler-checked
 * `switch` with structured payloads instead of an opaque `KotlinResult`.
 *
 * [Success] means the packet was handed to the OS for broadcast. Wake-on-LAN
 * is fire-and-forget over UDP — there is no acknowledgement that the target
 * received the packet or actually powered on, so [Success] reports "sent", not
 * "the device is awake."
 */
public sealed interface WakeResult {
    /** The 102-byte magic packet was handed to the OS for broadcast. */
    public data object Success : WakeResult

    /**
     * The [mac][Wake.wake] string could not be parsed into a 48-bit address.
     *
     * @property reason a human-readable description of why parsing failed.
     */
    public data class InvalidMacAddress(
        val reason: String,
    ) : WakeResult

    /**
     * The underlying socket send failed (e.g. the broadcast address was
     * unresolvable, the socket could not be opened, or `sendto` returned an
     * error).
     *
     * @property message the platform error message or errno description.
     */
    public data class NetworkError(
        val message: String,
    ) : WakeResult
}

/**
 * Sends Wake-on-LAN / Wake-on-Wireless magic packets.
 *
 * Obtain an instance from the platform factory:
 *
 * - **Apple (iOS / macOS):** `Wake()` (no arguments), backed by POSIX UDP
 *   broadcast sockets.
 * - **Android:** `Wake()`, backed by `java.net.DatagramSocket`. Requires the
 *   `android.permission.INTERNET` permission, which the library declares in
 *   its own manifest so it merges into the consuming app.
 *
 * Instances are stateless and reusable — there is nothing to close. Each
 * [wake] call is independent.
 *
 * ### Kotlin
 *
 * ```kotlin
 * val wake = Wake()
 * when (val result = wake.wake("AA:BB:CC:DD:EE:FF")) {
 *     is WakeResult.Success -> println("magic packet sent")
 *     is WakeResult.InvalidMacAddress -> println("bad MAC: ${result.reason}")
 *     is WakeResult.NetworkError -> println("send failed: ${result.message}")
 * }
 * ```
 *
 * ### Swift
 *
 * ```swift
 * let wake = Wake()
 * switch await wake.wake(mac: "AA:BB:CC:DD:EE:FF") {
 * case .success: print("magic packet sent")
 * case let .invalidMacAddress(reason): print("bad MAC: \(reason)")
 * case let .networkError(message): print("send failed: \(message)")
 * }
 * ```
 */
public interface Wake {
    /**
     * Build the magic packet for [mac] and broadcast it over UDP.
     *
     * The MAC may be formatted with colons (`AA:BB:CC:DD:EE:FF`), hyphens
     * (`aa-bb-cc-dd-ee-ff`), or no separators (`aabbccddeeff`); parsing is
     * case-insensitive. Malformed input returns [WakeResult.InvalidMacAddress]
     * rather than throwing — this method never throws across the Swift
     * boundary.
     *
     * @param mac the target device's hardware (MAC) address.
     * @param broadcastAddress the IPv4 broadcast target. Defaults to the
     *   limited broadcast [DEFAULT_BROADCAST_ADDRESS]; pass a subnet-directed
     *   broadcast to cross a router that forwards directed broadcasts.
     * @param port the destination UDP port. Defaults to [DEFAULT_WAKE_PORT].
     * @return [WakeResult.Success] when the packet was sent,
     *   [WakeResult.InvalidMacAddress] when [mac] is unparseable, or
     *   [WakeResult.NetworkError] when the socket send fails.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName(swiftName = "wake")
    public suspend fun wake(
        mac: String,
        broadcastAddress: String = DEFAULT_BROADCAST_ADDRESS,
        port: Int = DEFAULT_WAKE_PORT,
    ): WakeResult
}
