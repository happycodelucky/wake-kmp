//
// Wake — Swift-surface tests, run by `mise run test:swift` (and CI's Apple leg).
//
// Gradle's tests cover the Kotlin side; this covers what only exists or only
// behaves one way in Swift: the SKIE rendering of `Wake.up` / `lookupMac`,
// KotlinResult's bundled `get()` / `result(as:)` bridging, `WakeException`
// matching with `onEnum(of:)`, and the bundled Swift `FakeWake`. A plain
// executable linked against the debug macOS WakeKit framework (no XCTest target
// to maintain); it exits non-zero on the first failed expectation.
//

import Foundation
import WakeKit

private var failures = 0

private func expect(_ condition: Bool, _ message: String, line: Int = #line) {
    if condition {
        print("  ok  \(message)")
    } else {
        failures += 1
        print("  FAIL \(message) (line \(line))")
    }
}

private func thrown(_ body: () async throws -> Void) async -> (any Error)? {
    do { try await body(); return nil } catch { return error }
}

/// A `WakeSender`-dependent feature, as a consumer would write it.
private struct WakeMyDesktop {
    let wake: WakeSender
    func run() async throws { try await wake.up(mac: "AA:BB:CC:DD:EE:FF").get() }
}

@main
struct WakeSwiftTests {
    static func main() async {
        print("Wake.up")
        let invalid = await thrown { try await Wake.up(mac: "zz").get() }
        if let e = invalid as? WakeException, case let .invalidMacAddress(x) = onEnum(of: e) {
            expect(x.mac == "zz", "invalid MAC throws WakeException.InvalidMacAddress carrying the input")
        } else {
            expect(false, "invalid MAC throws WakeException.InvalidMacAddress (got \(String(describing: invalid)))")
        }
        let network = await thrown {
            try await Wake.up(mac: "AA:BB:CC:DD:EE:FF", broadcastAddress: "999.1.1.1").get()
        }
        expect(network is WakeException.NetworkError, "bad broadcast address throws WakeException.NetworkError")
        let failed = try? await Wake.up(mac: "bad")
        if case .failure(let e)? = failed?.result(as: Void.self) {
            expect(e is WakeException.InvalidMacAddress, "result(as:) gives Swift.Result.failure with the Kotlin exception")
        } else {
            expect(false, "result(as:) gives Swift.Result.failure")
        }

        print("lookupMac")
        let lookup = await thrown { let _: String = try await lookupMac(ip: "not-an-ip").get() }
        expect(lookup is MacLookupException.InvalidIpAddress, "invalid IP throws MacLookupException.InvalidIpAddress")

        print("Wake.asSender()")
        let real = await thrown { try await Wake.asSender().up(mac: "zz").get() }
        expect(real is WakeException.InvalidMacAddress, "the production sender behaves like Wake.up")

        print("FakeWake")
        let fake = FakeWake()
        expect(!fake.wasCalled, "starts with no calls")
        let succeeded = await thrown { try await WakeMyDesktop(wake: fake).run() }
        expect(succeeded == nil, "succeeds by default")
        expect(fake.callCount == 1, "records the call")
        expect(
            fake.lastCall == FakeWake.Call(mac: "AA:BB:CC:DD:EE:FF", broadcastAddress: "255.255.255.255", port: 9),
            "records the arguments, with up(mac:) filling the library defaults"
        )
        fake.result = KotlinResult<KotlinUnit>(failure: WakeException.NetworkError(message: "no route", cause: nil))
        let scripted = await thrown { try await WakeMyDesktop(wake: fake).run() }
        expect((scripted as? WakeException.NetworkError)?.message == "no route", "returns the scripted failure")
        fake.reset()
        expect(fake.calls.isEmpty, "reset() clears recorded calls")

        final class EvenOddFake: FakeWake, @unchecked Sendable {
            override func respond(to call: Call) -> KotlinResult<KotlinUnit> {
                call.port % 2 == 0
                    ? KotlinResult<KotlinUnit>(value: KotlinUnit())
                    : KotlinResult<KotlinUnit>(failure: WakeException.NetworkError(message: "odd port", cause: nil))
            }
        }
        let custom = EvenOddFake()
        let even = await thrown { try await custom.up(mac: "AABBCCDDEEFF", broadcastAddress: "10.0.0.255", port: 8).get() }
        let odd = await thrown { try await custom.up(mac: "AABBCCDDEEFF", broadcastAddress: "10.0.0.255", port: 9).get() }
        expect(even == nil && odd is WakeException.NetworkError, "subclasses script per-call results via respond(to:)")

        print(failures == 0 ? "All Swift-surface tests passed." : "\(failures) Swift-surface test(s) FAILED.")
        exit(failures == 0 ? 0 : 1)
    }
}
