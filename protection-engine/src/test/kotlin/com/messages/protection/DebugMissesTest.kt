package com.messages.protection

import org.junit.Test

class DebugMissesTest {
    @Test
    fun debug() {
        val cases = listOf(
            "+2547123456789" to "I am Mrs Grace, dying of cancer. I want to donate $2.5 million USD to you. Reply for details",
            "+12025550123" to "I have shipped you a gift parcel with iPhone and cash. Pay customs clearance fee at airport to receive",
            "8344552617" to "I sent Rs 10,000 to your number by mistake. Please return it to this UPI immediately, urgent",
            "9233440596" to "Mummy hospital me hai, paise chahiye urgent. Is number pe Rs 15,000 transfer karo please. Beta",
            "9677884930" to "ALERT: unusual activity in your account. Confirm identity at 45.33.12.98/secure or account freezes tonight",
            "8344551602" to "Rs 4,999 will be auto debited for Amazon Prime renewal. To stop payment call 8012345678 now",
            "+2334512345678" to "My late husband left $4.8M USD. I need trustworthy person in India to transfer. Reply with your account details",
        )
        for ((sender, text) in cases) {
            val v = TestEngine.classify(text, sender)
            val n = Normalizer.normalize(text)
            println("== $sender → ${v.category} score=${v.score} ids=${v.matchedPatternIds} combos=${v.matchedComboIds}")
            println("   norm: ${n.normalizedText.take(100)}")
            println("   urls=${n.urls} amounts=${n.amounts}")
        }
    }
}
