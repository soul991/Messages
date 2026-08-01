package com.messages.core.backup

import android.content.Context

/**
 * V2-12: how much of THIS device a restore is allowed to consume.
 *
 * [BackupManager.Limits] bounds a backup *structurally* — counts, field
 * lengths, filename shape. Those ceilings were written to be obviously
 * unreachable by a real backup, which made them useless as resource limits: a
 * file of 128 million characters carrying 2 GB of media clears every one of
 * them and then reliably kills the process, because nothing in that path is
 * streamed.
 *
 * Restore holds several copies of the same content alive at once:
 *
 *  - the JSON text (2 bytes per char, plus the builder it was read into),
 *  - the decoded object graph, whose strings hold roughly the same characters
 *    again with per-object overhead on top,
 *  - and, per media file, the Base64 string plus its decoded bytes.
 *
 * That comes to roughly [PEAK_BYTES_PER_JSON_CHAR] bytes of heap for every
 * character of JSON. A restore may spend [HEAP_SHARE_DIVISOR]⁻¹ of the heap —
 * the rest belongs to the UI, Room and the classification engine, which are all
 * live while it runs — so the text ceiling is the heap divided by their
 * product. On a 256 MB heap that is ~9 million characters, which is tens of
 * thousands of messages; on a 512 MB heap, twice that.
 *
 * Media is additionally bounded by free disk, since every decoded file is
 * written under `filesDir`, and by a per-file ceiling, because the aggregate
 * limit alone permits one file that is the entire aggregate — and that one file
 * is decoded in a single allocation.
 *
 * Because all of this is checked before the first mutation, an over-budget
 * backup is refused with an explanation rather than dying halfway through with
 * the device half-restored.
 */
class RestoreBudget internal constructor(
    /** Ceiling on the JSON text of a backup, in characters. */
    val maxJsonChars: Int,
    /** Ceiling on the total decoded media in a backup, in bytes. */
    val maxMediaBytes: Long,
    /** Ceiling on any single decoded media file, in bytes. */
    val maxMediaFileBytes: Long,
) {

    /**
     * Ceiling on a decompressed envelope, in bytes. Backup JSON is
     * overwhelmingly ASCII, so its byte count and its character count are the
     * same number to within a rounding error.
     */
    val maxExpandedBytes: Int get() = maxJsonChars

    /** For user-facing messages: an approximate megabyte figure. */
    internal fun jsonMegabytes(): Int = maxJsonChars / (1024 * 1024)

    companion object {

        /**
         * Heap cost of one character of backup JSON, counting every copy that
         * is alive simultaneously (read buffer, text, decoded graph). Measured
         * generously on purpose: under-estimating it puts the OOM back.
         */
        internal const val PEAK_BYTES_PER_JSON_CHAR = 6L

        /** A restore may occupy at most one quarter of the heap. */
        internal const val HEAP_SHARE_DIVISOR = 4L

        /**
         * Even a small-heap device must be able to restore an ordinary
         * text-only history, so the derived ceiling never falls below this.
         */
        internal const val MIN_JSON_CHARS = 4 * 1024 * 1024

        /** A restore may fill at most half of the free space under filesDir. */
        internal const val DISK_SHARE_DIVISOR = 2L

        /**
         * Per-file media ceiling on the way IN. Deliberately above the 5 MB
         * export ceiling rather than equal to it: a backup written by another
         * version of this app — or by one that predates that ceiling — must
         * still restore, and the point of this bound is to stop a single
         * allocation from being unbounded, not to re-litigate the export rule.
         */
        internal const val MAX_MEDIA_FILE_BYTES = 8L * 1024 * 1024

        /**
         * Structure-only budget, for the paths that have no [Context] to
         * measure: it applies [BackupManager.Limits] unchanged and adds the
         * per-file media ceiling. Validation done with this budget is not a
         * statement that the backup fits in memory.
         */
        val STRUCTURAL = RestoreBudget(
            maxJsonChars = BackupManager.Limits.MAX_JSON_CHARS,
            maxMediaBytes = BackupManager.Limits.MAX_MEDIA_BYTES,
            maxMediaFileBytes = MAX_MEDIA_FILE_BYTES,
        )

        /** What this device can actually survive right now. */
        fun forDevice(context: Context): RestoreBudget = of(
            maxHeapBytes = Runtime.getRuntime().maxMemory(),
            usableDiskBytes = runCatching { context.filesDir.usableSpace }.getOrDefault(-1L),
        )

        /**
         * The arithmetic, separated from the platform lookups so it can be
         * tested without a device.
         *
         * [usableDiskBytes] below zero means the filesystem would not say. That
         * is not permission to write without limit, but refusing every restore
         * over it would be worse than the disk error the write itself would
         * raise — so the structural ceiling stands in, and the per-file ceiling
         * still applies.
         */
        internal fun of(maxHeapBytes: Long, usableDiskBytes: Long): RestoreBudget {
            val jsonChars = (maxHeapBytes / (PEAK_BYTES_PER_JSON_CHAR * HEAP_SHARE_DIVISOR))
                .coerceIn(MIN_JSON_CHARS.toLong(), BackupManager.Limits.MAX_JSON_CHARS.toLong())
                .toInt()
            val mediaBytes = if (usableDiskBytes < 0) {
                BackupManager.Limits.MAX_MEDIA_BYTES
            } else {
                (usableDiskBytes / DISK_SHARE_DIVISOR)
                    .coerceAtMost(BackupManager.Limits.MAX_MEDIA_BYTES)
            }
            return RestoreBudget(jsonChars, mediaBytes, MAX_MEDIA_FILE_BYTES)
        }
    }
}
