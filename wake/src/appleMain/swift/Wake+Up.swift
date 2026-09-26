//
// Wake — Swift-side ergonomic sweetener for `Wake.up(mac:)`.
//
// `Wake` is a Kotlin `object` (a singleton), so SKIE renders it on the Swift
// side as a `WakeKit.Wake` class whose one instance is reached through a
// generated `shared` accessor. The raw call site would read:
//
//     try await Wake.shared.up(mac: "AA:BB:CC:DD:EE:FF", broadcastAddress: …, port: …).get()
//
// This extension adds a *static* `up(...)` on `Wake` so the public surface for
// Swift consumers is simply the intended pun, matching Kotlin's
// `Wake.up("AA:BB:CC:DD:EE:FF")` one-for-one:
//
//     try await Wake.up(mac: "AA:BB:CC:DD:EE:FF").get()
//
// The result is `Outcome<KotlinUnit>` (from `:outcome`); `.get()` — from
// `:outcome`'s own bundled Swift, compiled into this same framework because WakeKit
// `export`s `:outcome` — returns on success and throws the `WakeException` itself
// on failure. Nothing error-specific lives here.
//
// This file lives in `:wake`'s `src/appleMain/swift/`. SKIE's Swift bundling
// compiles it, at framework link, into the same module (`WakeKit`) as the
// SKIE-generated Swift wrappers. That requires `skie.swiftBundling.enabled = true`
// (set in the convention plugin) — with bundling off SKIE silently skips this file
// and `Wake.up(mac:)` doesn't exist.
//
// Bridging facts, verified against the built framework:
//   - The singleton accessor is exactly `Wake.shared`.
//   - Kotlin `suspend fun up` renders `async throws`; the `throws` only carries
//     task cancellation (`up` reports failures in the `Outcome`), so this
//     extension is `async throws` and forwards with `try await`.
//   - Kotlin `Int` bridges to `Swift.Int32`, so `port` is `Int32`; the defaults
//     `"255.255.255.255"` and `9` match `DEFAULT_BROADCAST_ADDRESS` /
//     `DEFAULT_WAKE_PORT`.
//

import Foundation

extension Wake {
    /// Build the Wake-on-LAN magic packet for `mac` and broadcast it over UDP.
    ///
    /// The MAC may be formatted with colons (`AA:BB:CC:DD:EE:FF`), hyphens
    /// (`aa-bb-cc-dd-ee-ff`), or no separators (`aabbccddeeff`); parsing is
    /// case-insensitive.
    ///
    /// ```swift
    /// do {
    ///     try await Wake.up(mac: "AA:BB:CC:DD:EE:FF").get()
    /// } catch let error as WakeException {
    ///     switch onEnum(of: error) {
    ///     case let .invalidMacAddress(e): print("bad MAC: \(e.mac)")
    ///     case let .networkError(e): print("send failed: \(e.message)")
    ///     }
    /// }
    /// ```
    ///
    /// - Parameters:
    ///   - mac: The target device's hardware (MAC) address.
    ///   - broadcastAddress: The IPv4 broadcast target. Defaults to the limited
    ///     broadcast `255.255.255.255`; pass a subnet-directed broadcast (e.g.
    ///     `192.168.1.255`) to cross a router that forwards directed broadcasts.
    ///   - port: The destination UDP port. Defaults to `9`, the most common
    ///     Wake-on-LAN convention.
    /// - Returns: A successful `Outcome` when the packet was handed to the OS for
    ///   broadcast (not a delivery guarantee), or a failed one holding a
    ///   `WakeException`. Unwrap with `get()`.
    /// - Throws: Only `CancellationError`, if the task is cancelled.
    public static func up(
        mac: String,
        broadcastAddress: String = "255.255.255.255",
        port: Int32 = 9
    ) async throws -> Outcome<KotlinUnit> {
        try await Wake.shared.up(mac: mac, broadcastAddress: broadcastAddress, port: port)
    }
}
