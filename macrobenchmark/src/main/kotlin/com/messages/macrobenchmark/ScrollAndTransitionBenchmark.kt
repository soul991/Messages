package com.messages.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark measuring frame timing and memory during:
 * - Home scroll (500 conversations)
 * - Home -> Chat transition
 * - Chat scroll (100 messages)
 * - Settings scroll and transitions
 */
@RunWith(AndroidJUnit4::class)
class ScrollAndTransitionBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun homeScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    ) {
        // Fling down through the conversation list
        val displayHeight = device.displayHeight
        val displayWidth = device.displayWidth
        val startX = displayWidth / 2
        val startY = (displayHeight * 0.8).toInt()
        val endY = (displayHeight * 0.2).toInt()

        repeat(3) {
            device.swipe(startX, startY, startX, endY, 15)
            device.waitForIdle()
        }
        repeat(3) {
            device.swipe(startX, endY, startX, startY, 15)
            device.waitForIdle()
        }
    }

    @Test
    fun homeToChatTransition() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    ) {
        // Tap first conversation item to trigger transition to Chat
        val displayHeight = device.displayHeight
        val displayWidth = device.displayWidth
        device.click(displayWidth / 2, (displayHeight * 0.35).toInt())
        device.waitForIdle()

        // Navigate back to Home
        device.pressBack()
        device.waitForIdle()
    }

    @Test
    fun chatScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            // Open first conversation
            val displayHeight = device.displayHeight
            val displayWidth = device.displayWidth
            device.click(displayWidth / 2, (displayHeight * 0.35).toInt())
            device.waitForIdle()
        }
    ) {
        // Scroll through the 100 messages in thread 1
        val displayHeight = device.displayHeight
        val displayWidth = device.displayWidth
        val startX = displayWidth / 2
        val startY = (displayHeight * 0.7).toInt()
        val endY = (displayHeight * 0.3).toInt()

        repeat(4) {
            device.swipe(startX, startY, startX, endY, 20)
            device.waitForIdle()
        }
        repeat(4) {
            device.swipe(startX, endY, startX, startY, 20)
            device.waitForIdle()
        }
    }

    @Test
    fun settingsTransitionsAndScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    ) {
        // Tap dock settings tab (dock icon on bottom right / 4th item)
        val displayHeight = device.displayHeight
        val displayWidth = device.displayWidth
        val dockY = displayHeight - 120
        val settingsX = (displayWidth * 0.85).toInt()
        device.click(settingsX, dockY)
        device.waitForIdle()

        // Scroll settings cards
        device.swipe(displayWidth / 2, (displayHeight * 0.7).toInt(), displayWidth / 2, (displayHeight * 0.3).toInt(), 15)
        device.waitForIdle()

        // Tap sub-page item
        device.click(displayWidth / 2, (displayHeight * 0.4).toInt())
        device.waitForIdle()

        // Back to settings hub
        device.pressBack()
        device.waitForIdle()

        // Back to inbox tab (1st item on dock)
        val inboxX = (displayWidth * 0.15).toInt()
        device.click(inboxX, dockY)
        device.waitForIdle()
    }

    companion object {
        const val TARGET_PACKAGE = "com.messages.app"
    }
}
