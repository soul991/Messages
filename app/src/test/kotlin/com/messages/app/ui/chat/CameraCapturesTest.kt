package com.messages.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * V2-32. `TakePicture` reports only success, and the callback had no else
 * branch — so a cancelled capture left the file in `cache/camera/` and left
 * `camera_target` set. These pin the two deletion rules that replaced that: a
 * discard removes exactly the canonical file the capture named and nothing else,
 * and the prune reaches only files nothing is waiting on.
 *
 * Both rules act on a name that arrives from outside this process (the URI comes
 * back through `SavedStateHandle`), so the containment cases below are the point,
 * not padding.
 */
class CameraCapturesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val cacheDir: File get() = tmp.root

    private fun capture(name: String, ageMillis: Long = 0L, now: Long = NOW): File =
        File(CameraCaptures.dir(cacheDir).apply { mkdirs() }, name).apply {
            writeText("jpeg")
            setLastModified(now - ageMillis)
        }

    @Test
    fun `a target is created inside the directory the FileProvider exposes`() {
        val target = CameraCaptures.newTarget(cacheDir, NOW)
        // Must match res/xml/file_paths.xml, or getUriForFile throws at runtime.
        assertEquals(CameraCaptures.DIR, target.parentFile!!.name)
        assertTrue(target.parentFile!!.isDirectory) // created eagerly
        assertTrue(CameraCaptures.isCaptureName(target.name))
    }

    @Test
    fun `a cancelled capture's file is deleted`() {
        val target = CameraCaptures.newTarget(cacheDir, NOW)
        target.writeText("partial") // what the camera app leaves behind
        assertTrue(CameraCaptures.discard(cacheDir, target.name))
        assertFalse(target.exists())
    }

    @Test
    fun `a cancellation before the camera wrote anything is not an error`() {
        // The common case: no file was ever created. Nothing to delete, and the
        // caller must not treat that as a failure.
        val target = CameraCaptures.newTarget(cacheDir, NOW)
        assertFalse(target.exists())
        assertFalse(CameraCaptures.discard(cacheDir, target.name))
    }

    @Test
    fun `a discard touches only the file that capture named`() {
        val mine = capture("capture_${NOW}.jpg")
        val other = capture("capture_${NOW + 1}.jpg")
        CameraCaptures.discard(cacheDir, mine.name)
        assertFalse(mine.exists())
        assertTrue("an unrelated in-flight capture was deleted", other.exists())
    }

    @Test
    fun `a discard refuses a name that is not ours`() {
        val foreign = capture("holiday.jpg")
        assertFalse(CameraCaptures.discard(cacheDir, foreign.name))
        assertTrue(foreign.exists())
    }

    @Test
    fun `a discard cannot escape the capture directory`() {
        // The name comes back through SavedStateHandle, i.e. from outside this
        // process. Traversal must fail on the name check and on containment.
        val outside = File(cacheDir, "capture_1.jpg").apply { writeText("x") }
        assertFalse(CameraCaptures.discard(cacheDir, "../capture_1.jpg"))
        assertTrue(outside.exists())
        assertFalse(CameraCaptures.discard(cacheDir, "../../etc/passwd"))
        assertFalse(CameraCaptures.discard(cacheDir, null))
    }

    @Test
    fun `an aged orphan is pruned`() {
        // The capture Android killed us during: its result went to a process
        // that no longer existed, so nothing will ever discard it.
        val orphan = capture("capture_1.jpg", ageMillis = CameraCaptures.ORPHAN_MAX_AGE_MS + 1)
        assertEquals(1, CameraCaptures.pruneOrphans(cacheDir, NOW))
        assertFalse(orphan.exists())
    }

    @Test
    fun `a recent capture is left alone`() {
        val recent = capture("capture_2.jpg", ageMillis = 60_000)
        assertEquals(0, CameraCaptures.pruneOrphans(cacheDir, NOW))
        assertTrue(recent.exists())
    }

    @Test
    fun `the in-flight target is never pruned however old it looks`() {
        // A capture can legitimately outlive the age bound — the user left the
        // camera open. Deleting it would break the attach on return.
        val inFlight = capture("capture_3.jpg", ageMillis = CameraCaptures.ORPHAN_MAX_AGE_MS * 10)
        assertEquals(0, CameraCaptures.pruneOrphans(cacheDir, NOW, keep = inFlight.name))
        assertTrue(inFlight.exists())
    }

    @Test
    fun `the prune ignores files this app did not create`() {
        val foreign = capture("something_else.jpg", ageMillis = CameraCaptures.ORPHAN_MAX_AGE_MS * 2)
        val subdir = File(CameraCaptures.dir(cacheDir), "nested").apply { mkdirs() }
        assertEquals(0, CameraCaptures.pruneOrphans(cacheDir, NOW))
        assertTrue(foreign.exists())
        assertTrue(subdir.isDirectory)
    }

    @Test
    fun `a prune with no directory yet is a no-op`() {
        // First run on a fresh install: nothing has opened the camera.
        assertEquals(0, CameraCaptures.pruneOrphans(cacheDir, NOW))
    }

    @Test
    fun `only a digit timestamp counts as a capture name`() {
        assertTrue(CameraCaptures.isCaptureName("capture_1700000000000.jpg"))
        assertFalse(CameraCaptures.isCaptureName("capture_.jpg"))
        assertFalse(CameraCaptures.isCaptureName("capture_abc.jpg"))
        assertFalse(CameraCaptures.isCaptureName("capture_1.png"))
        assertFalse(CameraCaptures.isCaptureName("xcapture_1.jpg"))
        assertFalse(CameraCaptures.isCaptureName("capture_1.jpg.bak"))
    }

    private companion object {
        /** Fixed clock — the prune's behaviour is about age, not wall time. */
        const val NOW = 1_700_000_000_000L
    }
}
