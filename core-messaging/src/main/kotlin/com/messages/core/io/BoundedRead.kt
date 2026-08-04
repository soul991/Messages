package com.messages.core.io

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * R-17: read attacker- or carrier-controlled content WITHOUT trusting its size.
 *
 * Every attachment/PDU path used to call `readBytes()` first and check the limit
 * afterwards, so a multi-gigabyte content URI or a hostile carrier payload was
 * already in the heap by the time the limit was consulted. These helpers
 * (a) reject early using the provider's declared length when it offers one, and
 * (b) otherwise stream, aborting the moment the source exceeds `limit` — so peak
 * allocation is bounded by `limit`, not by the source's true size.
 */
object BoundedRead {

    /**
     * The provider's declared length in bytes, or null when it will not say
     * (`UNKNOWN_LENGTH`, a pipe, an unreadable descriptor). A declared length is
     * a cheap early rejection; a missing one is NOT permission to read
     * unbounded — the streaming reader still applies.
     */
    fun declaredSize(resolver: ContentResolver, uri: Uri): Long? = try {
        resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            afd.length.takeIf { it >= 0 }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Read at most [limit] bytes from [uri]. Returns null when the content is
     * larger than [limit] (declared or actual), unreadable, or absent — callers
     * treat null as "too big / not usable", never as "empty".
     */
    fun readUri(context: Context, uri: Uri, limit: Int): ByteArray? {
        val resolver = context.contentResolver
        declaredSize(resolver, uri)?.let { if (it > limit) return null }
        return try {
            resolver.openInputStream(uri)?.use { readStream(it, limit) }
        } catch (_: Exception) {
            null
        }
    }

    /** Read at most [limit] bytes from [file]; null when it is bigger. */
    fun readFile(file: File, limit: Int): ByteArray? {
        if (!file.exists()) return null
        if (file.length() > limit) return null
        return try {
            file.inputStream().use { readStream(it, limit) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Drain [stream] into memory, aborting as soon as more than [limit] bytes
     * have been seen. The overshoot is bounded by one buffer, so a source that
     * lies about its size still cannot force an unbounded allocation.
     */
    fun readStream(stream: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE * 8))
        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
