package com.messages.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * Macrobenchmark measuring frame timing across the Phase 6.3 user journeys:
 * a. Home fling down/up x5 over 500 conversations
 * b. Home: open search overlay, back x10
 * c. Home: switch dock Personal -> Bank -> Offers -> Review -> Spam
 * d. Open 100-message chat, fling up/down; send 5 messages and 3 quick replies
 * e. Settings -> Conversations -> Quick replies: swipe-delete one row, enter Edit mode, add a reply
 * f. Chat <-> Home open/close x10 (including back gesture)
 *
 * 10 iterations each, evaluated under CompilationMode.Partial and CompilationMode.None.
 */
@RunWith(AndroidJUnit4::class)
class ScrollAndTransitionBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun setUp() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val output = device.executeShellCommand(
            "am broadcast -n $TARGET_PACKAGE/com.messages.app.debug.SeedDataReceiver -a com.messages.app.debug.SEED --ez wipe true"
        )
        check(output.contains("result=-1")) {
            "Database seeding failed! Output was: $output"
        }
    }

    private fun partialMode() = CompilationMode.Partial(
        baselineProfileMode = androidx.benchmark.macro.BaselineProfileMode.Disable,
        warmupIterations = 1,
    )

    // ------------------------------------------------------------------------
    // Journey a: Home fling down/up x5 over the 500 conversations
    // ------------------------------------------------------------------------
    @Test
    fun journey1HomeFlingPartial() = runHomeFling(partialMode())

    @Test
    fun journey1HomeFlingNone() = runHomeFling(CompilationMode.None())

    private fun runHomeFling(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
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
        repeat(5) {
            list?.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(5) {
            list?.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    // ------------------------------------------------------------------------
    // Journey b: Home: open search overlay, back x10
    // ------------------------------------------------------------------------
    @Test
    fun journey2HomeSearchOverlayPartial() = runSearchOverlay(partialMode())

    @Test
    fun journey2HomeSearchOverlayNone() = runSearchOverlay(CompilationMode.None())

    private fun runSearchOverlay(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
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
    // Journey c: Home: switch dock Personal -> Bank -> Offers -> Review -> Spam
    // ------------------------------------------------------------------------
    @Test
    fun journey3DockCategorySwitchPartial() = runDockSwitch(partialMode())

    @Test
    fun journey3DockCategorySwitchNone() = runDockSwitch(CompilationMode.None())

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

    // ------------------------------------------------------------------------
    // Journey d: Open 100-message chat, fling up/down; send 5 messages and 3 quick replies
    // ------------------------------------------------------------------------
    @Test
    fun journey4ChatScrollAndSendPartial() = runChatScrollAndSend(partialMode())

    @Test
    fun journey4ChatScrollAndSendNone() = runChatScrollAndSend(CompilationMode.None())

    private fun runChatScrollAndSend(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            launchAndEnsureHomeScreen(device)
            val firstConv = device.findObject(By.textContains("+15550000001"))
            firstConv?.click()
            device.wait(Until.hasObject(byTag("chat_message_list")), 5000)
            device.waitForIdle()
        }
    ) {
        val chatList = device.findObject(byTag("chat_message_list"))
        chatList?.fling(Direction.DOWN)
        device.waitForIdle()
        chatList?.fling(Direction.UP)
        device.waitForIdle()

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
    // Journey e: Settings -> Conversations -> Quick replies: swipe-delete one row, enter Edit mode, add a reply
    // ------------------------------------------------------------------------
    @Test
    fun journey5SettingsQuickRepliesPartial() = runSettingsQuickReplies(partialMode())

    @Test
    fun journey5SettingsQuickRepliesNone() = runSettingsQuickReplies(CompilationMode.None())

    private fun runSettingsQuickReplies(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
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
    // Journey f: Chat <-> Home open/close x10 (including back gesture)
    // ------------------------------------------------------------------------
    @Test
    fun journey6ChatOpenClosePartial() = runChatOpenClose(partialMode())

    @Test
    fun journey6ChatOpenCloseNone() = runChatOpenClose(CompilationMode.None())

    private fun runChatOpenClose(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            launchAndEnsureHomeScreen(device)
            device.wait(Until.hasObject(By.textContains("+15550000001")), 5000)
            device.waitForIdle()
        }
    ) {
        repeat(10) {
            val firstConv = device.wait(Until.findObject(By.textContains("+15550000001")), 5000)
            if (firstConv != null) {
                firstConv.click()
                device.wait(Until.hasObject(byTag("chat_message_list")), 5000)
                device.waitForIdle()

                // Navigate back via back gesture
                device.pressBack()
                device.wait(Until.hasObject(byTag("home_conversation_list")), 5000)
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
            device.wait(Until.hasObject(By.textContains("+1555")), 5000)
        }
    }
}
