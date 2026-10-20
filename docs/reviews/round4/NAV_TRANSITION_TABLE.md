# Navigation Transition Inventory & Analysis

## 4.1 Route Transition Inventory

The application defines 25 composable destinations in `MainActivity.kt`.

### NavHost Default Transitions
- **enterTransition**: `sharedAxisEnter(forward = true)`
  - Translation: `slideInHorizontally(Motion.spatialDefault()) { full / 5 }` (damping 0.88f, stiffness 420f)
  - Opacity: `fadeIn(Motion.effectsDefault())` (damping 1.0f, stiffness 1600f)
- **exitTransition**: `sharedAxisExit(forward = true)`
  - Translation: `slideOutHorizontally(Motion.spatialDefault()) { -full / 5 }` (damping 0.88f, stiffness 420f)
  - Opacity: `fadeOut(Motion.effectsFast())` (damping 1.0f, stiffness 3800f)
- **popEnterTransition**: `sharedAxisEnter(forward = false)`
  - Translation: `slideInHorizontally(Motion.spatialDefault()) { -full / 5 }` (damping 0.88f, stiffness 420f)
  - Opacity: `fadeIn(Motion.effectsDefault())` (damping 1.0f, stiffness 1600f)
- **popExitTransition**: `sharedAxisExit(forward = false)`
  - Translation: `slideOutHorizontally(Motion.spatialDefault()) { full / 5 }` (damping 0.88f, stiffness 420f)
  - Opacity: `fadeOut(Motion.effectsFast())` (damping 1.0f, stiffness 3800f)

---

### Route-by-Route Specification Table

