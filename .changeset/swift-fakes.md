---
title: "Swift: WakeSender, Wake.asSender() and a FakeWake for tests"
change: minor
description: WakeSender is now visible to Swift, with Wake.asSender() for production and a bundled Swift FakeWake (recording, scriptable KotlinResult) for tests.
---

Swift code can now depend on the same test seam as Kotlin:

- **`WakeSender`** is exported as a Swift protocol. `wake.up(mac:)`,
  `up(mac:broadcastAddress:)` and `up(mac:broadcastAddress:port:)` return
  `KotlinResult<KotlinUnit>`.
- **`Wake.asSender()`** is the production sender, and behaves exactly like
  `Wake.up`.
- **`FakeWake`** (Swift, in WakeKit) is a recording fake for tests. It has
  `calls`, `lastCall`, `callCount`, `wasCalled` and `reset()`, and a settable
  `result`. Subclass it and override `respond(to:)` for per-call results.

```swift
let fake = FakeWake(result: KotlinResult(failure: WakeException.NetworkError(message: "no route", cause: nil)))
await #expect(throws: WakeException.NetworkError.self) { try await fake.up(mac: "AA:BB:CC:DD:EE:FF").get() }
```

Use `FakeWake` rather than conforming to `WakeSender` by hand: SKIE renames the
protocol's requirement to `__up(mac:broadcastAddress:port:)`.

Kotlin is unaffected: `:wake-testing`'s `FakeWake` is still the Kotlin fake.
