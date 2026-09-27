# LESSONS — Wake

Terse log of non-obvious things learned while building this library, one line
each. IDs are stable (CLAUDE.md, code comments and PRs cite them), so append new
ones and don't renumber. Anything a comment at its point of use already explains
belongs in that comment, not here.

- **D-NNN** — Decisions (load-bearing architecture choices).
- **B-NNN** — Bugs / gotchas (the thing that bit, and the fix).
- **N-NNN** — Notes (build-system / toolchain quirks).

## Decisions

- **D-001** — Releases are changeset-driven (`.changeset/`, `scripts/changeset.py`). `version=` in gradle.properties is the single source of the version, bumped only by the rolling release PR; release.yml publishes exactly that version. A changeset's `change` is the author's call and the source of truth (nothing cross-checks it); while 0.x a `major` bumps the minor, and only a `version:` pin leaves 0.x. A PR needs a changeset only when it touches a file in release scope (`.changeset/config.toml` include/exclude globs); `no-changeset` covers the rest. A manual dispatch cuts pre-releases (any branch) or retries gradle.properties' version on main — never a new stable version — so main, CHANGELOG.md and Maven Central can't drift.
- **D-002** — The Apple framework / Swift module is `WakeKit` (`<PascalName>Kit`, derived in the convention plugin and matched by KMMBridge's `frameworkName`). A module named like one of its public types (`Wake` module + `object Wake`) makes SKIE rename the type in Swift (`Wake_`) and lets the bare type shadow the module qualifier in SKIE's generated Swift. Renaming a shipped framework breaks every Swift consumer's `import`.
- **D-003** — Line length is 140, set once as `max_line_length` in the root `.editorconfig`: editors show it, ktlint enforces it and `mise run format` wraps to it. detekt's `MaxLineLength` is off (a second copy of the number could drift). ktlint ignores `max_line_length` in EVERY rule when its `max-line-length` rule is disabled, so that rule stays on. ktlint_official's parameter-count forced-multiline class/function signatures are `unset` (it otherwise wraps a 1-param constructor).
- **D-004** — SKIE Swift bundling is ON (kmp-template defaults it off). SKIE's `processSwiftSources<Target>` is `onlyIf { swiftBundling.enabled }`, so with bundling off every `src/<sourceSet>/swift/` file is silently dropped from the framework — `Wake.up(mac:)` never shipped until it was turned on. On, SKIE compiles the bundled Swift of EVERY linked klib into the framework, and that Swift only compiles where its types keep their plain names — hence `export(libs.kotlinresult)` in `wake/build.gradle.kts`.
- **D-005** — Every published jar and the AAR carry `llms.txt` + `llms-full.txt` (the module's public API with KDoc, from Dokka Markdown) under `META-INF/<groupId>/<artifactId>/` — namespaced because a bare `META-INF/llms.txt` from two libraries fails a consumer's Android packaging (duplicate java resource). In the AAR they sit at the archive root, not in classes.jar, so they never reach an APK. Klibs hold no resources; native targets ship them in their sources jars and host-specific `-metadata.jar`s (KGP's `<target>MetadataElements` Jar task). Packed only when the requested tasks include a publish task: the build consumes its own jars (`:wake-testing`, `:apps:cli`), and packing always would run Dokka on every check. `mise run llms:check` (CI's Apple leg) verifies the published set.

## Bugs

- **B-001** — AGP stamps a library AAR's `minCompileSdk` with the compileSdk it was BUILT with, and every consumer's `check<Variant>AarMetadata` enforces it — surfacing only when they assemble. Fixed by `android { aarMetadata { minCompileSdk = <android-min-compile-sdk> } }` in the convention plugin — a separate catalog key (~3 years back), decoupled from compileSdk and minSdk.

## Notes

- **N-001** — Build JDK stays 21: detekt 1.23.8's embedded Kotlin compiler crashes on JDK 25 (detekt/detekt#8714), and it's also the remaining Gradle 10 blocker (`ReportingExtension.file(String)`). Both are fixed in detekt 2.x — revisit when it's stable. Check `build/reports/problems/` after Gradle bumps.
- **N-002** — A git worktree doesn't carry the gitignored `local.properties`; AGP fails at task-graph time ("SDK location not found") — copy it from `local.properties.example`.
- **N-003** — AGP's KMP Android target is a `DecoratedExternalKotlinTarget`, NOT a `KotlinJvmTarget` — `targets.withType<KotlinJvmTarget>()` never reaches it, and unset its `jvmTarget` follows the build JDK. Set `jvmTarget` explicitly on `android { compilerOptions {} }` AND `jvm { compilerOptions {} }` (the convention plugin reads the catalog's `jvm-target`).
- **N-004** — Kotlin 2.4 removed `abiValidation.enabled`: the block's presence enables validation, so a module can't opt out itself — the convention plugin skips the block for the modules in `modulesWithoutAbiValidation` (`wake-testing`).
- **N-005** — PR templates and YAML issue forms apply only in GitHub's web UI; `gh pr/issue create --body` bypasses them, so agents follow them only because CLAUDE.md §12 says to. A submitted form renders as `### <label>` + answer per field (`_No response_` when skipped). HTML comments don't nest: a template can't quote `<!-- AI: … -->` inside a comment.
- **N-006** — Every `- [ ]` in a PR body is live (one click toggles it) and feeds the "N of M tasks" counter, which a pick-one group can never complete. So checkboxes appear only in the done-gate; choices are plain bullets you prune, and human review is signalled by leaving draft.
- **N-007** — A push or PR made with `GITHUB_TOKEN` triggers no workflows (workflow_dispatch excepted), and creating a PR with it needs "Allow GitHub Actions to create and approve pull requests". release-pr.yml therefore dispatches ci.yml + changeset.yml on `release/next` itself — unless a GitHub App token (`RELEASE_APP_CLIENT_ID`) is configured.
- **N-008** — main is branch-protected, so no workflow pushes to it. The release commit carrying the remote-binary Package.swift lives ONLY on its `vX.Y.Z` tag (a detached commit); main keeps the stub Package.swift. SPM resolves the manifest from the tag.
- **N-009** — Dokka 2 still generates Markdown (`gfm-plugin`), but has no Gradle switch for it: register a `DokkaFormatPlugin(formatName = "markdown")` subclass (an `@InternalDokkaGradlePluginApi` opt-in — re-check on Dokka bumps) for `dokkaGenerate*Markdown` (`LlmsTxt.kt`). Applying it in the modules only leaves the root's HTML aggregation working.
- **N-010** — `scripts/changeset.py` is stdlib-only and runs on 3.9, except reading the release scope (`tomllib`) needs 3.11+. python isn't a mise tool here (mise 2026.2.x fetched a broken free-threaded 3.14 build), so locally use a Homebrew `python3`; the GitHub runners have 3.11+.
