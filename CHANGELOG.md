# Changelog

Every release of Wake, newest first. Each entry is assembled from the
changesets merged since the previous release, when its release PR is opened
(`.changeset/README.md`). Releases before the changeset flow (`v0.1`,
`v1.0.0-rc.1`) are described on their GitHub Releases pages.

<!-- changesets: the Release PR workflow inserts each new release below this line. Keep it. -->

## 1.1.0 — 2026-09-27

### Features

#### Wake.up and lookupMac return KotlinResult, a Swift-friendly kotlin.Result ([#11](https://github.com/happycodelucky/wake-kmp/pull/11))

WakeResult and MacLookupResult are replaced by Result<T> from KotlinResult (com.happycodelucky.kotlinresult): the kotlin.Result API in Kotlin, KotlinResult with try get() / Swift.Result in Swift, with failures as sealed WakeException / MacLookupException.

> **Why `minor`, although this removes public API:** `WakeResult` and
> `MacLookupResult` shipped only in 1.0.0, which nobody depends on yet, so the
> author chose a minor bump over 2.0.0.


`WakeResult` and `MacLookupResult` are gone. `Wake.up` returns `Result<Unit>` and
`lookupMac` (macOS / JVM) returns `Result<String>`, from
[KotlinResult](https://github.com/happycodelucky/kotlinresult-kmp) 1.0.0
(`com.happycodelucky.kotlinresult:kotlinresult`, brought in transitively by
`wake`). It wraps and mirrors `kotlin.Result`; Swift sees it as `KotlinResult`. A failure always holds a sealed exception:
`WakeException` or `MacLookupException`.

| Before | After |
|---|---|
| `WakeResult.Success` | `result.isSuccess` |
| `WakeResult.InvalidMacAddress(reason)` | `WakeException.InvalidMacAddress(mac)`; `message` has the reason |
| `WakeResult.NetworkError(message)` | `WakeException.NetworkError(message, cause)` |
| `MacLookupResult.Found(macAddress)` | a success holding the MAC |
| `MacLookupResult.NotInCache` | `MacLookupException.NotInCache(ip)` |
| `MacLookupResult.Error(message)` | `MacLookupException.InvalidIpAddress(ip)` or `.LookupFailed(message)` |

###### Kotlin

`Result` has `kotlin.Result`'s API and semantics, as members: `isSuccess`,
`getOrNull`, `getOrThrow`, `exceptionOrNull`, `fold`, `map`, `mapCatching`,
`recover`, `onSuccess`, `onFailure`, `getOrElse` and `getOrDefault`. Import
`com.happycodelucky.kotlinresult.Result` where you name the type; use
`toStdlibResult()` / `toResult()` to convert to and from the stdlib type.

```kotlin
// Before
when (val r = Wake.up(mac)) {
    is WakeResult.Success -> …
    is WakeResult.InvalidMacAddress -> …
    is WakeResult.NetworkError -> …
}

// After
Wake.up(mac)
    .onSuccess { … }
    .onFailure { e ->
        when (e as WakeException) {
            is WakeException.InvalidMacAddress -> …
            is WakeException.NetworkError -> …
        }
    }
```

`WakeSender.up` and `FakeWake` follow suit:
`FakeWake(result = Result.failure(WakeException.NetworkError("…")))`, with KotlinResult's `Result` imported.

###### Swift

Unwrap with `get()`. It returns on success, or throws the Kotlin exception itself
as a Swift `Error`, so you catch it by class and switch exhaustively with
`onEnum(of:)`. For a value, name its type. `result(as:)` gives a `Swift.Result`.

```swift
// Before
switch try await Wake.up(mac: mac) {
case .success: …
case let .invalidMacAddress(reason): …
case let .networkError(message): …
}

// After
do {
    try await Wake.up(mac: mac).get()
} catch let e as WakeException {
    switch onEnum(of: e) {
    case let .invalidMacAddress(x): …   // x.mac
    case let .networkError(x): …        // x.message
    }
}

let mac: String = try await lookupMac(ip: "192.168.1.42").get()   // macOS
```

If you link `wake` into your own Apple framework (KMP), also `export`
`com.happycodelucky.kotlinresult:kotlinresult` into it: KotlinResult's bundled
Swift only compiles where the type keeps its plain `KotlinResult` name.

`WakeSender` and `FakeWake` are now Kotlin-only (hidden from the framework). A
Swift test seam is your own protocol over `Wake.up(mac:)`.

## 1.0.0 — 2026-09-25

### Features

#### Ship the Swift Wake.up(mac:) API; Kotlin 2.4.10 toolchain ([#8](https://github.com/happycodelucky/wake-kmp/pull/8))

Swift callers get the documented static Wake.up(mac:); Android bytecode is pinned to Java 21; built with Kotlin 2.4.10.

The first stable release after `1.0.0-rc.1`, and the first cut by the
changeset-driven release flow.

**Swift: `Wake.up(mac:)` now exists.** The README, KDoc and CLAUDE.md have always
shown `try await Wake.up(mac: "AA:BB:CC:DD:EE:FF")`, but the Swift extension that
provides it was never compiled into `WakeKit.xcframework` (SKIE's Swift bundling
was switched off). Earlier builds only offered
`Wake.shared.up(mac:broadcastAddress:port:)`. Both now work; nothing to migrate.

```swift
switch try await Wake.up(mac: "AA:BB:CC:DD:EE:FF") {
case .success: print("magic packet sent")
case let .invalidMacAddress(reason): print("bad MAC: \(reason)")
case let .networkError(message): print("send failed: \(message)")
}
```

**Android: bytecode pinned to Java 21.** The AAR's class files followed whatever
JDK built them; they are now explicitly Java 21 (class-file major 65), the same
as the JVM target. No change for current consumers.

**Toolchain.** Built with Kotlin 2.4.10, SKIE 0.10.14, AGP 9.4.1 and Gradle 9.7.1.
The public Kotlin API is unchanged (no ABI dump diff).
