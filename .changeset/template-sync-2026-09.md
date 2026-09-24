---
title: Ship the Swift Wake.up(mac:) API; Kotlin 2.4.10 toolchain
change: minor
description: Swift callers get the documented static Wake.up(mac:); Android bytecode is pinned to Java 21; built with Kotlin 2.4.10.
version: 1.0.0
---

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
