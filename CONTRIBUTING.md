# Contributing to Wake

Thanks for contributing. This repo uses [mise](https://mise.jdx.dev) as the task
contract — every action is a `mise run <task>`. Run `mise tasks` to see them all.

## Setup

```bash
brew install mise
mise trust
mise install            # provisions JDK, Gradle, gh
cp local.properties.example local.properties   # point sdk.dir at your Android SDK
```

Xcode is not managed by mise — install a recent Xcode that SKIE supports.

## Workflow

1. Read [`CLAUDE.md`](CLAUDE.md), then `gradle/libs.versions.toml`.
2. Need platform-specific behavior? Walk CLAUDE.md §5 in order — don't skip to
   `expect`/`actual`. Keep the seam tiny; push logic into `commonMain`.
3. Public API that crosses to Swift? Apply CLAUDE.md §8 (SKIE — sealed result
   types, `@ObjCName`/`@Throws`, no `kotlin.Result` at the boundary) at design
   time, not after.
4. Adding a dependency? Web-search the latest stable and add it to
   `gradle/libs.versions.toml` only. `mise run dependencies:outdated` lists what
   has newer stable releases.

## The done gate

```bash
mise run check
```

`check` runs ktlint + detekt + the public-API/ABI check + every unit-test target
(iOS simulator, macOS, Android host, JVM), both modules. A JVM-only run hides
native-test-compile and detekt failures, so `check` — not `test:jvm` — is the
gate. Format first if ktlint complains:

```bash
mise run format
```

### Changing the public API

The public surface is pinned by committed dumps under `wake/api/` and
`wake-testing/api/`. If you intentionally change a public symbol, `check` fails
until you regenerate and commit the dump:

```bash
mise run api:dump      # rewrites the api/ dumps — review the diff like code
```

An unexpected `api:check` failure means you changed the public API by accident —
revert or narrow visibility (CLAUDE.md §3: `internal` by default).

## Commits & PRs

- Keep commits focused; explain *why* in the body when it isn't obvious.
- CI runs the same gate across two legs: a fast Ubuntu leg (lint + JVM/Android
  tests + dependency analysis) and a macOS leg (full `check` + the release
  `WakeKit.xcframework`). Green CI is required to merge.

## Releases

Releases are CI-driven via [`.github/workflows/release.yml`](.github/workflows/release.yml)
(computes the version, dry-run by default). See the maintainer runbook in
[`.github/PUBLISHING.md`](.github/PUBLISHING.md). Don't hand-edit `Package.swift` —
it's generated (CLAUDE.md §9).
