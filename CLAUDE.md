# CLAUDE.md — Kotlin Multiplatform Project Guide

Rules for working in this repo. Read before starting any task.

---

## 1. Scope

**Wake** is a Wake-on-LAN / Wake-on-Wireless library: it builds a 102-byte
magic packet (six `0xFF` bytes + the target's 6-byte MAC repeated 16×) and
sends it as a UDP broadcast datagram to wake a device on the network.

**Shared:** packet construction, MAC / address parsing and validation, the
public `Wake` API, the platform UDP send. Anything that isn't actually UI.

**Not shared:** UI. Each platform ships its own native UI layer (SwiftUI on
iOS, Jetpack Compose on Android, SwiftUI/AppKit on macOS desktop). **Do not add
Compose Multiplatform.** Do not propose it. The shared module is headless.

**Wake is stateless and one-shot.** Each `Wake.up(...)` opens a socket, sends,
and closes — there is no long-lived observer. The public entry point is a Kotlin
`object Wake` with a static `suspend fun up(...)`; it holds no mutable state.
There is **no `AutoCloseable`, no `androidx.startup` attach, and no global
test-override stack.** Because `Wake` is an `object` (not an instance) there is
no constructor to inject, so consumers who want a test seam depend on the small
`WakeSender` interface (production = `Wake.asSender()`, tests = `FakeWake` from
`:wake-testing`); callers who don't need a seam just call `Wake.up(...)`.

**Targets:**

- `iosArm64` (device) + `iosSimulatorArm64` (Apple Silicon simulator)
- `macosArm64` (desktop)
- Android `arm64-v8a`
- `jvm` (desktop, JVM 21) — architecture-neutral bytecode; the ARM-only rule
  below applies to the *native* slices and the Android ABI, not to the JVM
  target (it runs on whatever JVM the consumer has).

**Native / Android targets are ARM only, no exceptions.** Android and the JVM
target share one `java.net` UDP broadcaster via the `jvmShared` source set —
neither touches a native socket.

**Out of scope:** native x86/x86_64 slices, `armeabi-v7a`, Intel-Mac native
targets, watchOS, tvOS, Linux/Windows *native*, Kotlin/JS. (The JVM target is
not a native slice — it is the one architecture-neutral target we ship.)

---

## 2. Versions

Use the **latest stable**. Never EAP, RC, or beta on `main`. All versions live
in `gradle/libs.versions.toml`.

Floors as of last edit:

- Kotlin 2.3.21 (bounded above by SKIE's supported range — do not bump past it)
- Gradle 9.x
- AGP 9.x with `com.android.kotlin.multiplatform.library` (use the new `android`
  block, not `androidTarget`)
- JVM target 21
- Latest stable Xcode that the current Kotlin release supports

**Before adding or bumping any dependency: web-search the latest stable
version.** Versions in your training data are stale. Don't guess.

K2 only. No K1 fallback.

---

## 3. Language standards

- `languageVersion` and `apiVersion` set to current stable.
- Stable APIs only. Experimental APIs require an explicit `@OptIn` with a
  one-line comment explaining why.
- No `!!` in production code.
- `internal` by default. Widen visibility only when needed. Explicit API mode
  is on — every public symbol needs a visibility modifier and KDoc.
- `data class`, `value class`, `sealed interface` over open hierarchies. Use
  `value class` for typed IDs (e.g. the internal `MacAddress`) — free at runtime.
- `kotlin.time` for durations. `kotlin.uuid.Uuid` for UUIDs.
- KDoc on all public API. Comments explain *why*, not *what*.
- 4-space indent, 120-col max, trailing commas on multi-line.
- ktlint + detekt must pass.

**Apple platform names — preserve their casing.** `iOS`, `macOS`, `tvOS`,
`watchOS`, `iPadOS`, `visionOS` are the canonical spellings; never lowercase the
trailing acronym in identifiers, file names, types, packages, or comments. The
Kotlin convention of camel-casing acronyms (`HtmlParser`) does **not** apply —
these are Apple brand names and we keep them recognisable.

Allowed exceptions:
- **Identifiers we don't own** — `applyDefaultHierarchyTemplate { withIos() /
  withMacos() }`, the JetBrains source-set names (`iosMain` / `macosMain`), and
  the K/N target names (`iosArm64`, `macosArm64`).
- **Package names** — Kotlin packages are conventionally all-lowercase
  (`com.happycodelucky.wake`).

**Concurrency:**

- `kotlinx.coroutines` only. Every `CoroutineScope` has a clear owner with a
  defined cancellation lifecycle. Wake holds no scopes — its sends run on
  `withContext(Dispatchers.IO)` (Android) / `Dispatchers.Default` (Apple K/N
  has no `IO`).
- No `GlobalScope`. Ever.
- `Flow`/`StateFlow`/`SharedFlow` over callbacks and `LiveData`.
- For shared mutable state guarded **across `suspend` boundaries**, use
  `kotlinx.coroutines.sync.Mutex` or actor-style coroutines.
- For short, **non-suspending** critical sections, use
  `kotlinx.atomicfu.locks.synchronized` with a `SynchronizedObject`.
  Single-flag state belongs in `kotlinx.atomicfu.atomic`.
- **Never** `kotlin.synchronized` (JVM-only), `java.util.concurrent.locks.*`,
  `@Synchronized`, `volatile`, or `Object.wait/notify`. None are portable.
- A `kotlinx.atomicfu.locks.synchronized` block must not call `suspend`
  functions. If the body needs to suspend, you wanted a `Mutex`.
- New memory model only. No legacy freezing logic.

---

## 4. Module layout

```
/wake                 headless KMP library — magic packet + UDP send
  /src/commonMain      object Wake + WakeSender, packet/MAC/IPv4 logic, the UdpBroadcaster seam
  /src/appleMain       PosixUdpBroadcaster (POSIX cinterop) + defaultBroadcaster() actual
                       + swift/Wake+Up.swift (the Wake.up(mac:) sweetener)
  /src/jvmSharedMain   JvmUdpBroadcaster (java.net) + defaultBroadcaster() actual — shared by
                       Android + JVM (both dependsOn it)
  /src/androidMain     AndroidManifest (INTERNET permission) — Android-only bits
  /src/jvmMain         (JVM-only bits, currently none beyond the shared broadcaster)
  /src/commonTest      pure-logic + performWake orchestration tests
  /src/jvmSharedTest   live loopback-socket test of the java.net send — runs on JVM + Android host
/wake-testing         public FakeWake recorder (implements WakeSender) for consumers' tests
/apps/*               native sample apps (deferred)
```

`applyDefaultHierarchyTemplate()` for the apple group; **Android + JVM share code
via a manually-wired `jvmShared` intermediate** (`sourceSets.create` +
`dependsOn`), because the `com.android.kotlin.multiplatform.library` plugin does
not support a custom group in `applyDefaultHierarchyTemplate` (JetBrains
KT-80409). Don't otherwise hand-roll source-set wiring.

`expect`/`actual` surface stays minimal — a single one-line
`internal expect fun defaultBroadcaster(): UdpBroadcaster` selects the platform
send (a tiny `expect fun` is fine; the rule bans a large `expect class`). The
platform UDP send itself is an internal `interface UdpBroadcaster` with
per-platform implementations, so it stays substitutable by a fake in tests.

---

## 5. Libraries — Kotlin-first, always

A "Kotlin-first" library is written in Kotlin, designed for KMP, idiomatic
(suspend, Flow, sealed types), published by JetBrains, the Kotlin Foundation, or
a Kotlin-focused vendor (Touchlab, etc.).

### Step 1 — Use these. No substitutions without a written reason.

| Concern | Library |
|---|---|
| HTTP (low-level) | **Ktor Client** |
| Serialization | **kotlinx.serialization** |
| Coroutines | **kotlinx.coroutines** |
| Atomics | **kotlinx.atomicfu** |
| Date/time | **kotlinx.datetime** (never `java.time` in `commonMain`) |
| I/O / buffers | **kotlinx.io** |
| Logging | **Kermit** (Touchlab) |
| Dependency injection | **Koin** (consumer's graph; not required by the library) |
| Testing | **kotlin.test** + **Turbine** + **kotlinx.coroutines.test** |

**DI is a user choice.** Library code uses constructor injection — no `Module`,
no service locator. `Wake.up(...)` is a zero-ceremony static call; a consumer who
wants a test seam depends on the `WakeSender` interface and has it injected by
constructor (`class Feature(val wake: WakeSender = Wake.asSender())`), wiring it
through their own app graph (Koin, Hilt, hand-rolled). The library itself never
depends on a DI container.

### Step 2 — KMP-capable third-party

If no Step 1 option exists, check **klibs.io**: active maintenance, supports our
Tier 1 targets, compatible with current Kotlin, permissive license.

### Step 3 — Native per-platform

The UDP broadcast send is here. **Apple:** raw POSIX sockets via
`platform.posix` cinterop (`socket` / `setsockopt(SO_BROADCAST)` / `sendto`).
**Android:** `java.net.DatagramSocket` with broadcast enabled.

> **Do NOT use Ktor for the UDP send.** Ktor's UDP socket path crashes on
> macOS/iOS (KTOR-6489). For a one-shot broadcast the platform primitive is
> genuinely simpler than the wrapper (§6) and avoids the bug. Note on Darwin:
> `htons` is a macro and `inet_pton` is not exported by `platform.posix` — the
> byte-order and dotted-quad math is done in pure Kotlin (`internal/Ipv4.kt`).

### Step 4 — Roll our own

Default answer: use the library. Override only with a measured benchmark or a
clearly missing capability. Never for crypto, TLS, JSON, HTTP, date/time, or
databases — correctness-hard and battle-tested.

---

## 6. Roll our own — when and when not

Default: use the library. Override only when it does far more than we need at a
binary-size cost, profiling shows it in a hot path, it forces a bad allocation
pattern, it lacks a capability we need, or **the platform primitive is genuinely
simple** (the UDP broadcast send is exactly this case). When proposing a
hand-written replacement, benchmark it on the slowest target device first and
document the numbers in a comment.

---

## 7. UI — native per platform

UI lives in the platform apps. The shared module exposes commands as
`suspend fun` returning sealed result types (`Wake.up` → `WakeResult`). The
shared module **never** depends on a UI framework. No `androidx.compose.*` in
any `/wake` source set.

---

## 8. Swift interop

Default Kotlin → ObjC → Swift bridge is bad. We fix it with SKIE + annotations.

### SKIE

**SKIE (Touchlab) is mandatory** on the iOS framework build:

- Real Swift enums for Kotlin `enum class` (exhaustive `switch`).
- Sealed class/interface exhaustivity via `onEnum(of:)`.
- `suspend` → `async`/`await` with cancellation.
- `Flow` → `AsyncSequence` with element types preserved.
- Default arguments preserved as Swift overloads.

Rules: don't disable SKIE; don't bump Kotlin past SKIE's supported range; don't
enable JetBrains' direct Swift export yet.

### Annotations

Every public Kotlin API consumed from Swift must read like Swift at the call
site.

**`@ObjCName(swiftName = "...")` to rename.** Strip the noun from the verb; let
the parameter label carry it. (`openUrl(url)` → `@ObjCName(swiftName = "open")`.)

> **Framework name vs. type name.** The framework module is **`WakeKit`** (the
> convention plugin appends a `Kit` suffix to the derived base name, and
> `kmmbridge { frameworkName }` matches). Keeping the module name distinct from
> the public type name `Wake` avoids SKIE renaming the type to `Wake_` on a
> module-vs-type clash; consumers `import WakeKit` and use a clean `Wake`. If you
> add a new module or rename the framework, keep the module name distinct from
> any public type name.
>
> **`object` → `.shared` in Swift, and the `Wake.up` sweetener.** `Wake` is a
> Kotlin `object`, which SKIE/Kotlin-Native render in Swift as a class reached
> through a generated `Wake.shared` singleton accessor — so the raw Swift call is
> `try await Wake.shared.up(mac:)`. To keep the `Wake.up(mac:)` pun in Swift, a
> hand-written extension in `wake/src/appleMain/swift/Wake+Up.swift` adds a
> *static* `Wake.up(...)` that delegates to `Wake.shared.up(...)`. SKIE
> auto-discovers `src/appleMain/swift/` (the convention plugin sets
> `swiftBundling.enabled = false`), so no Gradle wiring is needed. This mirrors
> the sibling `:reachable` repo's `Reachability+Shared.swift`. Note SKIE renders
> a bridged `suspend fun` as `async throws` (the `throws` carries cancellation),
> so the extension is `async throws` and forwards with `try await`.

**`@HiddenFromObjC`** — hide Kotlin-only APIs from the generated header.

**`@Throws(...)`** — required on every public `suspend fun` and any public
function that can throw across the boundary. List the **domain exceptions** the
function actually throws. **`Wake.up` never throws — it returns a `WakeResult` —
so no `@Throws` is needed.** (SKIE still renders the bridged signature as
`async throws` to carry cancellation; that is automatic and is not something we
annotate.)

**Do NOT include `CancellationException` in `@Throws`.** SKIE routes coroutine
cancellation through Swift's native `Task.cancel()`; adding it pollutes the
generated signature.

### Sealed result types over `kotlin.Result<T>`

**No `kotlin.Result<T>` in any public Swift-facing signature.** SKIE has no
mapping for it — Swift sees an opaque `KotlinResult` with no exhaustive
`switch`. Use a project-defined `sealed interface`, which SKIE renders as an
exhaustive Swift `enum` via `onEnum(of:)`.

```kotlin
// Right — SKIE renders this as a Swift enum; `onEnum(of:)` makes it exhaustive.
public sealed interface WakeResult {
    public data object Success : WakeResult
    public data class InvalidMacAddress(val reason: String) : WakeResult
    public data class NetworkError(val message: String) : WakeResult
}

public object Wake {
    public suspend fun up(mac: String, ...): WakeResult
}
```

**Template in this repo:** `wake/src/commonMain/kotlin/com/happycodelucky/wake/Wake.kt`.

The same rule applies to `Pair<A, B>` / `Triple<…>` at the public boundary —
define a named `data class`.

**Internal use of `runCatching` is fine.** This rule is about return types
crossing the Swift boundary.

### API design for Swift consumers

1. Verbs without the object. `open(url:)`, not `openUrl(url:)`.
2. `sealed interface` for results, not nullable + error code.
3. `Flow<T>` over callbacks. Never a callback-based public API in `commonMain`.
4. No `kotlin.Result<T>` at the boundary.
5. No `Pair`/`Triple` in public API.
6. No star-projected generics across the boundary. Concrete types.
7. No companion-object factories for Swift-facing entry points. Top-level
   functions, constructors, or — as with `Wake` — a top-level `object` whose
   `.shared` Swift rendering is smoothed over by a hand-written Swift extension
   (§8 "object → `.shared`"). Don't reach for a `companion object` factory: SKIE
   renders it as `Type.companion.make(...)`, which no extension cleanly hides.
8. `Int` over `Long` when the range allows.

---

## 9. Apple distribution — KMMBridge → GitHub Releases → SPM

We use **Touchlab's KMMBridge** to publish the Apple framework. **CocoaPods is
forbidden.** Two channels, no overlap:

- **Maven Central** (vanniktech `gradle-maven-publish-plugin`) — Android AAR,
  `kotlinMultiplatform` metadata, per-target klibs. For Gradle/KMP consumers.
- **GitHub Releases** (KMMBridge) — the SKIE-enhanced `WakeKit.xcframework` zip
  for pure-Swift SPM consumers, referenced from the root `Package.swift` by URL +
  sha256 checksum.

**Rules:**

- KMMBridge config lives in the `kmmbridge { }` block in `wake/build.gradle.kts`;
  the framework name is `WakeKit`, matching the convention plugin's `baseName`.
- Versioning: the release workflow computes the version and passes
  `-Pversion=X.Y.Z`; KMMBridge tags `v${version}`.
- Publishing is CI-only: `kmmBridgePublish` only exists when
  `-PENABLE_PUBLISHING=true` is passed.
- Swift engineers never open a Gradle file. They `swift package update`.
- Don't vendor `XCFramework` zips. Everything flows through Release assets + the
  committed `Package.swift`.
- `Package.swift` is generated (`kmmBridgePublish` writes the released form,
  `spmDevBuild` the local-dev form). Don't hand-edit it beyond the initial stub;
  never commit the local-dev form.

> The `release.yml` workflow drives both channels (Maven Central via vanniktech,
> SPM via KMMBridge); see [`.github/PUBLISHING.md`](.github/PUBLISHING.md) for the
> maintainer runbook and one-time secret setup.

**Local development override:** `mise run spm:dev` (`./gradlew :wake:spmDevBuild`)
rebuilds the debug XCFramework and flips `Package.swift` to a local path;
`mise run spm:restore` restores the committed version.

---

## 10. Build & tooling

- Single source of truth: `gradle/libs.versions.toml`.
- Local toolchain pinned in `mise.toml` (JDK, Gradle wrapper, gh, Python). Run
  `mise install` on first checkout. mise does not pin Kotlin/AGP/SKIE — the
  version catalog owns those.
- Configuration cache + build cache on. Don't disable.
- Per-PR CI (`.github/workflows/ci.yml`): `mise run check` (build commonMain +
  every Tier 1 target, ktlint + detekt + unit tests) and `mise run build`
  (assemble the XCFramework).

---

## 11. Testing

- All shared logic gets `commonTest` coverage. The magic-packet builder, MAC
  parser, IPv4 parser, and the `performWake` orchestration are all pure and
  tested there with no real sockets (the orchestration via a recording
  `UdpBroadcaster` fake).
- `wake/src/jvmSharedTest` exercises the real `java.net` send against a loopback
  `DatagramSocket` — the one broadcaster verified end-to-end live. It runs on
  both the `jvm` target and the Android host-test JVM (both depend on
  `jvmShared`).
- `kotlinx.coroutines.test` with `runTest` and virtual time. Never
  `Thread.sleep`.
- `:wake-testing`'s `FakeWake` is the consumer-facing fake; it implements the
  `WakeSender` seam, so inject it by constructor where your code depends on a
  `WakeSender` (there is no `Wake` instance or singleton to install).

---

## 12. Task workflow

1. Read this file. Read `gradle/libs.versions.toml`.
2. Adding a dependency? Web-search the latest stable version first.
3. Need platform-specific behavior? Walk Section 5 in order. Don't skip to
   `expect`/`actual`.
4. Considering a hand-written replacement? Section 6 process. Default is "use
   the library."
5. Adding a public API consumed from Swift? Apply Section 8 rules at design
   time, not after.
6. Done means: `mise run check` passes and
   `./gradlew :wake:linkDebugFrameworkIosArm64` builds clean.
7. Opting into experimental APIs? One-line comment explaining what's
   experimental and the rollback path.

---

## 13. Hard rules

- No Compose Multiplatform.
- No CocoaPods.
- No x86/x86_64.
- No `GlobalScope`.
- No `!!`.
- No `java.time` in `commonMain`.
- No Ktor UDP (KTOR-6489 crashes on Apple).
- No `kotlin.synchronized`, `@Synchronized`, `java.util.concurrent.locks.*`, or
  `volatile` — only `kotlinx.atomicfu.locks.synchronized` (non-suspending) and
  `kotlinx.coroutines.sync.Mutex` (suspending).
- No suspend calls inside a `kotlinx.atomicfu.locks.synchronized` block.
- No EAP/RC/beta on `main`.
- No callback-based public APIs in `commonMain`.
- No UI dependencies in `/wake`.
- No hand-edited `Package.swift` (beyond the initial stub).
- No vendored `XCFramework` in the repo.
