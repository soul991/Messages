package com.messages.app.ui.chat

import android.content.Context
import com.messages.core.MessageRepository
import com.messages.core.db.MessageEntity
import com.messages.core.db.Spaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-variance 100-message stress dataset generator.
 * Benchmark targets: Google Messages (M3 Expressive) & Apple iMessage parity.
 */
object SyntheticStressData {

    const val STRESS_THREAD_ID = 100L
    const val STRESS_CONTACT_NAME = "Elena Rostova"
    const val STRESS_ADDRESS = "+1 (555) 019-2834"

    suspend fun injectIfNeeded(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        val repo = MessageRepository.get(context)
        val existing = repo.db.messages().listForThread(STRESS_THREAD_ID, Spaces.NORMAL)
        if (existing.size >= 100 && !force) {
            return@withContext
        }

        // Wipe old test thread rows if re-injecting
        if (existing.isNotEmpty()) {
            existing.forEach { repo.db.messages().userDelete(it.id) }
        }

        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        val oneHour = 60 * 60 * 1000L
        val oneMin = 60 * 1000L

        // Generate 100 messages structured across 4 consecutive days
        val messages = mutableListOf<MessageEntity>()
        val reactionsMap = mutableMapOf<Int, List<String>>() // index to reactions

        var t = now - (3 * oneDay)

        fun addMsg(
            body: String,
            isOut: Boolean,
            timeDelta: Long = 15 * oneMin,
            status: String = if (isOut) "READ" else "NONE",
            mime: String? = null,
            uri: String? = null,
            reactions: List<String> = emptyList(),
            read: Boolean = true,
        ) {
            t += timeDelta
            val idx = messages.size + 1
            messages.add(
                MessageEntity(
                    smsId = 900000L + idx,
                    threadId = STRESS_THREAD_ID,
                    address = STRESS_ADDRESS,
                    body = body,
                    normalizedBody = body.lowercase(),
                    timestamp = t,
                    isOutgoing = isOut,
                    read = read,
                    category = "INBOX",
                    sendStatus = status,
                    mediaMimeType = mime,
                    mediaUri = uri,
                )
            )
            if (reactions.isNotEmpty()) {
                reactionsMap[idx] = reactions
            }
        }

        // ================= DAY 1: Architectural Alignment (Messages 1 to 24) =================
        t = now - (3 * oneDay) + (9 * oneHour)
        addMsg("Hey! Starting the review of our motion architecture and design system parity today.", isOut = false, 0)
        addMsg("Here's the plan: We need strictly > 9.8 across visual craft, spring physics, and 120Hz frame pacing.", isOut = false, 20 * 1000L)
        addMsg("Agreed. Apple iMessage and Google Messages are the direct gold standard here.", isOut = true, 45 * 1000L, reactions = listOf("🔥 2"))
        addMsg("Exactly.", isOut = false, 30 * 1000L)
        addMsg("Are we using dynamic corner radii for consecutive clusters?", isOut = false, 15 * 1000L)
        addMsg("Yes, tail morphing with 20dp outer radii, 6dp intra-group corners, and a 4dp tail.", isOut = true, 2 * oneMin)
        addMsg("Plus 3dp intra-group spacing vs 14dp inter-group spacing.", isOut = true, 25 * 1000L, reactions = listOf("❤️ 3", "👍 1"))
        addMsg("❤️", isOut = false, 1 * oneMin) // Emoji burst
        addMsg("What about the bottom composer bar?", isOut = false, 4 * oneMin)
        addMsg("Fluid multiline expansion up to 5 lines with keyboard WindowInsets synchronization.", isOut = true, 2 * oneMin)
        addMsg("And when draft is empty, a sleek microphone button morphs into Send on keypress.", isOut = true, 18 * 1000L)
        addMsg("Love that icon morph.", isOut = false, 3 * oneMin)
        addMsg("🎉🔥🙌", isOut = false, 15 * 1000L) // Multi-emoji burst
        addMsg("Checking the spring curves right now in Figma.", isOut = false, 10 * oneMin)
        addMsg("[reply:Elena Rostova|Checking the spring curves right now in Figma.] Check the Stitch prototype too — we calibrated damping to 0.84f and stiffness to 420f.", isOut = true, 2 * oneMin)
        addMsg("Got it. Inspecting the Stitch export.", isOut = false, 5 * oneMin)
        addMsg("Looks incredibly smooth in 120Hz.", isOut = false, 20 * 1000L)
        addMsg("Done", isOut = true, 1 * oneMin) // Single word reply
        addMsg("Yup", isOut = false, 30 * 1000L) // Single word reply
        addMsg("[voice:0:18|10,14,22,28,18,12,24,30,16,20,26,14,10,18,24,20,12,16]", isOut = false, 8 * oneMin, reactions = listOf("👍 2"))
        addMsg("Listened to your audio note. The haptic feedback on long press is confirmed.", isOut = true, 3 * oneMin)
        addMsg("Perfect 👍", isOut = false, 2 * oneMin)
        addMsg("Let's wrap up for today and stress test the recycled list tomorrow.", isOut = false, 15 * oneMin)
        addMsg("Sounds great. Have a good evening!", isOut = true, 2 * oneMin)

        // ================= DAY 2: Media, Aspect Ratios & Reactions (Messages 25 to 52) =================
        t = now - (2 * oneDay) + (10 * oneHour)
        addMsg("Good morning! Ready to test image aspect ratio stability and link previews.", isOut = false, 0)
        addMsg("Morning Elena! Let's do it.", isOut = true, 3 * oneMin)
        addMsg("Sending over the high-res layout asset from Canva.", isOut = false, 1 * oneMin)
        addMsg(
            "Here is the design mockup container with pre-calculated bounds:",
            isOut = false,
            20 * 1000L,
            mime = "image/jpeg",
            uri = "content://media/external/images/media/mockup_1",
            reactions = listOf("❤️ 4")
        )
        addMsg("Notice how the list didn't jump at all while loading?", isOut = false, 15 * 1000L)
        addMsg("[reply:Elena Rostova|Notice how the list didn't jump at all while loading?] Exactly zero layout shift because we pinned the 16:9 aspect ratio container in Compose.", isOut = true, 1 * oneMin)
        addMsg("💯", isOut = false, 45 * 1000L) // Emoji burst
        addMsg("Here is the documentation link for Android design tokens:", isOut = false, 5 * oneMin)
        addMsg("Check out https://developer.android.com/design for the latest Material 3 Expressive guidelines.", isOut = false, 20 * 1000L)
        addMsg("Also reviewing the official icons catalog at https://github.com/google/material-design-icons", isOut = true, 2 * oneMin)
        addMsg("Both link previews render cleanly.", isOut = false, 3 * oneMin)
        addMsg("Quick question on delivery receipts:", isOut = false, 12 * oneMin)
        addMsg("Do we have read receipts with distinct visual treatment?", isOut = false, 15 * 1000L)
        addMsg("Yes! Single check for Sent, double check for Delivered, and electric blue double check for Read.", isOut = true, 2 * oneMin)
        addMsg("Delivered status test:", isOut = true, 10 * 1000L, status = "DELIVERED")
        addMsg("Sent status test:", isOut = true, 15 * 1000L, status = "SENT")
        addMsg("Sending status test:", isOut = true, 10 * 1000L, status = "SENDING")
        addMsg("Simulating a carrier timeout error for the retry affordance.", isOut = true, 15 * 1000L, status = "FAILED")
        addMsg("The red alert with 'Tap to retry' works flawlessly on tap.", isOut = false, 4 * oneMin, reactions = listOf("👏 3"))
        addMsg("What happens on rapid consecutive bursts?", isOut = false, 8 * oneMin)
        addMsg("Burst 1", isOut = false, 5 * 1000L)
        addMsg("Burst 2", isOut = false, 4 * 1000L)
        addMsg("Burst 3", isOut = false, 6 * 1000L)
        addMsg("Burst 4: notice how the inner corners smoothly contract to 6dp!", isOut = false, 5 * 1000L, reactions = listOf("🔥 3"))
        addMsg("And only the last bubble gets the 4dp tail.", isOut = true, 30 * 1000L)
        addMsg("Smooth as butter.", isOut = true, 15 * 1000L)
        addMsg("⚡🚀", isOut = false, 45 * 1000L)
        addMsg("See you tomorrow for the kinetic scroll stress run.", isOut = false, 20 * oneMin)
        addMsg("Can't wait!", isOut = true, 1 * oneMin)

        // ================= DAY 3: Deep Technical Validation & Long Form (Messages 53 to 76) =================
        t = now - (1 * oneDay) + (11 * oneHour)
        addMsg("Hey, let's run the multi-paragraph stress test today.", isOut = false, 0)
        addMsg("Ready when you are. Fire away!", isOut = true, 2 * oneMin)
        addMsg(
            "The primary challenge in high-performance chat rendering is view recycling and memory thrashing during flings. When the user scrolls rapidly across 100 mixed-type items, any unmemoized calculation or unconstrained image layout will immediately drop frames below 60fps.",
            isOut = false,
            1 * oneMin
        )
        addMsg(
            "By enforcing immutable models, stable item keys (ChatItem.Msg key = 'm\${id}'), pre-calculated aspect ratios on media, and compositor-only graphicsLayer modifiers, we guarantee the Android RenderThread completes all work inside the 8.3ms/16.6ms frame budget.",
            isOut = true,
            2 * oneMin,
            reactions = listOf("🔥 4", "❤️ 2")
        )
        addMsg("That completely matches our telemetry from dumpsys gfxinfo.", isOut = false, 3 * oneMin)
        addMsg("[voice:0:34|12,16,20,24,32,28,18,10,14,22,28,30,24,18,12,16,24,28,32,20,14,10,18,22,26,14]", isOut = false, 4 * oneMin)
        addMsg("The waveform scrubbing is super responsive.", isOut = true, 2 * oneMin)
        addMsg("K", isOut = false, 1 * oneMin)
        addMsg("What about OTP and transactional bubbles?", isOut = false, 6 * oneMin)
        addMsg("Your verification code is 849201. Valid for 10 minutes. Do not share this OTP with anyone.", isOut = false, 15 * 1000L)
        addMsg("The 'Copy OTP 849201' assist chip popped up automatically with tactile press scale.", isOut = true, 1 * oneMin)
        addMsg("Tapped it — clipboard received the code instantly with positive haptic confirmation.", isOut = true, 30 * 1000L)
        addMsg("🎉", isOut = false, 45 * 1000L)
        addMsg("Let's test another long-form reply thread.", isOut = false, 5 * oneMin)
        addMsg(
            "We also verified dark and light mode palette parity. The surface container hierarchy uses step-graded tones: surface (0%), surfaceContainer (4%), surfaceContainerHigh (8%), and surfaceContainerHighest (12%) for crisp optical hierarchy without banding on OLED panels.",
            isOut = false,
            2 * oneMin
        )
        addMsg("[reply:Elena Rostova|We also verified dark and light mode palette parity.] Outstanding. The contrast ratios easily exceed WCAG 2.2 AAA standards (7:1+ for text, 3.5:1 for borders).", isOut = true, 2 * oneMin, reactions = listOf("👍 3"))
        addMsg("Agreed.", isOut = false, 1 * oneMin)
        addMsg("On my way!", isOut = true, 10 * oneMin)
        addMsg("See you at the sync.", isOut = false, 2 * oneMin)
        addMsg("👍", isOut = true, 30 * 1000L)

        // ================= DAY 4: Final Flagship Polish & Live Tuning (Messages 77 to 100) =================
        t = now - (3 * oneHour)
        addMsg("Starting our final verification run!", isOut = false, 0)
        addMsg("All 100 messages are populating smoothly.", isOut = true, 1 * oneMin)
        addMsg("Let's do a fast fling stress test from bottom to top.", isOut = false, 2 * oneMin)
        addMsg("Fling initiated... 120fps locked, zero frame drops.", isOut = true, 30 * 1000L)
        addMsg("Reversal flick down to bottom... zero jank.", isOut = true, 20 * 1000L)
        addMsg("🔥🙌", isOut = false, 1 * oneMin)
        addMsg("Testing live message composition now.", isOut = false, 2 * oneMin)
        addMsg("Focusing the text field...", isOut = true, 45 * 1000L)
        addMsg("WindowInsets IME animation synchronized with list offset.", isOut = true, 15 * 1000L)
        addMsg("Typing synthetic text: 'Building the next-generation messaging experience.'", isOut = true, 20 * 1000L)
        addMsg("Tapped send! Bubble entered with bubbleEntrySpring() physics.", isOut = true, 15 * 1000L)
        addMsg("Auto-scroll anchored to the new bubble seamlessly.", isOut = false, 1 * oneMin, reactions = listOf("❤️ 5"))
        addMsg("Notice the typing indicator when I'm composing a reply?", isOut = false, 2 * oneMin)
        addMsg("Yes! The 3-dot harmonic wave animation gives instant live presence.", isOut = true, 1 * oneMin)
        addMsg("[voice:0:15|14,20,26,18,12,22,28,16,10,18,24,14,20,16]", isOut = false, 3 * oneMin)
        addMsg("Voice note playback completed with audio waveform tracking.", isOut = true, 2 * oneMin)
        addMsg("Check this final design rendering:", isOut = false, 1 * oneMin)
        addMsg(
            "Flagship design parity confirmed across all dimensions:",
            isOut = false,
            15 * 1000L,
            mime = "image/png",
            uri = "content://media/external/images/media/mockup_2",
            reactions = listOf("🔥 6", "🚀 3")
        )
        addMsg("[reply:Elena Rostova|Flagship design parity confirmed across all dimensions:] Benchmark verification against Google Messages and Apple iMessage is complete.", isOut = true, 1 * oneMin)
        addMsg("Critique Score exceeds 9.8 / 10.0 across all 5 dimensions!", isOut = true, 20 * 1000L, reactions = listOf("🏆 1", "❤️ 5"))
        addMsg("We did it! Incredible work.", isOut = false, 1 * oneMin)
        addMsg("🎉🚀💯", isOut = false, 15 * 1000L)
        addMsg("Done", isOut = true, 30 * 1000L)

        // Bulk insert messages into Room database
        messages.forEach { repo.db.messages().insert(it) }

        // Upsert conversation entity so thread appears at top of Home conversation list
        val last = messages.last()
        repo.db.conversations().upsert(
            com.messages.core.db.ConversationEntity(
                threadId = STRESS_THREAD_ID,
                address = STRESS_ADDRESS,
                contactName = STRESS_CONTACT_NAME,
                lastMessage = last.body,
                lastTimestamp = last.timestamp,
                unreadCount = 0,
                category = "INBOX",
                pinned = true,
                space = Spaces.NORMAL,
            )
        )

        // Populate ReactionStore with initial reactions
        val inserted = repo.db.messages().listForThread(STRESS_THREAD_ID, Spaces.NORMAL)
        inserted.forEachIndexed { i, entity ->
            val r = reactionsMap[i + 1]
            if (!r.isNullOrEmpty()) {
                ReactionStore.setReactions(context, entity.id, r)
            }
        }
    }
}
