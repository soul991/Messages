package com.messages.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Reusable Liquid-Glass Indicator (Phase 5 / Orchid Refresh).
 *
 * Driven by:
 * - [xAnim]: horizontal position in pixels
 * - [liftAnim]: physical elevation progress (0f = rest, 1f = lifted)
 *
 * Glass look WITHOUT AGSL shaders, blur, or Brush gradients (audit gate compliant):
 * - Dark: fill = primary at 0.14 alpha at rest, 0.06 while lifted; rim = 1dp onSurface at 0.22 alpha
 *   while lifted; top highlight = 1dp arc/line stroke of onSurface at 0.5 alpha along upper edge.
 * - Light: fill = primary at 0.18 alpha at rest, 0.10 while lifted; top highlight = 1dp stroke of
 *   pure white at 0.9 alpha along upper edge; rim = 1dp onSurface at 0.12 alpha along lower edge only.
 *   (A dark top line is a bug in light mode).
 *
 * Values are read strictly within graphicsLayer and draw lambdas to ensure 0 recompositions per frame.
 */
@Composable
fun GlassIndicator(
    xAnim: Animatable<Float, *>,
    liftAnim: Animatable<Float, *>,
    widthDp: Dp,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .offset { IntOffset(xAnim.value.roundToInt(), 0) }
            .width(widthDp)
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .graphicsLayer {
                val l = liftAnim.value
                scaleX = 1f + 0.2f * l
                scaleY = 1f + 0.1f * l
            }
            .drawBehind {
                val l = liftAnim.value
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@drawBehind

                val r = h / 2f
                val cornerRadius = CornerRadius(r, r)

                // Fill alpha: dark = 0.14 rest, 0.06 lifted; light = 0.18 rest, 0.10 lifted
                val fillAlpha = if (isDark) {
                    0.14f - 0.08f * l
                } else {
                    0.18f - 0.08f * l
                }

                // Draw translucent primary fill
                drawRoundRect(
                    color = primary.copy(alpha = fillAlpha.coerceIn(0f, 1f)),
                    cornerRadius = cornerRadius,
                )

                if (l > 0.001f) {
                    val strokeWidth = 1.dp.toPx()
                    val inset = strokeWidth / 2f
                    val innerR = (r - inset).coerceAtLeast(0.1f)
                    val rightBound = (w - r).coerceAtLeast(r)

                    if (isDark) {
                        // Dark: rim = 1dp onSurface at 0.22 alpha while lifted
                        drawRoundRect(
                            color = onSurface.copy(alpha = (0.22f * l).coerceIn(0f, 1f)),
                            topLeft = Offset(inset, inset),
                            size = Size(
                                (w - strokeWidth).coerceAtLeast(0f),
                                (h - strokeWidth).coerceAtLeast(0f),
                            ),
                            cornerRadius = CornerRadius(innerR, innerR),
                            style = Stroke(width = strokeWidth),
                        )

                        // Top highlight = 1dp arc/line stroke of onSurface at 0.5 alpha along upper edge
                        val topHighlightPath = Path().apply {
                            arcTo(
                                rect = Rect(inset, inset, inset + 2 * innerR, inset + 2 * innerR),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = true,
                            )
                            lineTo(rightBound, inset)
                            arcTo(
                                rect = Rect(w - inset - 2 * innerR, inset, w - inset, inset + 2 * innerR),
                                startAngleDegrees = 270f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false,
                            )
                        }
                        drawPath(
                            path = topHighlightPath,
                            color = onSurface.copy(alpha = (0.50f * l).coerceIn(0f, 1f)),
                            style = Stroke(width = strokeWidth),
                        )
                    } else {
                        // Light: top highlight = 1dp stroke of pure white at 0.9 alpha along upper edge
                        val topHighlightPath = Path().apply {
                            arcTo(
                                rect = Rect(inset, inset, inset + 2 * innerR, inset + 2 * innerR),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = true,
                            )
                            lineTo(rightBound, inset)
                            arcTo(
                                rect = Rect(w - inset - 2 * innerR, inset, w - inset, inset + 2 * innerR),
                                startAngleDegrees = 270f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false,
                            )
                        }
                        drawPath(
                            path = topHighlightPath,
                            color = Color.White.copy(alpha = (0.90f * l).coerceIn(0f, 1f)),
                            style = Stroke(width = strokeWidth),
                        )

                        // Light: rim = 1dp onSurface at 0.12 alpha along lower edge only
                        val bottomRimPath = Path().apply {
                            arcTo(
                                rect = Rect(inset, h - inset - 2 * innerR, inset + 2 * innerR, h - inset),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = -90f,
                                forceMoveTo = true,
                            )
                            lineTo(rightBound, h - inset)
                            arcTo(
                                rect = Rect(w - inset - 2 * innerR, h - inset - 2 * innerR, w - inset, h - inset),
                                startAngleDegrees = 90f,
                                sweepAngleDegrees = -90f,
                                forceMoveTo = false,
                            )
                        }
                        drawPath(
                            path = bottomRimPath,
                            color = onSurface.copy(alpha = (0.12f * l).coerceIn(0f, 1f)),
                            style = Stroke(width = strokeWidth),
                        )
                    }
                }
            },
    )
}
