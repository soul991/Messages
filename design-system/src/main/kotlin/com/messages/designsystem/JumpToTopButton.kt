package com.messages.designsystem

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch

object JumpToTopDefaults {
    /**
     * Number of scrolled items past which the button springs in.
     * Hidden near the top (<= 6 items), visible when firstVisibleItemIndex > 6.
     */
    const val VISIBILITY_THRESHOLD_INDEX = 6

    fun isVisible(firstVisibleItemIndex: Int): Boolean =
        firstVisibleItemIndex > VISIBILITY_THRESHOLD_INDEX
}

/**
 * Floating glass Jump-to-top button.
 *
 * Sits at bottom-start (16dp edge clearance, 88dp bottom clearance above the dock).
 * Features:
 * - 44dp visual presentation, 48dp touch target
 * - True backdrop blur via Haze with solid near-opaque fallback on pre-API 31
 * - 1dp hairline border
 * - High-contrast neutral arrow icon (contrast >= 3:1 against dock glass, NOT green)
 * - Scale & alpha spring entrance/exit only (no slide translation)
 * - Smooth scroll to index 0 on tap
 */
@Composable
fun JumpToTopButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    visible: Boolean = true,
    contentDescription: String = "Back to top",
    onClickExtra: (() -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val showButton by remember(listState) {
        derivedStateOf { JumpToTopDefaults.isVisible(listState.firstVisibleItemIndex) }
    }

    AnimatedVisibility(
        visible = visible && showButton,
        enter = scaleIn(Motion.butterSpring()) + fadeIn(Motion.effectsDefault()),
        exit = scaleOut(Motion.butterSpring()) + fadeOut(Motion.effectsFast()),
        modifier = modifier.testTag("jump_to_top_button"),
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.92f else 1f,
            animationSpec = Motion.butterSpring(),
            label = "jump-to-top-scale",
        )

        val isDark = LocalDarkTheme.current
        val tintColor = if (isDark) DesignTokens.Dark.Dock else DesignTokens.Light.Dock
        val fallbackColor = if (isDark) Color(0xF21C1C1F) else Color(0xF5FFFFFF)

        val buttonHazeStyle = remember(isDark, tintColor, fallbackColor) {
            HazeStyle(
                backgroundColor = tintColor,
                tints = listOf(HazeTint(tintColor)),
                blurRadius = 22.dp,
                noiseFactor = 0f,
                fallbackTint = HazeTint(fallbackColor),
            )
        }

        // 48dp touch target container
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 44dp visual round glass surface
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeChild(
                                state = hazeState,
                                style = buttonHazeStyle,
                            )
                        } else {
                            Modifier.background(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) tintColor else fallbackColor,
                                CircleShape,
                            )
                        }
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true),
                        onClick = {
                            onClickExtra?.invoke()
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                    ),
                shape = CircleShape,
                color = Color.Transparent,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.dockBorder),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = contentDescription,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
