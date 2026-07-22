package com.messages.app.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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

    fun uriFor(context: Context, address: String): String? {
        val version = ContactSync.refreshVersion.value
        if (version != cachedVersion) {
            cache.clear()
            cachedVersion = version
        }
        if (address.contains(';')) return null
        cache[address]?.let { return it.ifEmpty { null } }
        val uri = MessageRepository.get(context).lookupContact(address)?.photoUri
        cache[address] = uri ?: ""
        return uri
    }
}

/** Off-main-thread photo lookup, re-keyed when a contacts refresh lands. */
@Composable
fun rememberContactPhoto(address: String?): String? {
    val context = LocalContext.current.applicationContext
    val version by ContactSync.refreshVersion.collectAsState()
    val photo by produceState<String?>(initialValue = null, address, version) {
        value = if (address.isNullOrBlank()) null
        else withContext(Dispatchers.IO) { ContactPhotos.uriFor(context, address) }
    }
    return photo
}
