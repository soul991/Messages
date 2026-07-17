package com.personal.detectivedialer.ui.detail

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A small audio player for the call recording. Uses MediaPlayer with play/pause,
 * a scrubber, and 1x/1.5x/2x speed control (PlaybackParams, API 23+).
 */
@Composable
fun AudioPlayer(url: String, modifier: Modifier = Modifier) {
    // One MediaPlayer per URL: setDataSource can only be called once per
    // instance, so a URL change must create a fresh player (the old one is
    // released by the DisposableEffect below).
    val player = remember(url) { MediaPlayer() }
    var prepared by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    var position by remember(url) { mutableFloatStateOf(0f) }
    var duration by remember(url) { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(1f) }
    var error by remember(url) { mutableStateOf(false) }

    DisposableEffect(player) {
        runCatching {
            player.setDataSource(url)
            player.setOnPreparedListener {
                prepared = true
                duration = it.duration.toFloat().coerceAtLeast(1f)
            }
            player.setOnCompletionListener { playing = false; position = 0f }
            player.setOnErrorListener { _, _, _ -> error = true; true }
            player.prepareAsync()
        }.onFailure { error = true }

        onDispose { runCatching { player.release() } }
    }

    LaunchedEffect(playing, player) {
        while (playing && prepared) {
            position = runCatching { player.currentPosition.toFloat() }.getOrDefault(position)
            delay(250)
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recording", style = MaterialTheme.typography.titleSmall)
            if (error) {
                Text("Recording unavailable", style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = prepared,
                    onClick = {
                        if (playing) {
                            runCatching { player.pause() }
                            playing = false
                        } else {
                            runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }
                            runCatching { player.start() }.onSuccess { playing = true }
                        }
                    },
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = if (playing) "Pause" else "Play")
                }
                Slider(
                    value = position,
                    onValueChange = {
                        if (prepared) {
                            position = it
                            runCatching { player.seekTo(it.toInt()) }
                        }
                    },
                    valueRange = 0f..duration,
                    enabled = prepared,
                    modifier = Modifier.weight(1f),
                )
                Text(formatMs(position.toInt()), style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1f, 1.5f, 2f).forEach { s ->
                    FilterChip(
                        selected = speed == s,
                        onClick = {
                            speed = s
                            if (playing) runCatching { player.playbackParams = player.playbackParams.setSpeed(s) }
                        },
                        label = { Text("${s}x") },
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
