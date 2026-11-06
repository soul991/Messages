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
 * Custom vector icons for conversation swipe actions (Archive, Delete, Pin, Read, Mute).
 *
 * Each action provides an Outlined variant (for pre-commit drag) and a Filled variant
 * (triggered when crossing the commit threshold) with 2.0dp stroke weight, rounded caps,
 * and unified optical balance.
 */
object SwipeIcons {

    private fun parseNodes(pathData: String) = PathParser().parsePathString(pathData).toNodes()

    // -------------------------------------------------------------------------
    // 1. Archive: Clean storage box with lid and deposit down-arrow
    // -------------------------------------------------------------------------

    val ArchiveOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwipeIcons.ArchiveOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Box lid
            addPath(
                pathData = parseNodes("M4 4h16c0.8 0 1.5 0.7 1.5 1.5v2c0 0.8-0.7 1.5-1.5 1.5H4C3.2 9 2.5 8.3 2.5 7.5v-2C2.5 4.7 3.2 4 4 4z"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Box body
            addPath(
                pathData = parseNodes("M4.5 9v9.5c0 1.4 1.1 2.5 2.5 2.5h10c1.4 0 2.5-1.1 2.5-2.5V9"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Center down-arrow
            addPath(
                pathData = parseNodes("M12 11.5v5.5 M9.5 14.5L12 17l2.5-2.5"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }

    val ArchiveFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwipeIcons.ArchiveFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Solid lid
            addPath(
                pathData = parseNodes("M4 4h16c0.8 0 1.5 0.7 1.5 1.5v2c0 0.8-0.7 1.5-1.5 1.5H4C3.2 9 2.5 8.3 2.5 7.5v-2C2.5 4.7 3.2 4 4 4z"),
                fill = SolidColor(Color.White),
            )
            // Solid body with EvenOdd cutout for down arrow
            addPath(
                pathData = parseNodes("M4.5 9.5v9c0 1.4 1.1 2.5 2.5 2.5h10c1.4 0 2.5-1.1 2.5-2.5v-9H4.5z M11 11.5h2v3.5h2l-3 3-3-3h2v-3.5z"),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.White),
            )
        }.build()
    }

    // -------------------------------------------------------------------------
    // 2. Delete: Streamlined trash can with lid, handle, and vertical ribs
    // -------------------------------------------------------------------------

    val DeleteOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwipeIcons.DeleteOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Lid handle
            addPath(
                pathData = parseNodes("M9.5 4.5c0-0.6 0.5-1 1-1h3c0.6 0 1 0.4 1 1v1h-5v-1z"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Lid horizontal bar
            addPath(
                pathData = parseNodes("M4 5.5h16"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            )
            // Can outline
            addPath(
                pathData = parseNodes("M5.5 7.5l1.1 11.2c0.1 1.2 1.1 2.1 2.3 2.1h6.2c1.2 0 2.2-0.9 2.3-2.1l1.1-11.2"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Ribs
            addPath(
                pathData = parseNodes("M10 10.5v6 M14 10.5v6"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            )
        }.build()
    }

    val DeleteFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwipeIcons.DeleteFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Lid handle
            addPath(
                pathData = parseNodes("M9.5 4.5c0-0.6 0.5-1 1-1h3c0.6 0 1 0.4 1 1v1h-5v-1z"),
                fill = SolidColor(Color.White),
            )
            // Lid bar
            addPath(
                pathData = parseNodes("M3.5 5.5h17c0.6 0 1 0.4 1 1s-0.4 1-1 1h-17c-0.6 0-1-0.4-1-1s0.4-1 1-1z"),
                fill = SolidColor(Color.White),
            )
            // Solid can body with EvenOdd cutout for ribs
            addPath(
                pathData = parseNodes("M5.5 8l1.1 10.8c0.1 1.2 1.1 2.2 2.3 2.2h6.2c1.2 0 2.2-1 2.3-2.2L18.5 8H5.5z M9.5 11h1.5v5.5H9.5V11z M13 11h1.5v5.5H13V11z"),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.White),
            )
        }.build()
    }

    // -------------------------------------------------------------------------
    // 3. Pin: Diagonal push pin
    // -------------------------------------------------------------------------

    val PinOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwipeIcons.PinOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = parseNodes("M16 12V4H8v8l-2 2v2h5v5l1 1 1-1v-5h5v-2l-2-2z"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }

    val PinFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwipeIcons.PinFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = parseNodes("M16 12V4H8v8l-2 2v2h5v5l1 1 1-1v-5h5v-2l-2-2z"),
                fill = SolidColor(Color.White),
            )
        }.build()
    }

    // -------------------------------------------------------------------------
    // 4. Read: Mark read checkmark / message
    // -------------------------------------------------------------------------

    val ReadOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwipeIcons.ReadOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = parseNodes("M3.5 12.5l4 4 10-10 M8 12.5l3.5 3.5 9-9"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }

    val ReadFilled: ImageVector by lazy {
        ReadOutlined
    }

    // -------------------------------------------------------------------------
    // 5. Mute: Bell with slash
    // -------------------------------------------------------------------------

    val MuteOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwipeIcons.MuteOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = parseNodes("M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2zm-2 1H8v-6c0-2.48 1.51-4.5 4-4.5s4 2.02 4 4.5v6z"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            addPath(
                pathData = parseNodes("M4 4l16 16"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
            )
        }.build()
    }

    val MuteFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwipeIcons.MuteFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = parseNodes("M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"),
                fill = SolidColor(Color.White),
            )
            addPath(
                pathData = parseNodes("M4 4l16 16"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
            )
        }.build()
    }
}
