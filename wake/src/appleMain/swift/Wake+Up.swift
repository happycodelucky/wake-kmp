//
// Wake — the Swift face of `Wake.up`.
//
// Kotlin's `Wake.up` returns `kotlin.Result<Unit>`, a value class that ObjC export
// erases to an untyped `Any?`, so it is `@HiddenFromObjC`. What Swift sees instead
// is the refined throwing bridge `Wake.shared.__up(mac:broadcastAddress:port:)`
// (Kotlin `upOrThrow`, `@ShouldRefineInSwift`), which throws the Kotlin
// `WakeException` wrapped in an `NSError`. This file re-exposes it as the static
// `Wake.up(mac:)` pun and rethrows that exception as the native `WakeError` enum
// below — so Swift callers write ordinary `do`/`try`/`catch`, and tests use
// `#expect(throws: WakeError.invalidMacAddress(mac: "zz"))`.
//
// This file lives in `:wake`'s `src/appleMain/swift/`. SKIE's Swift bundling
// compiles it, at framework link, into the same module (`WakeKit`) as the
// SKIE-generated Swift. That requires `skie.swiftBundling.enabled = true` (set in
// the convention plugin) — with bundling off SKIE silently skips this file and
// `Wake.up(mac:)` / `WakeError` don't exist.
//
// Bridging facts, verified against the built framework:
//   - `@Throws` exceptions arrive as an `NSError` whose `kotlinException` holds the
//     Kotlin object; a plain `catch let e as WakeException` would not match.
//   - SKIE surfaces coroutine cancellation as Swift `CancellationError`, which is
//     rethrown untouched — hence untyped `throws` rather than `throws(WakeError)`
//     (SE-0413 also recommends untyped throws for library API).
//   - The bridge has no Kotlin default arguments (SKIE #151: `@Throws` is not
//     carried onto default-argument overloads), so the defaults live here.
//     `"255.255.255.255"` and `9` match `DEFAULT_BROADCAST_ADDRESS` /
//     `DEFAULT_WAKE_PORT`; Kotlin `Int` bridges to `Int32`.
//

import Foundation

/// Why ``Wake/up(mac:broadcastAddress:port:)`` failed.
///
/// The native Swift mirror of the Kotlin `WakeException` hierarchy.
public enum WakeError: Error, Equatable, Hashable, Sendable {
    /// `mac` could not be parsed into a 48-bit address. Carries the input verbatim.
    case invalidMacAddress(mac: String)

    /// The socket send failed. Carries the platform error message or errno description.
    case networkError(message: String)
}

extension WakeError: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case let .invalidMacAddress(mac): "could not parse MAC address: \"\(mac)\""
        case let .networkError(message): message
        }
    }
}

extension WakeError {
    /// Maps the Kotlin exception case-for-case (exhaustive via SKIE's `onEnum(of:)`).
    init(_ exception: WakeException) {
        switch onEnum(of: exception) {
        case let .invalidMacAddress(e): self = .invalidMacAddress(mac: e.mac)
        case let .networkError(e): self = .networkError(message: e.message)
        }
    }
}

extension Wake {
    /// Build the Wake-on-LAN magic packet for `mac` and broadcast it over UDP.
    ///
    /// The MAC may be formatted with colons (`AA:BB:CC:DD:EE:FF`), hyphens
    /// (`aa-bb-cc-dd-ee-ff`), or no separators (`aabbccddeeff`); parsing is
    /// case-insensitive.
    ///
    /// Returning normally means the packet was handed to the OS for broadcast —
    /// Wake-on-LAN is fire-and-forget, so it does not mean the device woke.
    ///
    /// ```swift
    /// do {
    ///     try await Wake.up(mac: "AA:BB:CC:DD:EE:FF")
    /// } catch let error as WakeError {
    ///     print(error.localizedDescription)
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
    /// - Throws: ``WakeError`` when `mac` is unparseable or the send fails, or
    ///   `CancellationError` if the task is cancelled.
    public static func up(
        mac: String,
        broadcastAddress: String = "255.255.255.255",
        port: Int32 = 9
    ) async throws {
        do {
            try await Wake.shared.__up(mac: mac, broadcastAddress: broadcastAddress, port: port)
        } catch let error as NSError {
            if let exception = error.kotlinException as? WakeException {
                throw WakeError(exception)
            }
            throw error
        }
    }
}
