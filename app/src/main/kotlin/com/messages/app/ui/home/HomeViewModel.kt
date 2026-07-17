package com.messages.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messages.core.MessageRepository
import com.messages.core.db.ConversationEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val folder = MutableStateFlow("INBOX")

    val conversations: StateFlow<List<ConversationEntity>> = folder
        .flatMapLatest { repo.db.conversations().byCategory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchQuery = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<com.messages.core.db.MessageEntity>>(emptyList())
    val searchResults: StateFlow<List<com.messages.core.db.MessageEntity>> = _searchResults

    fun folderUnread(category: String) = repo.db.messages().unreadCount(category)

    fun setFolder(f: String) { folder.value = f }

    fun search(query: String) {
        searchQuery.value = query
        viewModelScope.launch {
            _searchResults.value = if (query.length >= 2) repo.db.messages().search(query) else emptyList()
        }
    }

    fun togglePin(threadId: Long, pinned: Boolean) = viewModelScope.launch {
        repo.db.conversations().setPinned(threadId, pinned)
    }

    fun archive(threadId: Long) = viewModelScope.launch {
        repo.db.conversations().setArchived(threadId, true)
    }
}
