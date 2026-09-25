/*
 * Wake — root build script.
 *
 * Plugins are declared here with `apply false`; they're applied in :wake
 * and :wake-testing (via the convention plugins). This keeps
 * `gradle/libs.versions.toml` as the single source of truth for versions
 * (CLAUDE.md §10).
 */

import nl.littlerobots.vcu.plugin.versionSelector

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    // kotlin.jvm is applied by the :apps:cli sample only. Declared here (apply
    // false) so its version is pinned once from the catalog and the leaf module
    // can apply it without re-resolving — applying `kotlin.jvm` with a version in
    // a build that already has the Kotlin Gradle plugin on the classpath (via
    // kotlin.multiplatform) otherwise fails the version-compatibility check.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.kmmbridge.github) apply false
    // vanniktech maven-publish — declared here `apply false` so its shared
    // MavenCentralBuildService loads once into the root classloader scope. Both
    // :wake and :wake-testing apply it via `wake.publish`; without this each
    // loads its own copy and `prepareMavenCentralPublishing` fails to type-check
    // the cross-project build-service under parallel + configuration-cache.
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false

    // Dokka v2: Kotlin API doc generator. Produces browsable HTML for the
    // public API of every source set (run `./gradlew dokkaGenerate`; output
    // lands in build/dokka/html). Standalone — there is no docs site.
    alias(libs.plugins.dokka)

    // Dependency-update tooling (mise dependencies:outdated / dependencies:update).
    // ben-manes reports updates; version-catalog-update rewrites libs.versions.toml.
    alias(libs.plugins.ben.manes.versions)
    alias(libs.plugins.version.catalog.update)

    // Build-health tooling. dependency-analysis adds the root `buildHealth` task
    // (mise dependencies:analyze) — unused/misused/transitive dependency advice.
    // (Gradle Doctor was removed: its JAVA_HOME/daemon checks were switched off
    // here, Gradle's problems report and `--profile` cover the rest, and it
    // blocked Gradle 10 — `mise run build:profile` replaces `build:doctor`.)
    alias(libs.plugins.dependency.analysis)
}

allprojects {
    group = "com.happycodelucky.wake"
    // `version` lives in gradle.properties: the last version released from
    // main, bumped only by the release PR (scripts/changeset.py). CI stamps
    // non-release builds with `-Pversion=…-ci.N`; a pre-release passes its own
    // `-Pversion`. Nothing ever writes an override back.
    version = providers.gradleProperty("version").get()
}

// Stable-only dependency updates (CLAUDE.md §2 / §10: no EAP / RC / Beta on
// main). ben-manes' `-Drevision=release` only controls which metadata it reads;
// it still lists pre-releases as candidates. This predicate rejects any version
// that isn't a stable release (catches -Beta, -RC, -alpha, -M1, -eap,
// -SNAPSHOT, …), so `mise run dependencies:outdated` shows only real upgrades.
fun isStableVersion(version: String): Boolean {
    val hasStableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val looksLikePlainNumber = "^[0-9,.v-]+(-r)?$".toRegex().matches(version)
    return hasStableKeyword || looksLikePlainNumber
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>().configureEach {
    rejectVersionIf {
        !isStableVersion(candidate.version)
    }
}

// version-catalog-update (`mise run dependencies:update`) resolves versions
// itself — it does NOT read ben-manes' report — and its default selector
// (PREFER_STABLE) accepts a pre-release whenever the current version is one. So
// it gets the same stable-only predicate explicitly, plus:
//   - pin `kotlin`: the Kotlin pin is SKIE-bound (CLAUDE.md §8); an automated
//     rewrite must never move it (the same hold renovate.json5 encodes).
//   - keepUnusedVersions: SDK levels, deployment targets and tool versions are
//     read via `findVersion(...)`/typed accessors, not `version.ref`, so VCU
//     would otherwise treat them as unused and delete them.
//   - sortByKey = false: keep the hand-grouped sections.
// VCU also drops end-of-line comments on rewrite, so catalog comments go on
// their own line above the entry.
versionCatalogUpdate {
    versionSelector { isStableVersion(it.candidate.version) }
    sortByKey.set(false)
    keep {
        keepUnusedVersions.set(true)
    }
    pin {
        versions.add("kotlin")
    }
}

subprojects {
    // ktlint + detekt wire onto the KMP plugin — i.e. onto the published
    // library modules only. CLAUDE.md §3: "ktlint + detekt must pass."
    //
    // Deliberate scope: sample apps (deferred for the initial scaffold) are
    // demo scaffolding, not shipped code, and will be excluded from Kotlin
    // lint and from CI's check task. Don't widen this hook to cover them — if
    // a sample stops compiling, the fix is in the sample, not the gate.
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "io.gitlab.arturbosch.detekt")
        // dependency-analysis must be applied to the SUBPROJECTS it analyzes, not
        // just the root (the root only hosts the aggregating `buildHealth` task).
        // In v2+ a root-only application is flagged as a likely misconfiguration —
        // "No project health reports found" — so scope it here to the published
        // library modules, alongside the lint plugins, and `mise run
        // dependencies:analyze` gets real per-module advice.
        apply(plugin = "com.autonomousapps.dependency-analysis")
    }

    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set(libs.versions.ktlint.cli.get())
            android.set(false)
            outputToConsole.set(true)
            ignoreFailures.set(false)
            filter {
                exclude { element -> element.file.path.contains("/build/generated/") }
                exclude("**/build/**")
                exclude("**/generated/**")
            }
        }

        tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
            exclude { element -> element.file.path.contains("/build/generated/") }
        }
    }

    plugins.withId("io.gitlab.arturbosch.detekt") {
        configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            // Shared override file at the repo root records only deviations
            // from detekt's defaults (test-source relaxations). See
            // config/detekt/detekt.yml.
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            // detekt's default source resolution only knows JVM layouts
            // (src/main/kotlin); point it at the module root so every KMP
            // source set (commonMain, appleMain, androidHostTest, …) is
            // scanned. The task itself filters to *.kt, and build/ output
            // is excluded by default.
            source.setFrom(files("src"))
        }
    }
}

// Aggregate Dokka HTML from the published modules into one site at the root.
dokka {
    moduleName.set("Wake")
}

dependencies {
    // Aggregate Dokka HTML from the published modules into the root build
    // (Dokka v2 pattern). `:wake-testing` is a public-API module too —
    // consumers writing tests want to see the FakeWake surface documented
    // next to the main library. `./gradlew dokkaGenerate` builds the HTML
    // under build/dokka/html.
    dokka(project(":wake"))
    dokka(project(":wake-testing"))
}
