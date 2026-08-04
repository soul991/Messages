package com.messages.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.messages.core.backfill.Backfill
import com.messages.core.backfill.BackfillWorker
import com.messages.designsystem.CategoryColors
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.messages.app.R

/**
 * Onboarding (§9): 3 screens max — what it does → set as default → done,
 * with the backfill classifying existing history as a live counter on the
 * final screen.
 */
@Composable
fun OnboardingScreen(
    isDefaultSmsApp: Boolean,
    onRequestDefault: () -> Unit,
    onDone: () -> Unit,
    onRestoreFromDrive: () -> Unit = {},
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    // Auto-advance off the set-as-default page once the role is granted.
    LaunchedEffect(isDefaultSmsApp) {
        if (isDefaultSmsApp && pagerState.currentPage == 1) {
            pagerState.animateScrollToPage(2)
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> IntroPage()
                1 -> DefaultAppPage(isDefaultSmsApp)
                2 -> DonePage()
            }
        }

        PageDots(current = pagerState.currentPage)
        Spacer(Modifier.height(16.dp))

        when (pagerState.currentPage) {
            0 -> Button(
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_continue)) }

            1 -> Column {
                Button(
                    onClick = { if (isDefaultSmsApp) scope.launch { pagerState.animateScrollToPage(2) } else onRequestDefault() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(
                        if (isDefaultSmsApp) stringResource(R.string.action_continue)
                        else stringResource(R.string.onboarding_set_default),
                    ) }
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_not_now)) }
            }

            2 -> Column {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_start_messaging))
                }
                // §8.3: offer restore during onboarding, right after the
                // default-app step — lands on the Drive backup screen.
                TextButton(onClick = onRestoreFromDrive, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_restore_from_drive))
                }
            }
        }
    }
}

@Composable
private fun IntroPage() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeroIcon(Icons.Filled.Shield, CategoryColors.Protected, CategoryColors.ProtectedContainer)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_hero_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FeatureRow(
            Icons.Filled.NotificationsOff,
            stringResource(R.string.onboarding_feature_silent),
        )
        FeatureRow(
            Icons.Filled.Shield,
            stringResource(R.string.onboarding_feature_on_device),
        )
        FeatureRow(
            Icons.Filled.Inbox,
            stringResource(R.string.onboarding_feature_nothing_deleted),
        )
    }
}

@Composable
private fun DefaultAppPage(isDefault: Boolean) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = isDefault, enter = scaleIn() + fadeIn()) {
            HeroIcon(Icons.Filled.CheckCircle, CategoryColors.Protected, CategoryColors.ProtectedContainer)
        }
        if (!isDefault) {
            HeroIcon(Icons.Filled.Sms, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            if (isDefault) stringResource(R.string.onboarding_default_done_title)
            else stringResource(R.string.onboarding_default_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (isDefault) stringResource(R.string.onboarding_default_done_body)
            else stringResource(R.string.onboarding_default_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DonePage() {
    val context = LocalContext.current
    val workInfos by Backfill.progressFlow(context).collectAsStateWithLifecycle(initialValue = emptyList())
    val info = workInfos.firstOrNull()
    val finished = info?.state == WorkInfo.State.SUCCEEDED

    // Progress data is cleared once the worker succeeds — fall back to the
    // checkpoint prefs the worker maintains for the final number.
    val prefs = context.getSharedPreferences(BackfillWorker.PREFS, android.content.Context.MODE_PRIVATE)
    val processed = if (finished) prefs.getInt(BackfillWorker.KEY_PROCESSED, 0)
    else info?.progress?.getInt(BackfillWorker.KEY_PROCESSED, 0) ?: 0
    val total = if (finished) processed
    else info?.progress?.getInt(BackfillWorker.KEY_TOTAL, 0) ?: 0
    val animated by animateIntAsState(targetValue = processed, label = "backfillCount")

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (finished || info == null) {
            HeroIcon(Icons.Filled.CheckCircle, CategoryColors.Protected, CategoryColors.ProtectedContainer)
        } else {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(96.dp))
                Icon(
                    Icons.Filled.Search, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            when {
                info == null -> stringResource(R.string.onboarding_backfill_ready)
                finished -> stringResource(R.string.onboarding_backfill_done)
                else -> stringResource(R.string.onboarding_backfill_running)
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                info == null -> stringResource(R.string.onboarding_backfill_ready_body)
                finished -> pluralStringResource(
                    R.plurals.onboarding_backfill_done_body, animated, animated,
                )
                total > 0 -> pluralStringResource(
                    R.plurals.onboarding_backfill_progress, total, animated, total,
                )
                else -> stringResource(R.string.onboarding_backfill_scanning)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HeroIcon(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
) {
    Box(
        Modifier.size(96.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PageDots(current: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(3) { i ->
            Box(
                Modifier
                    .padding(4.dp)
                    .size(if (i == current) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}
