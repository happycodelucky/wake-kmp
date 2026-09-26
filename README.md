# Wake

[![Maven Central](https://img.shields.io/maven-central/v/com.happycodelucky.wake/wake?style=for-the-badge&logo=apachemaven&label=Maven%20Central)](https://central.sonatype.com/artifact/com.happycodelucky.wake/wake)
[![CI](https://img.shields.io/github/actions/workflow/status/happycodelucky/wake-kmp/ci.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white&label=CI)](https://github.com/happycodelucky/wake-kmp/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

![iOS 18+](https://img.shields.io/badge/iOS-18%2B-blue.svg?style=for-the-badge&logo=apple)
![macOS 15+](https://img.shields.io/badge/macOS-15%2B-blue.svg?style=for-the-badge&logo=apple)
![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)
![JVM 21+](https://img.shields.io/badge/JVM-21%2B-orange.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Kotlin 2.4](https://img.shields.io/badge/Kotlin-2.4-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)

Kotlin Multiplatform **Wake-on-LAN / Wake-on-Wireless**: send a magic packet
over UDP broadcast to wake a device on the network by its MAC address. One small
headless library, shared across iOS, macOS, Android, and the JVM.

> Status: initial scaffold. Library core (`:wake` + `:wake-testing`), the build
> toolchain, CI, and the Maven Central + SPM release workflow are in place — see
> [`.github/PUBLISHING.md`](.github/PUBLISHING.md) to cut a release. API
> reference HTML is generated on demand by Dokka (`mise run docs`).

## What it does

A Wake-on-LAN *magic packet* is 102 bytes: six `0xFF` bytes followed by the
target's 6-byte MAC address repeated sixteen times, sent as a UDP broadcast
datagram (port 9 by convention). A network card listening in Wake-on-LAN mode
powers its host on when it sees that pattern addressed to its own MAC.

Wake-on-Wireless is the same mechanism over Wi-Fi — no different code path. Be
aware, though, that **WoL is far more reliable over Ethernet than Wi-Fi.** A
sleeping wired NIC keeps its PHY powered to watch for the magic packet; many
Wi-Fi chipsets instead power the *radio* down in sleep, so nothing is listening
and no magic packet — from this library or any other — can reach the device. If a
device won't wake on Wi-Fi, that's usually the cause, not the packet. Wiring it to
Ethernet (and using that interface's MAC) is the dependable path; WoWLAN over
Wi-Fi works only on the subset of devices and access points that keep the radio in
a low-power listen mode. (Example: a Roku wakes reliably on Ethernet but typically
not over Wi-Fi, since its radio sleeps.)

## Install

### Gradle (KMP / Android / JVM)

<!-- x-release-version-start -->
```kotlin
// gradle/libs.versions.toml
[libraries]
wake = { module = "com.happycodelucky.wake:wake", version = "1.0.0" }
wake-testing = { module = "com.happycodelucky.wake:wake-testing", version = "1.0.0" }

// build.gradle.kts
commonMain.dependencies { implementation(libs.wake) }
commonTest.dependencies { implementation(libs.wake.testing) } // FakeWake
```
<!-- x-release-version-end -->

`wake` brings `com.happycodelucky.wake:outcome` (the `Outcome` result type) with
it transitively.

### Swift (SPM)

Add this repository as a package dependency, pinned to a release tag. The
`WakeKit` XCFramework ships as a GitHub Release asset (see
[`.github/PUBLISHING.md`](.github/PUBLISHING.md)).

<!-- x-release-version-start -->
```swift
.package(url: "https://github.com/happycodelucky/wake-kmp.git", from: "1.0.0")
```
<!-- x-release-version-end -->

## Usage

### Kotlin

`Wake.up` returns `Outcome<Unit>` — a Swift-friendly mirror of the standard
library's `Result`, with the same API (`isSuccess`, `getOrThrow`, `fold`,
`onFailure`, `map`…, and `toResult()` for the stdlib type). A failure always
holds a sealed `WakeException`, so a `when` over it is exhaustive:

```kotlin
Wake.up("AA:BB:CC:DD:EE:FF")
    .onSuccess { println("magic packet sent") }
    .onFailure { e ->
        when (e as WakeException) {
            is WakeException.InvalidMacAddress -> println("bad MAC: ${e.mac}")
            is WakeException.NetworkError -> println("send failed: ${e.message}")
        }
    }

Wake.up(mac).getOrThrow() // or throw instead

// Cross a router that forwards directed broadcasts, or change the port:
Wake.up("aa-bb-cc-dd-ee-ff", broadcastAddress = "192.168.1.255", port = 7)
```

`Wake` is a stateless object — there's nothing to construct or close; just call
`Wake.up(...)`. MAC strings may use colons (`AA:BB:CC:DD:EE:FF`), hyphens
(`aa-bb-cc-dd-ee-ff`), or no separators (`aabbccddeeff`), case-insensitive.

### Swift

```swift
import WakeKit

do {
    try await Wake.up(mac: "AA:BB:CC:DD:EE:FF").get()
    print("magic packet sent")
} catch let error as WakeException {
    switch onEnum(of: error) {
    case let .invalidMacAddress(e): print("bad MAC: \(e.mac)")
    case let .networkError(e): print("send failed: \(e.message)")
    }
}
```

The Swift module / SPM product is `WakeKit` (the framework is named with a
"Kit" suffix so the module name doesn't collide with the `Wake` type).
`Wake.up(mac:)` returns the same `Outcome`; unwrap it with `get()`, which returns
on success and throws the Kotlin `WakeException` itself as a Swift `Error` —
catch it by class and switch exhaustively with `onEnum(of:)`, or assert
`#expect(throws: WakeException.InvalidMacAddress.self)` in Swift Testing. For a
value, name the type: `let mac: String = try outcome.get()` (or
`get(as: String.self)`); `outcome.result(as:)` gives a `Swift.Result`, and
`Outcome<NSString>(value:)` / `Outcome<KotlinUnit>(failure:)` build one (handy in
Swift test fakes). Task cancellation arrives as `CancellationError`.

### Looking up a MAC from an IP (macOS + JVM desktop only)

You often know a device's *IP* but Wake-on-LAN needs its *MAC*. `lookupMac(ip)`
reads the host's ARP cache to resolve one — capture the MAC while the device is
awake, then wake it by MAC later (ARP entries age out once a host goes idle).

```kotlin
lookupMac("192.168.1.42")                       // Outcome<String>
    .onSuccess { mac -> Wake.up(mac) }          // feeds straight into up()
    .onFailure { e ->
        if (e is MacLookupException.NotInCache) println("no ARP entry — ping it first")
    }
```

```swift
// A global async function (not a Wake member):
do {
    let mac: String = try await lookupMac(ip: "192.168.1.42").get()
    try await Wake.up(mac: mac).get()
} catch is MacLookupException.NotInCache {
    print("no ARP entry — ping it first")
}
```

**This is intentionally *not* a cross-platform API.** Reading the ARP cache is
impossible on iOS (the kernel returns a spoofed address to sandboxed apps since
iOS 10.2) and on modern Android (`/proc/net/arp` is SELinux-blocked from API 29,
with no replacement). So `lookupMac` is declared **only** on the macOS and JVM
desktop targets — calling it from iOS or Android is a *compile error*, never a
fake result. The companion sample CLI (`mise run cli -- --ip 192.168.1.42`) uses
it for its "wake by IP" path.

## Platforms

| Target | Implementation |
|---|---|
| iOS / macOS (`iosArm64`, `iosSimulatorArm64`, `macosArm64`) | POSIX UDP sockets (`socket` / `setsockopt(SO_BROADCAST)` / `sendto`) via Kotlin/Native cinterop; iOS apps must declare `NSLocalNetworkUsageDescription` |
| Android (`arm64-v8a`, minSdk 30) | `java.net.DatagramSocket` with broadcast enabled; declares `android.permission.INTERNET` |
| JVM (desktop, JVM 21) | `java.net.DatagramSocket` — the same `java.net` broadcaster as Android, shared via a `jvmShared` source set. No permission or manifest needed |

The same `Wake.up(...)` call shown above works on every target. Android and the
JVM desktop target share their entire UDP send path (one `java.net` broadcaster
in a `jvmShared` intermediate source set); the only difference is that Android
contributes an `INTERNET`-permission manifest entry, while a plain JVM process
needs no permission to open a broadcast socket. Native targets are ARM-only, no
x86 (CLAUDE.md §1). The library is headless — any UI lives in the consuming app.

On iOS the first broadcast *send* triggers the Local Network privacy prompt, so
the embedding app must supply `NSLocalNetworkUsageDescription` in its Info.plist
— without it iOS silently drops the packet and `Wake.up` still reports
success (the datagram was handed to the OS, not delivered). This is
the app's own Info.plist, not something the library can vendor — unlike Android's
`INTERNET` permission, which merges in from the library manifest. macOS does not
prompt. If broadcasts still don't leave the device, the app may additionally need
the `com.apple.developer.networking.multicast` entitlement.

## Testing

Because `Wake.up(...)` is a static call, a feature you want to unit-test depends
on the small `WakeSender` interface instead — production wires `Wake.asSender()`,
tests wire `FakeWake` from `:wake-testing`. `FakeWake` records every `up` call
and returns a programmable `Outcome` without opening a socket:

```kotlin
// Production: WakeMyDesktop(wake = Wake.asSender())
class WakeMyDesktop(private val wake: WakeSender = Wake.asSender()) {
    suspend fun run() = wake.up("AA:BB:CC:DD:EE:FF")
}

// Test:
val fake = FakeWake(result = Outcome.failure(WakeException.NetworkError("no route to host")))
val result = WakeMyDesktop(wake = fake).run()
assertIs<WakeException.NetworkError>(result.exceptionOrNull())
assertEquals("AA:BB:CC:DD:EE:FF", fake.lastCall?.mac)
```

Code that doesn't need a test seam can ignore `WakeSender` and call `Wake.up(...)`
directly. `WakeSender` and `FakeWake` are Kotlin-only; in Swift, declare your own
protocol over `Wake.up(mac:)`.

## Sample CLI

`:apps:cli` is a small JVM command-line tool that wakes a device by MAC or by IP
(IP → ARP-resolve → wake). It's a sample, not a published artifact — a plain
`kotlin("jvm")` app depending on `:wake`.

```bash
mise run cli -- AA:BB:CC:DD:EE:FF        # wake by MAC
mise run cli -- <wifi-mac> <eth-mac>     # wake several MACs (one packet each)
mise run cli -- --ip 192.168.1.42        # resolve the IP's MAC, then wake it
mise run cli -- --ip 192.168.1.42 --port 7 --broadcast 192.168.1.255
./gradlew :apps:cli:run --args="--help"  # full usage (--help via the raw form)
```

"Wake by IP" relies on `lookupMac`, so it works on a Linux or macOS desktop but
not, say, inside a container with no `arp`/`/proc/net/arp`.

Passing several MACs sends a packet to each. Some devices listen on more than one
interface — a Roku in deep sleep, for instance, has separate Wi-Fi and Ethernet
MACs (visible under **Settings → Network → About** on the device), and you have
to wake the interface it's actually on. Listing both is the reliable move.

## Build

```bash
mise install        # provision JDK, Gradle, gh
mise run check      # ktlint + detekt + all unit tests (all targets, both modules)
mise run build      # assemble the release WakeKit.xcframework
mise run cli -- …   # run the sample CLI (see above)
```

See [`mise.toml`](mise.toml) for the full task surface and [`CLAUDE.md`](CLAUDE.md)
for the project rules.

## Deferred / future work

- **Subnet-directed broadcast** computation from the device's IP + netmask.
- **Apple `IP_BOUND_IF`** egress-interface binding for multi-interface hosts.
- **Android `WifiManager` multicast lock** + a `Context`-taking factory overload,
  for chipsets that gate broadcast egress behind a held lock.
- **Repeated sends** — many WoL tools send the packet a few times with a small
  delay; this version sends once.

## License

MIT License. See [LICENSE](LICENSE).
