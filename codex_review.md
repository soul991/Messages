# Codex Security, Correctness, and Quality Review

Audit date: 2026-07-27  
Reviewed revision: 7f19405845f67c5abf40048b6fae082ff6997823  
Project: Messages, Android default-SMS application  
Review mode: read-only static analysis, except for creation of this report

## Executive summary

The project has a thoughtful privacy architecture in several important areas, a substantial deterministic protection-engine test suite, disciplined normal/locked-space DAO filtering, and a valid signed release artifact. It is not ready for broad distribution without remediation, however. The review identified 12 high-severity, 14 medium-severity, and 6 low-severity findings.

The most urgent release blockers are:

1. Android platform backup is enabled for app-private state containing messages, drafts, secret-space authentication material, locally cached keys, media, and Drive settings.
2. stale notification bubbles can open a chat after app lock or a conversation lock is enabled;
3. the unread home-screen widget exposes senders and message bodies outside the app's privacy gates;
4. secret-space drafts can be persisted under normal-space keys;
5. Drive master-key lookup ignores pagination and backup/key creation is not serialized;
6. restore and backup-crypto input handling are non-transactional and insufficiently bounded;
7. the production secret-key wrapper silently stores a key-encryption key in plaintext when Android Keystore fails;
8. multipart/group SMS state is collapsed into one race-prone message status;
9. sensitive values removed from the current tree remain reachable in Git history.

Green unit tests and release lint do not invalidate these findings. Most defects occur at boundaries that the current suites do not model: stale PendingIntents, widgets, hostile backup envelopes, ContentProvider partial failure, Drive pagination/concurrency, redirects/DNS, and recipient/part result aggregation.

## Scope and method

The repository tree was enumerated from the root, including source sets, Gradle configuration, resources, tests, documentation, generated reports, release artifacts, Git references/history, and design references. The current tree contained 6,443 files and 1,927 directories outside the internal .git traversal. Project-controlled text files were reviewed as source. Binary/generated material was assessed through format metadata, archive integrity, manifests, test/lint summaries, signatures, and relevant embedded metadata.

No source, configuration, test, existing documentation, artifact, or Git state was changed. No Gradle command or test was run because doing so would create or change caches and build outputs, contrary to the requested read-only constraint. This report is the only created file.

Limitations:

- Findings are static-analysis conclusions unless explicitly tied to an existing generated result.
- No live device, carrier network, Google Drive account, or hostile HTTP endpoint was used.
- No online dependency/CVE lookup was performed. Dependency ages are recorded as maintenance signals, not claims that a particular CVE is present.
- Binary design files cannot be reviewed semantically byte-by-byte like source; they were validated by type, dimensions/metadata, archive integrity, and active-content/reference checks.
- Corrected code below is an implementation pattern. It must be integrated with the project's exact APIs and covered by tests before release.

## Severity model

| Severity | Meaning |
|---|---|
| High | Plausible sensitive-data exposure, authorization bypass, destructive data loss, cryptographic failure, or a release-blocking integrity defect. |
| Medium | Material privacy, availability, correctness, or reliability defect requiring normal user action, malformed input, concurrency, or a less common platform condition. |
| Low | Limited-impact defect, debug/process risk, UX glitch, or hardening opportunity. |

## Existing evidence and positive controls

- Existing generated test reports show 80 protection-engine tests, 73 core-messaging tests, and 39 app tests passing with zero failures/errors.
- app/build/intermediates/lint_vital_intermediate_text_report/release/lintVitalReportRelease/lint-results-release.txt says “No issues found.”
- app/build/outputs/apk/release/app-release.apk has valid ZIP integrity and APK Signature Scheme v2 signing. The signing certificate matches docs/ops/RELEASE_SIGNING.md.
- The release artifact reports versionCode 1, versionName 1.0, minSdk 26, and targetSdk 35.
- Space-aware Room filtering is generally disciplined and is specifically exercised by SpaceInvisibilityTest.
- Incoming SMS/MMS classification failures use a safe Inbox fallback rather than silently dropping content.
- Secret-space notifications deliberately use generic content.
- Release exported SMS/MMS components are protected with platform permissions; internal result/action receivers are not exported.
- The bundled protection library contains 122 unique patterns and the regression corpus contains 513 entries.
- Existing generated Room implementation evidence shows FTS triggers are recreated after migrations.
- All three design-reference ZIP files pass integrity testing. Raster files are valid PNGs; the PDF is one-page, unencrypted, and contains no JavaScript. The SVGs contain embedded/internal references and foreignObject content, but no script elements or external URL references were found.

## Prioritized findings

| ID | Severity | Finding |
|---|---|---|
| R-01 | High | Android Auto Backup exposes app-private sensitive state |
| R-02 | High | Stale bubbles bypass a lock enabled after notification creation |
| R-03 | High | Unread widget leaks sender and message previews |
| R-04 | High | Secret-space drafts leak into normal-space state/UI |
| R-05 | High | Deleting a group SMS removes only one provider row |
| R-06 | High | Conversation preview/category summaries can become stale or incorrect |
| R-07 | High | Drive master-key discovery is paginated incorrectly and key creation races |
| R-08 | High | Restore is non-transactional, weakly validated, and insufficiently bounded |
| R-09 | High | Backup crypto accepts attacker-controlled CPU/memory work and unauthenticated metadata |
| R-10 | Medium | Drive upload/download duplicates or buffers whole backup files |
| R-11 | Medium | Restore dedupe can discard legitimate messages |
| R-12 | Medium | Locked-space MMS media is omitted from backups |
| R-13 | High | Multipart/group SMS status is message-level and race-prone |
| R-14 | Medium | Headless quick replies are not persisted or status-tracked |
| R-15 | Medium | “Not spam” leaves fraud state and its warning notification behind |
| R-16 | Medium | Intake/provider fallbacks can collide, double-count, or leave partial state |
| R-17 | Medium | MMS/attachment paths read unbounded input before enforcing limits |
| R-18 | High | Secret KEK silently falls back to plaintext storage |
| R-19 | Medium | Restored secret authentication accepts untrusted encoding and work factors |
| R-20 | Medium | Link previews create SSRF and message-content privacy exposure |
| R-21 | Medium | Imported pattern packs and user regexes permit ReDoS/unbounded input |
| R-22 | Medium | Privacy policy contradicts implemented network and storage behavior |
| R-23 | High | Redacted personal/configuration data remains reachable in Git history |
| R-24 | Low | Debug-only destructive/injection receivers are callable by any installed app |
| R-25 | Medium | Database versions 1–3 are destructively migrated |
| R-26 | Low | App-lock state causes a first-frame lock-screen glitch when lock is disabled |
| R-27 | Medium | CI omits important modules, release checks, and security gates |
| R-28 | Medium | Build supply-chain verification is incomplete |
| R-29 | Low | Worker cancellation and poison-row completion semantics need correction |
| R-30 | Low | OTP auto-copy exposes codes through the global clipboard |
| R-31 | Low | Several conversation preferences are keyed only by thread ID, not space |
| R-32 | Low | Generated/design artifacts create privacy, size, and repository-hygiene risks |

