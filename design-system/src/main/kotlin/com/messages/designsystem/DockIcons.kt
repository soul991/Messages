package com.messages.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Bespoke, Telegram-styled category dock icons for Messages.
 *
 * Each category (Personal, Bank, Offers, Review, Spam) is redesigned
 * to match Telegram's visual metaphor standards with matching Outlined
 * and Filled state pairs.
 */
object DockIcons {

    private fun parseNodes(pathData: String) = PathParser().parsePathString(pathData).toNodes()

    // -------------------------------------------------------------------------
    // 1. Personal (INBOX): Telegram-style User Profile Avatar in Circle
    // -------------------------------------------------------------------------

    val PersonalOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "DockIcons.PersonalOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Circle boundary
            addPath(
                pathData = parseNodes("M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2Z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Avatar head
            addPath(
                pathData = parseNodes("M12 6.5C13.38 6.5 14.5 7.62 14.5 9C14.5 10.38 13.38 11.5 12 11.5C10.62 11.5 9.5 10.38 9.5 9C9.5 7.62 10.62 6.5 12 6.5Z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Avatar shoulders
            addPath(
                pathData = parseNodes("M6.8 18C7.6 15.2 9.6 13.8 12 13.8C14.4 13.8 16.4 15.2 17.2 18"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }

    val PersonalFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "DockIcons.PersonalFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = parseNodes("M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2ZM12 6C13.66 6 15 7.34 15 9C15 10.66 13.66 12 12 12C10.34 12 9 10.66 9 9C9 7.34 10.34 6 12 6ZM6.3 18.2C7.3 15 9.4 13.5 12 13.5C14.6 13.5 16.7 15 17.7 18.2C16.1 19.6 14.2 20.5 12 20.5C9.8 20.5 7.9 19.6 6.3 18.2Z"),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.White),
        ).build()
    }

    // -------------------------------------------------------------------------
    // 2. Bank (TRANSACTIONS): Payment Card with Magnetic Stripe and EMV Chip
    // -------------------------------------------------------------------------

    val BankOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "DockIcons.BankOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Card outline
            addPath(
                pathData = parseNodes("M5 4.5H19C20.38 4.5 21.5 5.62 21.5 7V17C21.5 18.38 20.38 19.5 19 19.5H5C3.62 19.5 2.5 18.38 2.5 17V7C2.5 5.62 3.62 4.5 5 4.5Z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Magnetic stripe
            addPath(
                pathData = parseNodes("M2.5 8.5H21.5"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // EMV chip
            addPath(
                pathData = parseNodes("M6.3 12.5H8.7C9.14 12.5 9.5 12.86 9.5 13.3V14.7C9.5 15.14 9.14 15.5 8.7 15.5H6.3C5.86 15.5 5.5 15.14 5.5 14.7V13.3C5.5 12.86 5.86 12.5 6.3 12.5Z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.2f,
            )
        }.build()
    }

    val BankFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "DockIcons.BankFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = parseNodes("M2.5 7C2.5 5.62 3.62 4.5 5 4.5H19C20.38 4.5 21.5 5.62 21.5 7V8.5H2.5V7ZM2.5 10.5V17C2.5 18.38 3.62 19.5 5 19.5H19C20.38 19.5 21.5 18.38 21.5 17V10.5H2.5ZM5.5 13C5.5 12.45 5.95 12 6.5 12H8.5C9.05 12 9.5 12.45 9.5 13V15C9.5 15.55 9.05 16 8.5 16H6.5C5.95 16 5.5 15.55 5.5 15V13Z"),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.White),
        ).build()
    }

    // -------------------------------------------------------------------------
    // 3. Offers (PROMOTIONS): Price / Discount Tag with Eyelet and Percent Slash
    // -------------------------------------------------------------------------

    val OffersOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "DockIcons.OffersOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Price tag outline
            addPath(
                pathData = parseNodes("M20.59 13.41L13.42 20.58C13.05 20.95 12.54 21.16 12 21.16C11.46 21.16 10.95 20.95 10.58 20.58L3.41 13.41C3.04 13.04 2.83 12.53 2.83 12V4C2.83 2.9 3.73 2 4.83 2H12.83C13.36 2 13.87 2.21 14.24 2.59L20.59 8.94C21.37 9.72 21.37 10.98 20.59 11.76L20.59 13.41Z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Tag eyelet hole
            addPath(
                pathData = parseNodes("M7.5 5C8.33 5 9 5.67 9 6.5C9 7.33 8.33 8 7.5 8C6.67 8 6 7.33 6 6.5C6 5.67 6.67 5 7.5 5Z"),
                fill = SolidColor(Color.White),
            )
            // Percent slash and dots
            addPath(
                pathData = parseNodes("M11 15L15 11M11.5 11.5H11.51M14.5 14.5H14.51"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
            )
        }.build()
    }

    val OffersFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "DockIcons.OffersFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = parseNodes("M12.83 2H4.83C3.73 2 2.83 2.9 2.83 4V12C2.83 12.53 3.04 13.04 3.41 13.41L10.58 20.58C11.36 21.36 12.64 21.36 13.42 20.58L20.59 13.41C21.37 12.63 21.37 11.35 20.59 10.57L14.24 2.59C13.87 2.21 13.36 2 12.83 2ZM7.5 7.5C8.05 7.5 8.5 7.05 8.5 6.5C8.5 5.95 8.05 5.5 7.5 5.5C6.95 5.5 6.5 5.95 6.5 6.5C6.5 7.05 6.95 7.5 7.5 7.5ZM11.3 14.7C11.1 14.5 11.1 14.2 11.3 14L14 11.3C14.2 11.1 14.5 11.1 14.7 11.3C14.9 11.5 14.9 11.8 14.7 12L12 14.7C11.8 14.9 11.5 14.9 11.3 14.7Z"),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.White),
        ).build()
    }

    // -------------------------------------------------------------------------
    // 4. Review (REVIEW): Attention / Inspection Eye Metaphor
    // -------------------------------------------------------------------------

    val ReviewOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "DockIcons.ReviewOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Eye contour
            addPath(
                pathData = parseNodes("M2 12C3.8 7.5 7.6 4.5 12 4.5C16.4 4.5 20.2 7.5 22 12C20.2 16.5 16.4 19.5 12 19.5C7.6 19.5 3.8 16.5 2 12Z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Pupil circle
            addPath(
                pathData = parseNodes("M12 9C13.66 9 15 10.34 15 12C15 13.66 13.66 15 12 15C10.34 15 9 13.66 9 12C9 10.34 10.34 9 12 9Z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }

    val ReviewFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "DockIcons.ReviewFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = parseNodes("M2 12C3.8 7.5 7.6 4.5 12 4.5C16.4 4.5 20.2 7.5 22 12C20.2 16.5 16.4 19.5 12 19.5C7.6 19.5 3.8 16.5 2 12ZM12 8.5C10.07 8.5 8.5 10.07 8.5 12C8.5 13.93 10.07 15.5 12 15.5C13.93 15.5 15.5 13.93 15.5 12C15.5 10.07 13.93 8.5 12 8.5ZM12 10.5C12.83 10.5 13.5 11.17 13.5 12C13.5 12.83 12.83 13.5 12 13.5C11.17 13.5 10.5 12.83 10.5 12C10.5 11.17 11.17 10.5 12 10.5Z"),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.White),
        ).build()
    }

    // -------------------------------------------------------------------------
    // 5. Spam (SPAM): Stop / Alert Octagon with Exclamation Mark
    // -------------------------------------------------------------------------

    val SpamOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "DockIcons.SpamOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Octagon boundary
            addPath(
                pathData = parseNodes("M8.2 2.5H15.8L21.5 8.2V15.8L15.8 21.5H8.2L2.5 15.8V8.2L8.2 2.5Z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Exclamation stem
            addPath(
                pathData = parseNodes("M12 7V13"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Exclamation dot
            addPath(
                pathData = parseNodes("M12 16C12.55 16 13 16.45 13 17C13 17.55 12.55 18 12 18C11.45 18 11 17.55 11 17C11 16.45 11.45 16 12 16Z"),
                fill = SolidColor(Color.White),
            )
        }.build()
    }

    val SpamFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "DockIcons.SpamFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = parseNodes("M8.2 2.5H15.8L21.5 8.2V15.8L15.8 21.5H8.2L2.5 15.8V8.2L8.2 2.5ZM11 7H13V13.5H11V7ZM12 15.8C12.66 15.8 13.2 16.34 13.2 17C13.2 17.66 12.66 18.2 12 18.2C11.34 18.2 10.8 17.66 10.8 17C10.8 16.34 11.34 15.8 12 15.8Z"),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.White),
        ).build()
    }
}
