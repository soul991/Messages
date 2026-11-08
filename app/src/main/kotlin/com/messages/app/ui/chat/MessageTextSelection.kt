package com.messages.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import com.messages.app.R
import com.messages.designsystem.Haptics
import kotlin.math.roundToInt

/**
 * Helper to calculate word boundaries around a character index in [text].
 */
fun findWordBoundaries(text: String, index: Int): IntRange {
    if (text.isEmpty()) return 0..0
    val safeIndex = index.coerceIn(0, text.length - 1)
    val wordSeparators = " \t\r\n.,;:!?'\"`()[]{}<>/\\|@#$%^&*+-="
    
    var start = safeIndex
    while (start > 0 && text[start - 1] !in wordSeparators) {
        start--
    }
    var end = safeIndex
    while (end < text.length && text[end] !in wordSeparators) {
        end++
    }
    if (start >= end) {
        // If tapped directly on a separator, select just that character or single space
        return safeIndex..(safeIndex + 1).coerceAtMost(text.length)
    }
    return start..end
}

/**
 * Selectable message text component supporting:
 * - Double-tap to select word
 * - iMessage-style handles (thin 2dp green stem, 10dp round knob)
 * - Platform-style loupe magnifier offset above the finger that stays within screen bounds
 * - Floating copy toolbar near selection
 */