## Detailed findings and fixes

### R-01 — Android Auto Backup exposes app-private sensitive state

Evidence:

- app/src/main/AndroidManifest.xml:24-31 sets android:allowBackup="true".
- No android:fullBackupContent or android:dataExtractionRules exclusion policy is declared.
- Sensitive state exists in Room, SharedPreferences, filesDir, cached secret material, imported patterns, drafts, wallpapers/media, and Drive configuration.

Impact:

Platform cloud backup or device transfer can copy plaintext indexed message content, conversation metadata, drafts, verifier/salt material, a cached secret-space KEK, locally cached Drive key material, pending locked restore blobs, and private media. This undermines the app's explicit backup and secret-space boundaries. R-18 makes this especially serious because a Keystore failure can place the KEK itself into SharedPreferences as plaintext Base64.

Fix:

Because the project already implements an explicit encrypted Drive backup, the safest policy is to disable platform backup entirely:

~~~xml
<application
    android:name=".MessagesApp"
    android:allowBackup="false"
    android:fullBackupContent="false"
    ...>
~~~

If product requirements insist on backing up a small set of non-sensitive preferences, use allowBackup with explicit Android 11 and Android 12+ rule files and default-deny every domain. Never include databases, drafts, secret-space preferences/files, Drive keys, pending envelopes, wallpapers, MMS media, or imported patterns. Test both cloud-backup and device-transfer behavior on supported Android versions and OEMs.

Required tests:

- merged release manifest asserts allowBackup=false;
- backup/restore integration test confirms no Room database, secret prefs, drafts, media, or key files enter an adb/bmgr data set;
- upgrade test confirms the policy applies to existing installations.

### R-02 — Stale bubbles bypass a lock enabled after notification creation

Evidence:

- app/src/main/kotlin/com/messages/app/notify/MessageNotifier.kt:258-277 checks AppLock only when BubbleMetadata is built.
- app/src/main/kotlin/com/messages/app/BubbleActivity.kt:16-42 accepts threadId and renders ChatScreen without rechecking app lock, legacy conversation lock, normal/locked space, or current authorization.
- PendingIntents can remain valid after settings or conversation state changes.

Impact:

A bubble created while unlocked can remain available after app lock is enabled or the conversation is moved/locked. Opening it renders conversation content outside MainActivity's authentication gate.

Fix:

Authorization must be checked at the destination on every resume. When app lock is enabled, a bubble should route through MainActivity's gate instead of rendering content directly. Include the expected space/message identity in the bubble intent and verify that it still resolves to an allowed normal-space row.

~~~kotlin
class BubbleActivity : FragmentActivity() {
    private var rendered = false

