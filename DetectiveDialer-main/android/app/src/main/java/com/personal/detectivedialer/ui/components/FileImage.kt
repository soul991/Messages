package com.personal.detectivedialer.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Decodes an image from a local file path into an [ImageBitmap], off the main
 * thread, recomputing when [path] changes. Returns null while loading or when
 * the path is blank/missing — the project has no image-loading library, so we
 * decode with [BitmapFactory] directly (fine for the single call wallpaper).
 */
@Composable
fun rememberFileImageBitmap(path: String?): ImageBitmap? =
    produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    // The stored path may carry a "?v=<mtime>" cache-busting tag so
                    // re-picking the same file forces a re-decode; strip it here.
                    val clean = path.substringBefore('?')
                    val file = File(clean)
                    if (file.exists()) BitmapFactory.decodeFile(clean)?.asImageBitmap() else null
                }.getOrNull()
            }
        }
    }.value

/**
 * Decodes a contact photo from a `content://` URI (ContactsContract PHOTO_URI)
 * into an [ImageBitmap], off the main thread, recomputing when [uri] changes.
 * Returns null while loading or when the contact has no photo — the caller falls
 * back to an [InitialAvatar]. Uses the ContentResolver directly (no image lib).
 */
@Composable
fun rememberContactPhotoBitmap(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri) {
        value = if (uri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }.value
}
