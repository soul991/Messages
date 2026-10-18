package com.messages.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark measuring frame timing across the required user journeys:
 * a. homeScroll: Home fling down/up over 500 conversations
 * b. homeSearchOpenClose: open search overlay, wait, press back x10 per iteration
 * c. dockSwitch: switch dock Personal -> Bank -> Offers -> Review -> Spam
 * d. chatScroll: 100-message chat, fling up/down; send 5 messages and 3 quick replies
 * e. quickRepliesSwipeDelete: Settings -> Conversations -> Quick replies, swipe-delete one row, enter Edit mode and leave it, add a reply
 * f. homeToChatTransition: Chat <-> Home open/close x10
 *
 * 10 iterations each, evaluated under CompilationMode.Partial and CompilationMode.None.
 */
@RunWith(AndroidJUnit4::class)
class ScrollAndTransitionBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun setUp() {
        BenchmarkSeeder.seed(wipe = true)
    }

    private fun partialMode() = CompilationMode.Partial(
        baselineProfileMode = androidx.benchmark.macro.BaselineProfileMode.Disable,
        warmupIterations = 1,
    )

    // ------------------------------------------------------------------------
    // Journey a: homeScroll
    // ------------------------------------------------------------------------
    @Test
    fun homeScroll_profile() = runHomeScroll(partialMode())

    @Test
    fun homeScroll_noProfile() = runHomeScroll(CompilationMode.None())

    private fun runHomeScroll(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            launchAndEnsureHomeScreen(device)
        }
    ) {
        val list = device.findObject(byTag("home_conversation_list"))
        repeat(2) {
            list?.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(2) {
            list?.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    // ------------------------------------------------------------------------
    // Journey b: homeSearchOpenClose: tap search pill, wait for overlay, press back x10
    // ------------------------------------------------------------------------
    @Test
    fun homeSearchOpenClose_profile() = runSearchOpenClose(partialMode())

    @Test
    fun homeSearchOpenClose_noProfile() = runSearchOpenClose(CompilationMode.None())

    private fun runSearchOpenClose(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            launchAndEnsureHomeScreen(device)
        }
    ) {
        repeat(10) {
            val searchPill = device.findObject(byTag("home_search_pill"))
            searchPill?.click()
            device.waitForIdle()

            device.pressBack()
            device.wait(Until.hasObject(byTag("home_search_pill")), 5000)
            device.waitForIdle()
        }
    }

    // ------------------------------------------------------------------------
    // Journey c: dockSwitch: Personal -> Bank -> Offers -> Review -> Spam
    // ------------------------------------------------------------------------
    @Test
    fun dockSwitch_profile() = runDockSwitch(partialMode())

    @Test
    fun dockSwitch_noProfile() = runDockSwitch(CompilationMode.None())

    private fun runDockSwitch(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            launchAndEnsureHomeScreen(device)
        }
    ) {
        val dockTags = listOf(
            "dock_INBOX",
            "dock_TRANSACTIONS",
            "dock_PROMOTIONS",
            "dock_REVIEW",
            "dock_SPAM",
        )
        for (tag in dockTags) {
            device.findObject(byTag(tag))?.click()
            device.waitForIdle()
        }
        // Return to Personal
        device.findObject(byTag("dock_INBOX"))?.click()
        device.waitForIdle()
    }

    // ------------------------------------------------------------------------
    // Journey d: chatScroll: fling up/down; send 5 messages and 3 quick replies
    // ------------------------------------------------------------------------
    @Test
    fun chatScroll_profile() = runChatScroll(partialMode())

    @Test
    fun chatScroll_noProfile() = runChatScroll(CompilationMode.None())

    private fun runChatScroll(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            launchAndEnsureHomeScreen(device)
            val firstConv = device.wait(Until.findObject(By.textContains("Alex Morgan")), 3000)
            firstConv?.click()
            if (!device.wait(Until.hasObject(byTag("chat_message_list")), 2000)) {
                val rowY = (device.displayHeight * 0.28).toInt()
                device.click(device.displayWidth / 2, rowY)
                device.wait(Until.hasObject(byTag("chat_message_list")), 5000)
            }
            device.waitForIdle()
        }
    ) {
        val chatList = device.findObject(byTag("chat_message_list"))
        if (chatList != null) {
            chatList.fling(Direction.DOWN)
            device.waitForIdle()
            chatList.fling(Direction.UP)
            device.waitForIdle()
        } else {
            val startY = (device.displayHeight * 0.70).toInt()
            val endY = (device.displayHeight * 0.30).toInt()
            device.swipe(device.displayWidth / 2, startY, device.displayWidth / 2, endY, 15)
            device.waitForIdle()
            device.swipe(device.displayWidth / 2, endY, device.displayWidth / 2, startY, 15)
            device.waitForIdle()
        }

        // Send 5 messages
        repeat(5) { i ->
            safeSetText(device, "chat_composer_field", "Benchmark test message $i")
            device.waitForIdle()
            safeClick(device, "chat_send_button")
            device.waitForIdle()
        }

        // Send 3 quick replies
        repeat(3) {
            val quickChips = device.findObjects(byTag("chat_quick_reply_chip"))
            if (quickChips.isNotEmpty()) {
                try {
                    quickChips.first().click()
                } catch (_: Exception) {}
                device.waitForIdle()
                val sheetChips = device.findObjects(byTag("chat_quick_reply_chip"))
                if (sheetChips.isNotEmpty()) {
                    try {
                        sheetChips.last().click()
                    } catch (_: Exception) {}
                    device.waitForIdle()
                    safeClick(device, "chat_send_button")
                    device.waitForIdle()
                }
            }
        }

        // Return to home
        device.pressBack()
        device.wait(Until.hasObject(byTag("home_conversation_list")), 5000)
        device.waitForIdle()
    }

    // ------------------------------------------------------------------------
    // Journey e: quickRepliesSwipeDelete: swipe-delete one row, edit mode, add reply
    // ------------------------------------------------------------------------
    @Test
    fun quickRepliesSwipeDelete_profile() = runQuickRepliesSwipeDelete(partialMode())

    @Test
    fun quickRepliesSwipeDelete_noProfile() = runQuickRepliesSwipeDelete(CompilationMode.None())

    private fun runQuickRepliesSwipeDelete(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            launchAndEnsureHomeScreen(device)
            device.findObject(byTag("home_settings_button"))?.click()
            device.wait(Until.hasObject(byTag("settings_page_conversations")), 5000)
            device.findObject(byTag("settings_page_conversations"))?.click()
            device.wait(Until.hasObject(byTag("quick_reply_list")), 5000)
            device.waitForIdle()
        }
    ) {
        // Swipe-delete one row
        try {
            val row = device.findObject(byTag("quick_reply_row_0"))
            row?.swipe(Direction.LEFT, 0.8f)
        } catch (_: Exception) {}
        device.waitForIdle()

        // Enter and exit edit mode
        safeClick(device, "quick_reply_edit_toggle")
        device.waitForIdle()
        safeClick(device, "quick_reply_edit_toggle")
        device.waitForIdle()

        // Add a reply
        if (device.findObject(byTag("quick_reply_input_field")) == null) {
            device.findObject(byTag("quick_reply_list"))?.scroll(Direction.DOWN, 0.4f)
        }
        safeSetText(device, "quick_reply_input_field", "Bench reply")
        device.waitForIdle()
        safeClick(device, "quick_reply_add_button")
        device.waitForIdle()

        // Return to home
        device.pressBack()
        device.waitForIdle()
        device.pressBack()
        device.wait(Until.hasObject(byTag("home_conversation_list")), 5000)
        device.waitForIdle()
    }

    // ------------------------------------------------------------------------
    // Journey f: homeToChatTransition: open and close chat x10
    // ------------------------------------------------------------------------
    @Test
    fun homeToChatTransition_profile() = runChatOpenClose(partialMode())

    @Test
    fun homeToChatTransition_noProfile() = runChatOpenClose(CompilationMode.None())

    private fun runChatOpenClose(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            launchAndEnsureHomeScreen(device)
            device.wait(Until.hasObject(By.textContains("Alex Morgan")), 5000)
            device.waitForIdle()
        }
    ) {
        repeat(10) {
            val firstConv = device.findObject(By.textContains("Alex Morgan"))
            firstConv?.click()
            if (!device.wait(Until.hasObject(byTag("chat_message_list")), 2000)) {
                val rowY = (device.displayHeight * 0.28).toInt()
                device.click(device.displayWidth / 2, rowY)
                device.wait(Until.hasObject(byTag("chat_message_list")), 5000)
            }
            device.waitForIdle()

            // Navigate back via back button in top bar or fallback
            val backButton = device.findObject(By.desc("Back"))
            if (backButton != null) {
                backButton.click()
            } else {
                device.pressBack()
            }
            device.wait(Until.hasObject(byTag("home_conversation_list")), 5000)
            device.waitForIdle()
        }
    }

    // ------------------------------------------------------------------------
    // Journey g: settingsTransitionsAndScroll: navigate to settings, scroll, open subpage, back
    // ------------------------------------------------------------------------
    @Test
    fun settingsTransitionsAndScroll_profile() = runSettingsTransitionsAndScroll(partialMode())

    @Test
    fun settingsTransitionsAndScroll_noProfile() = runSettingsTransitionsAndScroll(CompilationMode.None())

    private fun runSettingsTransitionsAndScroll(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            launchAndEnsureHomeScreen(device)
        }
    ) {
        // Open Settings
        device.findObject(byTag("home_settings_button"))?.click()
        device.wait(Until.hasObject(byTag("settings_page_conversations")), 5000)
        device.waitForIdle()

        // Fling / scroll settings list
        val displayHeight = device.displayHeight
        val displayWidth = device.displayWidth
        device.swipe(displayWidth / 2, (displayHeight * 0.7).toInt(), displayWidth / 2, (displayHeight * 0.3).toInt(), 15)
        device.waitForIdle()

        // Open Conversations subpage
        device.findObject(byTag("settings_page_conversations"))?.click()
        device.wait(Until.hasObject(byTag("quick_reply_list")), 5000)
        device.waitForIdle()

        // Back to settings hub
        device.pressBack()
        device.wait(Until.hasObject(byTag("settings_page_conversations")), 5000)
        device.waitForIdle()

        // Back to home
        device.pressBack()
        device.wait(Until.hasObject(byTag("home_conversation_list")), 5000)
        device.waitForIdle()
    }

    private fun safeSetText(device: UiDevice, tag: String, text: String, maxAttempts: Int = 3) {
        for (i in 0 until maxAttempts) {
            try {
                val obj = device.findObject(byTag(tag))
                if (obj != null) {
                    obj.text = text
                    return
                }
            } catch (e: androidx.test.uiautomator.StaleObjectException) {
                device.waitForIdle()
            }
        }
    }

    private fun safeClick(device: UiDevice, tag: String, maxAttempts: Int = 3) {
        for (i in 0 until maxAttempts) {
            try {
                val obj = device.findObject(byTag(tag))
                if (obj != null) {
                    obj.click()
                    return
                }
            } catch (e: androidx.test.uiautomator.StaleObjectException) {
                device.waitForIdle()
            }
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.messages.app.benchmark"

        fun byTag(tag: String): BySelector = By.res(Pattern.compile(".*(:id/)?$tag"))

        fun launchAndEnsureHomeScreen(device: UiDevice) {
            device.executeShellCommand("am start -W -n $TARGET_PACKAGE/com.messages.app.MainActivity")
            device.wait(Until.hasObject(byTag("home_conversation_list")), 5000)
            ensureHomeScreen(device)
        }

        fun ensureHomeScreen(device: UiDevice) {
            var attempts = 0
            while (!device.hasObject(byTag("home_conversation_list")) && attempts < 5) {
                device.pressBack()
                device.wait(Until.hasObject(byTag("home_conversation_list")), 1000)
                attempts++
            }
            device.wait(Until.hasObject(byTag("home_conversation_list")), 5000)
            check(device.hasObject(byTag("home_conversation_list"))) {
                "home_conversation_list not found on screen"
            }
            device.wait(Until.hasObject(By.textContains("Alex Morgan")), 5000)
        }
    }
}
