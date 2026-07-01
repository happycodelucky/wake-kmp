/*
 * :apps:cli — JVM sample CLI for the Wake library.
 *
 * A plain `kotlin("jvm")` application (NOT the `wake.kmp-library` convention
 * plugin): it is a runnable tool, not a published KMP library, so it ships no
 * framework/AAR/SKIE/Maven artifact, declares no ABI dump, and is not wired into
 * the ktlint/detekt/dependency-analysis hooks (those key on the multiplatform
 * plugin in the root build). It depends on `:wake` and drives the public
 * `Wake.up(...)` and JVM `lookupMac(...)` APIs.
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    // JVM 21 toolchain (CLAUDE.md §2), matching the library's JVM target.
    jvmToolchain(21)
}

dependencies {
    implementation(project(":wake"))
    // runBlocking, to bridge `main` to the suspend `Wake.up` / `lookupMac`.
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
}

application {
    // `main` lives in Main.kt → the generated facade class is MainKt.
    mainClass.set("com.happycodelucky.wake.cli.MainKt")
}
