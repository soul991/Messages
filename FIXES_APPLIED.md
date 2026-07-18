# Fixes applied — Messages

**Branch:** `codex/s`
**Date:** 2026-07-18

This is an implementation handoff for reviewing the changes in this branch.
It maps each fix to the source files, includes the key code, and records how it
was verified. It intentionally shows the essential changed logic rather than
duplicating full source files.

## 1. Make MMS classification failure-safe

**Problem:** SMS already used a safe Inbox fallback; MMS did not.

**Changed file:** `core-messaging/src/main/kotlin/com/messages/core/MessageRepository.kt`

The shared helper is now used by incoming SMS, historical indexing, and incoming
MMS:

```kotlin
private suspend fun classifyIncomingOrInbox(
    address: String,
    body: String,
    source: String,
): Verdict = runCatching { classify(address, body) }.getOrElse { t ->
    android.util.Log.e("MessageRepository", "$source classification failed — defaulting to Inbox", t)
    Verdict(
        Category.INBOX,
        explanations = listOf("Classification unavailable — defaulted to Inbox"),
    )
}
```

Incoming MMS now calls:

```kotlin
val verdict = classifyIncomingOrInbox(senderAddress, textBody, source = "MMS")
```

The catch deliberately covers `Throwable`, not only `Exception`: an Android-only
regex compilation problem can surface during static initialization as an error.
The message had already been written to the Telephony provider; this fallback
also preserves the Room index, categorization, and notification path.

## 2. Preserve malformed/unavailable MMS deliveries visibly

**Changed file:** `app/src/main/kotlin/com/messages/app/receiver/OtherReceivers.kt`

All previous early returns for missing PDU metadata, missing callback file paths,
failed downloads, parse failures, and receiver exceptions route through one
fallback policy:

```kotlin
internal object MmsReceiveFallback {
    const val UNKNOWN_SENDER = "Unknown MMS sender"
    const val PARSE_FAILURE = "notification could not be read"
    const val DOWNLOAD_FAILURE = "couldn't be downloaded"
    const val PROCESSING_FAILURE = "couldn't be processed"

    fun addressOrUnknown(address: String?): String =
        address?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN_SENDER

    fun placeholderBody(reason: String): String = "[MMS message — $reason]"
}
```

The stored fallback is intentionally a normal incoming MMS row, so it remains
searchable and visible in the conversation list rather than becoming a log-only
event:

```kotlin
val result = repo.onIncomingMms(
    safeAddress,
    MmsReceiveFallback.placeholderBody(reason),
    System.currentTimeMillis(),
    transactionId,
    emptyList(),
) ?: return
```

**Regression test:**
`app/src/test/kotlin/com/messages/app/receiver/MmsReceiveFallbackTest.kt`

It verifies sender retention, explicit unknown-sender handling, and user-facing
placeholder text.

## 3. Avoid MMS media filename collisions

**Changed file:** `core-messaging/src/main/kotlin/com/messages/core/MessageRepository.kt`

Before:

```kotlin
java.io.File(dir, "${timestamp}_${att.data.size}.$ext")
```

After:

```kotlin
java.io.File(dir, "${timestamp}_${java.util.UUID.randomUUID()}.$ext")
```

Two attachments that arrive in the same millisecond with the same byte length
can no longer overwrite one another.

## 4. Add a real theme-mode setting

**New file:** `app/src/main/kotlin/com/messages/app/ThemePreferences.kt`

```kotlin
object ThemePreferences {
    private const val PREFS = "settings"
    private const val KEY_THEME_MODE = "theme_mode"

    fun current(context: Context): ThemeMode {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return ThemeMode.values().firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
}
```

**Root wiring:** `app/src/main/kotlin/com/messages/app/MainActivity.kt`

```kotlin
private var themeMode by mutableStateOf(ThemeMode.SYSTEM)

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    themeMode = ThemePreferences.current(this)
    // ...
}

MessagesTheme(mode = themeMode) {
    // app content
}
```

**Settings UI:** `app/src/main/kotlin/com/messages/app/ui/settings/SettingsScreen.kt`

The Appearance section offers System default, Light, Dark, and AMOLED black.
The selected option is saved and passed back to `MainActivity`, which recomposes
the root `MessagesTheme` immediately.

## 5. Add honest release documentation

**New file:** `README.md`

The README now documents deterministic/offline filtering, the never-delete
guarantee, protected-message behavior, the PRD-required honest limitation,
local build steps, module boundaries, and unresolved device/Drive release gates.

**Updated file:** `PROGRESS.md`

Corrected current corpus/pattern counts and stale feature statements, then added
this branch’s MMS hardening, theme picker, documentation, and verification
record.

## Verification

Ran successfully after the implementation changes:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :app:testDebugUnitTest :core-messaging:testDebugUnitTest :protection-engine:test :app:assembleDebug
```

The build still emits the existing AGP 8.5.2 / compile SDK 35 compatibility
warning. That needs a tested toolchain upgrade before release; it was not hidden
or suppressed by this branch.
