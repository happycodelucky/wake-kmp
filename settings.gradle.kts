/*
 * Wake — KMP Wake-on-LAN / Wake-on-Wireless library.
 *
 * /wake is the headless KMP module that exposes a `Wake` interface and
 * platform implementations that send a UDP-broadcast magic packet over
 * POSIX sockets (Apple) and `java.net.DatagramSocket` (Android). Platform
 * apps live outside this Gradle build and consume /wake via KMMBridge →
 * Maven → SPM on Apple (CLAUDE.md §9) and as an AAR on Android.
 */

@file:Suppress("UnstableApiUsage")

pluginManagement {
    // Convention plugins (`wake.kmp-library`, `wake.publish`) live in
    // gradle/plugins; versions still come from gradle/libs.versions.toml,
    // which gradle/plugins shares.
    includeBuild("gradle/plugins")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Project-level repos win; subprojects must not redeclare.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "wake"

include(":wake")

// :wake-testing — public, scriptable `FakeWake` that records the wake calls
// a unit under test makes without opening a real socket. Headless KMP module;
// same targets as `:wake`; published as a sibling Maven Central artifact
// (`com.happycodelucky.wake:wake-testing`). Consumers wire it on
// `testImplementation` (or KMP `commonTest` deps).
include(":wake-testing")

// --- Sample apps (CLAUDE.md §4) -----------------------------------------------
// :apps:cli — a JVM-only sample command-line tool that wakes a device by MAC
// (direct) or by IP (ARP-resolve the IP → MAC, then send the magic packet).
// Hand-rolled arg parsing, zero deps beyond `:wake` + coroutines. Sample-grade:
// it applies `kotlin("jvm")` (NOT the `wake.kmp-library` convention plugin), so
// it ships no framework/AAR/SKIE/Maven artifact and is excluded from the
// ktlint/detekt/ABI hooks (those key on the multiplatform plugin — see the
// `subprojects {}` block in build.gradle.kts). Gradle derives the project
// directory `apps/cli` from the `:apps:cli` path.
include(":apps:cli")

// The native sample apps (apps/ios, apps/macos, apps/android) remain deferred;
// when added, the Android sample becomes a `:androidApp` Gradle subproject and
// the Apple samples consume the shared module via SPM (not Gradle). See the
// deferred-work notes in README / CLAUDE.md.
