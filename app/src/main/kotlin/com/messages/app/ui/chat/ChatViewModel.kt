package com.messages.app.ui.chat

import androidx.annotation.StringRes
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messages.app.R
import com.messages.app.mms.MmsSender
import com.messages.app.schedule.Scheduler
import com.messages.app.schedule.SmsRadio
import com.messages.core.MessageRepository
import com.messages.core.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.messages.core.secret.LockedContent

class ChatViewModel(
    app: Application,
    private val threadId: Long,
    /** Recipient for a brand-new thread with no conversation row yet (compose flow). */
    private val fallbackAddress: String? = null,
    /** Secret space: which side of the wall this chat instance lives on. The
     *  same threadId can have one conversation per space (New locked chat). */
    val space: String = com.messages.core.db.Spaces.NORMAL,
    /** Survives process death: staged attachment + in-flight camera target —
     *  Android routinely kills us while the camera app is foreground. */
    private val savedState: androidx.lifecycle.SavedStateHandle =
        androidx.lifecycle.SavedStateHandle(),
) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val inLockedSpace: Boolean get() = space == com.messages.core.db.Spaces.LOCKED

    val messages: StateFlow<List<MessageEntity>> =
        repo.db.messages().messagesForThread(threadId, space)
            // V2-6: locked bodies are stored sealed. This is the only path that
            // renders them, so it is the only one that has to open them.
            .map { LockedContent.open(app, it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contactName = MutableStateFlow<String?>(null)
    val address = MutableStateFlow("")

    /** Conversation category — tints the top-bar avatar to match the list row. */
    val category = MutableStateFlow<String?>(null)

    /** Locked conversation (§8.2): gate the chat UI until authenticated. */
    val locked = MutableStateFlow(false)
    val chatUnlocked = MutableStateFlow(false)

    /** Dual-SIM (§8.1): available SIMs and this chat's choice (null = system default). */
    data class SimOption(val subId: Int, val slotIndex: Int, val displayName: String)
    val simOptions = MutableStateFlow<List<SimOption>>(emptyList())
    val selectedSubId = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch {
            val conv = repo.db.conversations().byThreadId(threadId, space)
            if (conv != null) {
                address.value = conv.address
                contactName.value = conv.contactName
                category.value = conv.category
                selectedSubId.value = conv.preferredSubId
                locked.value = conv.locked
            } else if (!fallbackAddress.isNullOrBlank()) {
                address.value = fallbackAddress
                contactName.value = withContext(Dispatchers.IO) {
                    repo.displayNameFor(fallbackAddress)
                }
            }
            repo.db.messages().markThreadRead(threadId, space)
            repo.db.conversations().clearUnread(threadId, space)
            com.messages.app.widget.WidgetUpdater.requestUpdate(getApplication())
            // Conversation shortcuts + direct-share ranking (§8.2). NEVER for
            // the secret space or legacy locked chats — no launcher identity.
            if (conv?.locked != true && !inLockedSpace) {
                val name = contactName.value ?: address.value
                if (name.isNotBlank()) {
                    com.messages.app.shortcut.ConversationShortcuts.push(
                        getApplication(), threadId, name,
                    )
                    com.messages.app.shortcut.ConversationShortcuts.reportUsed(
                        getApplication(), threadId,
                    )
                }
            }
            loadSimOptions()
        }
    }

    private fun loadSimOptions() {
        // V2-48: the enumeration (and the "only offer a choice with 2+ SIMs"
        // rule) now lives in SimChoices, shared with the outbox's change-SIM
        // action so the two cannot disagree about what is selectable.
        simOptions.value = com.messages.app.sms.SimChoices.active(getApplication())
            .map { SimOption(it.subId, it.slotIndex, it.displayName) }
    }

    /** Per-chat SIM choice, persisted on the conversation row. */
    fun selectSim(subId: Int?) {
        selectedSubId.value = subId
        // V2-24: without `space` this wrote the SIM choice onto the normal-space
        // row with the same threadId — a different conversation entirely.
        viewModelScope.launch { repo.db.conversations().setPreferredSubId(threadId, subId, space) }
    }

    fun send(text: String) {
        val to = address.value
        if (to.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            // V2-30: an exception here (provider refusal, lost default-SMS role,
            // invalid subscription) used to end as a cancelled coroutine with
            // nothing on screen. The row may not even exist, so there is no
            // FAILED bubble to retry from — the ViewModel is the last boundary
            // that can still tell the user.
            guard(
                SendIssue.SEND_FAILED, R.string.chat_send_failed, R.string.action_retry,
                action = { send(text) },
            ) {
                val subId = selectedSubId.value
                val entity = repo.storeOutgoing(to, text, System.currentTimeMillis(), subId, space)
                SmsRadio.send(getApplication(), repo, entity)
            }
        }
    }

    /** Scheduled send (§8.2): index-only row now, worker fires at [sendAt]. */
    fun scheduleSend(text: String, sendAt: Long) {
        val to = address.value
        if (to.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            // WorkManager.enqueue can throw (storage full, scheduler limit). If
            // it does, the SCHEDULED row exists with no worker behind it and
            // would sit in the chat forever pretending to be pending — so the
            // row is withdrawn before the failure is reported.
            var entityId: Long? = null
            guard(
                SendIssue.SCHEDULE_FAILED, R.string.chat_schedule_failed, R.string.action_retry,
                action = { scheduleSend(text, sendAt) },
                onFailure = { entityId?.let { runCatching { repo.cancelScheduled(it) } } },
            ) {
                val entity = repo.storeScheduledSms(to, text, sendAt, selectedSubId.value, space)
                entityId = entity.id
                Scheduler.scheduleSend(getApplication(), entity.id, sendAt)
            }
        }
    }

    /** "Send now" on a scheduled bubble. */
    // Explicit return type: the retry closure below calls this function, and an
    // expression body that references itself cannot be type-inferred.
    fun sendScheduledNow(messageId: Long): kotlinx.coroutines.Job = viewModelScope.launch {
        // V2-6b: promoteScheduledToSending declines locked rows it cannot open
        // (content key down) by returning null — indistinguishable here from
        // "the worker claimed it first". Say why up front instead of nothing.
        if (space == com.messages.core.db.Spaces.LOCKED &&
            !com.messages.core.secret.LockedContent.available(getApplication())
        ) {
            report(SendIssue.LOCKED_UNAVAILABLE, R.string.secret_seal_blocked_write)
            return@launch
        }
        guard(
            SendIssue.SEND_FAILED, R.string.chat_send_failed, R.string.action_retry,
            action = { sendScheduledNow(messageId) },
        ) {
            Scheduler.cancelSend(getApplication(), messageId)
            // null = the due worker claimed it first (V2-19). Not an error:
            // the message is on its way, just not by this path.
            val entity = repo.promoteScheduledToSending(messageId) ?: return@guard
            SmsRadio.send(getApplication(), repo, entity)
        }
    }

    /** Cancel a scheduled bubble: drop the row + the pending worker. */
    fun cancelScheduled(messageId: Long) = viewModelScope.launch {
        guard(SendIssue.CANCEL_FAILED, R.string.chat_cancel_failed) {
            Scheduler.cancelSend(getApplication(), messageId)
            repo.cancelScheduled(messageId)
        }
    }

    /** Snooze / remind-me-about-this-message (§8.2). */
    fun snooze(messageId: Long, remindAt: Long) {
        Scheduler.snooze(getApplication(), messageId, remindAt)
    }

    fun resend(message: MessageEntity) {
        // Failed MMS: rebuild from the locally saved media file, don't degrade to text.
        if (message.mmsTransactionId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                // R-17: the saved media file is on disk and can be arbitrarily
                // large (the user picked it; a later edit can grow it). Read it
                // through the bounded reader with the same ceiling the send path
                // enforces — null past the limit, never a full-file readBytes().
                val attachment = message.mediaUri
                    ?.let { ref ->
                        // V2-25: a live attachment is a file in app storage; a
                        // backfilled one references `content://mms/part/…` in
                        // the shared provider. Both read through the bounded
                        // reader, but only via the matching door.
                        val file = com.messages.core.media.MediaRef.asFile(ref)
                        if (file != null) {
                            com.messages.core.io.BoundedRead.readFile(
                                file, MmsSender.MAX_ATTACHMENT_BYTES,
                            )
                        } else {
                            com.messages.core.io.BoundedRead.readUri(
                                ctx, android.net.Uri.parse(ref), MmsSender.MAX_ATTACHMENT_BYTES,
                            )
                        }
                    }
                    ?.let { bytes ->
                        com.messages.core.mms.MmsPduParser.Attachment(
                            message.mediaMimeType ?: "application/octet-stream", null, bytes,
                        )
                    }
                if (attachment == null && message.body.isBlank()) {
                    report(SendIssue.NOTHING_TO_SEND, R.string.chat_nothing_to_resend)
                    return@launch
                }
                // The media was recorded but could not be rebuilt (missing, or
                // now past the attachment ceiling). Say so instead of quietly
                // resending a text-only shadow of the original message.
                if (attachment == null && message.mediaUri != null) {
                    report(
                        SendIssue.ATTACHMENT_MISSING, R.string.chat_attachment_missing,
                        // V2-23: the text-only fallback exists, but only as an
                        // explicit choice — never as a silent substitution.
                        actionLabel = if (message.body.isBlank()) null else R.string.chat_send_text_only,
                        action = if (message.body.isBlank()) null else {
                            { send(message.body) }
                        },
                    )
                    return@launch
                }
                guard(
                    SendIssue.SEND_FAILED, R.string.chat_resend_failed, R.string.action_retry,
                    action = { resend(message) },
                ) {
                    MmsSender.send(
                        ctx, message.address, message.body, attachment,
                        selectedSubId.value, space,
                    )
                }
            }
        } else {
            send(message.body)
        }
    }

    // ---- Per-chat customization (§8.2): bubble color + wallpaper ----
    val bubbleStyleId = MutableStateFlow(ChatStyle.bubbleId(app, threadId, space))
    val wallpaperId = MutableStateFlow(ChatStyle.wallpaperId(app, threadId, space))

    /** Bumped on photo import so a re-imported image invalidates the cache. */
    val wallpaperVersion = MutableStateFlow(0)

    fun setBubbleStyle(id: String) {
        ChatStyle.setBubble(getApplication(), threadId, id, space)
        bubbleStyleId.value = id
    }

    fun setWallpaper(id: String) {
        ChatStyle.setWallpaper(getApplication(), threadId, id, space)
        wallpaperId.value = id
    }

    fun importWallpaper(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        if (ChatStyle.importPhoto(getApplication(), threadId, uri, space)) {
            wallpaperId.value = ChatStyle.WALLPAPER_PHOTO
            wallpaperVersion.value++
        } else {
            report(SendIssue.WALLPAPER_FAILED, R.string.chat_wallpaper_failed)
        }
    }

    /** Attachment picked in the composer, pending send (process-death safe). */
    val pendingAttachment: StateFlow<Uri?> =
        savedState.getStateFlow("pending_attachment", null)

    /** FileProvider target of an in-flight camera capture (process-death safe:
     *  TakePicture only returns a boolean — we must remember where it wrote). */
    val cameraTarget: StateFlow<Uri?> = savedState.getStateFlow("camera_target", null)

    // ---- V2-30: structured operational state for the send/schedule paths ----

    /**
     * Why an operation could not complete. The *kind* is the durable part: the
     * message wording is presentation, the enum is what a caller (or a test)
     * can branch on without string matching.
     */
    enum class SendIssue {
        /** The picked file could not be turned into an MMS part. */
        ATTACHMENT_UNREADABLE,

        /** A previously saved attachment is gone or now past the size ceiling. */
        ATTACHMENT_MISSING,

        /** Neither text nor media survived — there is nothing to put on the wire. */
        NOTHING_TO_SEND,

        /** The message could not be stored or handed to the radio. */
        SEND_FAILED,

        /** The row was created but no worker could be enqueued behind it. */
        SCHEDULE_FAILED,

        /** A scheduled message could not be withdrawn. */
        CANCEL_FAILED,

        /** Chat wallpaper import failed. */
        WALLPAPER_FAILED,

        /** V2-6b: the locked space refuses writes while its content key is
         *  unavailable (Keystore outage) — see LockedWriteBlockedException. */
        LOCKED_UNAVAILABLE,
    }

    /**
     * A failure the user can act on. [action] is the retry contract the review
     * asked for: the composer has already been cleared by the time this is
     * shown, so the closure — not the UI — owns the text being retried.
     */
    data class SendProblem(
        val issue: SendIssue,
        val message: String,
        val actionLabel: String? = null,
        val action: (() -> Unit)? = null,
    )

    val sendProblem = MutableStateFlow<SendProblem?>(null)

    /**
     * V2-36. Callers name the copy by resource id; a ViewModel has no
     * composition to read it from, so it resolves through the Application.
     */
    private fun report(
        issue: SendIssue,
        @StringRes message: Int,
        @StringRes actionLabel: Int? = null,
        action: (() -> Unit)? = null,
    ) {
        val app = getApplication<Application>()
        sendProblem.value = SendProblem(
            issue,
            app.getString(message),
            actionLabel?.let(app::getString),
            action,
        )
    }

    /**
     * Runs [block], turning any failure into user-visible state instead of a
     * silently cancelled coroutine. CancellationException is rethrown — a
     * cancelled scope is not a send failure and must not be reported as one.
     */
    private suspend fun guard(
        issue: SendIssue,
        @StringRes message: Int,
        @StringRes actionLabel: Int? = null,
        action: (() -> Unit)? = null,
        onFailure: suspend () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: com.messages.core.secret.LockedWriteBlockedException) {
            // V2-6b: not the caller's generic failure — the locked space is
            // refusing writes while the content key is down. Same retry
            // closure: the composer is already cleared, so the closure owns
            // the text, and the key may well be back by the time it's tapped.
            runCatching { onFailure() }
            report(SendIssue.LOCKED_UNAVAILABLE, R.string.secret_seal_blocked_write, actionLabel, action)
        } catch (e: Exception) {
            android.util.Log.w("ChatViewModel", "$issue: ${e.javaClass.simpleName}")
            runCatching { onFailure() }
            report(issue, message, actionLabel, action)
        }
    }

    fun attach(uri: Uri?) {
        savedState["pending_attachment"] = uri
    }

    fun setCameraTarget(uri: Uri?) {
        savedState["camera_target"] = uri
    }

    /**
     * The result of a camera capture, both outcomes.
     *
     * V2-32: the callback used to be `if (ok) attach(target)` with no else
     * branch, so a cancelled capture left the file in the cache *and* left
     * `camera_target` set. Success wasn't clean either — the target stayed set
     * after being attached, so it was still readable long after it had been
     * consumed.
     */
    fun onCameraResult(ok: Boolean) {
        val target = cameraTarget.value
        if (ok) {
            // Consumed: it belongs to the staged attachment now, so the capture
            // slot must not keep pointing at it (and must not be pruned).
            savedState["camera_target"] = null
            if (target != null) attach(target)
        } else {
            discardCameraTarget()
        }
    }

    /**
     * V2-32: the capture didn't happen — `TakePicture` reported failure, which
     * in practice means the user backed out of the camera.
     *
     * Both halves matter. The state has to be cleared or a later action can read
     * a URI for a capture that never completed (and after process death it comes
     * back looking like one still in flight). The file has to go or every
     * cancellation leaves a `capture_*.jpg` in the cache forever — the camera app
     * usually creates it before the user backs out.
     *
     * The delete only ever touches the canonical file this capture named; see
     * [CameraCaptures.discard].
     */
    fun discardCameraTarget() {
        val uri = cameraTarget.value
        savedState["camera_target"] = null
        if (uri == null) return
        val name = uri.lastPathSegment ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { CameraCaptures.discard(getApplication<Application>().cacheDir, name) }
        }
    }

    /**
     * Delete capture files that no capture is waiting on. Called when the
     * attachment flow opens: the cancellations we *observe* are handled by
     * [discardCameraTarget], but a capture in flight when Android kills us
     * delivers its result to a process that no longer exists, and that file
     * would otherwise be unreachable and permanent.
     */
    fun pruneCameraCaptures() {
        val keep = cameraTarget.value?.lastPathSegment
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CameraCaptures.pruneOrphans(
                    cacheDir = getApplication<Application>().cacheDir,
                    now = System.currentTimeMillis(),
                    keep = keep,
                )
            }
        }
    }

    /** Send text + pending attachment as MMS (falls back to plain SMS when no attachment). */
    fun sendWithAttachment(text: String) {
        val uri = pendingAttachment.value
        if (uri == null) {
            send(text)
            return
        }
        val to = address.value
        if (to.isBlank()) return
        // V2-23: the staged attachment is NOT cleared here. It used to be
        // dropped before preparation was even attempted, so a failure sent the
        // text alone, with the image gone from the composer — the user had
        // every reason to believe it went out. It now survives until the part
        // is built, and a failure leaves it staged so "remove" or a second
        // attempt are both still available.
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val attachment = withContext(Dispatchers.IO) {
                runCatching { MmsSender.prepareAttachment(ctx, uri) }.getOrNull()
            }
            if (attachment == null) {
                report(
                    SendIssue.ATTACHMENT_UNREADABLE,
                    R.string.chat_attachment_unreadable,
                    // The text-only fallback is still reachable, but only as a
                    // deliberate choice, and it discards the attachment openly.
                    actionLabel = if (text.isBlank()) null else R.string.chat_send_text_only,
                    action = if (text.isBlank()) null else {
                        {
                            attach(null)
                            send(text)
                        }
                    },
                )
                return@launch
            }
            // Prepared: the staged attachment has served its purpose. Cleared on
            // the main thread — SavedStateHandle writes back a LiveData.
            savedState["pending_attachment"] = null
            guard(
                SendIssue.SEND_FAILED, R.string.chat_send_failed, R.string.action_retry,
                action = {
                    attach(uri)
                    sendWithAttachment(text)
                },
            ) {
                withContext(Dispatchers.IO) {
                    MmsSender.send(ctx, to, text, attachment, selectedSubId.value, space)
                }
            }
        }
    }

    fun clearSendProblem() {
        sendProblem.value = null
    }

    fun markChatUnlocked() {
        chatUnlocked.value = true
    }

    // ---- Secret locked space: "Lock chat" bottom sheet actions ----

    /**
     * "New locked chat": existing history stays here in the normal list; a
     * locked conversation for the same address appears in the locked space
     * and claims all future incoming messages from it.
     */
    fun lockNewChat(onDone: () -> Unit) = viewModelScope.launch {
        try {
            repo.createLockedConversation(threadId)
        } catch (_: com.messages.core.secret.LockedWriteBlockedException) {
            report(SendIssue.LOCKED_UNAVAILABLE, R.string.secret_seal_blocked_write)
            return@launch
        }
        removeLauncherIdentity()
        onDone()
    }

    /** Shortcut, per-conversation channel, and any visible notification go
     *  away the moment a chat is locked (spec: "remove any existing on lock"). */
    private fun removeLauncherIdentity() {
        com.messages.app.shortcut.ConversationShortcuts.remove(getApplication(), threadId)
        com.messages.app.notify.ConversationChannels.remove(getApplication(), threadId)
        // R-15: one API that knows every id a thread can own — cancelling only
        // threadId.toInt() left the separate fraud warning on screen.
        com.messages.app.notify.MessageNotifier.cancelThread(getApplication(), threadId)
    }

    /** "Unlock chat" from inside the locked space: whole thread returns. */
    fun unlockChat(onDone: () -> Unit) = viewModelScope.launch {
        try {
            repo.moveThreadToSpace(
                threadId, com.messages.core.db.Spaces.LOCKED, com.messages.core.db.Spaces.NORMAL,
            )
        } catch (_: com.messages.core.secret.LockedWriteBlockedException) {
            report(SendIssue.LOCKED_UNAVAILABLE, R.string.secret_seal_blocked_write)
            return@launch
        }
        onDone()
    }

    /**
     * "Move entire chat": the whole thread disappears from the normal list,
     * FTS, and search, and lands in the locked space.
     */
    fun lockMoveChat(onDone: () -> Unit) = viewModelScope.launch {
        try {
            repo.moveThreadToSpace(
                threadId, com.messages.core.db.Spaces.NORMAL, com.messages.core.db.Spaces.LOCKED,
            )
        } catch (_: com.messages.core.secret.LockedWriteBlockedException) {
            report(SendIssue.LOCKED_UNAVAILABLE, R.string.secret_seal_blocked_write)
            return@launch
        }
        removeLauncherIdentity()
        com.messages.app.ui.common.DraftStore.clear(getApplication(), threadId)
        onDone()
    }

    fun moveToInbox(messageId: Long) = viewModelScope.launch { repo.moveToInbox(messageId) }
    fun moveToSpam(messageId: Long) = viewModelScope.launch { repo.moveToSpam(messageId) }
    fun star(messageId: Long, starred: Boolean) = viewModelScope.launch {
        repo.db.messages().setStarred(messageId, starred)
    }
    /** User delete → Trash (§6.4): provider row removed, restorable for 60 days. */
    fun delete(messageId: Long) = viewModelScope.launch { repo.moveToTrash(messageId) }

    /** Delete the whole conversation → Trash (§6.4). */
    fun deleteThread(onDone: () -> Unit) = viewModelScope.launch {
        repo.moveThreadToTrash(threadId, space)
        onDone()
    }

    /** Phase 4 item 16: plain-text export of this conversation to a SAF uri. */
    fun exportConversation(uri: android.net.Uri, onDone: (Boolean) -> Unit) =
        viewModelScope.launch {
            val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val text = com.messages.core.export.ConversationExporter.format(
                        messages.value.filter { it.sendStatus != "SCHEDULED" },
                        conversationName = contactName.value
                            ?: address.value.ifBlank {
                                getApplication<Application>().getString(R.string.chat_unknown_sender)
                            },
                    )
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            onDone(ok)
        }

    /** Phase 4 item 14: conversations for the forward picker (blank query = recents). */
    suspend fun conversationsForForward(query: String): List<com.messages.core.db.ConversationEntity> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            // '' LIKE-matches everything → recents by lastTimestamp. Locked
            // conversations are excluded: forwarding into them from an
            // unauthenticated picker would leak their existence.
            repo.db.conversations().searchByNameOrAddress(query.trim())
                .filter { !it.locked }
        }
}
