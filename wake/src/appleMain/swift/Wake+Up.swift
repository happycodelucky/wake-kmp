//
// Wake — Swift-side ergonomic sweetener for `Wake.up(mac:)`.
//
// `Wake` is a Kotlin `object` (a singleton), so SKIE renders it on the Swift
// side as a `WakeKit.Wake` class whose one instance is reached through a
// generated `shared` accessor. The raw call site would read:
//
//     try await Wake.shared.up(mac: "AA:BB:CC:DD:EE:FF", broadcastAddress: …, port: …)
//
// — `Wake.shared` is the object singleton; `.up(...)` is the suspend member
// bridged to Swift `async throws`. That works but leaks the `.shared` plumbing
// into every call site. This extension adds a *static* `up(...)` on `Wake` so
// the public surface for Swift consumers is simply the intended pun:
//
//     try await Wake.up(mac: "AA:BB:CC:DD:EE:FF")
//
// matching the Kotlin call site (`Wake.up("AA:BB:CC:DD:EE:FF")`) one-for-one.
//
// Name resolution is unambiguous and non-recursive: `Wake.up(...)` with no
// receiver is THIS static method; `Wake.shared.up(...)` has an instance
// receiver (`Wake.shared`, the K/N-generated class property) and resolves to the
// SKIE-generated member. The two never collide.
//
// This file lives in `:wake`'s `src/appleMain/swift/`, which SKIE
// auto-discovers and compiles into the same framework module (`WakeKit`) as the
// SKIE-generated Swift wrappers. No additional Gradle wiring is required — the
// convention plugin already sets `skie.swiftBundling.enabled = false`, so this
// extension is compiled directly into `WakeKit.framework` rather than
// re-extracted and recompiled in downstream modules. Mirrors the sibling
// `:reachable` project's `Reachability+Shared.swift` pattern: a static member on
// an extension that delegates to the SKIE singleton accessor. Zero runtime cost.
//
// Bridging facts, verified against SKIE 0.10.12's generated `Wake.Wake.swift`
// for this module:
//   - The singleton accessor is exactly `Wake.shared`.
//   - Kotlin `suspend fun up` renders `async throws` (SKIE always adds `throws`
//     to a bridged suspend function so coroutine cancellation can surface as a
//     thrown error), so this extension is `async throws` and forwards with
//     `try await`. `up` itself never throws across the boundary — it models
//     every failure as a `WakeResult` case; the `throws` is structural,
//     carrying Swift task cancellation only.
//   - Kotlin `Int` bridges to `Swift.Int32`, so `port` is `Int32`; the default
//     literal `9` matches `DEFAULT_WAKE_PORT`, and `"255.255.255.255"` matches
//     `DEFAULT_BROADCAST_ADDRESS`.
//

import Foundation

extension Wake {
    /// Build the Wake-on-LAN magic packet for `mac` and broadcast it over UDP.
    ///
    /// The MAC may be formatted with colons (`AA:BB:CC:DD:EE:FF`), hyphens
    /// (`aa-bb-cc-dd-ee-ff`), or no separators (`aabbccddeeff`); parsing is
    /// case-insensitive. Malformed input resolves to
    /// ``WakeResult/invalidMacAddress(reason:)`` rather than throwing — `up`
    /// reports failures through ``WakeResult``, never by raising an error.
    ///
    /// - Parameters:
    ///   - mac: The target device's hardware (MAC) address.
    ///   - broadcastAddress: The IPv4 broadcast target. Defaults to the limited
    ///     broadcast `255.255.255.255`; pass a subnet-directed broadcast (e.g.
    ///     `192.168.1.255`) to cross a router that forwards directed broadcasts.
    ///   - port: The destination UDP port. Defaults to `9`, the most common
    ///     Wake-on-LAN convention.
    /// - Returns: ``WakeResult/success`` when the packet was handed to the OS
    ///   for broadcast, ``WakeResult/invalidMacAddress(reason:)`` when `mac` is
    ///   unparseable, or ``WakeResult/networkError(message:)`` when the socket
    ///   send fails.
    ///
    /// The `throws` on this signature is structural: it carries Swift task
    /// cancellation through the SKIE coroutine bridge. `up` does not otherwise
    /// throw, so a non-cancelled call always returns a ``WakeResult``.
    ///
    /// Bridges to the Kotlin-side `Wake.up(...)` via the SKIE singleton accessor
    /// `Wake.shared`.
    public static func up(
        mac: String,
        broadcastAddress: String = "255.255.255.255",
        port: Int32 = 9
    ) async throws -> WakeResult {
        return try await Wake.shared.up(
            mac: mac,
            broadcastAddress: broadcastAddress,
            port: port
        )
    }
}
