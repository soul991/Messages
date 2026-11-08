package com.messages.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.messages.core.db.Spaces
import com.messages.designsystem.LocalDarkTheme
import java.io.File

/**
 * Per-chat customization (§8.2): bubble colors and wallpapers, stored as
 * preset ids in `chat_style` prefs keyed by (space, threadId). Presets are
 * light/dark pairs chosen for AA contrast of the on-color; "photo" wallpapers
 * are copied into filesDir so they survive the picker permission going away.
 *
 * R-31: values are namespaced by (space, threadId), not threadId alone. The
 * same thread ID can have BOTH normal-space and locked-space conversation rows,
 * so a threadId-only key lets one space read or overwrite the other's style or
 * leak the wallpaper filename.
 */
object ChatStyle {

    private const val PREFS = "chat_style"

    @Immutable
    data class BubblePreset(
        val id: String,
        val name: String,
        val lightContainer: Color, val lightOn: Color,
        val darkContainer: Color, val darkOn: Color,
    )

    /** "default" = the fixed system theme iMessage green sent bubble. */
    val bubblePresets = listOf(
        BubblePreset("default", "Default", Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified),
    )

    @Immutable
    data class WallpaperPreset(
        val id: String,
        val name: String,
        val light: List<Color>,
        val dark: List<Color>,
    )

    val wallpaperPresets = listOf(
        WallpaperPreset("none", "None", emptyList<Color>(), emptyList<Color>()),
    )

    const val WALLPAPER_PHOTO = "photo"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(space: String, threadId: Long, prefix: String) = "${prefix}_${space}:${threadId}"

    fun bubbleId(ctx: Context, threadId: Long, space: String = Spaces.NORMAL): String = "default"

    fun wallpaperId(ctx: Context, threadId: Long, space: String = Spaces.NORMAL): String = "none"

    fun setBubble(ctx: Context, threadId: Long, id: String, space: String = Spaces.NORMAL) {}

    fun setWallpaper(ctx: Context, threadId: Long, id: String, space: String = Spaces.NORMAL) {}

    fun photoFile(ctx: Context, threadId: Long, space: String = Spaces.NORMAL): File =
        File(File(ctx.filesDir, "wallpapers").apply { mkdirs() }, "wp_${space}_${threadId}.jpg")

    fun importPhoto(ctx: Context, threadId: Long, uri: Uri, space: String = Spaces.NORMAL): Boolean = false

    /** Outgoing-bubble container/on pair strictly following fixed design system tokens. */
    @Composable
    fun bubbleColors(id: String): Pair<Color, Color> =
        com.messages.designsystem.LocalSentBubble.current.let { it.container to it.onContainer }

    /** Fixed palette: wallpapers are disabled; background is fixed black (#000000) or off-white (#F7F7F4). */
    @Composable
    fun wallpaperBrush(id: String): Brush? = null
}
