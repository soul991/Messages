package com.messages.app.ui.update

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.messages.app.BuildConfig
import com.messages.app.R
import com.messages.app.update.AppUpdateManager
import com.messages.app.update.UpdateCheck
import com.messages.designsystem.GlassDepth
import com.messages.designsystem.LiquidGlassSurface

private const val COMMITS_URL = "https://github.com/soul991/Messages/commits/main"

/**
 * Update Settings Screen — matching Image 2 reference:
 *
 * 1. **System update row**: Leads to [UpdateScreen] for check & download.
 * 2. **Auto-check toggle**: Periodically checks for updates on unmetered networks.
 * 3. **Update Notifications toggle**: Posts notification when a new version is released.
 * 4. **Clear downloaded updates**: Shows cached APK count & size; tapping deletes them.
 * 5. **Commits link**: Opens GitHub commit history in browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSettingsScreen(
    onBack: () -> Unit,
    onNavigateToCheck: () -> Unit,
) {
    val context = LocalContext.current

    var autoCheck by remember { mutableStateOf(UpdateCheck.autoCheckEnabled(context)) }
    var notifyUpdates by remember { mutableStateOf(UpdateCheck.notifyEnabled(context)) }

    var cachedCount by remember { mutableIntStateOf(AppUpdateManager.listCachedApks(context).size) }
    var cachedBytes by remember { mutableLongStateOf(AppUpdateManager.cachedSizeBytes(context)) }

    val openUrl: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.update_settings_title),
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
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // 1. System Update Card
            LiquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                depth = GlassDepth.LOW,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    UpdateSettingsNavRow(
                        icon = Icons.Outlined.SystemUpdate,
                        title = stringResource(R.string.update_system_update),
                        subtitle = stringResource(R.string.update_version_subtitle, BuildConfig.VERSION_NAME),
                        onClick = onNavigateToCheck,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 2. Automated Preferences Card
            LiquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                depth = GlassDepth.LOW,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // Auto-check toggle
                    UpdateSettingsSwitchRow(
                        icon = Icons.Outlined.Sync,
                        title = stringResource(R.string.update_auto_check),
                        subtitle = stringResource(R.string.update_auto_check_subtitle),
                        checked = autoCheck,
                        onCheckedChange = { on ->
                            autoCheck = on
                            UpdateCheck.setAutoCheckEnabled(context, on)
                        },
                    )

                    Spacer(Modifier.height(16.dp))

                    // Notifications toggle
                    UpdateSettingsSwitchRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = stringResource(R.string.update_notifications),
                        subtitle = stringResource(R.string.update_notifications_subtitle),
                        checked = notifyUpdates,
                        onCheckedChange = { on ->
                            notifyUpdates = on
                            UpdateCheck.setNotifyEnabled(context, on)
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 3. Maintenance & Storage Card
            LiquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                depth = GlassDepth.LOW,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val cacheSubtitle = if (cachedCount > 0) {
                        stringResource(
                            R.string.update_clear_downloads_subtitle,
                            cachedCount,
                            AppUpdateManager.formatBytes(cachedBytes),
                        )
                    } else {
                        stringResource(R.string.update_clear_downloads_empty)
                    }

                    UpdateSettingsNavRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = stringResource(R.string.update_clear_downloads),
                        subtitle = cacheSubtitle,
                        onClick = {
                            if (cachedCount > 0) {
                                val cleared = AppUpdateManager.clearCache(context)
                                cachedCount = AppUpdateManager.listCachedApks(context).size
                                cachedBytes = AppUpdateManager.cachedSizeBytes(context)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.update_cleared_toast, cleared),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.update_no_cached_toast),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 4. Commits Card
            LiquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                depth = GlassDepth.LOW,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    UpdateSettingsNavRow(
                        icon = Icons.Outlined.Commit,
                        title = stringResource(R.string.update_commits),
                        subtitle = stringResource(R.string.update_commits_subtitle),
                        onClick = { openUrl(COMMITS_URL) },
                        external = true,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun UpdateSettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    external: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (external) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun UpdateSettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
