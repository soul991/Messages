# Phase 3 Check

## Summary of Phase 3 Changes
- **3A Category Dock (`Dock.kt`, `HomeScreen.kt`):**
  - Height 64dp, corner 32dp, horizontal margin 16dp, bottom margin 12dp + navigationBarsPadding.
  - Solid `surfaceContainerHigh` fill, 1dp `outlineVariant` at 50% alpha border, 8dp shadow elevation.
  - Active tab: borderless pill filled with `primaryContainer`, labelMedium SemiBold onPrimaryContainer. Inactive: icon only in `onSurfaceVariant`.
  - Icon stroke to filled morph: `Icons.Outlined` to `Icons.Filled` crossfade 120ms + scale 0.9 -> 1.0 with spring(dampingRatio = 0.82f, stiffness = 380f).
  - Badges: height 16dp, min width 16dp, text 10sp SemiBold, background category tint (TRANSACTIONS / PROMOTIONS / primary / SPAM), 1.5dp ring in `surfaceContainerHigh` fill colour, 99+ cap.
  - Active-pill slide: `animateFloatAsState` spring(dampingRatio = 0.82f, stiffness = 380f) evaluated inside `Modifier.offset` and `Modifier.layout` lambdas without layout recomposition.

- **3B Settings Pages (`SettingsComponents.kt`, `SettingsScreen.kt`, `NotificationSettingsScreen.kt`):**
  - Grouped cards: `SettingsGroupCard` with corner 28dp, fill `colorScheme.groupCard`, 1dp `outlineVariant` at 50% alpha hairline border.
  - Inset dividers starting at 74dp (`SettingsCardDivider`).
  - Left icons: 40dp squircle badges with corner 12dp, fill `categoryPalette(...).container`, glyph `categoryPalette(...).onContainer` (contrast >= 4.5:1).
  - Mapping: Swipe right -> TRANSACTIONS, Swipe left -> SPAM, Delivery reports -> primaryContainer/onPrimaryContainer, Link previews -> REVIEW, Summary cards -> PROMOTIONS, Quick replies -> primaryContainer/onPrimaryContainer.
  - Subtitles: 1 line with ellipsis. Multi-line explanations moved to card footer captions (`bodySmall`, 12sp, onSurfaceVariant, padding start 20dp, top 8dp, bottom 16dp). Strings extracted to `strings.xml`.

- **3C Quick Replies (`QuickReplies.kt`, `SettingsScreen.kt`, `SettingsComponents.kt`):**
  - Grouped card with header "Quick replies" and text toggle "Edit" / "Done".
  - Edit mode shows leading checkboxes with "Delete (n)" button in error colour for batch deletion.
  - Swipe-to-reveal row (`SwipeToRevealRow`): swiping left reveals `errorContainer` with delete icon + label in `onErrorContainer`, spring-back with `Motion.butterSpring()`, 42% commit threshold, commits with Undo snackbar. Implemented with `graphicsLayer` translation and `derivedStateOf`.
  - Accessibility: Custom accessibility action "Delete" on each row; Edit mode provides non-gesture path.
  - Integrated input pill: height 52dp, corner 28dp, fill `surfaceContainer`, hairline border, trailing 40dp circular "+" button with spring scale (0.9 -> 1.0) and 90-degree rotation animation on tap, adds reply and new row enters with Phase 4 entrance spring.

## Compilation Check: `./gradlew :app:compileDebugKotlin`
```
> Task :core-messaging:preBuild UP-TO-DATE
> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :protection-engine:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :design-system:preBuild UP-TO-DATE
> Task :app:preBuild UP-TO-DATE
> Task :design-system:preDebugBuild UP-TO-DATE
> Task :core-messaging:preDebugBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :core-messaging:writeDebugAarMetadata UP-TO-DATE
> Task :app:generateDebugBuildConfig UP-TO-DATE
> Task :design-system:writeDebugAarMetadata UP-TO-DATE
> Task :app:generateDebugResValues UP-TO-DATE
> Task :design-system:generateDebugResValues UP-TO-DATE
> Task :core-messaging:generateDebugResValues UP-TO-DATE
> Task :design-system:generateDebugResources UP-TO-DATE
> Task :core-messaging:generateDebugResources UP-TO-DATE
> Task :design-system:packageDebugResources UP-TO-DATE
> Task :core-messaging:packageDebugResources UP-TO-DATE
> Task :core-messaging:extractDeepLinksDebug UP-TO-DATE
> Task :design-system:extractDeepLinksDebug UP-TO-DATE
> Task :core-messaging:processDebugManifest UP-TO-DATE
> Task :design-system:processDebugManifest UP-TO-DATE
> Task :core-messaging:compileDebugLibraryResources UP-TO-DATE
> Task :design-system:compileDebugLibraryResources UP-TO-DATE
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
> Task :core-messaging:kspDebugKotlin UP-TO-DATE
> Task :app:mapDebugSourceSetPaths UP-TO-DATE
> Task :app:generateDebugResources UP-TO-DATE
> Task :app:mergeDebugResources UP-TO-DATE
> Task :app:packageDebugResources UP-TO-DATE
> Task :app:parseDebugLocalResources UP-TO-DATE
> Task :core-messaging:compileDebugKotlin UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :core-messaging:compileDebugJavaWithJavac UP-TO-DATE
> Task :core-messaging:bundleLibCompileToJarDebug UP-TO-DATE
> Task :app:processDebugMainManifest UP-TO-DATE
> Task :app:processDebugManifest UP-TO-DATE
> Task :app:processDebugManifestForPackage UP-TO-DATE
> Task :app:processDebugResources UP-TO-DATE
> Task :design-system:javaPreCompileDebug UP-TO-DATE
> Task :design-system:compileDebugKotlin
> Task :design-system:compileDebugJavaWithJavac NO-SOURCE
> Task :design-system:bundleLibCompileToJarDebug
> Task :app:kspDebugKotlin
> Task :app:compileDebugKotlin

BUILD SUCCESSFUL in 33s
45 actionable tasks: 4 executed, 41 up-to-date
```
