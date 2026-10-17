package com.messages.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline profile generator collecting critical user journeys:
 * - App startup & Home screen display
 * - Home conversation list scroll
 * - Transition into Chat screen
 * - Chat thread message scroll & interaction
 * - Transition back to Home
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = baselineRule.collect(
        packageName = "com.messages.app",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // Home list scroll
        val displayHeight = device.displayHeight
        val displayWidth = device.displayWidth
        device.swipe(displayWidth / 2, (displayHeight * 0.7).toInt(), displayWidth / 2, (displayHeight * 0.3).toInt(), 15)
        device.waitForIdle()

        // Open first chat thread
        device.click(displayWidth / 2, (displayHeight * 0.35).toInt())
        device.waitForIdle()

        // Chat thread message scroll
        device.swipe(displayWidth / 2, (displayHeight * 0.7).toInt(), displayWidth / 2, (displayHeight * 0.3).toInt(), 15)
        device.waitForIdle()

        // Back to Home
        device.pressBack()
        device.waitForIdle()
    }
}
