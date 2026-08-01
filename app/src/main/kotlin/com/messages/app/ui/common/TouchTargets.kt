package com.messages.app.ui.common

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * V2-40 / V2-41. The minimum interactive size everything tappable must reserve.
 *
 * Compose already grows a `clickable`'s hit area toward
 * `ViewConfiguration.minimumTouchTargetSize`, but that expansion happens
 * *outside* the layout bounds — so on a small control it either overlaps the
 * neighbour or gets clipped by it, and which one you get depends on draw order
 * rather than on anything visible. Reserving the space in layout is what makes
 * the expansion actually available.
 */
val MinTouchTarget: Dp = 48.dp

/**
 * Reserves at least [size] in both axes so a small glyph still has a full touch
 * target around it. The glyph keeps its own size and is centered by the parent;
 * only the interactive box grows.
 *
 * Use this on the element that carries the click, not on the glyph — putting it
 * on the glyph pads the visual and leaves the hit area exactly where it was.
 */
fun Modifier.minTouchTarget(size: Dp = MinTouchTarget): Modifier =
    sizeIn(minWidth = size, minHeight = size)
