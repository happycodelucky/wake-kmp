---
title: Bump Kotlin, AGP, SKIE, Gradle and the build toolchain to latest stable
change: patch
description: "Dependency and toolchain refresh ported from kmp-template: no public API change."
---

Routine toolchain refresh ported from kmp-template — no public API change, no
required consumer action.

- Kotlin 2.4.10 → 2.4.21 (highest version SKIE 0.10.16 supports; Kotlin 2.5.0 is
  still RC-only and held per CLAUDE.md §2)
- SKIE 0.10.14 → 0.10.16, AGP 9.4.1 → 9.5.3, Gradle wrapper 9.7.1 → 9.9.2
- Dokka 2.2.0 → 2.3.0 (fixes CVE-2026-84939, a path-traversal vulnerability)
- dependency-analysis 3.19.2 → 3.20.4, ben-manes-versions 0.64.0 → 0.67.0,
  version-catalog-update 1.1.1 → 1.1.2
- `api:check` now also covers the Android target's public API (Kotlin 2.4.20,
  KT-85950) — a new `wake/api/android/wake.api` dump, no surface changed
- CI: `jdx/mise-action` v4 → v5 (v5.0.1 fixes a supply-chain issue where the
  action could run a cached binary before verifying it)

Not ported from the template this round: Dependabot (deferred — opt-in
pending), the androidx.startup Context-capture initializer (skipped — Wake is
stateless/one-shot and holds no application-level state by design, CLAUDE.md
§1), and the mkdocs toolchain bump / Src-file-rename script fix (not
applicable — this repo has no docs site or init.sh scaffolding).
