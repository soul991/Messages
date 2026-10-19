package com.messages.core

import org.junit.Assert.assertFalse
import org.junit.Test

class AiFeatureGateTest {
    @Test
    fun research_only_build_cannot_be_enabled_by_a_saved_user_preference() {
        assertFalse(AiFeatureGate.allowsRouting(false))
        assertFalse(AiFeatureGate.allowsRouting(true))
    }
}
