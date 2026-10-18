# Data Loss Assessment: Synthetic Stress Data Injection in v2.1.0

## 1. Executive Summary

In release v2.1.0, `MessagesApp.onCreate()` unconditionally scheduled synthetic stress data injection via `SyntheticStressData.injectIfNeeded(context)`. Before inserting 100 synthetic messages under thread ID 100 (`STRESS_THREAD_ID = 100L`), the injection routine checked existing rows in that thread. If the row count was between 1 and 99, it executed `repo.db.messages().userDelete(it.id)` on every message returned.

This document details the exact conditions under which real user messages were deleted, whether those messages are recoverable, and how affected users can verify message integrity.

---

## 2. Exact Conditions Affecting Real Messages

A user's messages were affected if and only if **all** of the following conditions were met:

1. **Version Installed**: The application was launched on version 2.1.0 (`commit f858e95`).
2. **Thread ID Collision**: The user had a real conversation assigned local `threadId == 100L`.
3. **Space**: The messages resided in `Spaces.NORMAL`.
4. **Message Count**: The conversation had fewer than 100 messages (`existing.size in 1..99`).

### Code Citation

In `SyntheticStressData.kt` (lines 16–29):
```kotlin
    const val STRESS_THREAD_ID = 100L
    const val STRESS_CONTACT_NAME = "Elena Rostova"
    const val STRESS_ADDRESS = "+1 (555) 019-2834"

    suspend fun injectIfNeeded(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        val repo = MessageRepository.get(context)
        val existing = repo.db.messages().listForThread(STRESS_THREAD_ID, Spaces.NORMAL)
        if (existing.size >= 100 && !force) {
            return@withContext
        }

        // Wipe old test thread rows if re-injecting
        if (existing.isNotEmpty()) {
            existing.forEach { repo.db.messages().userDelete(it.id) }
        }
```

And in `MessagesApp.kt` (lines 29, 32–40 in v2.1.0):
```kotlin
        sealLockedBacklog()
        injectSyntheticStressData()
    }

    private fun injectSyntheticStressData() {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        ).launch {
            runCatching {
                com.messages.app.ui.chat.SyntheticStressData.injectIfNeeded(this@MessagesApp)
            }
        }
    }
```

---

## 3. Mechanism of Deletion: Hard Delete vs. Soft Delete (Trash)

The deletion performed by `SyntheticStressData.kt` was a **hard delete** at the local database layer, not a soft delete.

### Room DAO Definition

In `core-messaging/src/main/kotlin/com/messages/core/db/MessagesDatabase.kt` (lines 317–318):
```kotlin
    // User-initiated only — the filter itself never calls delete (§6). Normal
    // user deletions go through the Trash flags below (§6.4); the permitted
    // hard-delete callers are: "Delete forever" in Trash, the 60-day trash
    // purge, the user-ENABLED OTP cleanup (§6.6 — bypasses Trash), and
    // cancelling a scheduled draft (never sent, nothing to retain).
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun userDelete(id: Long)
```

In contrast, soft-deletion to Trash uses `moveToTrash` (lines 322–323):
```kotlin
    @Query("UPDATE messages SET trashed = 1, trashedAt = :at WHERE id = :id")
    suspend fun moveToTrash(id: Long, at: Long)
```

Because `userDelete()` executes `DELETE FROM messages WHERE id = :id`:
- Affected rows were permanently removed from the Room `messages` table.
- Affected rows were **not** marked `trashed = 1`.
- Affected rows do **not** exist in the in-app Trash folder.

---

## 4. Recoverability Assessment

### Room Local Database
- **Recoverable from Room**: No. The rows were deleted from SQLite without a tombstone or retention backup.

### System Telephony Provider (Android SMS/MMS Store)
- **Telephony copy intact**: **Yes**.
- **Proof**: `SyntheticStressData.kt` directly invoked `repo.db.messages().userDelete(it.id)` on the Room DAO. It did **not** interact with `android.content.ContentResolver`, did **not** touch `android.provider.Telephony.Sms.CONTENT_URI` or `Telephony.Mms.CONTENT_URI`, and did **not** call `MessageRepository.deleteMessage()` (which synchronizes deletions with the system telephony provider).
- Therefore, the system-level SMS/MMS database maintained by Android telephony services was never modified by this code. All underlying SMS messages remain intact in the device's system telephony database.

---

## 5. How a User Can Check

1. **System SMS Store Verification**:
   Users can open any system SMS application (e.g., standard Google Messages or manufacturer messaging app) or inspect system provider data via adb:
   ```bash
   adb shell content query --uri content://sms --where "thread_id=100"
   ```
   If messages were present in the conversation with thread ID 100, the system telephony provider will return them.

2. **In-App Verification**:
   In the Messages app, checking the Trash folder confirms that the messages are not in Trash.
