# Wake

Kotlin Multiplatform **Wake-on-LAN / Wake-on-Wireless**: send a magic packet
over UDP broadcast to wake a device on the network by its MAC address. One small
headless library, shared across iOS, macOS, Android, and the JVM.

> Status: initial scaffold. Library core (`:wake` + `:wake-testing`), the build
> toolchain, and CI are in place. The release workflow is deferred — see
> [Deferred / future work](#deferred--future-work). API reference HTML is
> generated on demand by Dokka (`mise run docs`).

## What it does

A Wake-on-LAN *magic packet* is 102 bytes: six `0xFF` bytes followed by the
target's 6-byte MAC address repeated sixteen times, sent as a UDP broadcast
datagram (port 9 by convention). A network card listening in Wake-on-LAN mode
powers its host on when it sees that pattern addressed to its own MAC.

Wake-on-Wireless is the same mechanism over Wi-Fi — no different code path,
though the target device and its access point may need WoWLAN enabled.

## Usage

### Kotlin

```kotlin
when (val result = Wake.up("AA:BB:CC:DD:EE:FF")) {
    is WakeResult.Success -> println("magic packet sent")
    is WakeResult.InvalidMacAddress -> println("bad MAC: ${result.reason}")
    is WakeResult.NetworkError -> println("send failed: ${result.message}")
}

// Cross a router that forwards directed broadcasts, or change the port:
Wake.up("aa-bb-cc-dd-ee-ff", broadcastAddress = "192.168.1.255", port = 7)
```

`Wake` is a stateless object — there's nothing to construct or close; just call
`Wake.up(...)`. MAC strings may use colons (`AA:BB:CC:DD:EE:FF`), hyphens
(`aa-bb-cc-dd-ee-ff`), or no separators (`aabbccddeeff`), case-insensitive.

### Swift

```swift
import WakeKit

switch try await Wake.up(mac: "AA:BB:CC:DD:EE:FF") {
case .success: print("magic packet sent")
case let .invalidMacAddress(reason): print("bad MAC: \(reason)")
case let .networkError(message): print("send failed: \(message)")
}
```

The Swift module / SPM product is `WakeKit` (the framework is named with a
"Kit" suffix so the module name doesn't collide with the `Wake` type). `Wake.up`
reads identically in Kotlin and Swift: a hand-written Swift extension maps the
static `Wake.up(mac:)` onto SKIE's `Wake.shared.up(...)`. The Swift form is
`try await` because SKIE renders the bridged suspend call as `async throws`
(carrying task cancellation); `up` itself reports failures through `WakeResult`,
never by throwing.

## Platforms

| Target | Implementation |
|---|---|
| iOS / macOS (`iosArm64`, `iosSimulatorArm64`, `macosArm64`) | POSIX UDP sockets (`socket` / `setsockopt(SO_BROADCAST)` / `sendto`) via Kotlin/Native cinterop |
| Android (`arm64-v8a`, minSdk 30) | `java.net.DatagramSocket` with broadcast enabled; declares `android.permission.INTERNET` |
| JVM (desktop, JVM 21) | `java.net.DatagramSocket` — the same `java.net` broadcaster as Android, shared via a `jvmShared` source set. No permission or manifest needed |

The same `Wake.up(...)` call shown above works on every target. Android and the
JVM desktop target share their entire UDP send path (one `java.net` broadcaster
in a `jvmShared` intermediate source set); the only difference is that Android
contributes an `INTERNET`-permission manifest entry, while a plain JVM process
needs no permission to open a broadcast socket. Native targets are ARM-only, no
x86 (CLAUDE.md §1). The library is headless — any UI lives in the consuming app.

## Testing

Because `Wake.up(...)` is a static call, a feature you want to unit-test depends
on the small `WakeSender` interface instead — production wires `Wake.asSender()`,
tests wire `FakeWake` from `:wake-testing`. `FakeWake` records every `up` call
and returns a programmable `WakeResult` without opening a socket:

```kotlin
// Production: WakeMyDesktop(wake = Wake.asSender())
class WakeMyDesktop(private val wake: WakeSender = Wake.asSender()) {
    suspend fun run() = wake.up("AA:BB:CC:DD:EE:FF")
}

// Test:
val fake = FakeWake(result = WakeResult.NetworkError("no route to host"))
WakeMyDesktop(wake = fake).run()
assertEquals("AA:BB:CC:DD:EE:FF", fake.lastCall?.mac)
```

Code that doesn't need a test seam can ignore `WakeSender` and call `Wake.up(...)`
directly.

## Build

```bash
mise install        # provision JDK, Gradle, gh
mise run check      # ktlint + detekt + all unit tests (all targets, both modules)
mise run build      # assemble the release WakeKit.xcframework
```

See [`mise.toml`](mise.toml) for the full task surface and [`CLAUDE.md`](CLAUDE.md)
for the project rules.

## Deferred / future work

- **Release workflow** — Maven Central + KMMBridge SPM publishing (the Gradle
  wiring is already in place; only the GitHub Actions `release.yml` is missing).
- **Subnet-directed broadcast** computation from the device's IP + netmask.
- **Apple `IP_BOUND_IF`** egress-interface binding for multi-interface hosts.
- **Android `WifiManager` multicast lock** + a `Context`-taking factory overload,
  for chipsets that gate broadcast egress behind a held lock.
- **Repeated sends** — many WoL tools send the packet a few times with a small
  delay; this version sends once.

## License

Apache License 2.0. See [LICENSE](LICENSE).
