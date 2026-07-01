/*
 * Wake — root build script.
 *
 * Plugins are declared here with `apply false`; they're applied in :wake
 * and :wake-testing (via the convention plugins). This keeps
 * `gradle/libs.versions.toml` as the single source of truth for versions
 * (CLAUDE.md §10).
 */

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
    // gradle-doctor warns on slow config, JVM mismatches, and cache misses on
    // every build (mise build:doctor surfaces its diagnostics explicitly).
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.gradle.doctor)
}

allprojects {
    group = "com.happycodelucky.wake"
    // The in-tree version carries `-SNAPSHOT` and a `0` patch slot. Humans bump
    // major/minor here and commit the change; the patch slot stays `0`. CI
    // overrides this at build time via `-Pversion=...` to stamp ephemeral
    // patches (run numbers for CI builds, exact `vX.Y.Z` for releases) without
    // ever committing the override back.
    version = providers.gradleProperty("version").getOrElse("0.1.0-SNAPSHOT")
}

// Gradle Doctor — build-health diagnostics. mise owns the JDK (via the [tools]
// pins in mise.toml, CLAUDE.md §10), so its JAVA_HOME checks are advisory here,
// not fatal: a fresh `git clone && mise run check` must never fail on a tool's
// environment opinion. The remaining checks (slow config, negative-avoidance,
// cache misuse) stay on.
doctor {
    javaHome {
        // mise puts the right JDK on PATH; JAVA_HOME may be unset. Warn, don't fail.
        ensureJavaHomeIsSet.set(false)
        ensureJavaHomeMatches.set(false)
        failOnError.set(false)
    }
    // Don't fail a build just because another Gradle daemon is alive (common
    // when an IDE holds one open alongside a terminal build).
    disallowMultipleDaemons.set(false)
}

// Stable-only dependency updates (CLAUDE.md §2 / §10: no EAP / RC / Beta on
// main). ben-manes' `-Drevision=release` only controls which metadata it reads;
// it still lists pre-releases as candidates. This predicate rejects any version
// that isn't a stable release (catches -Beta, -RC, -alpha, -M1, -eap,
// -SNAPSHOT, …), so `mise run dependencies:outdated` shows only real upgrades.
// littlerobots' version-catalog-update reuses this same predicate, so
// `mise run dependencies:update` never rewrites the catalog to a pre-release.
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
            version.set(libs.versions.ktlint.get())
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
