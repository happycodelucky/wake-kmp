---
title: Wake.up and lookupMac return Outcome, a Swift-friendly kotlin.Result
change: major
description: "WakeResult and MacLookupResult are replaced by Outcome<T> (new com.happycodelucky.wake:outcome artifact): the kotlin.Result API in Kotlin, try get() / Swift.Result in Swift, with failures as sealed WakeException / MacLookupException."
---

`WakeResult` and `MacLookupResult` are gone. `Wake.up` returns `Outcome<Unit>` and
`lookupMac` (macOS / JVM) returns `Outcome<String>`. `Outcome` is a new, reusable
result type (`com.happycodelucky.wake:outcome`, brought in transitively by `wake`)
that wraps and mirrors `kotlin.Result`. A failure always holds a sealed exception:
`WakeException` or `MacLookupException`.

| Before | After |
|---|---|
| `WakeResult.Success` | `outcome.isSuccess` |
| `WakeResult.InvalidMacAddress(reason)` | `WakeException.InvalidMacAddress(mac)`; `message` has the reason |
| `WakeResult.NetworkError(message)` | `WakeException.NetworkError(message, cause)` |
| `MacLookupResult.Found(macAddress)` | a success holding the MAC |
| `MacLookupResult.NotInCache` | `MacLookupException.NotInCache(ip)` |
| `MacLookupResult.Error(message)` | `MacLookupException.InvalidIpAddress(ip)` or `.LookupFailed(message)` |

### Kotlin

`Outcome` has `kotlin.Result`'s API and semantics: `isSuccess`, `getOrNull`,
`getOrThrow`, `exceptionOrNull`, `fold`, `map`, `mapCatching`, `recover`,
`onSuccess`, `onFailure`, `getOrElse` and `getOrDefault`.
Use `toResult()` / `toOutcome()` to convert to and from the stdlib type.

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
`FakeWake(result = Outcome.failure(WakeException.NetworkError("…")))`.

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

`WakeSender` and `FakeWake` are now Kotlin-only (hidden from the framework). A
Swift test seam is your own protocol over `Wake.up(mac:)`.
