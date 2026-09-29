---
title: "KotlinResult 1.1.0; Android compileSdk 34 floor for consumers; llms.txt in every artifact"
change: patch
description: Wake now depends on KotlinResult 1.1.0. The AAR asks consumers for compileSdk 34 instead of 36, and every published jar and AAR carries llms.txt + llms-full.txt for AI tools.
---

Dependency and packaging changes, no API change:

- **KotlinResult 1.1.0.** `wake` now depends on
  [KotlinResult 1.1.0](https://github.com/happycodelucky/kotlinresult-kmp/releases/tag/v1.1.0).
  Its API and bundled Swift helpers are unchanged. It no longer ships a
  Swift package of its own, which never affected WakeKit: Swift gets
  `KotlinResult` through WakeKit, as before.

- **Lower Android compileSdk floor.** The `wake` AAR used to declare
  `minCompileSdk` 36 (the SDK it was built with), so an app compiling against
  an older SDK failed `checkDebugAarMetadata`. It now declares **34**
  (Android 14). `minSdk` is unchanged (30).
- **llms.txt for AI tools.** Every published jar and the AAR now carry
  `llms.txt` and `llms-full.txt` under
  `META-INF/com.happycodelucky.wake/<artifactId>/`. `llms-full.txt` holds the
  module's full public API with its KDoc, for exactly the version you depend
  on. The files are namespaced, so they can't collide with another library's,
  and they sit outside `classes.jar`, so they never reach your APK.