@Composable
fun SelectableMessageText(
    text: AnnotatedString,
    rawText: String,
    style: TextStyle,
    color: Color,
    onLinkClick: ((String) -> Unit)? = null,
    onLongPress: () -> Unit,
    selectionMode: Boolean,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var selectionRange by remember { mutableStateOf<IntRange?>(null) }
    var isDraggingStart by remember { mutableStateOf(false) }
    var isDraggingEnd by remember { mutableStateOf(false) }
    var dragTouchPosition by remember { mutableStateOf(Offset.Zero) }

    // Clear text selection if whole-message selection mode becomes active
    LaunchedEffect(selectionMode) {
        if (selectionMode) {
            selectionRange = null
            isDraggingStart = false
            isDraggingEnd = false
        }
    }

    val greenAccent = Color(0xFF30D158)
    val selectionBg = Color(0x4D30D158) // rgba(48,209,88,.30) matching TextSelect.html

    // Build annotated string with highlight if selection is active
    val displayedText = remember(text, selectionRange) {
        val range = selectionRange
        if (range == null || range.first >= range.last) {
            text
        } else {
            buildAnnotatedString {
                append(text)
                addStyle(
                    SpanStyle(background = selectionBg),
                    range.first.coerceIn(0, text.length),
                    range.last.coerceIn(0, text.length),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .pointerInput(selectionMode, selectionRange) {
                detectTapGestures(
                    onTap = { offset ->
                        if (selectionMode) {
                            onToggleSelect()
                        } else if (selectionRange != null) {
                            // Tap outside clears text selection
                            selectionRange = null
                        } else {
                            // Check if a link was clicked
                            val layout = textLayoutResult
                            if (layout != null && onLinkClick != null) {
                                val charIndex = layout.getOffsetForPosition(offset)
                                text.getStringAnnotations(start = charIndex, end = charIndex)
                                    .firstOrNull()?.let { annotation ->
                                        onLinkClick(annotation.item)
                                    }
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!selectionMode) {
                            val layout = textLayoutResult
                            if (layout != null && rawText.isNotEmpty()) {
                                val charIndex = layout.getOffsetForPosition(offset)
                                val boundaries = findWordBoundaries(rawText, charIndex)
                                if (boundaries.first < boundaries.last) {
                                    selectionRange = boundaries
                                    Haptics.tick(view)
                                }
                            }
                        }
                    },
                    onLongPress = {
                        selectionRange = null
                        onLongPress()
                    },
                )
            }
    ) {
        // Main Text Rendering
        Text(
            text = displayedText,
            style = style,
            color = color,
            onTextLayout = { textLayoutResult = it },
        )

        val layout = textLayoutResult
        val range = selectionRange

        if (layout != null && range != null && range.first < range.last && !selectionMode) {
            val safeStart = range.first.coerceIn(0, rawText.length - 1)
            val safeEnd = (range.last - 1).coerceIn(0, rawText.length - 1)
            val startRect = layout.getBoundingBox(safeStart)
            val endRect = layout.getBoundingBox(safeEnd)

            // Start Handle (Knob at top, stem downwards)
            // Left: -1px; top: -4px; height: 22px; knob: left -5px, top -12px (per TextSelect.html)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            startRect.left.roundToInt(),
                            startRect.top.roundToInt(),
                        )
                    }
                    .zIndex(20f)
            ) {
                // Interactive hit area around start handle
                Box(
                    modifier = Modifier
                        .offset(x = (-16).dp, y = (-20).dp)
                        .size(36.dp, 44.dp)
                        .pointerInput(range) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDraggingStart = true
                                    dragTouchPosition = Offset(startRect.left + offset.x - 16.dp.toPx(), startRect.top + offset.y - 20.dp.toPx())
                                    Haptics.tick(view)
                                },
                                onDragEnd = { isDraggingStart = false },
                                onDragCancel = { isDraggingStart = false },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragTouchPosition += dragAmount
                                    val newOffset = layout.getOffsetForPosition(dragTouchPosition)
                                    val newStart = newOffset.coerceIn(0, range.last - 1)
                                    if (newStart != range.first) {
                                        selectionRange = newStart..range.last
                                    }
                                }
                            )
                        }
                ) {
                    // Thin green stem (2dp wide, ~22dp height)
                    Box(
                        modifier = Modifier
                            .offset(x = 15.dp, y = 8.dp)
                            .width(2.dp)
                            .height(22.dp)
                            .background(greenAccent)
                    )
                    // Round knob (10dp) at the top of the stem
                    Box(
                        modifier = Modifier
                            .offset(x = 11.dp, y = 0.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(greenAccent)
                    )
                }
            }

            // End Handle (Stem upwards, knob at bottom)
            // Right: -1px; top: -2px; height: 22px; knob: right -5px, top 18px (per TextSelect.html)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            endRect.right.roundToInt(),
                            endRect.top.roundToInt(),
                        )
                    }
                    .zIndex(20f)
            ) {
                // Interactive hit area around end handle
                Box(
                    modifier = Modifier
                        .offset(x = (-16).dp, y = (-4).dp)
                        .size(36.dp, 48.dp)
                        .pointerInput(range) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDraggingEnd = true
                                    dragTouchPosition = Offset(endRect.right + offset.x - 16.dp.toPx(), endRect.top + offset.y - 4.dp.toPx())
                                    Haptics.tick(view)
                                },
                                onDragEnd = { isDraggingEnd = false },
                                onDragCancel = { isDraggingEnd = false },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragTouchPosition += dragAmount
                                    val newOffset = layout.getOffsetForPosition(dragTouchPosition)
                                    val newEnd = newOffset.coerceIn(range.first + 1, rawText.length)
                                    if (newEnd != range.last) {
                                        selectionRange = range.first..newEnd
                                    }
                                }
                            )
                        }
                ) {
                    // Thin green stem (2dp wide, ~22dp height)
                    Box(
                        modifier = Modifier
                            .offset(x = 15.dp, y = 4.dp)
                            .width(2.dp)
                            .height(22.dp)
                            .background(greenAccent)
                    )
                    // Round knob (10dp) at the bottom of the stem
                    Box(
                        modifier = Modifier
                            .offset(x = 11.dp, y = 24.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(greenAccent)
                    )
                }
            }

            // Floating Copy Toolbar (near selection when not dragging)
            if (!isDraggingStart && !isDraggingEnd) {
                val toolbarX = ((startRect.left + endRect.right) / 2f) - with(density) { 45.dp.toPx() }
                val toolbarY = (startRect.top - with(density) { 48.dp.toPx() }).coerceAtLeast(with(density) { (-40).dp.toPx() })

                Surface(
                    modifier = Modifier
                        .offset { IntOffset(toolbarX.roundToInt(), toolbarY.roundToInt()) }
                        .shadow(8.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                        .zIndex(25f),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                val selectedSnippet = rawText.substring(range.first, range.last)
                                if (selectedSnippet.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(selectedSnippet))
                                    Haptics.confirm(view)
                                }
                                selectionRange = null
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_copy),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Platform-Style Loupe Magnifier (when actively dragging handles)
            // Specs from TextSelect.html:
            // width: 150px, height: 56px, border-radius: 28px, background: #1C1C1F,
            // border: 1px solid rgba(255,255,255,.14), shadow: 0 10px 30px rgba(0,0,0,.5), font-size: 19px
            val isDragging = isDraggingStart || isDraggingEnd
            if (isDragging) {
                val loupeWidthPx = with(density) { 150.dp.toPx() }
                val loupeHeightPx = with(density) { 56.dp.toPx() }
                
                // Offset strictly above the finger (~80dp above) so it never covers the words
                val loupeTargetX = dragTouchPosition.x - (loupeWidthPx / 2f)
                val loupeTargetY = dragTouchPosition.y - loupeHeightPx - with(density) { 32.dp.toPx() }

                // Clamp horizontally within screen edges
                val clampedX = loupeTargetX.coerceIn(
                    with(density) { 16.dp.toPx() },
                    (screenWidthPx - loupeWidthPx - with(density) { 16.dp.toPx() }).coerceAtLeast(0f)
                )

                // Selected snippet for the loupe
                val snippet = remember(range, rawText) {
                    val wordStart = range.first.coerceIn(0, rawText.length)
                    val wordEnd = range.last.coerceIn(0, rawText.length)
                    val before = rawText.substring((wordStart - 8).coerceAtLeast(0), wordStart)
                    val selected = rawText.substring(wordStart, wordEnd)
                    val after = rawText.substring(wordEnd, (wordEnd + 8).coerceAtMost(rawText.length))
                    Triple(before, selected, after)
                }

                Surface(
                    modifier = Modifier
                        .offset { IntOffset(clampedX.roundToInt(), loupeTargetY.roundToInt()) }
                        .size(150.dp, 56.dp)
                        .shadow(12.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(28.dp))
                        .zIndex(30f),
                    color = Color(0xFF1C1C1F),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (snippet.first.isNotEmpty()) {
                                Text(
                                    text = snippet.first,
                                    fontSize = 17.sp,
                                    color = Color(0xFF8E8E93),
                                    maxLines = 1,
                                )
                            }
                            Text(
                                text = snippet.second,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFF2F2F4),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x4D30D158))
                                    .padding(horizontal = 2.dp),
                                maxLines = 1,
                            )
                            if (snippet.third.isNotEmpty()) {
                                Text(
                                    text = snippet.third,
                                    fontSize = 17.sp,
                                    color = Color(0xFF8E8E93),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