    override fun onResume() {
        super.onResume()
        val threadId = intent.getLongExtra("threadId", -1L)
        if (threadId <= 0L) return finish()

        lifecycleScope.launch {
            val allowed = withContext(Dispatchers.IO) {
                if (AppLock.isEnabled(this@BubbleActivity)) return@withContext false
                val conv = MessageRepository.get(applicationContext)
                    .db.conversations().byThreadId(threadId, Spaces.NORMAL)
                conv != null && !conv.locked
            }
            if (!allowed) {
                startActivity(Intent(this@BubbleActivity, MainActivity::class.java).apply {
                    putExtra("threadId", threadId)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
                finish()
                return@launch
            }
            if (!rendered) {
                rendered = true
                setContent { MessagesTheme { ChatScreen(threadId, onBack = ::finish) } }
            }
        }
    }
}
~~~

Required tests:

- create bubble, enable app lock, then launch its PendingIntent;
- create bubble, lock/move the conversation, then launch it;
- background an already-open bubble and enable lock before resuming;
- verify locked-space thread IDs never render through BubbleActivity.

### R-03 — Unread widget leaks sender and message previews

Evidence:

- app/src/main/kotlin/com/messages/app/widget/Widgets.kt:68-83 renders contact/address and lastMessage.
- core-messaging/src/main/kotlin/com/messages/core/db/MessagesDatabase.kt:382-392 does not exclude legacy locked=true conversations.
- Widget code does not consult AppLock.isEnabled, AppLock.hidePreviews, or a current privacy state.

Impact:

The launcher can expose sender identity and message content even while the app requires authentication or previews are hidden. Legacy locked normal-space rows can also be shown.

Fix:

Treat a widget as an unauthenticated surface. If app lock is enabled or previews are hidden, show count-only generic content. Exclude locked rows at the DAO layer regardless of UI state.

~~~kotlin
@Query(
    "SELECT * FROM conversations " +
    "WHERE category='INBOX' AND unreadCount>0 AND archived=0 " +
    "AND space='NORMAL' AND locked=0 " +
    "ORDER BY lastTimestamp DESC LIMIT :limit"
)
suspend fun recentWidgetRows(limit: Int): List<ConversationEntity>

val privateSurface =
    AppLock.isEnabled(context) || AppLock.hidePreviews(context)
val lines = if (privateSurface) {
    if (count == 0) "" else "Open Messages to view"
} else {
    repo.db.conversations().recentWidgetRows(3).joinToString("\n") { conv ->
        val sender = conv.contactName ?: conv.address
        (sender + " · " + conv.lastMessage).take(40)
    }
}
views.setTextViewText(R.id.widget_unread_lines, lines)
~~~

Also refresh widgets immediately whenever app-lock, hide-preview, conversation-lock, or secret-space routing state changes.

### R-04 — Secret-space drafts leak into normal-space state/UI

Evidence:

- app/src/main/kotlin/com/messages/app/ui/chat/ChatScreen.kt:254-270 correctly avoids restoration and debounced persistence in locked space.
- ChatScreen.kt:272-276 unconditionally saves the latest draft in DisposableEffect.onDispose.
- app/src/main/kotlin/com/messages/app/ui/common/DraftStore.kt:8-41 keys values only by threadId.
- app/src/main/kotlin/com/messages/app/ui/home/HomeScreen.kt:505 and 619 reads that shared map for normal rows.

Impact:

Leaving a secret chat can persist its composer text under a normal-space key. A normal Home conversation with the same thread ID can then show the secret draft preview. Platform backup further amplifies the leak.

Immediate fix:

~~~kotlin
val latestDraft = rememberUpdatedState(draft)
DisposableEffect(threadId, inLockedSpace) {
    onDispose {
        if (!inLockedSpace) {
            DraftStore.save(context, Spaces.NORMAL, threadId, latestDraft.value)
        }
    }
}
~~~

Structural fix:

Namespace every per-conversation store by a stable ConversationKey(space, threadId), even when the current policy chooses not to persist locked drafts:

~~~kotlin
data class ConversationKey(val space: String, val threadId: Long) {
    fun preferenceKey() = space + ":" + threadId
}
~~~

Do not blindly clear an old threadId-only key when opening a locked row; the same thread can legitimately have a normal conversation and normal draft. Migrate legacy keys as NORMAL and provide a targeted cleanup for values known to have been written by the buggy locked-space disposal path.

### R-05 — Deleting a group SMS removes only one provider row

Evidence:

- core-messaging/src/main/kotlin/com/messages/core/MessageRepository.kt:588-643 writes one Telephony.Sms.Sent row per recipient but stores only firstSmsId.
- MessageRepository.kt:1033-1046 deletes only msg.smsId.

Impact:

Deleting/trashing a group SMS removes only the first recipient's provider row. Other rows remain visible to other SMS applications and outside this app's trash/retention model. Restore/delete can become inconsistent.

Fix:

Model provider rows as a one-to-many relation:

~~~kotlin
@Entity(
    tableName = "provider_rows",
    indices = [Index("messageId")],
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ProviderRowEntity(
    @PrimaryKey val uri: String,
    val messageId: Long,
    val recipient: String,
    val kind: String
)
~~~

Return all inserted provider URIs, create mappings in the same Room transaction as the local message, and on trash/delete attempt every mapped URI. Record deletion failures for retry instead of discarding them. Migration must seed one mapping from existing smsId/mmsId values.

Required tests:

- two- and three-recipient sends produce all mappings;
- trash/delete touches every provider URI;
- one provider deletion failure is retained and retried;
- undo/restore does not duplicate provider rows.

### R-06 — Conversation preview/category summaries can become stale or incorrect

Evidence:

- MessageRepository.kt:706-735 overwrites lastMessage/lastTimestamp even for older out-of-order arrivals.
- MessageRepository.kt:795-811 changes the conversation category when any selected message is recategorized, even when it is not the latest message.
- MessageRepository.kt:1094-1107 refreshes body/time but omits category and only writes when body/time differ.
- core-messaging/src/main/kotlin/com/messages/core/backup/BackupManager.kt:600-630 preserves an existing category even when a newer restored message wins.

Impact:

Folder membership and conversation preview can disagree with the latest live message. Old SMS arriving late can roll a thread backward. Recategorizing an old message can move the whole conversation. Restore can retain stale categories.

Fix:

Create one authoritative summary recomputation used by intake, recategorization, deletion, restore, and send-result paths:

~~~kotlin
suspend fun refreshConversationSummary(threadId: Long, space: String) {
    val existing = db.conversations().byThreadId(threadId, space) ?: return
    val latest = db.messages().latestForThread(threadId, space)
    if (latest == null) {
        db.conversations().deleteByThreadId(threadId, space)
        return
    }
    db.conversations().upsert(
        existing.copy(
            address = latest.address,
            lastMessage = latest.body.ifBlank { mediaPreview(latest.mediaMimeType) },
            lastTimestamp = latest.timestamp,
            category = latest.category
        )
    )
}
~~~

For the fast intake path, increment unread independently but update preview/category only when timestamp is newer than or equal to the stored latest timestamp. Call authoritative recomputation after any mutation that can affect ordering/category.

### R-07 — Drive master-key discovery is paginated incorrectly and key creation races

Evidence:

- app/src/main/kotlin/com/messages/app/drive/DriveClient.kt:61-77 requests only 25 files, does not request nextPageToken, and ignores pagination.
- app/src/main/kotlin/com/messages/app/drive/DriveBackup.kt:176-197 finds the key through that incomplete listing and accepts the first match.
- DriveBackup.kt:429-450 gives periodic and manual backups different unique-work names, so both can run concurrently.

Impact:

Once the key file falls outside the first 25 results, a new key may be minted. Concurrent first backups can also create different keys. Snapshots then become split across master keys; later restores can fail depending on which duplicate key is selected.

Fix:

Paginate every Drive list and query the exact key filename:

~~~kotlin
fun list(query: String? = null): List<RemoteFile> {
    val result = mutableListOf<RemoteFile>()
    var pageToken: String? = null
    do {
        val params = buildList {
            add("spaces=appDataFolder")
            add("pageSize=1000")
            add("orderBy=createdTime desc")
            add("fields=nextPageToken,files(id,name,size,createdTime)")
            query?.let { add("q=" + URLEncoder.encode(it, "UTF-8")) }
            pageToken?.let { add("pageToken=" + URLEncoder.encode(it, "UTF-8")) }
        }.joinToString("&")
        val json = JSONObject(request("GET", DRIVE_FILES_URL + "?" + params))
        result += parseFiles(json.getJSONArray("files"))
        pageToken = json.optString("nextPageToken").takeIf(String::isNotBlank)
    } while (pageToken != null)
    return result
}

val keys = client.list("name = 'messages_master_key.bin' and trashed = false")
require(keys.size <= 1) { "Multiple Drive master keys require recovery" }
~~~

Serialize all manual/periodic backup execution through one process-wide mutex or a persistent backup lease, and ensure every worker enters the same critical section. After uploading a first key, re-list and reject duplicates instead of silently choosing one. Keep old keys during recovery; never delete a duplicate until every snapshot has been mapped/decrypted.

### R-08 — Restore is non-transactional, weakly validated, and insufficiently bounded

Evidence:

- core-messaging/src/main/kotlin/com/messages/core/backup/BackupManager.kt:287-462 decodes an unrestricted JSON string and incrementally changes settings, pattern files, rules, reputations, provider rows, media files, Room rows, conversation summaries, and pending secret state.
- app/src/main/kotlin/com/messages/app/ui/settings/SettingsScreen.kt:254-266 reads the selected backup fully into a String before import.
- A Room transaction cannot atomically cover SharedPreferences, Telephony ContentProvider, and filesystem writes.

Impact:

A corrupt, huge, or deliberately malformed backup can exhaust memory/storage, partially apply settings and rows, duplicate provider messages, leave orphan media, or leave a restore that cannot be safely retried.

Fix architecture:

1. Stream into a bounded temporary staging file.
2. Validate envelope/JSON schema, counts, lengths, enum values, timestamps, filenames, Base64 sizes, and total expanded media before mutation.
3. Generate a stable backupId and persist a restore journal.
4. Stage media under a temporary restore directory with canonical-path checks.
5. Apply Room changes in one withTransaction block.
6. Apply ContentProvider writes through an idempotent journal keyed by backupId + message UUID.
7. Atomically rename committed media, then apply settings last.
8. On restart, resume or compensate from the journal.

~~~kotlin
data class RestoreLimits(
    val maxEnvelopeBytes: Long = 512L * 1024 * 1024,
    val maxJsonBytes: Long = 128L * 1024 * 1024,
    val maxMessages: Int = 500_000,
    val maxRules: Int = 5_000,
    val maxMediaFiles: Int = 20_000,
    val maxMediaBytes: Long = 2L * 1024 * 1024 * 1024
)

suspend fun importValidated(stage: ValidatedBackup) {
    db.withTransaction {
        restoreJournal.startIfAbsent(stage.backupId)
        insertRulesAndMessagesIdempotently(stage)
        rebuildAffectedConversations(stage.affectedThreadSpaces)
        restoreJournal.markRoomCommitted(stage.backupId)
    }
    replayProviderJournal(stage.backupId)
    commitStagedMedia(stage.backupId)
    applyValidatedSettings(stage.settings)
    restoreJournal.markComplete(stage.backupId)
}
~~~

Required hostile-input tests include truncation at every phase, disk-full simulation, malformed Base64, path traversal filenames, duplicate message IDs, provider permission loss, process death/resume, and repeated import.

### R-09 — Backup crypto accepts attacker-controlled CPU/memory work and unauthenticated metadata

Evidence:

- BackupCrypto.kt:229-253 accepts any header length up to blob size, then decodes unrestricted metadata.
- BackupCrypto.kt:171-186 trusts iterations, salt, nonce, and wrapped-key encoding from the header before PBKDF2/AES work.
- BackupCrypto.kt:271-272 expands GZIP with readBytes and no decompressed-size limit.
- Header metadata is plaintext and is not supplied as AES-GCM additional authenticated data.

Impact:

A malicious local/Drive backup can request extreme PBKDF2 work, allocate large values, trigger GZIP bombs, crash restore/listing, or modify unauthenticated metadata such as counts/timestamps/device model. Repeated password wraps multiply work.

Fix:

Enforce limits before decoding/derivation and introduce a versioned format that authenticates a canonical header as AAD:

~~~kotlin
private const val MAX_BLOB = 512 * 1024 * 1024
private const val MAX_HEADER = 64 * 1024
private const val MAX_EXPANDED = 128 * 1024 * 1024
private const val MIN_ITERATIONS = 100_000
private const val MAX_ITERATIONS = 2_000_000

fun validateHeader(blob: ByteArray): Pair<Header, ByteArray> {
    require(blob.size in 9..MAX_BLOB)
    require(String(blob, 0, 4, Charsets.US_ASCII) == MAGIC)
    val length = bytesToInt(blob, 4)
    require(length in 1..minOf(MAX_HEADER, blob.size - 8))
    val bytes = blob.copyOfRange(8, 8 + length)
    val header = json.decodeFromString(Header.serializer(), bytes.toString(Charsets.UTF_8))
    require(header.formatVersion in SUPPORTED_VERSIONS)
    require(header.wrappedKeys.size in 1..4)
    require(unb64(header.nonce).size == 12)
    header.wrappedKeys.forEach { key ->
        require(key.method in SUPPORTED_METHODS)
        require(unb64(key.nonce).size == 12)
        require(unb64(key.wrapped).size == 48)
        if (key.method == METHOD_PASSWORD) {
            require(key.iterations in MIN_ITERATIONS..MAX_ITERATIONS)
            require(unb64(key.salt).size in 16..64)
        }
    }
    return header to bytes
}

private fun gunzipBounded(bytes: ByteArray, limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(bytes.size * 2, limit))
    GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Backup expands beyond limit" }
            output.write(buffer, 0, count)
        }
    }
    return output.toByteArray()
}
~~~

