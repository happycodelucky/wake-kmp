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
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.kmmbridge.github) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false

    // Dokka v2: Kotlin API doc generator. Produces HTML for the public API of
    // every source set. The HTML is copied into docs/api/ for mkdocs to bundle.
    alias(libs.plugins.dokka)
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

// Apply Dokka to the published modules and aggregate into docs/api/.
dokka {
    moduleName.set("Wake")
}

dependencies {
    // Aggregate Dokka HTML from the published modules into the root build
    // (Dokka v2 pattern). `:wake-testing` is a public-API module too —
    // consumers writing tests want to see the FakeWake surface documented
    // next to the main library.
    dokka(project(":wake"))
    dokka(project(":wake-testing"))
}

/**
 * Copies Dokka v2 HTML output into docs/api/, where mkdocs picks it up.
 *
 * The aggregated HTML lives at build/dokka/html after dokkaGeneratePublicationHtml.
 * mkdocs looks at docs/api/ when it builds the site; CI runs Dokka before mkdocs.
 */
tasks.register<Copy>("copyDokkaToDocs") {
    group = "documentation"
    description = "Copies aggregated Dokka HTML into docs/api/ for mkdocs."

    dependsOn("dokkaGeneratePublicationHtml")
    from(layout.buildDirectory.dir("dokka/html"))
    into(layout.projectDirectory.dir("docs/api"))
}
