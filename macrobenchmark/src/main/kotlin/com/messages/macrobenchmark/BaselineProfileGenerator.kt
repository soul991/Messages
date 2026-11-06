package com.messages.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
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
 * Baseline profile generator collecting critical user journeys a to f:
 * a. Home fling down/up x5 over 500 conversations
 * b. Home search overlay open/back
 * c. Dock switch across all 5 tabs
 * d. 100-message chat scroll, send messages, quick replies
 * e. Settings -> Conversations -> Quick replies
 * f. Chat <-> Home open/close transitions
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Before
    fun setUp() {
        BenchmarkSeeder.seed(wipe = true)
    }

    @Test
    fun generateBaselineProfile() = baselineRule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        device.executeShellCommand("am start -W -n $TARGET_PACKAGE/com.messages.app.MainActivity")
        device.wait(Until.hasObject(byTag("home_conversation_list")), 5000)

        // Journey a: Home fling
        val homeList = device.findObject(byTag("home_conversation_list"))
        repeat(2) {
            homeList?.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(2) {
            homeList?.fling(Direction.UP)
            device.waitForIdle()
        }

        // Journey b: Search overlay
        repeat(2) {
            device.findObject(byTag("home_search_pill"))?.click()
            device.waitForIdle()
            device.pressBack()
            device.wait(Until.hasObject(byTag("home_search_pill")), 5000)
            device.waitForIdle()
        }

        // Journey c: Dock switch
        val dockTags = listOf("dock_INBOX", "dock_TRANSACTIONS", "dock_PROMOTIONS", "dock_REVIEW", "dock_SPAM", "dock_INBOX")
        for (tag in dockTags) {
            device.findObject(byTag(tag))?.click()
            device.waitForIdle()
        }

        // Journey d: Chat scroll and send
        val firstConv = device.findObject(By.textContains("Alex Morgan"))
        firstConv?.click()
        device.wait(Until.hasObject(byTag("chat_message_list")), 5000)
        device.waitForIdle()

        val chatList = device.findObject(byTag("chat_message_list"))
        chatList?.fling(Direction.DOWN)
        device.waitForIdle()
        chatList?.fling(Direction.UP)
        device.waitForIdle()

        val composer = device.findObject(byTag("chat_composer_field"))
        composer?.text = "Baseline profile test"
        device.waitForIdle()
        device.findObject(byTag("chat_send_button"))?.click()
        device.waitForIdle()

        // Quick replies in chat
        val quickChips = device.findObjects(byTag("chat_quick_reply_chip"))
        if (quickChips.isNotEmpty()) {
            quickChips.first().click()
            device.waitForIdle()
            val sheetChips = device.findObjects(byTag("chat_quick_reply_chip"))
            if (sheetChips.isNotEmpty()) {
                sheetChips.last().click()
                device.waitForIdle()
                device.findObject(byTag("chat_send_button"))?.click()
                device.waitForIdle()
            }
        }

        // Back to Home
        device.pressBack()
        device.wait(Until.hasObject(byTag("home_conversation_list")), 5000)
        device.waitForIdle()

        // Journey e: Settings -> Conversations -> Quick replies
        device.findObject(byTag("home_settings_button"))?.click()
        device.wait(Until.hasObject(byTag("settings_page_conversations")), 5000)
        device.findObject(byTag("settings_page_conversations"))?.click()
        device.wait(Until.hasObject(byTag("quick_reply_list")), 5000)
        device.waitForIdle()

        val row = device.findObject(byTag("quick_reply_row_0"))
        row?.swipe(Direction.LEFT, 0.8f)
        device.waitForIdle()

        device.findObject(byTag("quick_reply_edit_toggle"))?.click()
        device.waitForIdle()
        device.findObject(byTag("quick_reply_edit_toggle"))?.click()
        device.waitForIdle()

        val input = device.findObject(byTag("quick_reply_input_field")) ?: run {
            device.findObject(byTag("quick_reply_list"))?.scroll(Direction.DOWN, 0.4f)
            device.findObject(byTag("quick_reply_input_field"))
        }
        input?.text = "Profile reply"
        device.waitForIdle()
        device.findObject(byTag("quick_reply_add_button"))?.click()
        device.waitForIdle()

        device.pressBack()
        device.waitForIdle()
        device.pressBack()
        device.wait(Until.hasObject(byTag("home_conversation_list")), 5000)
        device.waitForIdle()

        // Journey f: Chat <-> Home open/close x2
        repeat(2) {
            device.findObject(By.textContains("Alex Morgan"))?.click()
            device.wait(Until.hasObject(byTag("chat_message_list")), 5000)
            device.waitForIdle()
            device.pressBack()
            device.wait(Until.hasObject(byTag("home_conversation_list")), 5000)
            device.waitForIdle()
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.messages.app.benchmark"

        fun byTag(tag: String): BySelector = By.res(Pattern.compile(".*(:id/)?$tag"))
    }
}
