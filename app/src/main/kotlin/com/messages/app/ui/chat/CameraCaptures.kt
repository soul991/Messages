package com.messages.app.ui.chat

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * V2-32: the composer's camera capture target, and what happens to it when the
 * capture doesn't happen.
 *
 * `ActivityResultContracts.TakePicture` reports **only** success. The old
 * callback was `if (ok) attach(...)` with no else, so a cancelled capture left
 * two things behind:
 *
 *  - **A file.** The camera app is handed a `FileProvider` URI and commonly
 *    creates (or partially writes) the file before the user backs out. Nothing
 *    deleted it, so every cancelled capture added a `capture_*.jpg` to
 *    `cache/camera/` permanently.
 *  - **State.** `camera_target` stayed set in the `SavedStateHandle`, so a
 *    later action could still read a URI for a capture that never completed —
 *    and after process death it was restored as though a capture were in flight.
 *
 * This object owns the file naming so both the discard and the prune can be
 * exact: a discard deletes *only* the canonical file that this capture was going
 * to write, never anything else in the directory, and the prune only ever
 * considers files that match the same canonical shape.
 *
 * Pure `java.io` so the deletion rules are testable without a device.
 */
internal object CameraCaptures {

    /** Subdirectory of `cacheDir`; must match `res/xml/file_paths.xml`. */
    const val DIR = "camera"

    private const val PREFIX = "capture_"
    private const val SUFFIX = ".jpg"

    /**
     * How long a capture file may sit before the prune treats it as an orphan.
     * Generous on purpose: a capture that outlives our process is legitimate,
     * and the in-flight target is excluded from the prune by name anyway, so
     * this bound only has to catch files nothing is waiting on.
     */
    val ORPHAN_MAX_AGE_MS: Long = TimeUnit.HOURS.toMillis(24)

    fun dir(cacheDir: File): File = File(cacheDir, DIR)

    /** The canonical file a capture started at [now] writes to. */
    fun newTarget(cacheDir: File, now: Long): File {
        val dir = dir(cacheDir).apply { mkdirs() }
        return File(dir, "$PREFIX$now$SUFFIX")
    }

    /**
     * Is [name] a file this app created as a capture target? Everything that
     * deletes checks this first — the name arrives from a URI's last path
     * segment, and an unrecognized name means "not ours, leave it alone".
     */
    fun isCaptureName(name: String): Boolean {
        if (!name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) return false
        val stamp = name.substring(PREFIX.length, name.length - SUFFIX.length)
        return stamp.isNotEmpty() && stamp.all { it.isDigit() }
    }

    /**
     * Delete the file a cancelled capture was going to write. Returns whether
     * anything was deleted — false is the ordinary case where the camera app
     * never created the file at all.
     *
     * Two guards, because [name] originates outside this process (the URI came
     * back through `SavedStateHandle`): the name must match the canonical
     * capture shape, and the resolved file must still sit directly inside the
     * capture directory. A name like `../../databases/messages.db` fails both.
     */
    fun discard(cacheDir: File, name: String?): Boolean {
        if (name == null || !isCaptureName(name)) return false
        val dir = dir(cacheDir)
        val target = File(dir, name)
        val contained = runCatching {
            target.canonicalFile.parentFile == dir.canonicalFile
        }.getOrDefault(false)
        if (!contained) return false
        return runCatching { target.delete() }.getOrDefault(false)
    }

    /**
     * Delete capture files older than [maxAgeMillis] that nothing is waiting on.
     *
     * The discard above handles the cancellation we observe. This handles the
     * ones we never got to observe — the camera app was foreground when Android
     * killed us and the result was delivered to a process that no longer
     * existed. Without it, those files are unreachable and permanent.
     *
     * [keep] is the in-flight target's name, which is never pruned however old
     * it looks.
     */
    fun pruneOrphans(
        cacheDir: File,
        now: Long,
        maxAgeMillis: Long = ORPHAN_MAX_AGE_MS,
        keep: String? = null,
    ): Int {
        val files = dir(cacheDir).listFiles() ?: return 0
        var deleted = 0
        for (file in files) {
            if (!file.isFile) continue
            if (!isCaptureName(file.name)) continue // not ours
            if (file.name == keep) continue
            if (now - file.lastModified() < maxAgeMillis) continue
            if (runCatching { file.delete() }.getOrDefault(false)) deleted++
        }
        return deleted
    }
}
