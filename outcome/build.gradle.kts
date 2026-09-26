/*
 * Outcome — :outcome module.
 *
 * A Swift-friendly mirror of `kotlin.Result`, intended to be shared by every
 * KMP library that needs a Result type at its public API (prototyped here in
 * wake-kmp; see Outcome.kt's header for the design). Pure commonMain Kotlin plus
 * one bundled Swift file (`src/appleMain/swift/`), which SKIE compiles into every
 * framework that `export`s this module.
 *
 * Published beside `:wake` because `:wake` exposes `Outcome` at its public API
 * (`api` dependency), so Maven consumers must be able to resolve it.
 */

plugins {
    id("wake.kmp-library")
    id("wake.publish")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // jvmShared / androidHost / jvm test leaves need a test runtime too.
        getByName("jvmSharedTest").dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Outcome")
        description.set(
            "A Swift-friendly mirror of kotlin.Result for Kotlin Multiplatform " +
                "libraries: the full Result API in Kotlin, and throwing get() / " +
                "Swift.Result accessors in Swift.",
        )
    }
}
