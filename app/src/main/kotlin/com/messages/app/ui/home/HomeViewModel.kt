package com.messages.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messages.app.ui.common.LoadState
import com.messages.app.ui.search.SavedSearches
import com.messages.core.MessageRepository
import com.messages.core.db.ConversationEntity
import com.messages.core.db.Spaces
import com.messages.core.search.MessageSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val folder = MutableStateFlow("INBOX")

    /**
     * V2-43. Bumping this restarts every cached conversation flow — what the
     * "Try again" button on the failure state is wired to.
     *
     * A Room flow that throws is *terminated*: `catch` can turn it into a
     * `Failed` value but cannot resume it, so retry has to mean "collect the
     * query again", which is what `flatMapLatest` over this tick does.
     */
    private val reload = MutableStateFlow(0)

    fun retry() { reload.value += 1 }

    /**
     * Per-folder conversation flows, cached so the animated folder switch can
     * render the outgoing and incoming folder simultaneously with each one's
     * own data.
     *
     * V2-43: these used to be `List<ConversationEntity>?` with `null` meaning
     * "not loaded yet", and the screen rendered nothing for it. A nullable list
     * has no room for the fourth outcome — a query that *failed* — so a broken
     * read was indistinguishable from a slow one, and the screen stayed blank
     * for good. [LoadState] gives the failure somewhere to go.
     */
    private val conversationCache =
        HashMap<String, StateFlow<LoadState<List<ConversationEntity>>>>()

    fun conversationsFor(category: String): StateFlow<LoadState<List<ConversationEntity>>> =
        conversationCache.getOrPut(category) {
            loadStateOf { repo.db.conversations().byCategory(category) }
        }

    /**
     * The "Unread" filter list — the DAO's byCategoryUnread, i.e. the SAME
     * `unreadCount > 0` predicate the row badges render and folderUnread
     * counts. One source of truth: the fix for the chip/filter drift where
     * the filter used conversation-level unread while the folder chips
     * counted message-level read=0 rows (backfilled history and
     * mark-as-unread disagree between those two by design).
     */
    private val unreadConversationCache =
        HashMap<String, StateFlow<LoadState<List<ConversationEntity>>>>()

    fun unreadConversationsFor(category: String): StateFlow<LoadState<List<ConversationEntity>>> =
        unreadConversationCache.getOrPut(category) {
            loadStateOf { repo.db.conversations().byCategoryUnread(category) }
        }

    /**
     * Wraps a DAO flow as a restartable [LoadState] flow.
     *
     * `onStart` re-emits `Loading` on every restart so a retry does not leave
     * the previous failure on screen while the new query runs. The `catch`
     * keeps the exception off `viewModelScope` — an uncaught one there cancels
     * the scope, taking every *other* folder's flow down with it and leaving
     * the whole screen dead with no error anywhere.
     */
    private fun loadStateOf(
        query: () -> kotlinx.coroutines.flow.Flow<List<ConversationEntity>>,
    ): StateFlow<LoadState<List<ConversationEntity>>> =
        reload
            .flatMapLatest {
                query()
                    .map<List<ConversationEntity>, LoadState<List<ConversationEntity>>> {
                        LoadState.Ready(it)
                    }
                    .onStart { emit(LoadState.Loading) }
                    .catch { t ->
                        android.util.Log.e("HomeViewModel", "conversation query failed", t)
                        emit(LoadState.Failed(t::class.java.simpleName))
                    }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, LoadState.Loading)

    /** Folder-chip badge: conversations with an unread badge (NOT unread
     *  message rows — same predicate as the row badges and the filter). */
    fun folderUnread(category: String) = repo.db.conversations().unreadConversationCount(category)

    /** Verified-sender badges: latest incoming message's fraud/protected-lane
     *  state per thread — drives badge suppression + elevation in list rows. */
    val latestIncomingMeta: StateFlow<Map<Long, com.messages.core.db.MessageDao.LatestIncomingMeta>> =
        repo.db.messages().latestIncomingMeta()
            .map { list -> list.associateBy { it.threadId } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    /**
     * V2-48: how many outgoing messages have not finished. Home shows this on
     * the Outbox entry so a scheduled or failed send is discoverable without
     * opening the screen to find out — the failure mode the finding described
     * was precisely that nobody knew to look.
     */
    val outboxCount: StateFlow<Int> = repo.outboxCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setFolder(f: String) { folder.value = f }

    // V2-24: Home only ever lists the normal space (every query it feeds from
    // is NORMAL-scoped), so it names that space rather than leaning on a
    // default that would follow the caller anywhere.
    private val pendingThreadJobs = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.Job>()

    fun togglePin(threadId: Long, pinned: Boolean) = viewModelScope.launch {
        try {
            repo.db.conversations().setPinned(threadId, pinned, Spaces.NORMAL)
        } catch (t: Throwable) {
            android.util.Log.e("HomeViewModel", "togglePin failed", t)
        }
    }

    fun archive(threadId: Long) {
        val job = viewModelScope.launch {
            try {
                repo.db.conversations().setArchived(threadId, true, Spaces.NORMAL)
            } catch (t: Throwable) {
                android.util.Log.e("HomeViewModel", "archive failed", t)
            } finally {
                pendingThreadJobs.remove(threadId)
            }
        }
        pendingThreadJobs[threadId] = job
    }

    // ---- Swipe actions (§8.2) + undo ----

    fun unarchive(threadId: Long) = viewModelScope.launch {
        try {
            pendingThreadJobs[threadId]?.join()
            repo.db.conversations().setArchived(threadId, false, Spaces.NORMAL)
        } catch (t: Throwable) {
            android.util.Log.e("HomeViewModel", "unarchive failed", t)
        }
    }

    fun trashThread(threadId: Long) {
        val job = viewModelScope.launch {
            try {
                repo.moveThreadToTrash(threadId, Spaces.NORMAL)
            } catch (t: Throwable) {
                android.util.Log.e("HomeViewModel", "trashThread failed", t)
            } finally {
                pendingThreadJobs.remove(threadId)
            }
        }
        pendingThreadJobs[threadId] = job
    }

    /** Undo for swipe-delete: restore all messages in thread. */
    fun undoTrashThread(threadId: Long, trashedAfter: Long = 0L) = viewModelScope.launch {
        try {
            pendingThreadJobs[threadId]?.join()
            repo.restoreThreadFromTrash(threadId, trashedAfter)
        } catch (t: Throwable) {
            android.util.Log.e("HomeViewModel", "undoTrashThread failed", t)
        }
    }

    fun markThreadRead(threadId: Long) = viewModelScope.launch {
        try {
            repo.db.messages().markThreadRead(threadId, Spaces.NORMAL)
            repo.db.conversations().clearUnread(threadId, Spaces.NORMAL)
        } catch (t: Throwable) {
            android.util.Log.e("HomeViewModel", "markThreadRead failed", t)
        }
    }

    /** Phase 4 item 13: UI-level unread marker (rows stay read, badge returns). */
    fun markThreadUnread(threadId: Long) = viewModelScope.launch {
        try {
            repo.db.conversations().markUnread(threadId, Spaces.NORMAL)
        } catch (t: Throwable) {
            android.util.Log.e("HomeViewModel", "markThreadUnread failed", t)
        }
    }

    /** Phase 4 item 12: mark the whole current folder read. */
    fun markFolderRead(category: String) = viewModelScope.launch {
        try {
            repo.db.messages().markCategoryRead(category)
            repo.db.conversations().clearUnreadForCategory(category)
        } catch (t: Throwable) {
            android.util.Log.e("HomeViewModel", "markFolderRead failed", t)
        }
    }

    // ---- Phase 4 item 12: unread-only filter ----

    val unreadOnly = MutableStateFlow(false)

    fun setUnreadOnly(on: Boolean) { unreadOnly.value = on }

    // ---- Phase 4 item 14: conversation multi-select ----

    val selectedThreads = MutableStateFlow<Set<Long>>(emptySet())

    fun toggleSelected(threadId: Long) {
        selectedThreads.value =
            if (threadId in selectedThreads.value) selectedThreads.value - threadId
            else selectedThreads.value + threadId
    }

    fun clearSelection() { selectedThreads.value = emptySet() }

    /** Bulk trash for multi-select; returns the cut timestamp for undo. */
    fun trashThreads(ids: Set<Long>): Long {
        val at = System.currentTimeMillis()
        val job = viewModelScope.launch {
            try {
                ids.forEach { repo.moveThreadToTrash(it, Spaces.NORMAL) }
            } catch (t: Throwable) {
                android.util.Log.e("HomeViewModel", "trashThreads failed", t)
            } finally {
                ids.forEach { pendingThreadJobs.remove(it) }
            }
        }
        ids.forEach { pendingThreadJobs[it] = job }
        return at
    }

    fun undoTrashThreads(ids: Set<Long>, trashedAfter: Long = 0L) = viewModelScope.launch {
        try {
            ids.forEach { id ->
                pendingThreadJobs[id]?.join()
                repo.restoreThreadFromTrash(id, trashedAfter)
            }
        } catch (t: Throwable) {
            android.util.Log.e("HomeViewModel", "undoTrashThreads failed", t)
        }
    }

    fun markThreadsRead(ids: Set<Long>) = viewModelScope.launch {
        try {
            ids.forEach {
                repo.db.messages().markThreadRead(it, Spaces.NORMAL)
                repo.db.conversations().clearUnread(it, Spaces.NORMAL)
            }
        } catch (t: Throwable) {
            android.util.Log.e("HomeViewModel", "markThreadsRead failed", t)
        }
    }

    fun markThreadsUnread(ids: Set<Long>) = viewModelScope.launch {
        try {
            ids.forEach { repo.db.conversations().markUnread(it, Spaces.NORMAL) }
        } catch (t: Throwable) {
            android.util.Log.e("HomeViewModel", "markThreadsUnread failed", t)
        }
    }

    fun archiveThreads(ids: Set<Long>) {
        val job = viewModelScope.launch {
            try {
                ids.forEach { repo.db.conversations().setArchived(it, true, Spaces.NORMAL) }
            } catch (t: Throwable) {
                android.util.Log.e("HomeViewModel", "archiveThreads failed", t)
            } finally {
                ids.forEach { pendingThreadJobs.remove(it) }
            }
        }
        ids.forEach { pendingThreadJobs[it] = job }
    }

    fun unarchiveThreads(ids: Set<Long>) = viewModelScope.launch {
        try {
            ids.forEach { id ->
                pendingThreadJobs[id]?.join()
                repo.db.conversations().setArchived(id, false, Spaces.NORMAL)
            }
        } catch (t: Throwable) {
            android.util.Log.e("HomeViewModel", "unarchiveThreads failed", t)
        }
    }

    fun toggleMute(threadId: Long, muted: Boolean) = viewModelScope.launch {
        try {
            repo.db.conversations().setMuted(threadId, muted, Spaces.NORMAL)
        } catch (t: Throwable) {
            android.util.Log.e("HomeViewModel", "toggleMute failed", t)
        }
    }

    // ---- §8.5 incremental multi-keyword search ----

    /** Committed keyword chips — unlimited, all equal, match-any (§8.5.2). */
    val chips = MutableStateFlow<List<String>>(emptyList())

    /** What's currently being typed (not yet committed to a chip). */
    val typing = MutableStateFlow("")

    /** Auto-label filter chip: OTP / BANK / DELIVERY / TRAVEL / BILL, or null. */
    val labelFilter = MutableStateFlow<String?>(null)

    data class SearchRowUi(
        val message: com.messages.core.db.MessageEntity,
        val displayName: String?,
        val matchedKeywords: List<String>,
        val matchCount: Int,
    )

    data class SearchState(
        /** Keywords the current results were computed for (chips + live-typed token). */
        val activeKeywords: List<String> = emptyList(),
        val results: List<SearchRowUi> = emptyList(),
        val suggestedChips: List<String> = emptyList(),
        /** §8.5.3: conversations whose contact name / number matches a keyword. */
        val conversationMatches: List<ConversationEntity> = emptyList(),
    )

    // ~200 ms debounce on the typed token only; chip edits and label changes
    // re-query immediately (they are deliberate taps, not keystrokes).
    private val debouncedTyping = typing.debounce(200)

    /**
     * V2-28: this whole pipeline used to run on the collector's dispatcher —
     * the main thread — including up to 200 `PhoneLookup` provider queries, one
     * per result row, per keystroke-driven re-query. Two changes fix that: the
     * upstream is now confined to IO with [flowOn], and the per-row lookup is a
     * single batched, process-wide-cached resolution over the handful of
     * distinct addresses a result set actually contains. `mapLatest` already
     * cancelled the obsolete search; it now cancels it before it can spend
     * anything on the provider.
     */
    val searchState: StateFlow<SearchState> =
        combine(chips, debouncedTyping, labelFilter) { c, t, l -> Triple(c, t, l) }
            .mapLatest { (chipList, typed, label) ->
                // 3-char junk-fragment guard on the live token (§8.5.1).
                val live = typed.trim().takeIf { it.length >= MessageSearch.MIN_QUERY_LENGTH }
                val keywords = (chipList + listOfNotNull(live)).distinct()
                if (keywords.isEmpty()) return@mapLatest SearchState()
                val raw = repo.search.search(keywords)
                val filtered =
                    if (label == null) raw else raw.filter { it.message.protectedLabel == label }
                // Contact-name / number matches ("mom" → mom's conversation).
                val convMatches = LinkedHashMap<Long, ConversationEntity>()
                for (k in keywords) {
                    repo.db.conversations().searchByNameOrAddress(k)
                        .forEach { convMatches.putIfAbsent(it.threadId, it) }
                }
                val shown = filtered.take(200)
                // One resolution pass over the distinct addresses in the page,
                // not one query per row.
                val names = repo.displayNamesFor(shown.map { it.message.address })
                SearchState(
                    activeKeywords = keywords,
                    conversationMatches = convMatches.values.toList(),
                    results = shown.map { r ->
                        SearchRowUi(
                            message = r.message,
                            displayName = names[r.message.address],
                            matchedKeywords = r.matchedKeywords,
                            matchCount = r.matchedKeywords.size,
                        )
                    },
                    suggestedChips = repo.search.suggestedChips(filtered, keywords),
                )
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    fun setTyping(text: String) {
        // Space/newline commits the current token as a chip (§8.5.2
        // type-and-select) — matching "add keywords by typing more words".
        if (text.isNotEmpty() && (text.last() == ' ' || text.last() == '\n')) {
            commitTyping(text.trim())
        } else {
            typing.value = text
        }
    }

    /** Commit the live-typed token (IME Search key, or trailing space). */
    fun commitTyping(text: String? = null) {
        val token = (text ?: typing.value).trim()
        typing.value = ""
        if (token.length >= 2) addChip(token)
    }

    fun addChip(keyword: String) {
        val k = keyword.trim()
        if (k.isEmpty()) return
        if (chips.value.none { it.equals(k, ignoreCase = true) }) chips.value = chips.value + k
    }

    fun removeChip(keyword: String) {
        chips.value = chips.value.filterNot { it.equals(keyword, ignoreCase = true) }
    }

    fun setLabelFilter(label: String?) {
        labelFilter.value = label
    }

    fun applySavedSearch(terms: List<String>) {
        typing.value = ""
        chips.value = terms
    }

    fun clearSearch() {
        typing.value = ""
        chips.value = emptyList()
        labelFilter.value = null
    }

    /** A result was opened — remember this combo as a saved search (§8.5.2). */
    fun recordSearchUse() {
        val terms = searchState.value.activeKeywords
        if (terms.isNotEmpty()) SavedSearches.record(getApplication(), terms)
    }

    fun savedSearches(): List<List<String>> = SavedSearches.top(getApplication())
}
