package com.messages.app.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.messages.core.MessageRepository
import com.messages.core.contacts.ContactSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime cache of contact photo URIs by address (no DB column — photos are
 * device-local and cheap to re-resolve). Cleared whenever [ContactSync]
 * completes a refresh, so newly saved photos appear without an app restart.
 * Group addresses (';'-joined) resolve to no photo — the monogram stands in.
 */
object ContactPhotos {

    /** address → photoUri; "" caches a confirmed miss. */
    private val cache = ConcurrentHashMap<String, String>()
    private var cachedVersion = -1

    private fun syncVersion() {
        val version = ContactSync.refreshVersion.value
        if (version != cachedVersion) {
            cache.clear()
            cachedVersion = version
        }
    }

    /**
     * Cache-only peek: photoUri, "" for a confirmed miss, null when this
     * address has never been resolved (caller must go through [uriFor]).
     */
    fun cached(address: String): String? {
        syncVersion()
        if (address.contains(';')) return ""
        return cache[address]
    }

    fun uriFor(context: Context, address: String): String? {
        syncVersion()
        if (address.contains(';')) return null
        cache[address]?.let { return it.ifEmpty { null } }
        val uri = MessageRepository.get(context).lookupContact(address)?.photoUri
        cache[address] = uri ?: ""
        return uri
    }
}

/**
 * Photo lookup, re-keyed when a contacts refresh lands. Cache hits (the
 * overwhelming case while scrolling) answer synchronously — no coroutine
 * round trip, no null→uri recomposition, no AsyncImage pop-in (Phase 6);
 * only a genuinely unknown address takes the IO path.
 */
@Composable
fun rememberContactPhoto(address: String?): String? {
    if (address.isNullOrBlank()) return null
    val context = LocalContext.current.applicationContext
    val version by ContactSync.refreshVersion.collectAsStateWithLifecycle()
    val cached = remember(address, version) { ContactPhotos.cached(address) }
    if (cached != null) return cached.ifEmpty { null }
    val photo by produceState<String?>(initialValue = null, address, version) {
        value = withContext(Dispatchers.IO) { ContactPhotos.uriFor(context, address) }
    }
    return photo
}
