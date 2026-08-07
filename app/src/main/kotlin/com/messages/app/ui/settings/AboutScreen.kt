package com.messages.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.messages.app.BuildConfig
import com.messages.app.R

/**
 * The public-facing "what is this app and who is answerable for it" screen
 * (Settings → About).
 *
 * Two decisions worth keeping:
 *
 * **The legal documents open in-app, never in a browser.** A privacy policy
 * whose first claim is "no network requests are made by this app" cannot be
 * fetched over the network to be read. Both bodies ship in the resource table
 * and render in the full-screen reader below, so the promise and the delivery
 * agree — and the documents stay readable with no connectivity at all.
 *
 * **The release/source rows deliberately DO leave the app.** They are the one
 * honest exception: they hand a URL to the browser via an Intent rather than
 * fetching anything themselves, so the app still makes no requests of its own.
 * They are marked with the open-in-new affordance so the exit is never a
 * surprise.
 */

private const val REPO_URL = "https://github.com/soul991/Messages"
private const val RELEASES_URL = "https://github.com/soul991/Messages/releases"

/** Which legal document the full-screen reader is showing, if any. */
private enum class LegalDoc(val titleRes: Int, val bodyRes: Int) {
    PRIVACY(R.string.about_privacy_policy, R.string.about_privacy_policy_body),
    TERMS(R.string.about_terms, R.string.about_terms_body),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var openDoc by remember { mutableStateOf<LegalDoc?>(null) }

    // The reader is a state of this screen rather than a nav route: system
    // back has to close the document and land back on About, not pop the whole
    // screen. Registered only while a document is open so About's own back
    // behaviour is untouched.
    openDoc?.let { doc ->
        BackHandler { openDoc = null }
        LegalDocumentScreen(doc = doc, onBack = { openDoc = null })
        return
    }

    // runCatching: a device with no browser (or a locked-down work profile)
    // throws ActivityNotFoundException, and About is not a screen worth
    // crashing on. The row simply does nothing there.
    val open: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AppIdentityHeader()

            SettingsSectionDivider()
            SettingsSectionHeader(stringResource(R.string.about_section_legal))
            SettingsNavRow(
                icon = Icons.Outlined.PrivacyTip,
                title = stringResource(R.string.about_privacy_policy),
                subtitle = null,
                onClick = { openDoc = LegalDoc.PRIVACY },
            )
            SettingsNavRow(
                icon = Icons.Outlined.Gavel,
                title = stringResource(R.string.about_terms),
                subtitle = null,
                onClick = { openDoc = LegalDoc.TERMS },
            )

            SettingsSectionDivider()
            SettingsSectionHeader(stringResource(R.string.about_section_updates))
            // The installed version sits on the row itself: "check for updates"
            // is only actionable if you know what you already have.
            SettingsRow(
                icon = Icons.Outlined.SystemUpdate,
                title = stringResource(R.string.about_check_updates),
                subtitle = stringResource(R.string.about_check_updates_subtitle),
                onClick = { open(RELEASES_URL) },
                trailing = {
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            SettingsSectionDivider()
            SettingsSectionHeader(stringResource(R.string.about_section_source))
            SettingsNavRow(
                icon = Icons.Outlined.Code,
                title = stringResource(R.string.about_view_source),
                subtitle = stringResource(R.string.about_view_source_subtitle),
                onClick = { open(REPO_URL) },
                external = true,
            )
            // No oss-licenses Gradle plugin in this build, so there is no
            // generated licence activity to launch. Rather than ship a plugin
            // (and a Play-services dependency) for one row, this points at the
            // repository, where the dependency set and its licences are
            // readable in the build files.
            SettingsNavRow(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.about_licenses),
                subtitle = stringResource(R.string.about_licenses_subtitle),
                onClick = { open(REPO_URL) },
                external = true,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Icon, name, version, and the one-line promise the app is built around. */
@Composable
private fun AppIdentityHeader() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The launcher icon is an adaptive icon, which painterResource cannot
        // inflate directly — its two vector layers are composed here the same
        // way the launcher composes them, clipped to the circular mask.
        androidx.compose.foundation.layout.Box(
            Modifier.size(72.dp).clip(CircleShape),
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
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Full-screen scrollable reader for one legal document. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalDocumentScreen(doc: LegalDoc, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(doc.titleRes)) },
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
