package com.personal.detectivedialer.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.detectivedialer.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.Settings())

    fun update(transform: (SettingsRepository.Settings) -> SettingsRepository.Settings) {
        viewModelScope.launch { repository.update(transform) }
    }

    /**
     * Copy the picked image into internal storage so it survives across reboots
     * (the content URI grant does not), then persist that internal path.
     */
    fun setIncomingWallpaper(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(context.filesDir, WALLPAPER_FILE)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file.absolutePath
                }.getOrNull()
            } ?: return@launch
            // Bust any bitmap cache keyed on the path by appending a version tag.
            repository.update { it.copy(incomingWallpaperPath = "$path?v=${file2Version(path)}") }
        }
    }

    fun clearIncomingWallpaper() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { File(context.filesDir, WALLPAPER_FILE).delete() }
            }
            repository.update { it.copy(incomingWallpaperPath = "") }
        }
    }

    fun setIncomingScrim(percent: Int) {
        update { it.copy(incomingScrimPercent = percent.coerceIn(0, 100)) }
    }

    private fun file2Version(path: String): Long =
        runCatching { File(path.substringBefore('?')).lastModified() }.getOrDefault(0L)

    private companion object {
        const val WALLPAPER_FILE = "incoming_wallpaper.jpg"
    }
}
