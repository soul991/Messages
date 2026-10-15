package com.messages.app.ui.why

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.messages.designsystem.groupCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.core.MessageRepository
import com.messages.core.db.MessageEntity
import com.messages.core.secret.LockedContent
import com.messages.designsystem.CategoryColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.messages.app.R

class WhyFilteredViewModel(app: Application, private val messageId: Long) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    /** Held as a property: `app` is a plain constructor parameter, so it is out
     *  of scope inside member functions. */
    private val ctx = app.applicationContext

    private val _message = MutableStateFlow<MessageEntity?>(null)
    val message: StateFlow<MessageEntity?> = _message

    // V2-6: this screen can be reached for a locked row (the "Why?" sheet is
    // offered in the locked space too), and a sealed body would render as a
    // base64 blob. open() is a no-op on everything else.
    private suspend fun load(): MessageEntity? =
        repo.db.messages().byId(messageId)?.let { LockedContent.open(ctx, it) }

    init {
        viewModelScope.launch { _message.value = load() }
    }

    fun moveToInbox() = viewModelScope.launch {
        repo.moveToInbox(messageId)
        _message.value = load()
    }
}

class WhyFilteredViewModelFactory(
    private val app: Application,
    private val messageId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WhyFilteredViewModel(app, messageId) as T
}

/**
 * "Why filtered?" (§7.6): every verdict is explainable — shows the folder,
 * the human-readable descriptions of every matched pattern/combo rule, the
 * raw matched IDs, and the score, straight from [MessageEntity].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhyFilteredScreen(
    messageId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vm: WhyFilteredViewModel = viewModel(
        factory = WhyFilteredViewModelFactory(context.applicationContext as Application, messageId)
    )
    val message by vm.message.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.why_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val msg = message ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VerdictHeader(msg)
            MessageCard(msg)
            // Phase 4 item 20 (Truecaller rec A5): links in Dangerous messages
            // do nothing in the chat; the ONLY way to a flagged link is this
            // deliberate reveal, and even then it never becomes tappable.
            if (msg.dangerous || msg.fraudWarning) {
                DangerousLinksSection(msg)
            }
            ExplanationList(msg)
            if (msg.category in listOf("SPAM", "PROMOTIONS", "REVIEW", "BLOCKED")) {
                Button(
                    onClick = vm::moveToInbox,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text(stringResource(R.string.why_not_spam_action))
                }
                Text(
                    stringResource(R.string.why_not_spam_effect, msg.address),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun VerdictHeader(msg: MessageEntity) {
    val (color, container, label) = when {
        msg.dangerous -> Triple(CategoryColors.Fraud, CategoryColors.FraudContainer,
            stringResource(R.string.category_spam_dangerous))
        msg.category == "SPAM" -> Triple(CategoryColors.Fraud, CategoryColors.FraudContainer,
            stringResource(R.string.category_spam))
        msg.category == "PROMOTIONS" -> Triple(CategoryColors.Promo, CategoryColors.PromoContainer,
            stringResource(R.string.category_promotions))
        msg.category == "REVIEW" -> Triple(CategoryColors.Review, CategoryColors.ReviewContainer,
            stringResource(R.string.category_review))
        msg.category == "BLOCKED" -> Triple(CategoryColors.Review, CategoryColors.ReviewContainer,
            stringResource(R.string.category_blocked))
        msg.category == "TRANSACTIONS" -> Triple(CategoryColors.Protected, CategoryColors.ProtectedContainer,
            stringResource(R.string.category_transactions))
        else -> Triple(CategoryColors.Protected, CategoryColors.ProtectedContainer,
            stringResource(R.string.category_inbox))
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        msg.dangerous || msg.fraudWarning -> Icons.Filled.Warning
                        msg.category in listOf("INBOX", "TRANSACTIONS") -> Icons.Filled.CheckCircle
                        else -> Icons.Filled.Info
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.why_verdict_score, msg.score, msg.address),
                    style = MaterialTheme.typography.bodySmall,
                    color = color.copy(alpha = 0.9f),
                )
            }
        }
    }
}

/**
 * Links in a Dangerous/fraud-flagged message (Phase 4 item 20). Hidden by
 * default behind a per-link "Show link" unlock; revealed links render as
 * selectable text with a red warning — they are never made tappable.
 */
@Composable
private fun DangerousLinksSection(msg: MessageEntity) {
    val urls = androidx.compose.runtime.remember(msg.id) {
        runCatching {
            com.messages.protection.Normalizer.normalize(msg.body).urls.distinct()
        }.getOrDefault(emptyList())
    }
    if (urls.isEmpty()) return
    var revealed by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(setOf<String>())
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.why_links_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.why_links_body),
            style = MaterialTheme.typography.bodySmall,
            color = CategoryColors.Fraud,
        )
        urls.forEach { url ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CategoryColors.FraudContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, CategoryColors.Fraud.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (url in revealed) {
                        androidx.compose.foundation.text.selection.SelectionContainer(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                url,
                                style = MaterialTheme.typography.bodySmall,
                                color = CategoryColors.Fraud,
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.why_link_hidden),
                            style = MaterialTheme.typography.bodySmall,
                            color = CategoryColors.Fraud,
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { revealed = revealed + url },
                        ) { Text(stringResource(R.string.why_link_reveal)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(msg: MessageEntity) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.groupCard,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                msg.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ExplanationList(msg: MessageEntity) {
    val explanations = msg.explanations.split('\n').filter { it.isNotBlank() }
    val patternIds = msg.matchedPatternIds.split(',').filter { it.isNotBlank() }
    val comboIds = msg.matchedComboIds.split(',').filter { it.isNotBlank() }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.groupCard,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.why_matched_rules),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (explanations.isEmpty() && patternIds.isEmpty() && comboIds.isEmpty()) {
                Text(
                    stringResource(R.string.why_no_patterns),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                explanations.forEach { line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .padding(top = 7.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (patternIds.isNotEmpty() || comboIds.isNotEmpty()) {
                    HorizontalDivider(
                        Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        stringResource(
                            R.string.why_pattern_ids,
                            (patternIds + comboIds).joinToString(", "),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.why_deterministic),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
