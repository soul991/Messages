package com.messages.app.ui.dashboard

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.R
import com.messages.core.MessageRepository
import com.messages.designsystem.categoryPalette
import com.messages.designsystem.groupCard
import com.messages.designsystem.Motion
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Protection dashboard (§8.2): satisfying counters, families, top senders. */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    data class Stats(
        val totalSilenced: Int = 0,
        val byCategory: Map<String, Int> = emptyMap(),
        val dangerous: Int = 0,
        val byFamily: List<Pair<String, Int>> = emptyList(),
        val topSenders: List<Pair<String, Int>> = emptyList(),
    )

    val period = MutableStateFlow("WEEK") // WEEK | MONTH
    val stats = MutableStateFlow(Stats())

    init {
        refresh()
    }

    fun setPeriod(p: String) {
        period.value = p
        refresh()
    }

    private fun refresh() = viewModelScope.launch {
        val days = if (period.value == "MONTH") 30L else 7L
        val since = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000
        val dao = repo.db.messages()
        val families = repo.engine.familiesByPatternId
        val familyCounts = HashMap<String, Int>()
        dao.filteredPatternIdsSince(since).forEach { csv ->
            // Count each message once per family it tripped.
            csv.split(',').mapNotNull { id -> familyDisplayName(families[id], id) }
                .distinct()
                .forEach { fam -> familyCounts[fam] = (familyCounts[fam] ?: 0) + 1 }
        }
        stats.value = Stats(
            totalSilenced = dao.totalSilenced(),
            byCategory = dao.filteredCountsSince(since).associate { it.category to it.count },
            dangerous = dao.dangerousCountSince(since),
            byFamily = familyCounts.entries.sortedByDescending { it.value }.take(8)
                .map { it.key to it.value },
            topSenders = dao.topFilteredSenders(since, 5).map { it.address to it.count },
        )
    }

    /**
     * V2-36. `family` is a detection-engine identifier, not copy — it is
     * title-cased for display with [Locale.ROOT] so a Turkish locale does not
     * turn "link" into "L\u0131nk". The two fallback labels are ours, so they
     * come from the resource table.
     */
    private fun familyDisplayName(family: String?, patternId: String): String? = when {
        family != null -> family.split('-', ' ').joinToString(" ") { word ->
            word.replaceFirstChar { c -> c.titlecase(Locale.ROOT) }
        }
        patternId.startsWith("link-") || patternId.startsWith("format-") ->
            getApplication<Application>().getString(R.string.dashboard_family_links_format)
        patternId.startsWith("C") && patternId.length <= 3 -> null // combo ids: shown via categories
        else -> getApplication<Application>().getString(R.string.dashboard_family_other)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    vm: DashboardViewModel = viewModel(),
) {
    val period by vm.period.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val spamPalette = categoryPalette("SPAM")
    val promoPalette = categoryPalette("PROMOTIONS")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {

            // Hero counter — "N spam messages silenced" with animated rolling odometer
            item {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        AnimatedContent(
                            targetState = stats.totalSilenced,
                            transitionSpec = {
                                if (targetState > initialState) {
                                    (slideInVertically(Motion.butterSpring()) { height -> height } + fadeIn(Motion.effectsDefault())) togetherWith
                                        (slideOutVertically(Motion.butterSpring()) { height -> -height } + fadeOut(Motion.effectsFast()))
                                } else {
                                    (slideInVertically(Motion.butterSpring()) { height -> -height } + fadeIn(Motion.effectsDefault())) togetherWith
                                        (slideOutVertically(Motion.butterSpring()) { height -> height } + fadeOut(Motion.effectsFast()))
                                }
                            },
                            label = "totalSilencedCounter",
                        ) { count ->
                            Text(
                                "%,d".format(count),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.dashboard_silenced_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // Period selector
            item {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = period == "WEEK",
                        onClick = { vm.setPeriod("WEEK") },
                        label = { Text(stringResource(R.string.dashboard_period_week)) },
                        shape = RoundedCornerShape(20.dp),
                    )
                    FilterChip(
                        selected = period == "MONTH",
                        onClick = { vm.setPeriod("MONTH") },
                        label = { Text(stringResource(R.string.dashboard_period_month)) },
                        shape = RoundedCornerShape(20.dp),
                    )
                }
            }

            // Category counters
            item {
                val spamTint = spamPalette?.tint ?: MaterialTheme.colorScheme.error
                val promoTint = promoPalette?.tint ?: MaterialTheme.colorScheme.tertiary
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatCard(stringResource(R.string.category_spam), stats.byCategory["SPAM"] ?: 0, spamTint, Modifier.weight(1f))
                    StatCard(stringResource(R.string.dashboard_stat_promos), stats.byCategory["PROMOTIONS"] ?: 0, promoTint, Modifier.weight(1f))
                    StatCard(stringResource(R.string.dashboard_stat_blocked), stats.byCategory["BLOCKED"] ?: 0, MaterialTheme.colorScheme.outline, Modifier.weight(1f))
                }
                if (stats.dangerous > 0) {
                    val dangerContainer = spamPalette?.container ?: MaterialTheme.colorScheme.errorContainer
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = dangerContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, spamTint.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = spamTint,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                pluralStringResource(
                                    R.plurals.dashboard_dangerous_stopped,
                                    stats.dangerous,
                                    stats.dangerous,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = spamTint,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // By family
            if (stats.byFamily.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.groupCard,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                stringResource(R.string.dashboard_what_was_caught),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(16.dp))
                            val max = stats.byFamily.maxOf { it.second }.coerceAtLeast(1)
                            stats.byFamily.forEachIndexed { index, (family, count) ->
                                if (index > 0) {
                                    Spacer(Modifier.height(12.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        family,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.width(140.dp),
                                    )
                                    LinearProgressIndicator(
                                        progress = { count.toFloat() / max },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "$count",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Top filtered senders
            if (stats.topSenders.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.groupCard,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                stringResource(R.string.dashboard_top_senders),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(14.dp))
                            stats.topSenders.forEachIndexed { index, (address, count) ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    )
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(spamPalette?.container ?: MaterialTheme.colorScheme.errorContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            address.take(1).uppercase(),
                                            color = spamPalette?.onContainer ?: MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        address,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        pluralStringResource(
                                            R.plurals.dashboard_sender_filtered, count, count,
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Empty state (§9 delight)
            if (stats.byFamily.isEmpty() && stats.topSenders.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.dashboard_empty_glyph),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.dashboard_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.groupCard,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (slideInVertically(Motion.butterSpring()) { height -> height } + fadeIn(Motion.effectsDefault())) togetherWith
                        (slideOutVertically(Motion.butterSpring()) { height -> -height } + fadeOut(Motion.effectsFast()))
                },
                label = "statCardCount",
            ) { targetCount ->
                Text(
                    "%,d".format(targetCount),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
