package com.messages.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

private const val REPO_URL = "https://github.com/soul991/Messages"
private const val RELEASES_URL = "https://github.com/soul991/Messages/releases"
private const val COMMITS_URL = "https://github.com/soul991/Messages/commits/main"

private enum class LegalDoc(val titleRes: Int, val bodyRes: Int) {
    PRIVACY(R.string.about_privacy_policy, R.string.about_privacy_policy_body),
    TERMS(R.string.about_terms, R.string.about_terms_body),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenUpdateSettings: () -> Unit = {},
    onOpenUpdateCheck: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openDoc by remember { mutableStateOf<LegalDoc?>(null) }

    // In-app update check state
    var checkInProgress by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheck.UpdateResult?>(null) }
    var notifyUpdates by remember { mutableStateOf(UpdateCheck.notifyEnabled(context)) }

    openDoc?.let { doc ->
        BackHandler { openDoc = null }
        LegalDocumentScreen(doc = doc, onBack = { openDoc = null })
        return
    }

    val open: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
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
            AppIdentityGlassCard()

            Spacer(Modifier.height(16.dp))

            // ================= Updates Section =================
            LiquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                depth = GlassDepth.LOW,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    stringResource(R.string.about_check_updates),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (checkInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                            )
                        } else {
                            OutlinedButton(
                                onClick = onOpenUpdateCheck,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.about_action_check))
                            }
                        }
                    }

                    // Update Status Banner
                    updateResult?.let { result ->
                        Spacer(Modifier.height(12.dp))
                        when (result) {
                            is UpdateCheck.UpdateResult.UpToDate -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.update_up_to_date, result.current),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            is UpdateCheck.UpdateResult.Available -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                                        .padding(14.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.NewReleases,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.update_available, result.version),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Button(
                                        onClick = onOpenUpdateCheck,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.update_action_download))
                                    }
                                }
                            }
                            is UpdateCheck.UpdateResult.Offline -> {
                                Text(
                                    stringResource(R.string.update_offline),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            is UpdateCheck.UpdateResult.RateLimited -> {
                                Text(
                                    stringResource(R.string.update_rate_limited),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            is UpdateCheck.UpdateResult.Malformed -> {
                                Text(
                                    stringResource(R.string.update_malformed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Update Notification Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    stringResource(R.string.update_notify_toggle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    stringResource(R.string.update_notify_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = notifyUpdates,
                            onCheckedChange = { on ->
                                notifyUpdates = on
                                UpdateCheck.setNotifyEnabled(context, on)
                                UpdateCheck.setAutoCheckEnabled(context, on)
                            },
                        )
                    }

                    // Clear downloaded APKs (if any)
                    var cachedCount by remember { mutableIntStateOf(AppUpdateManager.listCachedApks(context).size) }
                    var cachedBytes by remember { mutableLongStateOf(AppUpdateManager.cachedSizeBytes(context)) }

                    if (cachedCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val deleted = AppUpdateManager.clearCache(context)
                                    cachedCount = AppUpdateManager.listCachedApks(context).size
                                    cachedBytes = AppUpdateManager.cachedSizeBytes(context)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.update_cleared_toast, deleted),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.update_clear_downloads),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    stringResource(
                                        R.string.update_clear_downloads_subtitle,
                                        cachedCount,
                                        AppUpdateManager.formatBytes(cachedBytes),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ================= Legal Section =================
            LiquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                depth = GlassDepth.LOW,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    AboutGlassRow(
                        icon = Icons.Outlined.PrivacyTip,
                        title = stringResource(R.string.about_privacy_policy),
                        subtitle = null,
                        onClick = { openDoc = LegalDoc.PRIVACY },
                    )
                    AboutGlassRow(
                        icon = Icons.Outlined.Gavel,
                        title = stringResource(R.string.about_terms),
                        subtitle = null,
                        onClick = { openDoc = LegalDoc.TERMS },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ================= Source & Licenses =================
            LiquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                depth = GlassDepth.LOW,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    AboutGlassRow(
                        icon = Icons.Outlined.Code,
                        title = stringResource(R.string.about_view_source),
                        subtitle = stringResource(R.string.about_view_source_subtitle),
                        onClick = { open(REPO_URL) },
                        external = true,
                    )
                    AboutGlassRow(
                        icon = Icons.Outlined.Commit,
                        title = stringResource(R.string.update_commits),
                        subtitle = stringResource(R.string.update_commits_subtitle),
                        onClick = { open(COMMITS_URL) },
                        external = true,
                    )
                    AboutGlassRow(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.about_licenses),
                        subtitle = stringResource(R.string.about_licenses_subtitle),
                        onClick = { open(REPO_URL) },
                        external = true,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Liquid Glass App Identity Card */
@Composable
private fun AppIdentityGlassCard() {
    LiquidGlassSurface(
        shape = RoundedCornerShape(24.dp),
        depth = GlassDepth.MEDIUM,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(80.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.about_icon_description),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun AboutGlassRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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

/** Full-screen scrollable reader for one legal document. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalDocumentScreen(doc: LegalDoc, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(doc.titleRes), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Text(
            stringResource(doc.bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}
