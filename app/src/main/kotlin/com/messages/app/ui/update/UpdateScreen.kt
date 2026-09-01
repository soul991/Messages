package com.messages.app.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messages.app.BuildConfig
import com.messages.app.R
import com.messages.app.update.AppUpdateManager
import com.messages.app.update.UpdateCheck
import com.messages.designsystem.GlassDepth
import com.messages.designsystem.LiquidGlassSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Update check screen — matches the reference design:
 *
 * 1. **Check phase**: Centered pulsing/morphing polygon animation with
 *    "Check for update" button at the bottom.
 * 2. **Update available**: Changelog display, release date, APK size,
 *    live download progress bar with percentage pill, and Install button.
 * 3. **Up to date**: Success animation with version confirmation.
 * 4. **Errors**: Offline / rate-limited / malformed feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var screenState by remember { mutableStateOf<UpdateScreenState>(UpdateScreenState.Idle) }
    var downloadState by remember { mutableStateOf<AppUpdateManager.DownloadState>(AppUpdateManager.DownloadState.Idle) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (screenState) {
                            is UpdateScreenState.Available ->
                                stringResource(
                                    R.string.update_new_update,
                                    (screenState as UpdateScreenState.Available).info.version,
                                )
                            else -> stringResource(R.string.update_check_title)
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = screenState,
                transitionSpec = {
                    (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.92f))
                        .togetherWith(fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.92f))
                },
                label = "update_state",
            ) { state ->
                when (state) {
                    is UpdateScreenState.Idle -> {
                        IdleCheckSection(
                            onCheck = {
                                screenState = UpdateScreenState.Checking
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { UpdateCheck.check() }
                                    screenState = when (result) {
                                        is UpdateCheck.UpdateResult.Available -> {
                                            val info = result.releaseInfo
                                            if (info != null) {
                                                UpdateScreenState.Available(info)
                                            } else {
                                                UpdateScreenState.Available(
                                                    UpdateCheck.ReleaseInfo(
                                                        version = result.version,
                                                        tagName = "v${result.version}",
                                                        publishedAt = "",
                                                        changelog = "",
                                                        apkUrl = result.pageUrl,
                                                        apkSizeBytes = 0L,
                                                        htmlUrl = result.pageUrl,
                                                    ),
                                                )
                                            }
                                        }
                                        is UpdateCheck.UpdateResult.UpToDate ->
                                            UpdateScreenState.UpToDate(result.current)
                                        is UpdateCheck.UpdateResult.Offline ->
                                            UpdateScreenState.Error(R.string.update_offline)
                                        is UpdateCheck.UpdateResult.RateLimited ->
                                            UpdateScreenState.Error(R.string.update_rate_limited)
                                        is UpdateCheck.UpdateResult.Malformed ->
                                            UpdateScreenState.Error(R.string.update_malformed)
                                    }
                                }
                            },
                        )
                    }
                    is UpdateScreenState.Checking -> {
                        CheckingSection()
                    }
                    is UpdateScreenState.Available -> {
                        AvailableSection(
                            info = state.info,
                            downloadState = downloadState,
                            onDownload = {
                                scope.launch {
                                    AppUpdateManager.download(
                                        context = context.applicationContext,
                                        url = state.info.apkUrl,
                                        version = state.info.version,
                                    ).collect { ds ->
                                        downloadState = ds
                                        if (ds is AppUpdateManager.DownloadState.Complete) {
                                            downloadedApk = ds.apkFile
                                        }
                                    }
                                }
                            },
                            onInstall = {
                                downloadedApk?.let { apk ->
                                    AppUpdateManager.installApk(context, apk)
                                }
                            },
                            onLater = onBack,
                        )
                    }
                    is UpdateScreenState.UpToDate -> {
                        UpToDateSection(version = state.version)
                    }
                    is UpdateScreenState.Error -> {
                        ErrorSection(
                            messageRes = state.messageRes,
                            onRetry = { screenState = UpdateScreenState.Idle },
                        )
                    }
                }
            }
        }
    }
}

private sealed class UpdateScreenState {
    object Idle : UpdateScreenState()
    object Checking : UpdateScreenState()
    data class Available(val info: UpdateCheck.ReleaseInfo) : UpdateScreenState()
    data class UpToDate(val version: String) : UpdateScreenState()
    data class Error(@androidx.annotation.StringRes val messageRes: Int) : UpdateScreenState()
}

// ============================================================================
// Idle — Pulsing polygon + "Check for update" button
// ============================================================================

@Composable
private fun IdleCheckSection(onCheck: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(60.dp))
        PulsingPolygon(
            modifier = Modifier.size(180.dp),
            pulsing = false,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.update_check_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.update_version_subtitle, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onCheck,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                stringResource(R.string.update_check_button),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ============================================================================
// Checking — Spinning + pulsing polygon
// ============================================================================

@Composable
private fun CheckingSection() {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(60.dp))
        PulsingPolygon(
            modifier = Modifier.size(180.dp),
            pulsing = true,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.update_checking_anim),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(80.dp))
    }
}

// ============================================================================
// Available — Changelog, download progress, install button
// ============================================================================

