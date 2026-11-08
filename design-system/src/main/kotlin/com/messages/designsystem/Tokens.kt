package com.messages.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design Tokens for the Messages app redesign.
 * Source of truth: tokens/design-tokens.json
 */
object DesignTokens {

    object Dark {
        val Bg = Color(0xFF000000)
        val Surface = Color(0xFF121214)
        val Hairline = Color(0xFF1F1F23)
        val Text = Color(0xFFF2F2F4)
        val TextMuted = Color(0xFF8E8E93)
        val Green = Color(0xFF30D158)
        val GreenTint16 = Color(0x2930D158) // rgba(48,209,88,0.16)
        val OnGreen = Color(0xFF04210E)
        val Dock = Color(0xD11C1C1F) // rgba(28,28,31,0.82)
        val DockBorder = Color(0x1FFFFFFF) // rgba(255,255,255,0.12)
        val DockActivePill = Color(0x17FFFFFF) // rgba(255,255,255,0.09)
        val SelectedRow = Color(0x2430D158) // rgba(48,209,88,0.14)
        val TopBar = Color(0xB8000000) // rgba(0,0,0,0.72)
        val TopBarBorder = Color(0x17FFFFFF) // rgba(255,255,255,0.09)
    }

    object Light {
        val Bg = Color(0xFFF7F7F4)
        val Surface = Color(0xFFFFFFFF)
        val Hairline = Color(0xFFE6E6E1)
        val Text = Color(0xFF111113)
        val TextMuted = Color(0xFF6B6B72)
        val Green = Color(0xFF34C759)
        val GreenText = Color(0xFF1E9A44)
        val OnGreen = Color(0xFF04210E)
        val Dock = Color(0xD9FFFFFF) // rgba(255,255,255,0.85)
        val DockBorder = Color(0x14000000) // rgba(0,0,0,0.08)
        val DockActivePill = Color(0x0F000000) // rgba(0,0,0,0.06)
        val SelectedRow = Color(0x2430D158) // rgba(48,209,88,0.14)
        val TopBar = Color(0xCCF7F7F4) // rgba(247,247,244,0.80)
        val TopBarBorder = Color(0x12000000) // rgba(0,0,0,0.07)
    }

    /**
     * Applied to dock icons ONLY. Nowhere else.
     */
    object DockIconColors {
        object Dark {
            val Personal = Color(0xFF30D158)
            val Promotional = Color(0xFFFFD60A)
            val Spam = Color(0xFFFF453A)
            val Review = Color(0xFF0A84FF)
        }
        object Light {
            val Personal = Color(0xFF1E9A44)
            val Promotional = Color(0xFFC98A00)
            val Spam = Color(0xFFE5352B)
            val Review = Color(0xFF007AFF)
        }
    }

    object SummaryCards {
        object Credit {
            val Fill = Color(0x1A30D158) // rgba(48,209,88,0.10)
            val Border = Color(0x4730D158) // rgba(48,209,88,0.28)
            val Text = Color(0xFF6EE7A0)
        }
        object Debit {
            val Fill = Color(0x1AFF453A) // rgba(255,69,58,0.10)
            val Border = Color(0x47FF453A) // rgba(255,69,58,0.28)
            val Text = Color(0xFFFF8A80)
        }
        val Blur = 20.dp
    }

    object Dimensions {
        val RowHeight = 68.dp
        val Avatar = 44.dp
        val Fab = 48.dp
        val DockHeight = 64.dp
        val DockRadius = 32.dp
        val DockInset = 16.dp
        val DockBottom = 24.dp
        val CardRadius = 20.dp
        val BubbleRadius = 20.dp
        val SearchHeight = 40.dp
        val TitleSize = 30.sp
        val RowTitle = 16.sp
        val Preview = 14.sp
        val Time = 12.sp
    }
}
