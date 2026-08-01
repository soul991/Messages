package com.messages.core.media

import java.io.File

/**
 * `MessageEntity.mediaUri` holds one of two things, and the difference matters
 * to every consumer:
 *
 * - **A local file path** — written by the live send/receive paths, which copy
 *   the attachment into app storage. We own these bytes: they can be read,
 *   backed up, resent, and deleted.
 * - **A `content://mms/part/…` provider URI** — written by the historical MMS
 *   backfill (V2-25). The bytes live in the Telephony provider and are shared
 *   with every other messaging app. Copying an entire MMS history into app
 *   storage could mean gigabytes, so the backfill references them instead.
 *
 * The column was a file path for its whole life before the backfill existed,
 * so `File(mediaUri)` is scattered across the codebase. This is the one place
 * that knows the distinction; call [asFile] rather than constructing a `File`
 * directly, and treat null as "not ours to touch".
 */
object MediaRef {

    /** True when the reference points into a content provider, not our storage. */
    fun isProviderUri(ref: String): Boolean =
        ref.startsWith("content://") || ref.startsWith("file://")

    /**
     * The local file this reference names, or null when the bytes belong to a
     * content provider. Deleting, reading, or backing up must go through this:
     * `File("content://mms/part/7")` is a relative path that resolves to
     * nonsense, not an error.
     */
    fun asFile(ref: String?): File? {
        if (ref.isNullOrBlank()) return null
        if (isProviderUri(ref)) return null
        return File(ref)
    }
}
