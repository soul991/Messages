# Codebase Issues Found

During a comprehensive review of the `main` branch, the following notable bugs and architectural oversights were discovered:

### 1. The Screen Rotation Lockout Glitch (`MainActivity.kt`)
In `MainActivity`, the `appUnlocked` state is defined as a simple mutable state property:
```kotlin
private var appUnlocked by mutableStateOf(false)
```
**The Bug:** Because this property is not saved across configuration changes (e.g., via `rememberSaveable` or a `ViewModel`), rotating the device destroys and recreates the Activity, resetting `appUnlocked` to `false`.
**The Impact:** If the user has AppLock enabled, they will be forced to re-authenticate with their fingerprint every time they rotate the screen. If AppLock is disabled, the Lock Screen will flash momentarily until `onStart()` corrects the state.

### 2. Swallowing Coroutine Cancellations (`MessageRepository.kt` & others)
Across the codebase, asynchronous work is heavily wrapped in catch-all exception blocks:
```kotlin
try {
    // Suspend functions
} catch (_: Exception) {
    // Swallowed
}
```
**The Bug:** Kotlin coroutines rely on throwing a `CancellationException` to safely cancel work when a scope is cancelled. Catching `Exception` (which is a superclass of `CancellationException`) without re-throwing it breaks structured concurrency.
**The Impact:** Background tasks (like syncing the inbox, running the FTS backfill, or classifying messages) will continue running invisibly even after they are supposed to be cancelled, leading to potential memory leaks, wasted CPU cycles, and battery drain.

### 3. Missing Conversation Summary Updates (`OtherReceivers.kt`)
In `SmsSentReceiver`, when a message successfully sends or fails to send, the repository correctly updates the `MessageEntity` in the database to `"SENT"` or `"FAILED"`.
**The Bug:** It forgets to update the parent `ConversationEntity`'s summary snippet.
**The Impact:** If a message fails to send, the Home Screen conversation list will still show the message text normally. The user won't know the message failed until they tap into the chat thread and see the red "Resend" UI. *(Note: This issue was fixed)*

### 4. Unmanaged Scopes in Broadcast Receivers (`OtherReceivers.kt`)
In the SMS receivers, `CoroutineScope(Dispatchers.IO).launch { ... }` is used after calling `goAsync()`.
```kotlin
val pending = goAsync()
CoroutineScope(Dispatchers.IO).launch {
    try {
        // DB work
    } finally {
        pending.finish()
    }
}
```
**The Bug:** `CoroutineScope()` creates an entirely unmanaged, orphaned scope. While `pending.finish()` is correctly called in the `finally` block, if the OS kills the process or if the database query hangs indefinitely, this unmanaged scope has no lifecycle boundaries.
**The Impact:** Potential memory leaks or app crashes if the process is shut down unexpectedly.
