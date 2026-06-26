/*
 * Wake — :wake-testing module.
 *
 * Public, scriptable test fake for consumers of `:wake`: `FakeWake`, a
 * recording implementation of the `WakeSender` seam (from `:wake`) that
 * captures every `up` call without opening a real socket and returns a
 * programmable `WakeResult`.
 * Same module shape as `:wake` via the `wake.kmp-library` convention plugin;
 * published in lockstep (same group / version / pipeline) via `wake.publish`.
 * Consumers wire it on `testImplementation` (or KMP `commonTest` deps); the
 * production `:wake` artifact does not depend on this module.
 *
 * No XCFramework and no SKIE `produceDistributableFramework()`: test code is
 * consumed as KMP klibs from Maven Central, not via SPM. The Apple targets
 * exist so KMP consumers can resolve this module from their Apple test source
 * sets, but we don't ship a binary framework for it.
 */

plugins {
    id("wake.kmp-library")
    id("wake.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api` so consumers writing `testImplementation(wake-testing)`
            // get `Wake` / `WakeResult` transitively — they will assert
            // against those types.
            api(project(":wake"))

            // Coroutines for the suspend `wake` override; atomicfu for the
            // FakeWake call recorder's atomic counter / last-call snapshot.
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        // jvmSharedTest is the intermediate test set that both `jvmTest` and
        // `androidHostTest` depend on (set up in the convention plugin). This
        // module has no JVM-only tests, but the leaves still need a test
        // runtime on the JVM side, so declare it once here.
        getByName("jvmSharedTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Wake Testing")
        description.set(
            "Test fakes for the Wake KMP library: FakeWake, a recording " +
                "WakeSender that captures up() calls without sending a real " +
                "packet.",
        )
    }
}