| # | Route | Enter Transition | Exit Transition | PopEnter Transition | PopExit Transition | Driving Springs | Symmetry Status |
|---|---|---|---|---|---|---|---|
| 1 | `onboarding` | NavHost default (`sharedAxisEnter(forward = true)`) | NavHost default (`sharedAxisExit(forward = true)`) | NavHost default (`sharedAxisEnter(forward = false)`) | NavHost default (`sharedAxisExit(forward = false)`) | `spatialDefault`, `effectsDefault`, `effectsFast` | Symmetric pair (root destination; stack clears via `popUpTo("onboarding") { inclusive = true }`) |
| 2 | `home` | `fadeThroughEnter()` (`fadeIn(effectsSlow)`) | Dynamic: <br>• to chat: `homeExitForChat()` (`scaleOut(0.96f) + fadeOut(effectsFast)`)<br>• to settings: `screenBehindExitForSubMenu()` (`scaleOut(0.94f) + fadeOut(effectsFast)`)<br>• else: `fadeThroughExit()` (`fadeOut(effectsFast)`) | Dynamic: <br>• from chat: `homePopEnterFromChat()` (`scaleIn(0.96f) + fadeIn(effectsDefault)`)<br>• from settings: `screenBehindPopEnterFromSubMenu()` (`scaleIn(0.94f) + fadeIn(effectsDefault)`)<br>• else: `fadeThroughEnter()` (`fadeIn(effectsSlow)`) | `fadeThroughExit()` (`fadeOut(effectsFast)`) | `chatEnter`, `screenSpring`, `effectsDefault`, `effectsFast`, `effectsSlow` | **Asymmetric**: Default exit/popEnter (`fadeThrough`) does not mirror sibling `sharedAxis` transitions on sub-screens (Archived, Outbox, Dashboard, Starred) |
| 3 | `secret_setup` | `vaultEnter()` (`scaleIn(0.92f) + fadeIn(effectsSlow)`) | `vaultExit()` (`scaleOut(0.96f) + fadeOut(effectsFast)`) | `vaultEnter()` (`scaleIn(0.92f) + fadeIn(effectsSlow)`) | `vaultExit()` (`scaleOut(0.96f) + fadeOut(effectsFast)`) | `spatialSlow`, `spatialFast`, `effectsSlow`, `effectsFast` | **Asymmetric**: PopEnter uses forward entrance scale (0.92f) instead of scale return from 0.96f |
| 4 | `secret_prompt` | `vaultEnter()` (`scaleIn(0.92f) + fadeIn(effectsSlow)`) | `vaultExit()` (`scaleOut(0.96f) + fadeOut(effectsFast)`) | `vaultEnter()` (`scaleIn(0.92f) + fadeIn(effectsSlow)`) | `vaultExit()` (`scaleOut(0.96f) + fadeOut(effectsFast)`) | `spatialSlow`, `spatialFast`, `effectsSlow`, `effectsFast` | **Asymmetric**: Same as secret_setup |
| 5 | `secret_space` | `vaultEnter()` (`scaleIn(0.92f) + fadeIn(effectsSlow)`) | `vaultExit()` (`scaleOut(0.96f) + fadeOut(effectsFast)`) | `vaultEnter()` (`scaleIn(0.92f) + fadeIn(effectsSlow)`) | `vaultExit()` (`scaleOut(0.96f) + fadeOut(effectsFast)`) | `spatialSlow`, `spatialFast`, `effectsSlow`, `effectsFast` | **Asymmetric**: Same as secret_setup |
| 6 | `secret_compose` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | Symmetric with sibling secret screens |
| 7 | `secret_settings` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | Symmetric with sibling secret screens |
| 8 | `secret_chat/{threadId}?address={address}` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | Symmetric (does not use chatEnter / shared elements) |
| 9 | `secret_why/{messageId}` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | Symmetric |
| 10 | `archived` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric with parent**: NavHost pushes with `sharedAxis`, but `home` exits with `fadeThroughExit()` and popEnters with `fadeThroughEnter()` |
| 11 | `outbox` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric with parent**: Same as archived |
| 12 | `dashboard` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric with parent**: Same as archived |
| 13 | `starred?thread={thread}` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric with parent**: Same as archived |
| 14 | `notification_settings` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric with caller**: `settings` exits to sub-menus with `screenBehindExitForSubMenu()` (scale 0.94f) |
| 15 | `about` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric with caller**: Same as notification_settings |
| 16 | `update_settings` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric with caller**: Same as notification_settings |
| 17 | `update_check` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric with caller**: Same as notification_settings |
| 18 | `settings` | `slideInHorizontally(screenSpring) { it / 4 } + fadeIn(effectsDefault)` | `screenBehindExitForSubMenu()` (`scaleOut(0.94f) + fadeOut(effectsFast)`) | `screenBehindPopEnterFromSubMenu()` (`scaleIn(0.94f) + fadeIn(effectsDefault)`) | `slideOutHorizontally(screenSpring) { it / 4 } + fadeOut(effectsFast)` | `screenSpring`, `effectsDefault`, `effectsFast` | **Asymmetric**: Enter uses `fadeIn(effectsDefault)` (stiffness 1600f), but PopExit uses `fadeOut(effectsFast)` (stiffness 3800f) which causes rapid vanishing (~50ms) before the slide completes |
| 19 | `drive_backup` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric with caller**: Same as notification_settings |
| 20 | `spam_backup_picker` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | Symmetric with caller (`drive_backup`) |
| 21 | `trash` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric with caller**: Same as notification_settings |
| 22 | `compose` | `fadeThroughEnter()` (`fadeIn(effectsSlow)`) | `fadeThroughExit()` (`fadeOut(effectsFast)`) | `fadeThroughEnter()` (`fadeIn(effectsSlow)`) | `fadeThroughExit()` (`fadeOut(effectsFast)`) | `effectsSlow`, `effectsFast` | Symmetric fade-through modal pair |
| 23 | `chat/{threadId}...` | `slideInHorizontally(chatEnter) { it / 6 } + fadeIn(effectsDefault)` | `slideOutHorizontally(chatEnter) { it / 6 } + fadeOut(effectsFast)` | `slideInHorizontally(chatEnter) { -it / 6 } + fadeIn(effectsDefault)` | `slideOutHorizontally(chatEnter) { it / 6 } + fadeOut(effectsFast)` | `chatEnter`, `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric & Broken Shared Element**: PopExit uses `fadeOut(effectsFast)` causing visual snapping; shared element fails when row is scrolled offscreen or during predictive back gestures |
| 24 | `contactDetail/{threadId}` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric with chat**: Chat does not mirror sharedAxis when opening contact detail |
| 25 | `why/{messageId}` | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | NavHost default (`sharedAxis`) | `spatialDefault`, `effectsDefault`, `effectsFast` | **Asymmetric with chat**: Same as contactDetail |

### Asymmetry Summary Count
- Total routes: **25**
- Routes with symmetric transition pairs: **5** (`onboarding`, `secret_compose`, `secret_settings`, `secret_chat`, `compose`)
- Routes with asymmetric pairs or mismatched container transitions: **20** (80% of routes)

---

## 4.2 Missing Back Transitions: Physical Verification Observations

Each route was tested on `emulator-5554` (API 36) across three interaction modes:
1. **(a) Top Bar Back Arrow Tap** (`adb shell input tap <x> <y>`)
2. **(b) Hardware / Keyevent Back** (`adb shell input keyevent 4`)
3. **(c) Predictive Back Gesture** (`adb shell input swipe 2 1200 700 1200 150`)

### Observations by Route Category

#### 1. Settings Screen (`settings`)
- **(a) Back Arrow Tap**: When backing out of Settings to Home, the settings screen fades out within ~50ms (`effectsFast`), leaving a blank surface before the Home screen completes its scale-in from 0.94f. The slide out animation is effectively invisible because alpha reaches zero almost instantly.
- **(b) Keyevent 4**: Same behavior as back arrow.
- **(c) Predictive Back Gesture**: As the predictive gesture starts, `fadeOut(Motion.effectsFast())` causes the Settings surface to vanish almost immediately at progress ~0.15. The user sees an abrupt snap rather than a continuous gesture-driven transition.

#### 2. Chat Screen (`chat/{threadId}`)
- **(a) Back Arrow Tap**:
  - *When conversation row is visible in Home list*: The shared avatar occasionally animates, but if the message was marked read and the list updated, the avatar jerks or snaps.
  - *When conversation row is scrolled offscreen in Home list*: The shared element match fails completely. The chat screen snaps away in ~50ms due to `fadeOut(effectsFast())`, and Home snaps into view.
  - *When opened from Pinned row or Search*: No shared avatar was registered on Home, causing an instantaneous snap back.
- **(b) Keyevent 4**: Same failure modes as (a).
- **(c) Predictive Back Gesture**: Severe snapping. Because `sharedThreadAvatar` checked `!transition.isRunning`, during predictive back gesture seeking (`isSeeking == true`, `isRunning == false`), the modifier returned `this` on initial frame, dropping the shared element entirely.

#### 3. Sub-Screens (`dashboard`, `archived`, `outbox`, `starred`, `trash`, `about`, `update_settings`, `drive_backup`)
- **(a) Back Arrow Tap**: The sub-screen slides to the right with `sharedAxisExit`, but `home` underneath does NOT slide in from the left; `home` performs a static fade-in (`fadeThroughEnter`). The motion is visually disconnected and uncoordinated.
- **(b) Keyevent 4**: Disconnected slide/fade mismatch identical to (a).
- **(c) Predictive Back Gesture**: Sub-screen slides away while background stays completely still and abruptly pops in.

---

## 4.3 Root Cause Analysis

### 1. Chat Back Works "Only Sometimes"
1. **Lifecycle Detachment via `if (currentState == targetState && !isRunning) return this`**:
   - `Transitions.kt` detached `sharedElement` whenever the screen was settled (`currentState == targetState && !isRunning`).
   - While this optimization was intended to reduce recomposition cost for lists on `HomeScreen`, it was also applied to `ChatScreen`'s top bar where there is only one avatar and one name.
   - When predictive back or navigation pop starts, `isSeeking` is active while `isRunning` is false. Because the modifier was not attached while settled, `SharedTransitionScope` does not have pre-measured layout coordinates for the source element on frame 0, causing the bounds match to fail and abort.
2. **LazyColumn Viewport Culling in `HomeScreen`**:
   - If the user scrolled the chat list before opening a thread, or if the thread row was outside the viewport, `HomeScreen` does not compose that row.
   - Without an active composable in `HomeScreen` carrying `rememberSharedContentState("avatar-$threadId")`, `SharedTransitionScope` has no target element.
   - When no target element exists, the navigation transition falls back to the route's `popExitTransition`.
   - The route's `popExitTransition` was configured as `slideOutHorizontally(chatEnter()) { it / 6 } + fadeOut(effectsFast())`. Because `effectsFast` has stiffness 3800f, the chat screen disappears in 50ms, resulting in an abrupt visual snap.
3. **Missing Shared Modifiers on Pinned and Search Items**:
   - `PinnedConversationsRow` and `ConversationMatchRow` did not attach `Modifier.sharedThreadAvatar`. Opening a thread from these locations and returning always triggered the broken 50ms fallback exit.

### 2. Settings Back Snaps and Fades Weirdly
1. **Stiffness Mismatch (`screenSpring` vs `effectsFast`)**:
   - `enterTransition` uses `slideInHorizontally(Motion.screenSpring()) { it / 4 } + fadeIn(Motion.effectsDefault())`. Both springs are harmonized (~380f and ~1600f).
   - `popExitTransition` used `slideOutHorizontally(Motion.screenSpring()) { it / 4 } + fadeOut(Motion.effectsFast())`. `effectsFast` has stiffness 3800f (~50ms decay), while `screenSpring` has stiffness 380f (~350ms duration).
   - Alpha vanishes after 14% of the slide duration, rendering the remaining 86% of the slide completely invisible.
2. **Predictive Back Velocity Sensitivity**:
   - With `effectsFast`, predictive back gesture seeking scales opacity to 0 almost immediately upon gesture start.
3. **Mismatched Background PopEnter**:
   - `homePopEnterFromSubMenu` uses `fadeIn(Motion.effectsDefault())`. The foreground fading out at 3800f while the background fades in at 1600f leaves a visible dark/empty transition gap.

### 3. Sub-Screens Feel Inconsistent
1. **Asymmetric Navigation Pair**:
   - NavHost default transitions move sibling screens along the X-axis (`sharedAxisEnter/Exit`).
   - `home` overrode `exitTransition` and `popEnterTransition` with `fadeThroughExit` (static fade out) and `fadeThroughEnter` (static fade in) for all routes other than `chat` and `settings`.
   - One screen translated while the other screen faded in place, breaking Material 3 Shared Axis spatial coherence.

---

## 4.4 Fix Specification (Preserving All Forward Transitions)

### 1. In `MainActivity.kt`:
- **Preserve Forward Transitions**:
  - `home` exit to `chat`: `homeExitForChat()` — **UNTOUCHED**.
  - `home` exit to `settings`: `screenBehindExitForSubMenu()` — **UNTOUCHED**.
  - `settings` enter: `slideInHorizontally(Motion.screenSpring()) { it / 4 } + fadeIn(Motion.effectsDefault())` — **UNTOUCHED**.
  - `chat` enter: `slideInHorizontally(Motion.chatEnter()) { it / 6 } + fadeIn(Motion.effectsDefault())` — **UNTOUCHED**.
  - `NavHost` default enter/exit — **UNTOUCHED**.
- **Fix Back Transitions**:
  - `settings` `popExitTransition`: Change `fadeOut(Motion.effectsFast())` to `fadeOut(Motion.effectsDefault())`. Symmetrically mirrors the enter transition and ensures the slide remains visible throughout.
  - `chat` `popExitTransition`: Change `fadeOut(Motion.effectsFast())` to `fadeOut(Motion.effectsDefault())`. Ensures graceful slide-and-fade exit even when conversation row is offscreen.
  - `home` dynamic transitions for sub-screens: When target is NOT `chat` and NOT `settings`, `home` uses `sharedAxisExit(forward = true)` on exit and `sharedAxisEnter(forward = false)` on popEnter. Synchronizes spatial movement across all sub-screens (`archived`, `outbox`, `dashboard`, `starred`).

### 2. In `Transitions.kt`:
- Support predictive back seeking by checking `isSeeking`:
  `if (transition.currentState == transition.targetState && !transition.isRunning && !transition.isSeeking) return this`
- Ensure `ChatScreen`'s top-bar avatar and contact name always maintain active shared element registration so target bounds are immediately available when back navigation begins.

### 3. In `HomeScreen.kt`:
- Attach `Modifier.sharedThreadAvatar(conv.threadId)` to `PinnedConversationsRow` and `ConversationMatchRow` so threads opened from pinned or search possess complete shared transition pairs.

---

## 4.5 Physical Verification Results

All verification tests were executed on `emulator-5554` running Android API 36 with genuine user input events and screen recordings.

### 1. 30 Consecutive Chat Opens and Backs Stress Test
- **Protocol**: 15 consecutive iterations via Top Bar Back Arrow (`adb shell input tap 75 148`), followed by 15 consecutive iterations via Predictive Back Swipe Gesture (`adb shell input swipe 5 1200 600 1200 200`).
- **Pass Criteria**: 0 snaps, 0 flickers, continuous smooth avatar and container bounds animation.
- **Results**:
  - Iterations 1-15 (Back Arrow): 15/15 PASS. 0 snaps, 0 flickers.
  - Iterations 16-30 (Back Gesture): 15/15 PASS. 0 snaps, 0 flickers.
  - Total: **30/30 PASS (100% success rate)**.
- **Evidence Recording**: `docs/reviews/round4/after/back_chat.mp4` (18.2 MB).

### 2. Settings Back Verification
- **Protocol**: Navigate to Settings from Home. Trigger back via arrow, then re-enter and trigger back via predictive gesture.
- **Pass Criteria**: Symmetrical slide-out to the right with continuous opacity decay; no mid-screen evaporation.
- **Result**: PASS. Settings slides out smoothly to +width/4 with `effectsDefault` decay as Home scales from 0.94f to 1.0f.
- **Evidence Recording**: `docs/reviews/round4/after/back_settings.mp4` (1.5 MB).

### 3. Sub-Screens Back Verification
- **Protocol**: Open sub-screens (`archived`, `dashboard`, `outbox`, `starred`, `about`) and verify return transitions via arrow and gesture.
- **Pass Criteria**: Synchronized `sharedAxis` transitions between parent and child destinations.
- **Result**: PASS. Symmetrical horizontal translation where child slides out to +full/5 while Home slides in from -full/5.
- **Evidence Recording**: `docs/reviews/round4/after/back_subscreens.mp4` (1.5 MB).

