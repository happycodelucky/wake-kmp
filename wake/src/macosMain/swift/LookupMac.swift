//
// Wake — the Swift face of the macOS `lookupMac`.
//
// Same shape as `Wake+Up.swift`: Kotlin's `lookupMac` returns `kotlin.Result<String>`
// and is `@HiddenFromObjC`; Swift sees the refined throwing bridge
// `__lookupMac(ip:)` (Kotlin `lookupMacOrThrow`) and this file re-exposes it as the
// global `lookupMac(ip:)`, rethrowing the Kotlin `MacLookupException` as the native
// `MacLookupError` enum.
//
// macOS-only: it lives in `src/macosMain/swift/`, which SKIE bundles into the
// macOS slice only — iOS has no `lookupMac` (the kernel spoofs ARP there).
//

import Foundation

/// Why ``lookupMac(ip:)`` produced no address.
///
/// The native Swift mirror of the Kotlin `MacLookupException` hierarchy.
public enum MacLookupError: Error, Equatable, Hashable, Sendable {
    /// The input is not a dotted-quad IPv4 address. Carries it verbatim.
    case invalidIpAddress(ip: String)

    /// The address is valid but has no current ARP-cache entry — common for an
    /// idle host. Contact it first (e.g. a ping) to populate the cache.
    case notInCache(ip: String)

    /// The OS reported an error reading the ARP cache.
    case lookupFailed(message: String)
}

extension MacLookupError: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case let .invalidIpAddress(ip): "could not parse IPv4 address: \"\(ip)\""
        case let .notInCache(ip): "no ARP entry for \(ip)"
        case let .lookupFailed(message): message
        }
    }
}

extension MacLookupError {
    /// Maps the Kotlin exception case-for-case (exhaustive via SKIE's `onEnum(of:)`).
    init(_ exception: MacLookupException) {
        switch onEnum(of: exception) {
        case let .invalidIpAddress(e): self = .invalidIpAddress(ip: e.ip)
        case let .notInCache(e): self = .notInCache(ip: e.ip)
        case let .lookupFailed(e): self = .lookupFailed(message: e.message)
        }
    }
}

/// Look up the hardware (MAC) address for `ip` in the macOS ARP cache.
///
/// The result is in the canonical `AA:BB:CC:DD:EE:FF` form, ready for
/// ``Wake/up(mac:broadcastAddress:port:)``.
///
/// - Parameter ip: The target device's IPv4 address, in dotted-quad form.
/// - Returns: The resolved MAC address.
/// - Throws: ``MacLookupError``, or `CancellationError` if the task is cancelled.
public func lookupMac(ip: String) async throws -> String {
    do {
        return try await __lookupMac(ip: ip)
    } catch let error as NSError {
        if let exception = error.kotlinException as? MacLookupException {
            throw MacLookupError(exception)
        }
        throw error
    }
}
