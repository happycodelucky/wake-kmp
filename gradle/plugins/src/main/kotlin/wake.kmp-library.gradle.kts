/*
 * Convention plugin: the shared module shape for Wake's published KMP
 * libraries (`:wake`, `:wake-testing`).
 *
 * Owns everything the two modules previously duplicated (CLAUDE.md §1, §2,
 * §4): the ARM-only target matrix, the apple intermediate source set, the
 * Android library block, compiler options, JVM target wiring, and the
 * SKIE settings that must match across modules. Per-module identity
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
