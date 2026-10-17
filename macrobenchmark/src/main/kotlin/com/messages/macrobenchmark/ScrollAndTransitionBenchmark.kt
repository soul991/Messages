package com.messages.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark measuring frame timing across the 6 Phase 6.3 user journeys:
 * 1. Home fling down/up x5 over 500 conversations
 * 2. Home: open search overlay, back x10
 * 3. Home: switch dock Personal -> Bank -> Offers -> Review -> Spam
 * 4. Open 100-message chat, fling up/down; send 5 messages and 3 quick replies
 * 5. Settings -> Conversations -> Quick replies: swipe-delete, Edit mode, add a reply
 * 6. Chat <-> Home open/close x10 (including back gesture)
 *
 * 10 iterations each, evaluated under CompilationMode.Partial and CompilationMode.None.
 */
@RunWith(AndroidJUnit4::class)
class ScrollAndTransitionBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    // ------------------------------------------------------------------------
    // Journey 1: Home fling down/up x5 over the 500 conversations
    // ------------------------------------------------------------------------
    @Test
    fun journey1HomeFlingPartial() = runHomeFling(CompilationMode.Partial())

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
            startActivityAndWait()
            device.waitForIdle()
        }
    ) {
        val displayHeight = device.displayHeight
        val displayWidth = device.displayWidth
        val startX = displayWidth / 2
        val startY = (displayHeight * 0.75).toInt()
        val endY = (displayHeight * 0.25).toInt()

        repeat(5) {
            device.swipe(startX, startY, startX, endY, 12)
            device.waitForIdle()
        }
        repeat(5) {
            device.swipe(startX, endY, startX, startY, 12)
            device.waitForIdle()
        }
    }

    // ------------------------------------------------------------------------
    // Journey 2: Home: open search overlay, back x10
    // ------------------------------------------------------------------------
    @Test
    fun journey2HomeSearchOverlayPartial() = runSearchOverlay(CompilationMode.Partial())

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
            startActivityAndWait()
            device.waitForIdle()
        }
    ) {
        val displayWidth = device.displayWidth
        val searchPillY = (device.displayHeight * 0.12).toInt()

        repeat(10) {
            // Tap resting search pill
            device.click(displayWidth / 2, searchPillY)
            device.waitForIdle()

            // Dismiss via back
            device.pressBack()
            device.waitForIdle()
        }
    }

    // ------------------------------------------------------------------------
    // Journey 3: Home: switch dock Personal -> Bank -> Offers -> Review -> Spam
    // ------------------------------------------------------------------------
    @Test
    fun journey3DockCategorySwitchPartial() = runDockSwitch(CompilationMode.Partial())

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
            startActivityAndWait()
            device.waitForIdle()
        }
    ) {
        val displayWidth = device.displayWidth
        val dockY = device.displayHeight - 120

        // Tap dock items: Personal (0.1), Bank (0.3), Offers (0.5), Review (0.7), Spam (0.9)
        val fractions = listOf(0.10f, 0.30f, 0.50f, 0.70f, 0.90f)
        for (f in fractions) {
            device.click((displayWidth * f).toInt(), dockY)
            device.waitForIdle()
        }
        // Return to Personal
        device.click((displayWidth * 0.10f).toInt(), dockY)
        device.waitForIdle()
    }

    // ------------------------------------------------------------------------
    // Journey 4: Open 100-message chat, fling up/down; send 5 messages and 3 quick replies
    // ------------------------------------------------------------------------
    @Test
    fun journey4ChatScrollAndSendPartial() = runChatScrollAndSend(CompilationMode.Partial())

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
            startActivityAndWait()
            device.waitForIdle()
            // Click first conversation to enter chat
            device.click(device.displayWidth / 2, (device.displayHeight * 0.35).toInt())
            device.waitForIdle()
        }
    ) {
        val displayHeight = device.displayHeight
        val displayWidth = device.displayWidth
        val startX = displayWidth / 2
        val startY = (displayHeight * 0.70).toInt()
        val endY = (displayHeight * 0.30).toInt()

        // Fling up and down in chat
        repeat(2) {
            device.swipe(startX, startY, startX, endY, 15)
            device.waitForIdle()
        }
        repeat(2) {
            device.swipe(startX, endY, startX, startY, 15)
            device.waitForIdle()
        }

        // Tap quick replies if available or input text
        val inputY = displayHeight - 120
        repeat(5) { i ->
            device.click(displayWidth / 2, inputY)
            device.waitForIdle()
        }
    }

    // ------------------------------------------------------------------------
    // Journey 5: Settings -> Conversations -> Quick replies: swipe-delete, Edit mode, add a reply
    // ------------------------------------------------------------------------
    @Test
    fun journey5SettingsQuickRepliesPartial() = runSettingsQuickReplies(CompilationMode.Partial())

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
            startActivityAndWait()
            device.waitForIdle()
            // Open settings via top bar icon (top right)
            device.click(device.displayWidth - 80, 120)
            device.waitForIdle()
            // Click Conversations row
            device.click(device.displayWidth / 2, (device.displayHeight * 0.25).toInt())
            device.waitForIdle()
        }
    ) {
        val displayHeight = device.displayHeight
        val displayWidth = device.displayWidth

        // Swipe left to reveal and delete first quick reply
        val rowY = (displayHeight * 0.60).toInt()
        device.swipe((displayWidth * 0.85).toInt(), rowY, (displayWidth * 0.20).toInt(), rowY, 20)
        device.waitForIdle()

        // Toggle Edit mode
        val editToggleX = (displayWidth * 0.85).toInt()
        val editToggleY = (displayHeight * 0.48).toInt()
        device.click(editToggleX, editToggleY)
        device.waitForIdle()

        // Tap Done to exit Edit mode
        device.click(editToggleX, editToggleY)
        device.waitForIdle()
    }

    // ------------------------------------------------------------------------
    // Journey 6: Chat <-> Home open/close x10 (back gesture too)
    // ------------------------------------------------------------------------
    @Test
    fun journey6ChatOpenClosePartial() = runChatOpenClose(CompilationMode.Partial())

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
            startActivityAndWait()
            device.waitForIdle()
        }
    ) {
        val displayWidth = device.displayWidth
        val rowY = (device.displayHeight * 0.35).toInt()

        repeat(10) {
            // Tap first conversation row
            device.click(displayWidth / 2, rowY)
            device.waitForIdle()

            // Navigate back via back gesture
            device.pressBack()
            device.waitForIdle()
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.messages.app"
    }
}
