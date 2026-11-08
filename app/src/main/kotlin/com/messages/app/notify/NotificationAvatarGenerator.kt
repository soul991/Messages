package com.messages.app.notify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import com.messages.protection.Category

/**
 * Generates neutral circular letter avatar bitmaps for notifications
 * (matching the chat list style) when a contact has no photo or when the message
 * is from a business/bank.
 */
object NotificationAvatarGenerator {

    private const val SIZE = 128 // dp-scaled density target (standard large icon)

    /**
     * Renders the app's launcher icon into a high-res [Bitmap]
     * for system notifications where the app icon itself is appropriate.
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
        category: Category? = null,
    ): IconCompat {
        val bitmap = generateBitmap(context, address, displayName, category)
        return IconCompat.createWithBitmap(bitmap)
    }

    /**
     * Generates a neutral circular letter avatar matching the chat list design
     * (neutral surface #161618, subtle border #28282C, neutral letter #8E8E93).
     */
    fun generateBitmap(
        context: Context,
        address: String,
        displayName: String?,
        category: Category? = null,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (SIZE * (density / 2.0f)).toInt().coerceIn(128, 256)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val name = displayName?.takeIf { it.isNotBlank() } ?: address
        val initial = extractInitial(name)

        val bgColor = 0xFF161618.toInt()
        val borderColor = 0xFF28282C.toInt()
        val textColor = 0xFF8E8E93.toInt()

        val radius = px / 2.0f

        // Draw solid neutral circular background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(radius, radius, radius, bgPaint)

        // Draw 1px equivalent subtle neutral hairline border
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            color = borderColor
        }
        canvas.drawCircle(radius, radius, radius - strokePaint.strokeWidth / 2f, strokePaint)

        // Draw Initial Letter in Center
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = px * 0.44f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textBounds = Rect()
        textPaint.getTextBounds(initial, 0, initial.length, textBounds)
        val yOffset = radius + (textBounds.height() / 2.0f) - textBounds.bottom
        canvas.drawText(initial, radius, yOffset, textPaint)

        return bitmap
    }

    private fun extractInitial(name: String): String {
        val clean = name.trim()
        val firstLetter = clean.firstOrNull { it.isLetterOrDigit() }
        return firstLetter?.uppercaseChar()?.toString() ?: "?"
    }
}
