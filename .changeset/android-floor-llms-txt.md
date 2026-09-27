---
title: "Android: compileSdk 34 floor for consumers; llms.txt in every artifact"
change: patch
description: The AAR now asks consumers for compileSdk 34 instead of 36, and every published jar and AAR carries llms.txt + llms-full.txt for AI tools.
---

Two packaging changes, no API change:

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
