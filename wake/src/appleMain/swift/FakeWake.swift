//
// Wake — a recording, scriptable `WakeSender` for Swift tests.
//
// The Swift twin of `:wake-testing`'s Kotlin `FakeWake`. Swift code that depends on
// the `WakeSender` protocol takes `Wake.asSender()` in production and this in tests:
//
//     let fake = FakeWake()
//     let feature = WakeMyDesktop(wake: fake)          // depends on WakeSender
//     try await feature.run()
//     XCTAssertEqual(fake.lastCall?.mac, "AA:BB:CC:DD:EE:FF")
//
//     let failing = FakeWake(result: KotlinResult(failure: WakeException.NetworkError(message: "no route", cause: nil)))
//
// Why this exists rather than "conform to WakeSender yourself": SKIE refines the
// protocol's suspend requirement to `__up(mac:broadcastAddress:port:)` (and adds the
// natural `up(mac:…)` as an extension that calls it), so a hand-written Swift
// conformance has to implement the `__`-prefixed name. `FakeWake` does that once. To
// script more than a fixed result, subclass it and override `respond(to:)` (restate
// `@unchecked Sendable` on the subclass, as Swift requires).
//
// It ships in WakeKit (bundled Swift, `src/appleMain/swift/`) because Swift
// consumers get only this one framework; it's tiny and has no side effects.
//

import Foundation

/// A recording `WakeSender` for Swift tests: records every `up` call and returns a
/// programmable `KotlinResult` without opening a socket.
open class FakeWake: NSObject, WakeSender, @unchecked Sendable {
    /// One recorded `up(mac:broadcastAddress:port:)` call.
    public struct Call: Equatable, Sendable {
        public let mac: String
        public let broadcastAddress: String
        public let port: Int32

        /// A call to compare against, e.g. `fake.lastCall == .init(mac: "…", broadcastAddress: "255.255.255.255", port: 9)`.
        public init(mac: String, broadcastAddress: String, port: Int32) {
            self.mac = mac
            self.broadcastAddress = broadcastAddress
            self.port = port
        }
    }

    private let lock = NSLock()
    private var recorded: [Call] = []
    private var scripted: KotlinResult<KotlinUnit>

    /// - Parameter result: What every `up` call returns. Defaults to success; a
    ///   failure should hold a `WakeException`, matching `Wake.up`'s contract.
    public init(result: KotlinResult<KotlinUnit> = KotlinResult<KotlinUnit>(value: KotlinUnit())) {
        scripted = result
        super.init()
    }

    /// What every subsequent `up` call returns.
    public var result: KotlinResult<KotlinUnit> {
        get { lock.withLock { scripted } }
        set { lock.withLock { scripted = newValue } }
    }

    /// Every `up` call, in the order received.
    public var calls: [Call] { lock.withLock { recorded } }

    /// The most recent `up` call, or `nil` if there hasn't been one.
    public var lastCall: Call? { calls.last }

    /// The number of `up` calls received.
    public var callCount: Int { calls.count }

    /// `true` once `up` has been called.
    public var wasCalled: Bool { callCount > 0 }

    /// Forget every recorded call.
    public func reset() {
        lock.withLock { recorded.removeAll() }
    }

    /// The result for `call`. Override to script per-call behaviour; the default
    /// returns ``result``.
    open func respond(to call: Call) -> KotlinResult<KotlinUnit> {
        result
    }

    /// The `WakeSender` requirement as SKIE exposes it. Call `up(mac:…)` instead.
    public func __up(
        mac: String,
        broadcastAddress: String,
        port: Int32
    ) async throws -> KotlinResult<KotlinUnit> {
        let call = Call(mac: mac, broadcastAddress: broadcastAddress, port: port)
        lock.withLock { recorded.append(call) }
        return respond(to: call)
    }
}
