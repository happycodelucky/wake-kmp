# Wake

Kotlin Multiplatform **Wake-on-LAN / Wake-on-Wireless**: send a magic packet
over UDP broadcast to wake a device on the network by its MAC address. One small
headless library, shared across iOS, macOS, and Android.

> Status: initial scaffold. Library core (`:wake` + `:wake-testing`), the build
> toolchain, and CI are in place. Sample apps, the release workflow, and the
> docs site are deferred — see [Deferred / future work](#deferred--future-work).

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
val wake = Wake()
when (val result = wake.wake("AA:BB:CC:DD:EE:FF")) {
    is WakeResult.Success -> println("magic packet sent")
    is WakeResult.InvalidMacAddress -> println("bad MAC: ${result.reason}")
    is WakeResult.NetworkError -> println("send failed: ${result.message}")
}

// Cross a router that forwards directed broadcasts, or change the port:
wake.wake("aa-bb-cc-dd-ee-ff", broadcastAddress = "192.168.1.255", port = 7)
```

MAC strings may use colons (`AA:BB:CC:DD:EE:FF`), hyphens
(`aa-bb-cc-dd-ee-ff`), or no separators (`aabbccddeeff`), case-insensitive.

### Swift

```swift
import WakeKit

let wake = Wake()
switch await wake.wake(mac: "AA:BB:CC:DD:EE:FF") {
case .success: print("magic packet sent")
case let .invalidMacAddress(reason): print("bad MAC: \(reason)")
case let .networkError(message): print("send failed: \(message)")
}
```

The Swift module / SPM product is `WakeKit` (the framework is named with a
"Kit" suffix so the module name doesn't collide with the `Wake` type). The
`Wake` type and `Wake()` factory read identically in Kotlin and Swift.

## Platforms

| Target | Implementation |
|---|---|
| iOS / macOS (`iosArm64`, `iosSimulatorArm64`, `macosArm64`) | POSIX UDP sockets (`socket` / `setsockopt(SO_BROADCAST)` / `sendto`) via Kotlin/Native cinterop |
| Android (`arm64-v8a`, minSdk 30) | `java.net.DatagramSocket` with broadcast enabled; declares `android.permission.INTERNET` |

ARM-only, no x86 (CLAUDE.md §1). The shared module is headless — UI lives in the
consuming apps.

## Testing

`:wake-testing` ships `FakeWake`, a recording implementation of the `Wake`
interface that captures every wake call and returns a programmable `WakeResult`
without opening a socket. Wake is stateless, so there is no singleton to install
— construct a `FakeWake` and inject it:

```kotlin
val fake = FakeWake(result = WakeResult.NetworkError("no route to host"))
val feature = MyFeature(wake = fake)
feature.wakeDesktop()
assertEquals("AA:BB:CC:DD:EE:FF", fake.lastCall?.mac)
```

## Build

```bash
mise install        # provision JDK, Gradle, gh, python
mise run check      # ktlint + detekt + all unit tests, both modules
mise run build      # assemble the release WakeKit.xcframework
```

See [`mise.toml`](mise.toml) for the full task surface and [`CLAUDE.md`](CLAUDE.md)
for the project rules.

## Deferred / future work

- **Sample apps** (`apps/ios`, `apps/macos`, `apps/android`).
- **Release workflow** — Maven Central + KMMBridge SPM publishing (the Gradle
  wiring is already in place; only the GitHub Actions `release.yml` is missing).
- **Docs site** — mkdocs content (Dokka API generation is wired up).
- **Subnet-directed broadcast** computation from the device's IP + netmask.
- **Apple `IP_BOUND_IF`** egress-interface binding for multi-interface hosts.
- **Android `WifiManager` multicast lock** + a `Context`-taking factory overload,
  for chipsets that gate broadcast egress behind a held lock.
- **Repeated sends** — many WoL tools send the packet a few times with a small
  delay; this version sends once.

## License

Apache License 2.0. See [LICENSE](LICENSE).
