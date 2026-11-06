# Phase 2 Recheck: SwipeableConversationRow Review Evidence

## 1. Background Color Modifier Lines

In `app/src/main/kotlin/com/messages/app/ui/home/HomeScreen.kt` within `SwipeableConversationRow`:

- **Line 1818** (non-swipeable fallback branch when selection active or both swipe directions disabled):
  ```kotlin
  Box(modifier.background(MaterialTheme.colorScheme.groupCard)) {
  ```
- **Line 1881** (swipeable card content container):
  ```kotlin
  .background(MaterialTheme.colorScheme.groupCard)
  ```

Both instances strictly consume `MaterialTheme.colorScheme.groupCard` (not `surface`).

---

## 2. Offset Read Sites (`liveOffset` and `returnOffset`)

All reads of `liveOffset` and `returnOffset` within `SwipeableConversationRow` are confined strictly to phase-deferred lambdas (`derivedStateOf`, lambda blocks, or `graphicsLayer`), never triggering recomposition in the composable body:

- **Line 1839** (inside `derivedStateOf` for swipe direction determination):
  ```kotlin
  val tx = liveOffset.floatValue + returnOffset.value
  ```
- **Line 1858** (inside `derivedStateOf` for commit threshold calculation):
  ```kotlin
  val tx = liveOffset.floatValue + returnOffset.value
  ```
- **Line 1872** (inside deferred lambda `progress = { ... }` passed to `SwipeActionBackground`):
  ```kotlin
  if (w > 0f) (kotlin.math.abs(liveOffset.floatValue + returnOffset.value) / w).coerceIn(0f, 1f) else 0f
  ```
- **Line 1880** (inside `graphicsLayer { ... }` draw/layout modifier lambda):
  ```kotlin
  .graphicsLayer { translationX = liveOffset.floatValue + returnOffset.value }
  ```
- **Gesture callbacks** (read inside `pointerInput` `detectHorizontalDragGestures` lambdas during user interaction):
  - Line 1886: `val next = liveOffset.floatValue + dragAmount` (`onHorizontalDrag`)
  - Line 1892: `val released = liveOffset.floatValue` (`onDragEnd`)
  - Line 1909: `val released = liveOffset.floatValue` (`onDragCancel`)

Zero reads occur in the composable function body during scrolling or dragging.

---

## 3. Recomposition Counts During 2-Second Drag

NOT RUN

**Reason**: Layout Inspector "Recomposition counts" requires an active interactive Android Studio GUI connection attached to a running process, which is unavailable in a headless terminal environment.
