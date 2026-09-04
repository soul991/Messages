package com.messages.app.notify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import com.messages.protection.Category

/**
 * Generates crisp, modern, circular branded avatar bitmaps for notifications
 * when a contact has no photo or when the message is from a business/bank.
 */
object NotificationAvatarGenerator {

    private const val SIZE = 128 // dp-scaled density target (standard large icon)

    /**
     * Renders the app's branded emerald squircle launcher icon into a high-res [Bitmap]
     * for system notifications (e.g. in-app updates, service notifications).
     */
    fun getAppIconBitmap(context: Context): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (SIZE * (density / 2.0f)).toInt().coerceIn(128, 256)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val drawable = androidx.core.content.ContextCompat.getDrawable(
            context,
            com.messages.app.R.mipmap.ic_launcher,
        ) ?: androidx.core.content.ContextCompat.getDrawable(
            context,
            com.messages.app.R.drawable.ic_launcher_foreground,
        )

        drawable?.let {
            it.setBounds(0, 0, px, px)
            it.draw(canvas)
        }
        return bitmap
    }

    /** Generates an [IconCompat] suitable for NotificationCompat.Builder.setLargeIcon(). */
    fun generateIcon(
        context: Context,
        address: String,
        displayName: String?,
        category: Category,
    ): IconCompat {
        val bitmap = generateBitmap(context, address, displayName, category)
        return IconCompat.createWithBitmap(bitmap)
    }

    /** Generates a circular [Bitmap] with a category-tailored or sender-hashed gradient. */
    fun generateBitmap(
        context: Context,
        address: String,
        displayName: String?,
        category: Category,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (SIZE * (density / 2.0f)).toInt().coerceIn(128, 256)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val name = displayName?.takeIf { it.isNotBlank() } ?: address
        val initials = extractInitials(name)

        // Palette gradients per category
        val (startColor, endColor) = when (category) {
            Category.TRANSACTIONS -> 0xFF10B981.toInt() to 0xFF047857.toInt() // Lush Emerald
            Category.PROMOTIONS -> 0xFFF59E0B.toInt() to 0xFFD97706.toInt()  // Vibrant Amber
            Category.SPAM -> 0xFFEF4444.toInt() to 0xFFB91C1C.toInt()        // Crimson Red
            Category.REVIEW -> 0xFF6B7280.toInt() to 0xFF374151.toInt()      // Slate
            Category.INBOX, Category.BLOCKED -> {
                // Sender-derived consistent pastel/vibrant gradient
                val hash = Math.abs(address.hashCode())
                val palette = USER_GRADIENTS[hash % USER_GRADIENTS.size]
                palette.first to palette.second
            }
        }

        // Draw Circle Background with Smooth Linear Gradient
        val radius = px / 2.0f
        val gradient = LinearGradient(
            0f, 0f, px.toFloat(), px.toFloat(),
            startColor, endColor,
            Shader.TileMode.CLAMP
        )
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
            style = Paint.Style.FILL
        }
        canvas.drawCircle(radius, radius, radius, bgPaint)

        // Draw Specular Inner Edge Highlight
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * density
            color = Color.argb(45, 255, 255, 255)
        }
        canvas.drawCircle(radius, radius, radius - strokePaint.strokeWidth / 2f, strokePaint)

        // Draw Initials Text in Center
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = px * (if (initials.length > 1) 0.38f else 0.46f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(4.0f * density, 0f, 2.0f * density, Color.argb(60, 0, 0, 0))
        }

        val textBounds = Rect()
        textPaint.getTextBounds(initials, 0, initials.length, textBounds)
        val yOffset = radius + (textBounds.height() / 2.0f) - textBounds.bottom
        canvas.drawText(initials, radius, yOffset, textPaint)

        return bitmap
    }

    private fun extractInitials(name: String): String {
        val clean = name.trim().replace(Regex("[^A-Za-z0-9\\s-]"), "")
        if (clean.isBlank()) return "?"

        // Bank / Sender headers like "AX-AIRBNK" or "HDFCBK" or "VK-ZOMATO"
        if (clean.contains("-")) {
            val parts = clean.split("-").filter { it.isNotBlank() }
            if (parts.size >= 2) {
                val prefix = parts[0].take(1)
                val body = parts[1].take(1)
                return (prefix + body).uppercase()
            }
        }

        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
            clean.length >= 2 && clean.all { it.isLetter() && it.isUpperCase() } -> clean.take(2)
            else -> clean.take(1).uppercase()
        }
    }

    private val USER_GRADIENTS = listOf(
        0xFF10B981.toInt() to 0xFF059669.toInt(), // Emerald
        0xFF3B82F6.toInt() to 0xFF1D4ED8.toInt(), // Sapphire
        0xFF8B5CF6.toInt() to 0xFF6D28D9.toInt(), // Violet
        0xFFEC4899.toInt() to 0xFFBE185D.toInt(), // Rose
        0xFF06B6D4.toInt() to 0xFF0E7490.toInt(), // Cyan
        0xFFF97316.toInt() to 0xFFC2410C.toInt(), // Coral Orange
        0xFF6366F1.toInt() to 0xFF4338CA.toInt(), // Indigo
    )
}
