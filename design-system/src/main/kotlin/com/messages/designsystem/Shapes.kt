package com.messages.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape scale mapping tokens/design-tokens.json:
 * cardRadius = 20dp, bubbleRadius = 20dp, dockRadius = 32dp, searchHeight = 40dp (20dp pill), fab = 48dp (24dp round).
 */
val MessagesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp), // Card & bubble radius
    extraLarge = RoundedCornerShape(32.dp), // Dock radius
)

object ShapeTokens {
    val CardRadius = 20.dp
    val BubbleRadius = 20.dp
    val DockRadius = 32.dp
    val PillRadius = 20.dp
    val FabRadius = 24.dp
    val BadgeRadius = 10.dp
}