For format version 2, serialize the canonical header before finalizing the payload cipher and call cipher.updateAAD(headerBytes) in both seal and open. Retain a bounded legacy version-1 reader only for migration.

### R-10 — Drive upload/download duplicates or buffers whole backup files

Evidence:

- DriveClient.kt:80-105 builds a second complete multipart ByteArray around the already-complete backup ByteArray.
- DriveClient.kt:109-193 downloads all bytes into ByteArrayOutputStream with no caller-supplied cap.
- downloadPrefix does not require HTTP 206 Partial Content and does not cap bytes if the server returns 200.
- DriveBackup.kt:341-366 falls back from an 8 KiB header probe to downloading the entire snapshot during list display.

Impact:

Media-heavy backups can require several times their size in heap, causing foreground crashes. A server/proxy ignoring Range can turn a small listing operation into a full unbounded download.

Fix:

- Generate encrypted backups to a bounded file/stream, not a String/ByteArray.
- Use Drive resumable upload and stream fixed-size chunks.
- Stream restore downloads to a bounded staging file.
- For prefix reads require status 206, validate Content-Range, and stop after exactly maxBytes.
- Never full-download a snapshot merely to render the chooser; mark an unreadable header as unavailable.
- Use a foreground WorkManager worker for user-visible large backup/restore operations.

### R-11 — Restore dedupe can discard legitimate messages

Evidence:

- BackupManager.kt:574-597 constructs a key from address, timestamp, direction, and Java String.hashCode().
- Java hash collisions are trivial; for example different strings such as “Aa” and “BB” share a hash.
- Even identical legitimate repeated messages at the same timestamp collapse.

Impact:

Restore can silently skip real messages and report them as duplicates.

Fix:

Add a persisted random message UUID/event ID at intake and carry it in backups. Dedupe by UUID. For legacy backups, use canonical SHA-256 over length-delimited fields plus an occurrence ordinal within the export; do not use String.hashCode().

