package com.messages.core

import com.messages.protection.AiLayer

/**
 * Release boundary for statistical message routing.
 *
 * The user preference can only opt in to a model that has already passed the
 * application's release gate. A preference must never be able to override a
 * research-only build flag.
 */
internal object AiFeatureGate {
    fun allowsRouting(userOptedIn: Boolean): Boolean =
        AiLayer.AI_LAYER_ENABLED && userOptedIn
}
