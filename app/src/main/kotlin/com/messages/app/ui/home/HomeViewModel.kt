package com.messages.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messages.app.ui.search.SavedSearches
import com.messages.core.MessageRepository
import com.messages.core.db.ConversationEntity
import com.messages.core.search.MessageSearch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val folder = MutableStateFlow("INBOX")

    val conversations: StateFlow<List<ConversationEntity>> = folder
        .flatMapLatest { repo.db.conversations().byCategory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun folderUnread(category: String) = repo.db.messages().unreadCount(category)

    fun setFolder(f: String) { folder.value = f }

    fun togglePin(threadId: Long, pinned: Boolean) = viewModelScope.launch {
        repo.db.conversations().setPinned(threadId, pinned)
    }

    fun archive(threadId: Long) = viewModelScope.launch {
        repo.db.conversations().setArchived(threadId, true)
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

    private val nameCache = HashMap<String, String?>()

    // ~200 ms debounce on the typed token only; chip edits and label changes
    // re-query immediately (they are deliberate taps, not keystrokes).
    private val debouncedTyping = typing.debounce(200)

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
                SearchState(
                    activeKeywords = keywords,
                    conversationMatches = convMatches.values.toList(),
                    results = filtered.take(200).map { r ->
                        SearchRowUi(
                            message = r.message,
                            displayName = nameCache.getOrPut(r.message.address) {
                                repo.displayNameFor(r.message.address)
                            },
                            matchedKeywords = r.matchedKeywords,
                            matchCount = r.matchedKeywords.size,
                        )
                    },
                    suggestedChips = repo.search.suggestedChips(filtered, keywords),
                )
            }
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
