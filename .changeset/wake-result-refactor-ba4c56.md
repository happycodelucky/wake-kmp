---
title: Wake.up returns kotlin.Result in Kotlin and throws WakeError in Swift
change: major
description: "Replaces the WakeResult / MacLookupResult sealed types with each language's native idiom: kotlin.Result plus sealed WakeException / MacLookupException in Kotlin, and throwing functions with native WakeError / MacLookupError enums in Swift."
---

`WakeResult` and `MacLookupResult` are gone. Each side of the library now speaks
its platform's own error idiom instead of a bridged sealed result.

### Kotlin

`Wake.up` returns `Result<Unit>`, and `lookupMac` (macOS / JVM) returns
`Result<String>`. A failure always holds a sealed exception, so every stdlib
`Result` operator works and a `when` over the exception is exhaustive.

| Before | After |
|---|---|
| `WakeResult.Success` | `result.isSuccess` |
| `WakeResult.InvalidMacAddress(reason)` | `WakeException.InvalidMacAddress(mac)` — `message` has the reason |
| `WakeResult.NetworkError(message)` | `WakeException.NetworkError(message, cause)` |
| `MacLookupResult.Found(macAddress)` | `Result.success(mac)` |
| `MacLookupResult.NotInCache` | `MacLookupException.NotInCache(ip)` |
| `MacLookupResult.Error(message)` | `MacLookupException.InvalidIpAddress(ip)` or `.LookupFailed(message)` |

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
`FakeWake(result = Result.failure(WakeException.NetworkError("…")))`.

### Swift

`Wake.up(mac:)` no longer returns a value. It returns normally on success and
throws a native `WakeError` enum on failure. Task cancellation arrives as
`CancellationError`. On macOS, `lookupMac(ip:)` returns the MAC `String` and
throws `MacLookupError`. Both error enums conform to `Error`, `Equatable` and
`LocalizedError`, so Swift Testing can use
`#expect(throws: WakeError.invalidMacAddress(mac: "zz"))`.

```swift
// Before
switch try await Wake.up(mac: mac) {
case .success: …
case let .invalidMacAddress(reason): …
case let .networkError(message): …
}

// After
do {
    try await Wake.up(mac: mac)
} catch let error as WakeError {
    switch error {
    case let .invalidMacAddress(mac): …
    case let .networkError(message): …
    }
}
```

`WakeSender` and `FakeWake` are now Kotlin-only (hidden from the framework). A
Swift test seam is your own protocol over `Wake.up(mac:)`.
