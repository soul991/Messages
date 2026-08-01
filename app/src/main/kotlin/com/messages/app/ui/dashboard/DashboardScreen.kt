package com.messages.app.ui.dashboard

import android.app.Application
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import java.util.Locale
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.core.MessageRepository
import com.messages.designsystem.CategoryColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.messages.app.R

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

            // Hero counter — "N spam messages silenced"
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Shield, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "%,d".format(stats.totalSilenced),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.dashboard_silenced_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // Period selector
            item {
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = period == "WEEK",
                        onClick = { vm.setPeriod("WEEK") },
                        label = { Text(stringResource(R.string.dashboard_period_week)) },
                    )
                    FilterChip(
                        selected = period == "MONTH",
                        onClick = { vm.setPeriod("MONTH") },
                        label = { Text(stringResource(R.string.dashboard_period_month)) },
                    )
                }
            }

            // Category counters
            item {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(stringResource(R.string.category_spam), stats.byCategory["SPAM"] ?: 0, CategoryColors.Fraud, Modifier.weight(1f))
                    StatCard(stringResource(R.string.dashboard_stat_promos), stats.byCategory["PROMOTIONS"] ?: 0, CategoryColors.Promo, Modifier.weight(1f))
                    StatCard(stringResource(R.string.dashboard_stat_blocked), stats.byCategory["BLOCKED"] ?: 0, MaterialTheme.colorScheme.outline, Modifier.weight(1f))
                }
                if (stats.dangerous > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.dashboard_dangerous_stopped,
                            stats.dangerous,
                            stats.dangerous,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CategoryColors.Fraud,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }

            // By family
            if (stats.byFamily.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Text(
                        stringResource(R.string.dashboard_what_was_caught),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    val max = stats.byFamily.maxOf { it.second }.coerceAtLeast(1)
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        stats.byFamily.forEach { (family, count) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    family,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(150.dp),
                                )
                                LinearProgressIndicator(
                                    progress = { count.toFloat() / max },
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "$count",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }

            // Top filtered senders
            if (stats.topSenders.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Text(
                        stringResource(R.string.dashboard_top_senders),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        stats.topSenders.forEach { (address, count) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .width(36.dp)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(CategoryColors.FraudContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        address.take(1).uppercase(),
                                        color = CategoryColors.Fraud,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    address,
                                    style = MaterialTheme.typography.bodyMedium,
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
                    Spacer(Modifier.height(24.dp))
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
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "%,d".format(count),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
