/*
 * Convention plugin: the shared module shape for Wake's published KMP
 * libraries (`:wake`, `:wake-testing`).
 *
 * Owns everything the two modules previously duplicated (CLAUDE.md §1, §2,
 * §4): the ARM-only native target matrix, the apple intermediate source set,
 * the Android library block, the JVM target, the `jvmShared` intermediate that
 * lets Android and JVM share their `java.net` UDP broadcaster, compiler
 * options, and the SKIE settings that must match across modules. Per-module
 * identity
 * (framework base name, bundle id, Android namespace) is derived from the
 * project name so adding a module means applying this plugin and nothing
 * else:
 *
 *   wake          → framework "WakeKit",        namespace com.happycodelucky.wake
 *   wake-testing  → framework "WakeTestingKit", namespace com.happycodelucky.wake.testing
 *
 * The framework gets a "Kit" suffix so the Swift module name (`WakeKit`) never
 * collides with the Kotlin `Wake` object. Without the suffix, SKIE would rename
 * the `Wake` type to `Wake_` in Swift (module-vs-type clash) and the bare `Wake`
 * symbol would shadow the module qualifier in SKIE's generated Swift.
 *
 * Module build scripts keep only what genuinely differs: dependencies,
 * the KMMBridge SPM distribution config (`:wake` only), and POM
 * name/description.
 */

import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("co.touchlab.skie")
    id("org.jetbrains.dokka")
}

// Typed `libs` accessors aren't generated inside precompiled script plugins;
// the named-lookup API reads the same catalog the main build uses.
val libs = the<VersionCatalogsExtension>().named("libs")

// wake → "WakeKit"; wake-testing → "WakeTestingKit". The "Kit" suffix keeps the
// Swift module name distinct from the Kotlin `Wake` type (see file header).
val frameworkBaseName =
    name.split("-").joinToString("") { part -> part.replaceFirstChar(Char::uppercase) } + "Kit"

// wake → com.happycodelucky.wake; wake-testing → ….wake.testing.
// Doubles as the framework bundle id, pinned so SKIE doesn't fall back to the
// framework name.
val moduleNamespace = "com.happycodelucky." + name.replace("-", ".")