~~~kotlin
fun legacyDigest(m: BackupMessage, occurrence: Int): String {
    val canonical = listOf(
        m.address, m.timestamp.toString(), m.isOutgoing.toString(),
        m.body.length.toString(), m.body, occurrence.toString()
    ).joinToString("\u0000")
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
~~~

### R-12 — Locked-space MMS media is omitted from backups

Evidence:

- BackupManager.kt:184-223 creates a media map only for normal included rows.
- BackupManager.kt:250-284 serializes locked rows with toBackupMessage(it, null) and LockedPayload has no media map.

Impact:

Users can believe locked chats are fully backed up while their MMS attachments are silently lost.

Fix:

Extend the credential-encrypted LockedPayload with its own bounded media map and per-message media reference. Apply the same file count/per-file/total-byte limits as normal media, but keep all media inside the locked sub-envelope. Report skipped oversize/corrupt files explicitly in the backup result rather than silently omitting them.

### R-13 — Multipart/group SMS status is message-level and race-prone

Evidence:

- app/src/main/kotlin/com/messages/app/schedule/Scheduling.kt:31-80 reuses request codes across recipients and sends every callback with only messageId.
- app/src/main/kotlin/com/messages/app/receiver/OtherReceivers.kt:252-297 updates one message row for every part/recipient callback.
- MessagesDatabase.kt:79-83 permits markSent after DELIVERED, so a late SENT callback can downgrade DELIVERED to SENT.
- One early success can mark the whole message SENT/DELIVERED before other parts fail.

Impact:

UI status can claim successful send/delivery for a partially failed group or multipart message, hide retry needs, or oscillate based on broadcast order.

Fix:

Persist one attempt per recipient and part:

~~~kotlin
@Entity(indices = [Index("messageId")])
data class SmsAttempt(
    @PrimaryKey val attemptId: String,
    val messageId: Long,
    val recipientIndex: Int,
    val partIndex: Int,
    val sentState: String = "PENDING",
    val deliveryState: String = "PENDING",
    val resultCode: Int? = null
)

fun requestCode(messageId: Long, recipient: Int, part: Int): Int =
    stableHash32(messageId, recipient, part)
~~~

Put attemptId in each PendingIntent and make its data URI unique. Receivers update only that attempt, then derive the aggregate in a transaction:

- FAILED if any required part failed;
- SENDING while any part is pending;
- SENT only when every part for every recipient is sent;
- DELIVERED only when every requested delivery report is delivered;
- state transitions are monotonic.

Add tests that permute callback order and mix success/failure over multiple recipients and parts.

### R-14 — Headless quick replies are not persisted or status-tracked

Evidence:

- app/src/main/kotlin/com/messages/app/service/HeadlessSmsSendService.kt:16-27 directly calls SmsManager.sendTextMessage.
- It does not divide multipart text, create a Telephony/Room history row, select the conversation SIM, request status callbacks, or surface errors.

Impact:

Dialer quick replies can be sent but absent from conversation history, fail silently, or fail for multipart content.

Fix:

Run the same repository + SmsRadio path as the composer inside a lifecycle-bound coroutine, stop the service only after persistence/send dispatch, validate URI schemes/recipient/text length, and record a failed row when dispatch fails.

### R-15 — “Not spam” leaves fraud state and its warning notification behind

Evidence:

- MessagesDatabase.kt:63 only updates category and dangerous.
- MessageRepository.kt:795-802 does not clear fraudWarning, score, matched IDs, or explanations.
- MessageNotifier.kt:324 uses a separate fraud notification ID.
- OtherReceivers.kt:343-346 cancels only threadId.toInt().

Impact:

A message moved to Inbox can still render as fraudulent/dangerous and its red warning can remain visible.

Fix:

Centralize reclassification with explicit user-override provenance:

~~~sql
UPDATE messages
SET category = 'INBOX',
    dangerous = 0,
    fraudWarning = 0,
    score = 0,
    matchedPatternIds = '',
    matchedComboIds = '',
    explanations = 'User marked as not spam'
WHERE id = :id
~~~

Recompute the conversation summary, cancel both the thread and fraud IDs through one notifier API, and preserve the original classifier result in separate audit fields if product UX needs “Why?” history.

### R-16 — Intake/provider fallbacks can collide, double-count, or leave partial state

Evidence:

- MessageRepository.kt:113-173 uses address.hashCode().toLong() as a thread-ID fallback.
- MessageRepository.kt:171-172 updates unread/summary even if a duplicate/ignored Room insert returns no new logical row.
- MessageRepository.kt:706-735 lets older arrivals overwrite a newer preview.
- MessageRepository.kt:322-370 and 477-540 performs multi-step MMS PDU/part/address provider writes without cleanup when an intermediate operation fails.

Impact:

Hash-colliding senders can merge, duplicate broadcasts can inflate unread counts/notifications, out-of-order messages can roll previews backward, and partial MMS provider records can remain.

Fix:

- Use a collision-resistant synthetic thread mapping table keyed by canonical recipient set, not hashCode.
- Enforce a unique provider identity/transaction ID and only update unread/notify when insertion actually created a row.
- Apply the newer-timestamp rule from R-06.
- If MMS provider construction fails after creating the PDU, delete the incomplete PDU and its parts/addresses; keep a retry journal.
- Add duplicate-delivery and out-of-order intake tests.

### R-17 — MMS/attachment paths read unbounded input before enforcing limits

Evidence:

- app/src/main/kotlin/com/messages/app/mms/MmsSender.kt:30-39 calls readBytes before checking MAX_ATTACHMENT_BYTES.
- app/src/main/kotlin/com/messages/app/receiver/OtherReceivers.kt:125-132 reads an entire downloaded PDU.
- app/src/main/kotlin/com/messages/app/ui/chat/ChatViewModel.kt:159-170 reads a complete saved file on resend.
- OtherReceivers.kt:83-87 grants specifically to com.android.phone, which is not portable across all OEM telephony implementations.

Impact:

Large/malicious content URIs or carrier payloads can exhaust heap/disk before the limit is evaluated. OEM MMS download can fail when the telephony package differs.

Fix:

Use AssetFileDescriptor length as an early rejection when available, then a bounded streaming reader that aborts at limit+1. Decode image bounds before allocation and use sampled decoding. Bound PDU size, part count, header lengths, and each part's data length in MmsPduParser. Resolve eligible telephony handlers and grant URI permission to each resolved package rather than hardcoding one package; revoke grants after the callback.

### R-18 — Secret KEK silently falls back to plaintext storage

Evidence:

- core-messaging/src/main/kotlin/com/messages/core/secret/LocalKeyBox.kt:13-18 describes plaintext as an acceptable degradation.
- LocalKeyBox.kt:27-34 catches every Keystore failure and stores plain: + Base64(secret).
- LocalKeyBox.kt:36-49 accepts that plaintext form in production.

Impact:

An unavailable, invalidated, or malfunctioning Android Keystore silently turns the backup key-encryption key into plaintext app data. With R-01, that key can be copied by platform backup. With extracted app-private data, it can allow decryption of the locked backup sub-envelope without the user's credential.

Fix:

Fail closed in production. Keep a plaintext/fake implementation only through test dependency injection:

~~~kotlin
internal interface KeyBox {
    fun encrypt(secret: ByteArray): String
    fun decrypt(value: String): ByteArray
}

internal object AndroidKeyBox : KeyBox {
    override fun encrypt(secret: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return "ks:" + Base64.getEncoder()
            .encodeToString(cipher.iv + cipher.doFinal(secret))
    }

    override fun decrypt(value: String): ByteArray {
        require(value.startsWith("ks:")) { "Unprotected key-box value rejected" }
        // Decode, require minimum length, require 12-byte nonce, then AES-GCM open.
        return decryptKeystoreValue(value.removePrefix("ks:"))
    }
}
~~~

On Keystore failure, clear only the unusable cache, require the user to re-enter the secret credential to derive a fresh KEK, and show a recoverable error. Add an upgrade migration that detects plain: values, rewraps after successful authentication, and never backs them up.

### R-19 — Restored secret authentication accepts untrusted encoding and work factors

Evidence:

- core-messaging/src/main/kotlin/com/messages/core/secret/SecretSpace.kt:164-174 parses an unrestricted pipe-delimited string and accepts any kind/iteration value.
- SecretSpace.kt:225-250 adopts and decodes untrusted Base64/iterations.
- SecretSpace.kt:253-254 uses Base64 decoder without length validation.

Impact:

A crafted backup can cause extreme PBKDF2 CPU work, malformed-Base64 crashes, invalid credential UI state, or oversized verifier/salt allocations during unlock.

Fix:

Use a versioned serialized object and validate before storing or deriving:

~~~kotlin
fun PendingAuth.Companion.parseValidated(value: String): PendingAuth? = runCatching {
    require(value.length <= 1024)
    val fields = value.split('|')
    require(fields.size == 5)
    val iterations = fields[3].toInt()
    require(iterations in 100_000..2_000_000)
    require(fields[4] in setOf(SecretCrypto.KIND_PIN, SecretCrypto.KIND_PATTERN,
        SecretCrypto.KIND_PASSWORD))
    require(Base64.getDecoder().decode(fields[0]).size == 16)
    require(Base64.getDecoder().decode(fields[1]).size == 32)
    require(Base64.getDecoder().decode(fields[2]).size == 16)
    PendingAuth(fields[0], fields[1], fields[2], iterations, fields[4])
}.getOrNull()
~~~

Validate again at the derivation boundary; do not rely only on the import path.

### R-20 — Link previews create SSRF and message-content privacy exposure

Evidence:

- app/src/main/kotlin/com/messages/app/ui/chat/LinkPreview.kt:54-83 fetches arbitrary HTTPS hosts and follows up to three redirects.
- It does not reject loopback, private, link-local, multicast, IPv6 ULA, or local DNS results.
- app/src/main/kotlin/com/messages/app/ui/chat/ChatScreen.kt:2083-2113 passes attacker-selected og:image to Coil, creating a second fetch outside LinkPreview's HTML limit/redirect policy.
- Enabling previews causes the recipient device to contact a sender-controlled host, revealing IP address, timing, and that the message was opened.

Impact:

A message can make the app probe local services/routers/cloud metadata endpoints, bypass a first-hop check via redirects or DNS rebinding, and disclose recipient activity. An image URL can download a large body or reach a separate internal target.

Fix:

At minimum, validate every redirect target and every resolved address:

~~~kotlin
fun requirePublicHttps(raw: String): URL {
    val url = URL(raw)
    require(url.protocol.equals("https", ignoreCase = true))
    require(url.userInfo == null)
    require(url.port == -1 || url.port == 443)
    val addresses = InetAddress.getAllByName(url.host)
    require(addresses.isNotEmpty())
    require(addresses.all { address ->
        val bytes = address.address
        val ipv6Ula = bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
        !address.isAnyLocalAddress &&
            !address.isLoopbackAddress &&
            !address.isLinkLocalAddress &&
            !address.isSiteLocalAddress &&
            !address.isMulticastAddress &&
            !ipv6Ula
    })
    return url
}
~~~

Pre-resolution alone does not close DNS rebinding because HttpURLConnection can resolve again. Use a network client that connects only to the validated/pinned DNS result, revalidates every redirect, and rejects redirects across hosts unless explicitly intended. Fetch og:image through the same bounded client, validate content type/dimensions/length, or omit remote images. The setting UI and privacy policy must clearly disclose the network behavior.

### R-21 — Imported pattern packs and user regexes permit ReDoS/unbounded input

Evidence:

- app/src/main/kotlin/com/messages/app/ui/settings/SettingsScreen.kt:202-218 reads an imported pattern pack with readText and no byte limit.
- core-messaging/src/main/kotlin/com/messages/core/MessageRepository.kt:95-103 accepts the parsed pack without count/field/regex complexity limits.
- MessageRepository.kt:438-454 passes user rules into classification.
- protection-engine/src/main/kotlin/com/messages/protection/PatternMatcher.kt:16-29 compiles and evaluates Kotlin/Java backtracking Regex.

Impact:

A large pack can exhaust heap/storage. Catastrophic backtracking in a pattern or custom rule can block message intake/classification and repeatedly affect future messages.

Fix:

- Limit imported file bytes before converting to String.
- Limit pattern count, IDs, category values, regex length, examples, combo references, and total serialized size.
- Prefer RE2/J or another linear-time engine for untrusted patterns.
- If Java Regex must remain, reject backreferences/lookbehind/nested ambiguous quantifiers and run classification in an isolated, cancellable boundary; coroutine cancellation alone cannot interrupt arbitrary regex CPU.
- Validate custom rules with the same policy before storing.

~~~kotlin
fun InputStream.readUtf8Limited(maxBytes: Int): String {
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "Pattern pack is too large" }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

fun validatePack(library: PatternLibrary) {
    require(library.patterns.size in 1..500)
    library.patterns.forEach { pattern ->
        require(pattern.id.length in 1..80)
        require(pattern.regex.length in 1..512)
        require(pattern.category in Category.entries)
        SafeRegexPolicy.requireAccepted(pattern.regex)
    }
}
~~~

### R-22 — Privacy policy contradicts implemented network and storage behavior

Evidence:

- docs/ops/privacy_policy.md:7-16 says messages/contacts/metadata are never uploaded to third parties and claims RCS storage.
- privacy_policy.md:24 says none of the data accessed by permissions is transmitted over the network.
- privacy_policy.md:26-30 acknowledges Drive but then says no third-party service collects data.
- Drive backup transmits encrypted message content/metadata to Google Drive.
- Link previews send requests to arbitrary third-party hosts and may fetch their images.
- RCS support is not implemented.
- privacy_policy.md:35-36 uses a messagesapp.example.com placeholder contact address.

Impact:

The published policy can be materially misleading, particularly for a privacy-focused messaging product. This creates user-trust and distribution/compliance risk.

Fix:

Rewrite before distribution to state precisely:

- what stays on-device;
- what encrypted backup contains, where it is sent, retention, key custody, and the “Google-account access can recover account-plain backups” model;
- that optional link previews contact the linked site/image host and reveal normal network metadata;
- which Google authentication/Drive services are used;
- no RCS claim until implemented;
- real operator identity/contact channel, effective date, deletion/retention instructions, and jurisdiction-appropriate rights.

Do not claim end-to-end secrecy from Google-account compromise when the master key is stored in the same Drive appDataFolder as snapshots.

### R-23 — Redacted personal/configuration data remains reachable in Git history

Evidence:

- Commit 4767016 redacts the working tree, but its parent 100b191 and earlier reachable commits retain the prior values.
- Reachable history contains the removed unrelated DetectiveDialer tree.
- Git object f062f89c18046270ac6f161b7694420a0df168f8 is associated with DetectiveDialer-main/android/app/google-services.json.
- The historical values were not reproduced in this report.
- git fsck reports refs/remotes/origin/HEAD as an invalid all-zero SHA and numerous unreachable commits/trees.

Impact:

Anyone receiving the repository history can recover values that appear deleted from the current checkout, including personal identifiers and a Firebase configuration/API-key blob. Unreachable objects can also retain sensitive material locally until reflogs expire and garbage collection occurs.

Fix:

1. Inventory every secret/personal value and decide whether it must be revoked, restricted, or rotated. Firebase API keys are identifiers rather than standalone server secrets, but should still be API-restricted and the associated project/config reviewed.
2. Coordinate a history rewrite using git-filter-repo to remove/redact exact paths and values from all branches/tags.
3. Verify with rev-list, grep over rewritten history, and a fresh clone.
4. Force-push only after coordination; require collaborators to reclone instead of merging old history.
5. Repair or remove the invalid origin/HEAD ref.
6. After backup/coordination, expire old reflogs and prune only when authorized; do not perform repository garbage collection as part of a review.

### R-24 — Debug-only destructive/injection receivers are callable by any installed app

Evidence:

- app/src/debug/AndroidManifest.xml:7-22 exports InjectSmsReceiver and CleanupTestResidueReceiver without a permission.
- One injects messages; the other can delete qualifying provider/index rows.

Impact:

On a debug build installed on a real device, any application can call these components. The cleanup action is materially destructive even though the components do not ship in release.

Fix:

Prefer non-exported instrumentation/test APIs. If adb broadcast is required, protect both receivers with a debug-only signature permission and validate an unpredictable per-install token. Make cleanup target explicit test IDs rather than broad message characteristics.

### R-25 — Database versions 1–3 are destructively migrated

Evidence:

- core-messaging/src/main/kotlin/com/messages/core/MessageRepository.kt:32-38 calls fallbackToDestructiveMigrationFrom(1, 2, 3).

Impact:

Users upgrading an older development/install build lose local categories, rules, reputation, search/index metadata, conversation preferences, and local-only state. Even if described as pre-release, versionCode is already 1 and personal-use data can be valuable.

Fix:

Ship explicit 1→current, 2→current, and 3→current migrations or a tested export/import bridge. Add Room MigrationTestHelper tests from every committed schema JSON. Remove destructive fallback before any build is distributed to users whose data matters.

### R-26 — App-lock state causes a first-frame lock-screen glitch when lock is disabled

Evidence:

- app/src/main/kotlin/com/messages/app/MainActivity.kt:54-55 initializes unlocked=false.
- MainActivity.kt:177-190 composes LockScreen while false.
- MainActivity.kt:607-617 corrects it in onStart only after composition/lifecycle progression.

Impact:

Users without app lock can see a transient lock screen/flicker, and UI tests can become timing-sensitive.

Fix:

Initialize state from AppLock.isEnabled before setContent, or use an explicit UNKNOWN/LOCKED/UNLOCKED state and render a neutral launch surface until resolved.

### R-27 — CI omits important modules, release checks, and security gates

Evidence:

- .github/workflows/ci.yml:9-52 runs only protection-engine tests and app assembleDebug.
- It does not run app tests, core-messaging tests, Room migration tests, full lint, release/R8 assembly, APK verification, dependency verification, or hostile backup/network tests.
- GitHub Actions use mutable major tags rather than reviewed full commit SHAs.

Impact:

Regressions in storage, backup, app UI logic, migrations, release shrinker configuration, and dependencies can merge while CI remains green.

Recommended minimum CI:

~~~yaml
jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@<verified-full-commit-sha>
      - uses: actions/setup-java@<verified-full-commit-sha>
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@<verified-full-commit-sha>
      - name: Tests, lint, and release build
        run: >
          ./gradlew --no-daemon
          :protection-engine:test
          :core-messaging:testDebugUnitTest
          :app:testDebugUnitTest
          lint
          :app:assembleRelease
      - name: Verify release artifact
        run: scripts/verify-release-artifact.sh app/build/outputs/apk/release/app-release.apk
~~~

Use reviewed, current full SHAs rather than inventing/pasting unknown hashes. Add separate jobs for Room migration matrices, backup fuzz/property tests, dependency verification, and emulator/device integration tests. Release CI should fail clearly if the intended signing configuration is absent; app/build.gradle.kts:11-50 currently permits a silent unsigned release.

### R-28 — Build supply-chain verification is incomplete

Evidence:

- gradle/wrapper/gradle-wrapper.properties has no distributionSha256Sum.
- No gradle/verification-metadata.xml is present.
- core-messaging/build.gradle.kts:13 references consumer-rules.pro, but no such file exists.
- gradle/libs.versions.toml uses a 2024-era AGP/Kotlin/AndroidX toolchain. This is an update signal only; no CVE assertion is made.

Impact:

The wrapper distribution and resolved dependency artifacts are not cryptographically pinned by project configuration. A missing consumer rules file creates avoidable build ambiguity. Silent unsigned release output can be mistaken for a distributable artifact.

Fix:

~~~properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
distributionSha256Sum=<official-SHA-256-for-gradle-8.9-bin.zip>
~~~

Obtain the checksum from Gradle's official checksum publication over a trusted channel and verify it independently; do not guess the value. Generate and review strict dependency verification metadata, commit it, and enable verification in CI. Either add a deliberate consumer-rules.pro or remove the reference. Schedule dependency updates with release tests, and make signing intent explicit per environment.

### R-29 — Worker cancellation and poison-row completion semantics need correction

Evidence:

- core-messaging/src/main/kotlin/com/messages/core/cleanup/OtpCleanupWorker.kt:24-29 and trash/TrashPurgeWorker.kt:23-26 catch cancellation inside runCatching and convert it to Result.retry().
- backfill/BackfillWorker.kt:55-104 logs per-row failures but still marks KEY_DONE=true.
- Backfill already correctly rethrows CancellationException in its own row/batch loops; cleanup workers should follow that pattern.

Impact:

Stopped work can be misreported/retried, and permanently failing backfill rows become silently excluded while the app records the import as complete.

Fix:

~~~kotlin
override suspend fun doWork(): Result = try {
    MessageRepository.get(applicationContext).purgeExpiredTrash()
    Result.success()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.retry()
}
~~~

Persist poison-row IDs and failure reasons, expose incomplete import state, and retry with a capped policy. Mark done only when no unindexed rows remain or when the user explicitly accepts a reported exception list.

### R-30 — OTP auto-copy exposes codes through the global clipboard

Evidence:

- app/src/main/kotlin/com/messages/app/notify/MessageNotifier.kt:49-57 performs opt-in automatic copy on receipt.
- app/src/main/kotlin/com/messages/app/notify/OtpClipboard.kt:30-46 places the code in the global ClipboardManager.
- The sensitive flag is advisory and Android versions before 13 provide weaker clipboard protections.

Impact:

Keyboards, accessibility services, overlays, foreground applications, and older-platform clipboard readers may observe the OTP. Automatic copy occurs before the user interacts with the message.

Fix:

Keep the feature off by default, add a strong warning for pre-Android 13, prefer an explicit notification action, and clear the clipboard after a short timeout only if it still contains the same OTP. Never auto-copy from locked-space, legacy-locked, dangerous, or fraud-warning messages.

### R-31 — Several conversation preferences are keyed only by thread ID, not space

Evidence:

- DraftStore.kt:8-41 keys drafts by threadId.
- app/src/main/kotlin/com/messages/app/ui/chat/ChatStyle.kt:80-105 keys bubble style, wallpaper preference, and wallpaper file by threadId.
- app/src/main/kotlin/com/messages/app/ui/chat/PinStore.kt:12-52 keys local pins by threadId.
- A thread can have both NORMAL and LOCKED conversation rows.

Impact:

Normal and locked conversations can share/cross-over style, wallpaper, draft, or pin state. A secret wallpaper preference/file name can be inferred or rendered on the normal row; message IDs in PinStore are also local/unstable.

Fix:

Use a single ConversationKey(space, threadId) across preferences, files, shortcuts, channels, drafts, pins, and caches. Define migration rules for old keys as NORMAL. Review every API that defaults space=Spaces.NORMAL and require an explicit space at secret-surface call sites.

### R-32 — Generated/design artifacts create privacy, size, and repository-hygiene risks

Evidence:

- design-refs contains 14 files totaling about 176 MB: three ZIPs, eight PNGs, two SVGs, and one PDF.
- ZIPs duplicate extracted visual assets; the largest SVGs are about 53 MB and 19 MB, and the PDF is about 23 MB.
- Generated test XML/report files contain local host/user metadata; manifest-merge reports contain absolute workstation and Gradle-cache paths.
- git fsck reports an invalid remote HEAD ref and numerous unreachable objects.

Impact:

Sharing the working directory rather than a clean clone can expose workstation metadata and unnecessarily distribute large third-party reference material. Large SVG foreignObject content can also be expensive or inconsistently rendered by tools, even though no scripts/external references were found.

Fix:

- Distribute from a clean, reproducible checkout/artifact, never the local build directory.
- Keep build/ and local reports out of source archives.
- Store design references outside the application repository or in a documented asset store/LFS if licensing permits.
- Retain one canonical format per reference where practical.
- Record provenance/license/attribution for community UI references.
- Repair origin/HEAD and perform object cleanup only after the R-23 history rewrite and explicit authorization.

## Cross-cutting architecture improvements

### 1. Make conversation identity explicit

Introduce ConversationKey(threadId, space) as a value object and require it through repositories, navigation, notifications, widgets, preferences, caches, channels, shortcuts, drafts, styles, and pins. Avoid default space parameters at security-sensitive call sites.

### 2. Separate message event identity from provider identity

Persist:

- a stable random message UUID for backup/idempotence;
- zero-to-many provider URIs;
- zero-to-many recipient/part send attempts;
- restore/import provenance.

This resolves R-05, R-11, R-13, and much of R-16 with one coherent model.

### 3. Centralize derived conversation state

Conversation preview, timestamp, category, unread count, and sender metadata are derived state. Define one transactional recomputation/aggregation path and prohibit ad-hoc conversation.copy(category=...) updates.

### 4. Treat every external byte source as hostile

Use shared bounded readers for SAF files, Drive responses, GZIP, Base64 media, content URIs, MMS PDUs, pattern packs, and HTTP bodies. Each parser should define:

- maximum container size;
- maximum element count;
- per-field/element limit;
- allowed enum/version values;
- expansion ratio/total;
- timeout or linear-time algorithm;
- canonical path/URL policy.

### 5. Add durable operation journals

Backup, restore, provider writes/deletes, MMS construction, and group sends cross process/storage boundaries. A Room-backed journal enables idempotent resume, explicit partial-failure UI, and compensation after process death.

### 6. Make privacy state a reusable policy

Create one PrivacyPolicy evaluator for app lock, preview hiding, normal/locked space, legacy locks, and surface type. Notifications, bubbles, widgets, shortcuts, reminders, share targets, exports, clipboard, and screenshots should ask that policy rather than implement partial independent checks.

## Required test and verification matrix

| Area | Required coverage |
|---|---|
| Platform backup | Merged-manifest assertion; Android 11 and 12+ backup/transfer exclusion test |
| Bubbles | Stale PendingIntent after app-lock/conversation-lock/space change; already-open bubble resume |
| Widgets | App lock, hide previews, legacy lock, no unread, locked-space invisibility |
| Drafts/preferences | NORMAL and LOCKED rows with same thread ID; disposal/recreation/process death |
| Provider rows | Multi-recipient insert/delete/undo; partial ContentProvider failure |
| Conversation summaries | Out-of-order intake, old-message recategorize, delete latest, media preview, restore newer/older |
| Drive | More than 25 files, multiple pages, duplicate key files, concurrent manual/periodic first backup |
| Backup parser | Header/iteration/Base64 bounds, GZIP bomb, huge JSON/media, unsupported version/method |
| Restore | Process death at each phase, disk full, provider role loss, retry idempotence, path traversal |
| Locked backup | Text plus media round trip; missing KEK; pending restore success/failure |
| SMS send | Every permutation of recipient/part callbacks, partial failure, late callbacks, monotonic status |
| MMS | Oversize URI/PDU/part counts, malformed lengths, OEM grant resolution, partial provider cleanup |
| Link preview | Private IPv4/IPv6, redirects, DNS rebinding harness, non-206/range, oversized HTML/image |
| Patterns | Oversize pack, invalid references/enums, known catastrophic regexes, classification time budget |
| Migrations | Every schema version to current, including provider-row/message-UUID/send-attempt additions |
| Release | Full tests, full lint, minified release build, signature/certificate/version/component verification |

## Ordered remediation roadmap

### Phase 0 — Stop privacy/authorization exposure

1. R-01 disable platform backup.
2. R-18 remove plaintext key fallback and migrate any existing plain values.
3. R-02 gate BubbleActivity at use time.
4. R-03 make widgets count-only under privacy settings and exclude locked rows.
5. R-04 fix secret draft disposal and introduce space-aware keys.
6. R-20 disable link-preview image loading or the whole feature until SSRF-safe fetching and disclosure exist.

### Phase 1 — Make backup/restore safe

1. R-07 implement exact paginated key lookup, duplicate detection, and serialized execution.
2. R-09 add strict crypto/header/decompression limits and a version-2 authenticated header.
3. R-08 implement staging, validation, journals, and idempotent restore.
4. R-10 stream Drive upload/download and enforce Range.
5. R-11 introduce stable message UUIDs.
6. R-12 include bounded locked media.
7. R-19 validate pending secret authentication.

### Phase 2 — Repair messaging data integrity

1. R-13 add recipient/part attempts and aggregate status.
2. R-05 store every provider row.
3. R-06 centralize conversation summaries.
4. R-16 repair dedupe/thread fallback/provider cleanup.
5. R-17 bound MMS inputs and make URI grants portable.
6. R-14 route headless replies through the standard path.
7. R-15 centralize classifier override/reset.

### Phase 3 — Release engineering and compliance

1. R-23 coordinate history rewrite and credential/configuration review.
2. R-22 publish an accurate privacy policy.
3. R-25 add complete migrations.
4. R-27 expand/pin CI and verify release artifacts.
5. R-28 enable wrapper/dependency verification and explicit signing behavior.
6. R-21 harden pattern imports and regex execution.

### Phase 4 — Hardening and cleanup

Address R-24, R-26, R-29, R-30, R-31, and R-32; then perform device/carrier/OEM regression testing.

## Release acceptance criteria

Do not treat the application as ready for broad distribution until:

- all Phase 0 and Phase 1 findings are fixed and covered by hostile-input/integration tests;
- multipart/group send and provider-row integrity are fixed;
- every supported Room schema migrates without destructive fallback;
- the privacy policy matches actual Drive/link-preview behavior;
- reachable history no longer contains the identified redacted/configuration material;
- CI runs all three test suites, full lint, migration tests, and a minified release build;
- release artifact signature, certificate, version, manifest exposure, and backup policy are automatically verified.

## Final assessment

The protection engine and much of the normal/locked-space database separation are strong foundations. The dominant risk is not the classifier; it is boundary consistency. Privacy checks are currently made when notifications are created rather than always when content is opened, derived conversation data is updated through multiple competing paths, and backup/send/provider operations cross several systems without bounded inputs or durable transaction journals.

Fixing the architectural seams described above will remove multiple findings at once and produce a system that is easier to reason about, test, and safely evolve.
