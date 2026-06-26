/*
 * Wake — :wake module.
 *
 * Headless KMP module: business logic only, no UI dependencies (CLAUDE.md
 * §1, §7). The module shape — ARM-only targets, apple intermediate source
 * set, Android library block, compiler options, SKIE settings — comes from
 * the `wake.kmp-library` convention plugin; Maven Central publishing comes
 * from `wake.publish` (both in /gradle/plugins). This script keeps only what
 * is unique to this module: dependencies, the KMMBridge SPM distribution
 * config, and POM name/description.
 */

plugins {
    id("wake.kmp-library")
    id("wake.publish")
    // KMMBridge (CLAUDE.md §9): aggregates the per-target frameworks the
    // convention plugin declared into `WakeKit.xcframework`
    // (build/XCFrameworks/{debug,release}/), publishes the release zip as a
    // GitHub Release asset, and regenerates the root /Package.swift. The
    // `.github` plugin variant is a superset of the core plugin in 1.2.x —
    // applying both produces a duplicate-extension error, so only this one.
    //
    // Do NOT redeclare `XCFramework("WakeKit")` in the kotlin { } block:
    // KMMBridge auto-creates the aggregator from the framework binaries at
    // config time (it provides `assembleWakeKit{Debug,Release}XCFramework`),
    // and a second declaration collides on those task names.
    alias(libs.plugins.kmmbridge.github)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Coroutines: `suspend fun wake(...)`, `withContext(Dispatchers.*)`
            // for the off-main-thread socket send. No atomicfu — Wake is
            // stateless and holds no mutable shared state in production.
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            // `:wake-testing` provides the public `FakeWake` recorder used in
            // tests. The dependency loop is acceptable: the testing module
            // `api`s `:wake`'s `main` configuration, and the back-edge here is
            // on `commonTest`, not `main` — Gradle resolves both without a
            // circular `main` dependency.
            implementation(project(":wake-testing"))
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }

        // androidHostTest source set is created by the convention plugin's
        // withHostTestBuilder. Configure its deps here.
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":wake-testing"))
        }
    }
}

skie {
    build {
        // Xcode 26 requires .swiftinterface files in every framework slice before
        // xcodebuild -create-xcframework will accept them (exit 70 otherwise).
        // produceDistributableFramework() enables Swift library evolution so SKIE
        // emits .swiftinterface alongside .swiftmodule, satisfying the requirement
        // for both debug and release XCFramework builds. `:wake-testing`
        // doesn't need this — it isn't shipped as an XCFramework.
        produceDistributableFramework()
    }
}

// --- KMMBridge: XCFramework → GitHub Release asset → SPM (CLAUDE.md §9) ------
//
// Two distribution channels run from this module, and they don't overlap:
//
//   1. Maven Central (`wake.publish` convention plugin) — Android AAR,
//      `kotlinMultiplatform` metadata, and per-target klibs. KMP consumers
//      resolve these from `commonMain`; no XCFramework involved.
//   2. GitHub Releases (this block) — the SKIE-enhanced `WakeKit.xcframework`
//      zip for pure-Swift consumers, referenced from the root /Package.swift
//      by URL + checksum so `swift package resolve` needs no local Gradle
//      build and no authentication.
//
// `gitHubReleaseArtifacts` uploads `WakeKit.xcframework.zip` to the GitHub
// Release tagged `v${project.version}`, creating the release if it doesn't
// exist. (`releasString` [sic] is KMMBridge 1.2.x's parameter name; without
// it the release tag would be the bare version, breaking the repo's `vX.Y.Z`
// tag convention.) Publishing is CI-only: the `kmmBridgePublish` umbrella
// task is only registered when `-PENABLE_PUBLISHING=true` is passed; the
// release workflow (deferred to a follow-up) supplies that plus the
// `GITHUB_REPO` / `GITHUB_PUBLISH_TOKEN` Gradle properties. Local builds skip
// the publish wiring entirely; the `spmDevBuild` task (always registered) is
// the local-dev entry point — see mise task `spm:dev`.
gitHubReleaseArtifacts(releasString = "v${project.version}")

kmmbridge {
    // The XCFramework's Swift module name. Must match the `baseName` the
    // convention plugin sets on each framework binary, or the generated
    // Package.swift references a binary that doesn't exist. "WakeKit" (not
    // "Wake") so the Swift module name doesn't collide with the Kotlin `Wake`
    // type — see the convention plugin's header comment.
    frameworkName.set("WakeKit")

    // `swiftToolVersion = "6.0"` because the platform constants `.iOS(.v18)`
    // and `.macOS(.v15)` need PackageDescription 6.0; KMMBridge defaults to
    // 5.3, which can't compile them.
    //
    // Platform floors match `gradle/libs.versions.toml`
    // (ios-deployment-target = 18.0, macos-deployment-target = 15.0). They're
    // spelled "18" / "15" here because KMMBridge emits `.iOS(.v$value)`
    // verbatim — "18.0" would produce the non-existent constant `.v18.0`.
    spm(swiftToolVersion = "6.0") {
        iOS { v("18") }
        macOS { v("15") }
    }
}

mavenPublishing {
    pom {
        name.set("Wake")
        description.set(
            "Kotlin Multiplatform Wake-on-LAN / Wake-on-Wireless: send a " +
                "magic packet over UDP broadcast to wake a device by MAC " +
                "address, on iOS, macOS, and Android.",
        )
    }
}
