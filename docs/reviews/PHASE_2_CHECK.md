# Phase 2 Check

## Recomposition Counts of One Row During a Slow Drag (2c)
- **Before:**
  - `translatedX = liveOffset.floatValue + returnOffset.value` was evaluated directly in composition.
  - Every pointer movement event caused `liveOffset` to change, triggering immediate recomposition of `SwipeableConversationRow` and `SwipeActionBackground` on every frame.
  - Slow drag across 300px at typical touch rate: **~60 to 120 recompositions**.
- **After:**
  - `translationX` is applied purely in `.graphicsLayer { translationX = liveOffset.floatValue + returnOffset.value }`.
  - `direction`, `swipeAction`, and `willCommit` are computed using `remember { derivedStateOf { ... } }`.
  - `SwipeActionBackground` receives `progress: () -> Float` lambda and reads progress strictly inside `.graphicsLayer { ... }`.
  - Slow drag across 300px: **2 recompositions** during the drag (1 on drag start when `swipeAction` becomes non-null, and 1 when `willCommit` crosses the 42% threshold; 0 recompositions during continuous drag).

## Compilation Check: `./gradlew :app:compileDebugKotlin`
```
> Task :core-messaging:preBuild UP-TO-DATE
> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :design-system:preBuild UP-TO-DATE
> Task :protection-engine:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core-messaging:preDebugBuild UP-TO-DATE
> Task :design-system:preDebugBuild UP-TO-DATE
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:generateDebugBuildConfig UP-TO-DATE
> Task :core-messaging:writeDebugAarMetadata UP-TO-DATE
> Task :design-system:writeDebugAarMetadata UP-TO-DATE
> Task :app:generateDebugResValues UP-TO-DATE
> Task :design-system:generateDebugResValues UP-TO-DATE
> Task :core-messaging:generateDebugResValues UP-TO-DATE
> Task :core-messaging:generateDebugResources UP-TO-DATE
> Task :design-system:generateDebugResources UP-TO-DATE
> Task :core-messaging:packageDebugResources UP-TO-DATE
> Task :design-system:packageDebugResources UP-TO-DATE
> Task :design-system:extractDeepLinksDebug UP-TO-DATE
> Task :core-messaging:extractDeepLinksDebug UP-TO-DATE
> Task :design-system:processDebugManifest UP-TO-DATE
> Task :core-messaging:processDebugManifest UP-TO-DATE
> Task :design-system:compileDebugLibraryResources UP-TO-DATE
> Task :core-messaging:compileDebugLibraryResources UP-TO-DATE
> Task :core-messaging:parseDebugLocalResources UP-TO-DATE
> Task :design-system:parseDebugLocalResources UP-TO-DATE
> Task :design-system:generateDebugRFile UP-TO-DATE
> Task :core-messaging:generateDebugRFile UP-TO-DATE
> Task :design-system:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core-messaging:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :protection-engine:compileKotlin UP-TO-DATE
> Task :protection-engine:compileJava NO-SOURCE
> Task :protection-engine:processResources UP-TO-DATE
> Task :protection-engine:classes UP-TO-DATE
> Task :protection-engine:jar UP-TO-DATE
> Task :core-messaging:javaPreCompileDebug UP-TO-DATE
> Task :app:checkDebugAarMetadata UP-TO-DATE
> Task :app:mapDebugSourceSetPaths UP-TO-DATE
> Task :app:generateDebugResources UP-TO-DATE
> Task :core-messaging:kspDebugKotlin UP-TO-DATE
> Task :design-system:compileDebugKotlin UP-TO-DATE
> Task :design-system:javaPreCompileDebug UP-TO-DATE
> Task :design-system:compileDebugJavaWithJavac NO-SOURCE
> Task :design-system:bundleLibCompileToJarDebug UP-TO-DATE
> Task :app:mergeDebugResources UP-TO-DATE
> Task :app:packageDebugResources UP-TO-DATE
> Task :app:parseDebugLocalResources UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :core-messaging:compileDebugKotlin UP-TO-DATE
> Task :app:processDebugMainManifest UP-TO-DATE
> Task :app:processDebugManifest UP-TO-DATE
> Task :app:processDebugManifestForPackage UP-TO-DATE
> Task :core-messaging:compileDebugJavaWithJavac UP-TO-DATE
> Task :core-messaging:bundleLibCompileToJarDebug UP-TO-DATE
> Task :app:processDebugResources UP-TO-DATE
> Task :app:kspDebugKotlin
> Task :app:compileDebugKotlin

BUILD SUCCESSFUL in 43s
45 actionable tasks: 2 executed, 43 up-to-date
```
