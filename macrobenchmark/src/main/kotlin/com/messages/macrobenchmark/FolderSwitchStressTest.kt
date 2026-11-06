package com.messages.macrobenchmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.Random
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderSwitchStressTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun setUp() {
        BenchmarkSeeder.seed(wipe = true)
        device.pressHome()
        launchAndEnsureHomeScreen()
    }

    @Test
    fun testFolderSwitchStress() {
        val args = InstrumentationRegistry.getArguments()
        val totalCycles = args.getString("cycles")?.toIntOrNull() ?: 60

        val folders = listOf(
            "INBOX" to "Personal",
            "TRANSACTIONS" to "Bank",
            "PROMOTIONS" to "Offers",
            "REVIEW" to "Review",
            "SPAM" to "Spam",
        )

        val random = Random(42L)
        var currentFolderIdx = 0

        for (cycle in 1..totalCycles) {
            try {
                val list = device.findObject(byTag("home_conversation_list"))
                list?.fling(Direction.DOWN, 8000)
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                // Outgoing list detached mid-fling during transition
            }

            // Every 10 cycles, also delay 80-150 ms so fling is mid-way through animation
            if (cycle % 10 == 0) {
                val delayMs = 80L + random.nextInt(71) // 80 to 150 ms
                Thread.sleep(delayMs)
            }

            // Alternate between round-robin and random order
            val nextFolderIdx = if (cycle % 2 == 0) {
                (currentFolderIdx + 1) % folders.size
            } else {
                (currentFolderIdx + 1 + random.nextInt(folders.size - 1)) % folders.size
            }

            val (nextKey, nextTitle) = folders[nextFolderIdx]
            val dockObj = findDockTab(nextKey, nextTitle)
            dockObj?.click()
            currentFolderIdx = nextFolderIdx

            // Immediately fling again without waiting for idle
            try {
                val list2 = device.findObject(byTag("home_conversation_list"))
                list2?.fling(Direction.DOWN, 8000)
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                // Outgoing list detached mid-fling during transition
            }

            // Assert app process is alive and foreground package is the target app
            val currentPkg = device.currentPackageName
            assertEquals(
                "App crashed or left foreground at cycle $cycle of $totalCycles (currentPkg=$currentPkg)",
                TARGET_PACKAGE,
                currentPkg,
            )

            val pids = device.executeShellCommand("pidof $TARGET_PACKAGE").trim()
            assertTrue(
                "App process died at cycle $cycle of $totalCycles (pids is empty)",
                pids.isNotEmpty(),
            )
        }
    }

    private fun findDockTab(key: String, title: String): UiObject2? {
        return device.findObject(byTag("dock_$key"))
            ?: device.findObject(By.desc(title))
            ?: device.findObject(By.text(title))
    }

    private fun launchAndEnsureHomeScreen() {
        device.executeShellCommand("am start -W -n $TARGET_PACKAGE/com.messages.app.MainActivity")
        device.wait(Until.hasObject(byTag("home_conversation_list")), 5000)
        var attempts = 0
        while (!device.hasObject(byTag("home_conversation_list")) && attempts < 5) {
            device.pressBack()
            device.wait(Until.hasObject(byTag("home_conversation_list")), 1000)
            attempts++
        }
        check(device.hasObject(byTag("home_conversation_list"))) {
            "home_conversation_list not found on screen"
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.messages.app.benchmark"

        fun byTag(tag: String): BySelector = By.res(Pattern.compile(".*(:id/)?$tag"))
    }
}
