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

    /** "default" = the theme's sentBubbleContainer role (dynamic color). */
    val bubblePresets = listOf(
        BubblePreset("default", "Dynamic", Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified),
        // The classic messenger green (Phase 5 §2): shipped as a preset, never
        // the default — refs-adjacent hues, AA-paired in both themes.
        BubblePreset("green", "Green", Color(0xFFDCF7C5), Color(0xFF12351A), Color(0xFF1F5423), Color(0xFFDCF7C5)),
        BubblePreset("ocean", "Ocean", Color(0xFF00629E), Color.White, Color(0xFF99CBFF), Color(0xFF003355)),
        BubblePreset("forest", "Forest", Color(0xFF1B6C31), Color.White, Color(0xFF88D896), Color(0xFF00391A)),
        BubblePreset("sunset", "Sunset", Color(0xFFA83C22), Color.White, Color(0xFFFFB59B), Color(0xFF5B1A00)),
        BubblePreset("berry", "Berry", Color(0xFF7B3E8F), Color.White, Color(0xFFE9B3F0), Color(0xFF3E0E4F)),
        BubblePreset("slate", "Slate", Color(0xFF4C5C6C), Color.White, Color(0xFFB4C4D4), Color(0xFF1E2A36)),
        BubblePreset("amber", "Amber", Color(0xFF7A5900), Color.White, Color(0xFFEFC047), Color(0xFF3F2E00)),
    )

    @Immutable
    data class WallpaperPreset(
        val id: String,
        val name: String,
        val light: List<Color>,
        val dark: List<Color>,
    )

    /** Subtle vertical gradients — bubbles must stay legible on top. */
    val wallpaperPresets = listOf(
        WallpaperPreset("none", "None", emptyList(), emptyList()),
        WallpaperPreset(
            "mist", "Mist",
            listOf(Color(0xFFE3F0FA), Color(0xFFFAF7F2)),
            listOf(Color(0xFF12202C), Color(0xFF17151F)),
        ),
        WallpaperPreset(
            "dawn", "Dawn",
            listOf(Color(0xFFFFF3E0), Color(0xFFFDE7EF)),
            listOf(Color(0xFF2A1E2E), Color(0xFF141016)),
        ),
        WallpaperPreset(
            "meadow", "Meadow",
            listOf(Color(0xFFE6F4E7), Color(0xFFF3F9EC)),
            listOf(Color(0xFF13241B), Color(0xFF101613)),
        ),
        WallpaperPreset(
            "sand", "Sand",
            listOf(Color(0xFFFFF8E1), Color(0xFFFBEDE7)),
            listOf(Color(0xFF262014), Color(0xFF161310)),
        ),
    )

    const val WALLPAPER_PHOTO = "photo"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(space: String, threadId: Long, prefix: String) = "${prefix}_${space}:${threadId}"

    fun bubbleId(ctx: Context, threadId: Long, space: String = Spaces.NORMAL): String =
        prefs(ctx).getString(key(space, threadId, "bubble"), "default") ?: "default"

    fun wallpaperId(ctx: Context, threadId: Long, space: String = Spaces.NORMAL): String =
        prefs(ctx).getString(key(space, threadId, "wallpaper"), "none") ?: "none"

    fun setBubble(ctx: Context, threadId: Long, id: String, space: String = Spaces.NORMAL) {
        prefs(ctx).edit().putString(key(space, threadId, "bubble"), id).apply()
    }

    fun setWallpaper(ctx: Context, threadId: Long, id: String, space: String = Spaces.NORMAL) {
        prefs(ctx).edit().putString(key(space, threadId, "wallpaper"), id).apply()
        if (id != WALLPAPER_PHOTO) photoFile(ctx, threadId, space).delete()
    }

    fun photoFile(ctx: Context, threadId: Long, space: String = Spaces.NORMAL): File =
        File(File(ctx.filesDir, "wallpapers").apply { mkdirs() }, "wp_${space}_${threadId}.jpg")

    /** Copies the picked image locally, then selects it. Returns false on failure. */
    fun importPhoto(ctx: Context, threadId: Long, uri: Uri, space: String = Spaces.NORMAL): Boolean = try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            photoFile(ctx, threadId, space).outputStream().use { input.copyTo(it) }
        } != null && run {
            prefs(ctx).edit().putString(key(space, threadId, "wallpaper"), WALLPAPER_PHOTO).apply()
            true
        }
    } catch (_: Exception) {
        false
    }

    /** Outgoing-bubble container/on pair for the active theme. */
    @Composable
    fun bubbleColors(id: String): Pair<Color, Color> {
        val preset = bubblePresets.firstOrNull { it.id == id && it.id != "default" }
            ?: return com.messages.designsystem.LocalSentBubble.current
                .let { it.container to it.onContainer }
        return if (LocalDarkTheme.current) preset.darkContainer to preset.darkOn
        else preset.lightContainer to preset.lightOn
    }

    /** Wallpaper brush for a gradient preset, or null (none / photo). */
    @Composable
    fun wallpaperBrush(id: String): Brush? {
        val preset = wallpaperPresets.firstOrNull { it.id == id && it.light.isNotEmpty() }
            ?: return null
        val colors = if (LocalDarkTheme.current) preset.dark else preset.light
        return Brush.verticalGradient(colors)
    }
}
