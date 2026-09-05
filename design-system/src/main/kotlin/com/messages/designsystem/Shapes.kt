package com.messages.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape scale (2026 visual refresh, M3 Expressive direction).
 *
 * M3 baseline scale: extraSmall=4dp, small=8dp, medium=12dp, large=16dp, extraLarge=28dp.
 * - small/medium sit under buttons, chips, and cards throughout the app and
 *   remain at stable baseline values to prevent regressions across high-frequency UI.
 * - large/extraLarge sit under dialogs, bottom sheets, and elevated container surfaces,
 *   nudged rounder (16 -> 20dp, 28 -> 32dp) for the softer, more approachable feel
 *   specified in the 2026 refresh.
 */
val MessagesShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