@Composable
private fun AvailableSection(
    info: UpdateCheck.ReleaseInfo,
    downloadState: AppUpdateManager.DownloadState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onLater: () -> Unit,
) {
    val isDownloading = downloadState is AppUpdateManager.DownloadState.Downloading
    val isComplete = downloadState is AppUpdateManager.DownloadState.Complete
    val isFailed = downloadState is AppUpdateManager.DownloadState.Failed

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        // Release metadata
        LiquidGlassSurface(
            shape = RoundedCornerShape(20.dp),
            depth = GlassDepth.LOW,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Date & size row
                val dateStr = formatPublishedDate(info.publishedAt)
                if (dateStr.isNotEmpty() || info.apkSizeBytes > 0) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (dateStr.isNotEmpty()) {
                            Text(
                                stringResource(R.string.update_released_on, dateStr),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (info.apkSizeBytes > 0) {
                            Text(
                                stringResource(
                                    R.string.update_size,
                                    AppUpdateManager.formatBytes(info.apkSizeBytes),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Changelog
                if (info.changelog.isNotBlank()) {
                    Text(
                        stringResource(R.string.update_changelog_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        info.changelog,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        lineHeight = 22.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Download progress bar
        AnimatedVisibility(
            visible = isDownloading,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            val progress = (downloadState as? AppUpdateManager.DownloadState.Downloading)?.progress ?: 0
            val animatedProgress by animateFloatAsState(
                targetValue = progress / 100f,
                animationSpec = tween(300),
                label = "download_progress",
            )
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Progress percentage pill
                LiquidGlassSurface(
                    shape = RoundedCornerShape(12.dp),
                    depth = GlassDepth.LOW,
                ) {
                    Box(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.update_downloading, progress),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        // Failed state
        AnimatedVisibility(visible = isFailed) {
            val errorMsg = (downloadState as? AppUpdateManager.DownloadState.Failed)?.error ?: ""
            LiquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                depth = GlassDepth.LOW,
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.update_download_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Action buttons
        if (isComplete) {
            // Install button
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onLater,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        stringResource(R.string.update_later),
                        fontWeight = FontWeight.Medium,
                    )
                }
                Button(
                    onClick = onInstall,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        stringResource(R.string.update_install),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else if (!isDownloading) {
            // Download button or retry
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onLater,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        stringResource(R.string.update_later),
                        fontWeight = FontWeight.Medium,
                    )
                }
                Button(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.update_action_download),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else {
            // During download: only "Later" button
            OutlinedButton(
                onClick = onLater,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    stringResource(R.string.update_later),
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ============================================================================
// Up-to-date — success icon + message
// ============================================================================

@Composable
private fun UpToDateSection(version: String) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(80.dp))
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.update_up_to_date_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.update_up_to_date_body, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(80.dp))
    }
}

// ============================================================================
// Error section
// ============================================================================

@Composable
private fun ErrorSection(@androidx.annotation.StringRes messageRes: Int, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(80.dp))
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(messageRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.about_action_check))
        }
        Spacer(Modifier.height(80.dp))
    }
}

// ============================================================================
// Pulsing morphing polygon — Canvas-drawn animated hexagon/pentagon
// ============================================================================

@Composable
private fun PulsingPolygon(
    modifier: Modifier = Modifier,
    pulsing: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "polygon")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(8000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "polygon_rotation",
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = if (pulsing) 0.85f else 1f,
        targetValue = if (pulsing) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "polygon_scale",
    )
    val morphFactor by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "polygon_morph",
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = (size.minDimension / 2f) * scale * 0.8f

        // Morph between hexagon (6) and pentagon (5) using vertex interpolation
        val sides = 6
        val path = Path()

        rotate(rotation, Offset(cx, cy)) {
            for (i in 0 until sides) {
                val angle = (2 * PI * i / sides - PI / 2).toFloat()
                // Subtle radius variation for organic feel
                val vertexRadius = radius * (1f + 0.08f * sin(morphFactor * PI.toFloat() + i * 1.2f))
                val px = cx + vertexRadius * cos(angle)
                val py = cy + vertexRadius * sin(angle)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()

            // Gradient fill
            drawPath(
                path,
                brush = Brush.linearGradient(
                    colors = listOf(primary.copy(alpha = 0.3f), secondary.copy(alpha = 0.15f)),
                    start = Offset(cx - radius, cy - radius),
                    end = Offset(cx + radius, cy + radius),
                ),
                style = Fill,
            )
            // Stroke
            drawPath(
                path,
                brush = Brush.linearGradient(
                    colors = listOf(primary, secondary),
                    start = Offset(cx - radius, cy - radius),
                    end = Offset(cx + radius, cy + radius),
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Center dot
        drawCircle(
            color = primary,
            radius = 8.dp.toPx() * scale,
            center = Offset(cx, cy),
        )
    }
}

// ============================================================================
// Date formatter
// ============================================================================

private fun formatPublishedDate(isoDate: String): String {
    if (isoDate.isBlank()) return ""
    return try {
        val epochMs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.time.Instant.parse(isoDate).toEpochMilli()
        } else {
            0L
        }
        if (epochMs > 0L) {
            com.messages.app.ui.common.AppDateFormat.dayMonthYear(epochMs)
        } else {
            isoDate.takeWhile { it != 'T' }
        }
    } catch (_: Exception) {
        isoDate.takeWhile { it != 'T' }
    }
}
