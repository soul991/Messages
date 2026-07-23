package com.messages.app.ui.why

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
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
import com.messages.designsystem.CategoryColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WhyFilteredViewModel(app: Application, private val messageId: Long) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    private val _message = MutableStateFlow<MessageEntity?>(null)
    val message: StateFlow<MessageEntity?> = _message

    init {
        viewModelScope.launch { _message.value = repo.db.messages().byId(messageId) }
    }

    fun moveToInbox() = viewModelScope.launch {
        repo.moveToInbox(messageId)
        _message.value = repo.db.messages().byId(messageId)
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
    val message by vm.message.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Why was this filtered?") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                Button(onClick = vm::moveToInbox, modifier = Modifier.fillMaxWidth()) {
                    Text("Not spam — move to Inbox")
                }
                Text(
                    "Moving it also boosts this sender's local trust score, so " +
                        "future messages from ${msg.address} are less likely to be filtered.",
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
        msg.dangerous -> Triple(CategoryColors.Fraud, CategoryColors.FraudContainer, "Spam · Dangerous")
        msg.category == "SPAM" -> Triple(CategoryColors.Fraud, CategoryColors.FraudContainer, "Spam")
        msg.category == "PROMOTIONS" -> Triple(CategoryColors.Promo, CategoryColors.PromoContainer, "Promotions")
        msg.category == "REVIEW" -> Triple(CategoryColors.Review, CategoryColors.ReviewContainer, "Review")
        msg.category == "BLOCKED" -> Triple(CategoryColors.Review, CategoryColors.ReviewContainer, "Blocked by you")
        msg.category == "TRANSACTIONS" -> Triple(CategoryColors.Protected, CategoryColors.ProtectedContainer, "Transactions")
        else -> Triple(CategoryColors.Protected, CategoryColors.ProtectedContainer, "Inbox")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                msg.dangerous || msg.fraudWarning -> Icons.Filled.Warning
                msg.category in listOf("INBOX", "TRANSACTIONS") -> Icons.Filled.CheckCircle
                else -> Icons.Filled.Info
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium, color = color)
            Text(
                "Score ${msg.score} · from ${msg.address}",
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
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
        Text("Links in this message", style = MaterialTheme.typography.titleSmall)
        Text(
            "Links are disabled everywhere for this message. Revealing one below " +
                "makes it readable, never tappable. Do not visit it.",
            style = MaterialTheme.typography.bodySmall,
            color = CategoryColors.Fraud,
        )
        urls.forEach { url ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CategoryColors.FraudContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                            "Hidden dangerous link",
                            style = MaterialTheme.typography.bodySmall,
                            color = CategoryColors.Fraud,
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { revealed = revealed + url },
                        ) { Text("Show link") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(msg: MessageEntity) {    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            msg.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun ExplanationList(msg: MessageEntity) {
    val explanations = msg.explanations.split('\n').filter { it.isNotBlank() }
    val patternIds = msg.matchedPatternIds.split(',').filter { it.isNotBlank() }
    val comboIds = msg.matchedComboIds.split(',').filter { it.isNotBlank() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Matched rules", style = MaterialTheme.typography.titleSmall)
        if (explanations.isEmpty() && patternIds.isEmpty() && comboIds.isEmpty()) {
            Text(
                "No patterns matched — this message scored 0 and was delivered normally.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            return@Column
        }
        explanations.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Text("•", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (patternIds.isNotEmpty() || comboIds.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                "Pattern IDs: ${(patternIds + comboIds).joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "The protection engine is fully deterministic — no AI, no network. " +
                "Every verdict comes from the rules listed above.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