kotlin {
    // CLAUDE.md §4: applyDefaultHierarchyTemplate. Don't hand-roll source set
    // wiring. iosMain + macosMain coalesce into a shared "appleMain"
    // intermediate — both platforms share the `platform.posix` cinterop
    // bindings 1:1 for the UDP broadcast send.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("apple") {
                withIos()
                withMacos()
            }
        }
    }

    // --- Apple targets (CLAUDE.md §1) ---------------------------------------
    // Static framework binaries with a stable bundle id. In `:wake`,
    // KMMBridge aggregates these into `WakeKit.xcframework` at config time
    // (no explicit XCFramework declaration — see wake/build.gradle.kts).
    listOf(iosArm64(), iosSimulatorArm64(), macosArm64()).forEach { target ->
        target.binaries.framework {
            baseName = frameworkBaseName
            isStatic = true
            binaryOption("bundleId", moduleNamespace)
        }
    }

    // --- Android target (CLAUDE.md §1, §4) ----------------------------------
    // The new com.android.kotlin.multiplatform.library plugin's android {} block.
    //
    // CLAUDE.md §1: arm64-v8a only. The new KMP Android plugin doesn't wire
    // ABI filters directly; consumers' app modules pin the splits. We test
    // arm64-v8a only; documented in README.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    android {
        namespace = moduleNamespace
        compileSdk =
            libs
                .findVersion("android-compile-sdk")
                .get()
                .requiredVersion
                .toInt()
        minSdk =
            libs
                .findVersion("android-min-sdk")
                .get()
                .requiredVersion
                .toInt()

        withHostTestBuilder { /* enables the androidHostTest source set */ }
    }

    // --- JVM target (CLAUDE.md §1) ------------------------------------------
    // Plain JVM desktop. The UDP send is pure `java.net.DatagramSocket`, so JVM
    // and Android share the exact same broadcaster — see the `jvmShared`
    // intermediate source set below. JVM target level is pinned to 21 by the
    // KotlinJvmTarget loop further down.
    jvm()

    // --- jvmShared intermediate source set ----------------------------------
    // Android + JVM share their `java.net` UDP broadcaster verbatim, but the new
    // `com.android.kotlin.multiplatform.library` plugin does NOT support a
    // custom group in `applyDefaultHierarchyTemplate` (JetBrains KT-80409), so
    // we wire the intermediate set manually with `dependsOn` instead of a
    // hierarchy-template group. `jvmSharedMain` sits between `commonMain` and
    // both platform leaves; `jvmSharedTest` does the same for the test sets.
    // The Apple targets never see `jvmShared`, so `java.*` stays off K/N.
    val jvmSharedMain = sourceSets.create("jvmSharedMain")
    val jvmSharedTest = sourceSets.create("jvmSharedTest")

    jvmSharedMain.dependsOn(sourceSets.getByName("commonMain"))
    jvmSharedTest.dependsOn(sourceSets.getByName("commonTest"))

    sourceSets.getByName("androidMain").dependsOn(jvmSharedMain)
    sourceSets.getByName("jvmMain").dependsOn(jvmSharedMain)

    // The Android host-test set is created lazily by withHostTestBuilder above;
    // wire it (and jvmTest) onto the shared test set once it exists.
    sourceSets.named("androidHostTest").configure { dependsOn(jvmSharedTest) }
    sourceSets.getByName("jvmTest").dependsOn(jvmSharedTest)

    // --- Compiler options (CLAUDE.md §2, §3) ---------------------------------
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        // K2 stable APIs only (CLAUDE.md §3).
        languageVersion.set(KotlinVersion.KOTLIN_2_3)
        apiVersion.set(KotlinVersion.KOTLIN_2_3)
        allWarningsAsErrors.set(true)
    }

    // Per-target JVM toolchain knobs — Android compilation needs JVM target 21
    // (CLAUDE.md §2).
    targets.withType<KotlinJvmTarget>().configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
        }
    }

    // --- Public-API / ABI validation (CLAUDE.md §10) ------------------------
    // The Kotlin Gradle plugin's built-in ABI validation tracks the public API
    // surface across ALL targets (JVM + KLib/native) in one checked-in dump.
    // `mise run api:check` (wired into `check` via `checkKotlinAbi`) fails CI if
    // the public surface changes without an explicit `mise run api:dump` — so
    // breaking changes to a published library, and to the Swift boundary, are
    // always deliberate and reviewed.
    //
    // When the host can't compile every target (e.g. the Ubuntu CI leg can't
    // build the Apple slices), the plugin infers their ABI from the prior dump
    // instead of failing — so the checked-in dump stays complete. The Apple-target
    // ABI is verified on the macOS leg of CI, which can build those slices.
    //
    // This is the DEFAULT for published library modules. `:wake-testing` (test
    // fakes for consumers) opts back out in its own build script — its surface
    // is meant to flex, so it isn't worth pinning.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }
}

skie {
    // SKIE handles the Kotlin → Swift bridge enhancements (CLAUDE.md §8):
    // exhaustive sealed switching, suspend → async/await, default-arg
    // overloads. All feature defaults stay on; tighten only when something
    // bites.
    analytics {
        // Disable opt-in analytics; we'll revisit if useful.
        disableUpload.set(true)
    }
    // Wake ships no hand-written Swift sweeteners (it has no `.shared`
    // singleton to bridge), so there's nothing for SKIE to bundle into the
    // klib. Disabling bundling is a harmless safeguard kept in lockstep with
    // the sibling repos' convention plugins.
    swiftBundling {
        enabled.set(false)
    }
}
