---
title: Wake.up and lookupMac return KotlinResult, a Swift-friendly kotlin.Result
change: major
description: "WakeResult and MacLookupResult are replaced by Result<T> from KotlinResult (com.happycodelucky.kotlinresult): the kotlin.Result API in Kotlin, KotlinResult with try get() / Swift.Result in Swift, with failures as sealed WakeException / MacLookupException."
---

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

### Kotlin

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

### Swift

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
