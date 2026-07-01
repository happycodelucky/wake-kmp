# Wake

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

### Looking up a MAC from an IP (macOS + JVM desktop only)

You often know a device's *IP* but Wake-on-LAN needs its *MAC*. `lookupMac(ip)`
reads the host's ARP cache to resolve one — capture the MAC while the device is
awake, then wake it by MAC later (ARP entries age out once a host goes idle).

```kotlin
when (val result = lookupMac("192.168.1.42")) {
    is MacLookupResult.Found -> Wake.up(result.macAddress)   // feeds straight into up()
    is MacLookupResult.NotInCache -> println("no ARP entry — ping it first")
    is MacLookupResult.Error -> println("lookup failed: ${result.message}")
}
```

```swift
// SKIE renders it as a global async function (not a Wake member):
switch try await lookupMac(ip: "192.168.1.42") {
case let .found(macAddress): _ = try await Wake.up(mac: macAddress)
case .notInCache: print("no ARP entry — ping it first")
case let .error(message): print("lookup failed: \(message)")
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
`WakeResult.Success` (the datagram was handed to the OS, not delivered). This is
the app's own Info.plist, not something the library can vendor — unlike Android's
`INTERNET` permission, which merges in from the library manifest. macOS does not
prompt. If broadcasts still don't leave the device, the app may additionally need
the `com.apple.developer.networking.multicast` entitlement.

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

Apache License 2.0. See [LICENSE](LICENSE).
