package com.messages.app.ui.outbox

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.R
import com.messages.app.schedule.Scheduler
import com.messages.app.schedule.SmsRadio
import com.messages.app.sms.SimChoices
import com.messages.app.ui.common.AppDateFormat
import com.messages.core.MessageRepository
import com.messages.core.db.AttemptState
import com.messages.core.db.MessageEntity
import com.messages.core.db.SmsAttemptEntity
import com.messages.core.send.SendFailure
import com.messages.core.send.SendRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * V2-48: the outbox — every outgoing message that has not finished, and the
 * explicit controls for it.
 *
 * The finding's complaint was that a queued send was a black box: no way to see
 * what state it was in, no way to edit it, cancel it, or move it to the other
 * SIM, and per-recipient results were invisible even though the database had
 * them. Everything here is a view onto state that already exists — this screen
 * adds no send path of its own, it reuses [SmsRadio] and the repository's
 * compare-and-set claims, so a button press and a worker firing at the same
 * instant still produce exactly one message.
 *
 * ## What is deliberately not offered
 *
 * A message that is already SENDING has no Cancel and no Edit. Once the parts
 * are with the radio there is nothing to cancel — the platform has no recall —
 * and an edit would change the text of something already gone. Saying "can't"
 * is honest; a Cancel button that quietly did nothing would not be.
 *
 * Locked-space messages never appear (the DAO filters them): this screen is
 * reachable without the locked-space credential.
 */
class OutboxViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val messages: StateFlow<List<MessageEntity>> = repo.observeOutbox()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Which row's per-recipient detail is open, and what it says. */
    val detailFor = MutableStateFlow<Long?>(null)
    val detail = MutableStateFlow<List<AttemptLine>>(emptyList())

    /** Transient one-line result of the last action, shown in a snackbar. */
    val status = MutableStateFlow<String?>(null)

    val simOptions: StateFlow<List<SimChoices.Choice>> =
        MutableStateFlow(SimChoices.active(app))

    /** One dispatch, resolved for display: which recipient, which part, how it went. */
    data class AttemptLine(
        val recipient: String,
        val partIndex: Int,
        val partCount: Int,
        val sentState: String,
        val deliveryState: String,
        val wantDelivery: Boolean,
        val resultCode: Int?,
    )

    fun openDetail(message: MessageEntity) = viewModelScope.launch {
        detailFor.value = message.id
        val attempts = repo.sendAttemptsFor(message.id)
        // recipientsOf touches the contacts/telephony helpers, so it is resolved
        // off the main thread with the rest of the lookup.
        detail.value = withContext(Dispatchers.IO) { lines(message, attempts) }
    }

    fun closeDetail() {
        detailFor.value = null
        detail.value = emptyList()
    }

    private fun lines(
        message: MessageEntity,
        attempts: List<SmsAttemptEntity>,
    ): List<AttemptLine> {
        val recipients = repo.recipientsOf(message.address)
        val partCount = attempts.maxOfOrNull { it.partIndex + 1 } ?: 1
        return attempts.map { a ->
            AttemptLine(
                recipient = recipients.getOrNull(a.recipientIndex)
                    ?.let { repo.lookupContactName(it) ?: it }
                    ?: message.address,
                partIndex = a.partIndex,
                partCount = partCount,
                sentState = a.sentState,
                deliveryState = a.deliveryState,
                wantDelivery = a.wantDelivery,
                resultCode = a.resultCode,
            )
        }
    }

    /**
     * Send a scheduled message now. Goes through the same
     * `promoteScheduledToSending` claim the worker uses, so if the worker fired
     * a second ago this does nothing rather than sending twice.
     */
    fun sendNow(message: MessageEntity) = viewModelScope.launch {
        val app = getApplication<Application>()
        Scheduler.cancelSend(app, message.id)
        val entity = repo.promoteScheduledToSending(message.id)
        if (entity == null) {
            status.value = string(R.string.outbox_too_late)
            return@launch
        }
        SmsRadio.send(app, repo, entity)
    }

    /**
     * Resend a failed message. Cancels any armed automatic retry first and
     * resets the budget: the user taking over is a fresh decision, and leaving
     * the old worker armed would send the same message again behind them.
     */
    fun resend(message: MessageEntity) = viewModelScope.launch {
        val app = getApplication<Application>()
        Scheduler.cancelRetry(app, message.id)
        repo.resetRetryBudget(message.id)
        val entity = repo.claimFailedForResend(message.id)
        if (entity == null) {
            status.value = string(R.string.outbox_too_late)
            return@launch
        }
        status.value = string(R.string.outbox_resending)
        SmsRadio.send(app, repo, entity)
    }

    fun cancelScheduled(message: MessageEntity) = viewModelScope.launch {
        val app = getApplication<Application>()
        Scheduler.cancelSend(app, message.id)
        repo.cancelScheduled(message.id)
        status.value = string(R.string.outbox_cancelled)
    }

    /** A failed message the user gives up on: trashed, not vanished. */
    fun discard(message: MessageEntity) = viewModelScope.launch {
        Scheduler.cancelRetry(getApplication(), message.id)
        repo.moveToTrash(message.id)
        status.value = string(R.string.outbox_discarded)
    }

    fun edit(message: MessageEntity, body: String) = viewModelScope.launch {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return@launch
        Scheduler.cancelRetry(getApplication(), message.id)
        val updated = repo.editUnsentMessage(message.id, trimmed)
        status.value = string(
            if (updated == null) R.string.outbox_too_late else R.string.outbox_edited,
        )
    }

    fun changeSim(message: MessageEntity, subId: Int?) = viewModelScope.launch {
        val ok = repo.changeSendSubId(message.id, subId)
        status.value = string(
            if (ok) R.string.outbox_sim_changed else R.string.outbox_too_late,
        )
    }

    fun consumeStatus() {
        status.value = null
    }

    private fun string(id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)
}

/** True while the message is with the radio — nothing on this screen may touch it. */
private fun inFlight(status: String): Boolean =
    status == "CLAIMED" || status == "SENDING"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboxScreen(
    onBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
    vm: OutboxViewModel = viewModel(),
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val detailFor by vm.detailFor.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val sims by vm.simOptions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<MessageEntity?>(null) }
    var choosingSimFor by remember { mutableStateOf<MessageEntity?>(null) }

    LaunchedEffect(status) {
        status?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.outbox_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Icon(
                        Icons.Outlined.Outbox, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        stringResource(R.string.outbox_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        stringResource(R.string.outbox_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                OutboxCard(
                    message = message,
                    onOpenThread = { onOpenThread(message.threadId) },
                    onSendNow = { vm.sendNow(message) },
                    onResend = { vm.resend(message) },
                    onCancel = { vm.cancelScheduled(message) },
                    onDiscard = { vm.discard(message) },
                    onEdit = { editing = message },
                    onChangeSim = { choosingSimFor = message }.takeIf { sims.isNotEmpty() },
                    onDetails = { vm.openDetail(message) },
                )
            }
        }
    }

    if (detailFor != null) {
        AttemptDetailDialog(lines = detail, onDismiss = vm::closeDetail)
    }
    editing?.let { message ->
        EditMessageDialog(
            initial = message.body,
            onDismiss = { editing = null },
            onSave = { text ->
                vm.edit(message, text)
                editing = null
            },
        )
    }
    choosingSimFor?.let { message ->
        SimPickerDialog(
            options = sims,
            selected = message.subId,
            onDismiss = { choosingSimFor = null },
            onPick = { subId ->
                vm.changeSim(message, subId)
                choosingSimFor = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutboxCard(
    message: MessageEntity,
    onOpenThread: () -> Unit,
    onSendNow: () -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
    onEdit: () -> Unit,
    onChangeSim: (() -> Unit)?,
    onDetails: () -> Unit,
) {
    val scheduled = message.sendStatus == "SCHEDULED"
    val failed = message.sendStatus == "FAILED"
    val busy = inFlight(message.sendStatus)

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                message.address,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                statusLine(message),
                style = MaterialTheme.typography.labelLarge,
                color = if (failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            retryLine(message)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (busy) {
                Text(
                    stringResource(R.string.outbox_in_flight),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            // Actions. Laid out in a flow so a narrow screen wraps instead of
            // hiding a control behind an overflow the user has to discover.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (scheduled) {
                    TextButton(onClick = onSendNow) {
                        Text(stringResource(R.string.outbox_action_send_now))
                    }
                }
                if (failed) {
                    TextButton(onClick = onResend) {
                        Text(stringResource(R.string.outbox_action_resend))
                    }
                }
                if (!busy) {
                    TextButton(onClick = onEdit) {
                        Text(stringResource(R.string.outbox_action_edit))
                    }
                }
                if (!busy && onChangeSim != null) {
                    TextButton(onClick = onChangeSim) {
                        Text(stringResource(R.string.outbox_action_change_sim))
                    }
                }
                TextButton(onClick = onDetails) {
                    Text(stringResource(R.string.outbox_action_details))
                }
                if (scheduled) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.outbox_action_cancel))
                    }
                }
                if (failed) {
                    TextButton(onClick = onDiscard) {
                        Text(stringResource(R.string.outbox_action_discard))
                    }
                }
                TextButton(onClick = onOpenThread) {
                    Text(stringResource(R.string.outbox_open_chat))
                }
            }
        }
    }
}

@Composable
private fun statusLine(message: MessageEntity): String = when (message.sendStatus) {
    "SCHEDULED" -> stringResource(
        R.string.outbox_status_scheduled,
        AppDateFormat.weekdayDayMonthClock(message.timestamp),
    )
    "FAILED" -> stringResource(
        R.string.outbox_status_failed,
        SendFailure.reasonFor(message.sendResultCode),
    )
    else -> stringResource(R.string.outbox_status_sending)
}

/**
 * What the automatic retry is doing, if anything. Reports an overdue retry as
 * overdue rather than as "trying again soon": the stored due time is the only
 * thing the app actually knows, and WorkManager may have been dropped with the
 * process.
 */
@Composable
private fun retryLine(message: MessageEntity): String? {
    if (message.sendStatus != "FAILED") return null
    val due = message.nextRetryAt
    return when {
        due == null && message.retryCount >= SendRetry.MAX_AUTO_RETRIES ->
            stringResource(R.string.outbox_retry_exhausted)
        due == null -> null
        due < System.currentTimeMillis() -> stringResource(R.string.outbox_retry_overdue)
        else -> stringResource(R.string.outbox_retry_pending, AppDateFormat.clock(due))
    }
}

@Composable
private fun AttemptDetailDialog(
    lines: List<OutboxViewModel.AttemptLine>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.outbox_details_title)) },
        text = {
            if (lines.isEmpty()) {
                Text(stringResource(R.string.outbox_attempts_empty))
            } else {
                Column {
                    lines.forEachIndexed { index, line ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(
                            if (line.partCount > 1) stringResource(
                                R.string.outbox_attempt_part,
                                line.recipient, line.partIndex + 1, line.partCount,
                            ) else line.recipient,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            attemptOutcome(line),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

/**
 * One dispatch's outcome in words. A failed part names the carrier's reason;
 * delivery is only mentioned when a report was actually asked for, because
 * "no delivery report" against a message that never requested one would read
 * as a problem.
 */
@Composable
private fun attemptOutcome(line: OutboxViewModel.AttemptLine): String = when {
    line.sentState == AttemptState.PENDING -> stringResource(R.string.outbox_attempt_pending)
    line.sentState == AttemptState.FAILED ->
        stringResource(R.string.outbox_attempt_failed) + " · " +
            SendFailure.reasonFor(line.resultCode)
    !line.wantDelivery -> stringResource(R.string.outbox_attempt_sent)
    line.deliveryState == AttemptState.OK -> stringResource(R.string.outbox_attempt_delivered)
    line.deliveryState == AttemptState.FAILED ->
        stringResource(R.string.outbox_attempt_not_delivered)
    else -> stringResource(R.string.outbox_attempt_sent)
}

@Composable
private fun EditMessageDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.outbox_edit_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.outbox_edit_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SimPickerDialog(
    options: List<SimChoices.Choice>,
    selected: Int?,
    onDismiss: () -> Unit,
    onPick: (Int?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.outbox_sim_title)) },
        text = {
            Column {
                SimRow(
                    label = stringResource(R.string.outbox_sim_default),
                    checked = selected == null,
                    onClick = { onPick(null) },
                )
                options.forEach { option ->
                    SimRow(
                        label = option.displayName,
                        checked = selected == option.subId,
                        onClick = { onPick(option.subId) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

/**
 * `selectable` rather than `clickable`: it makes the whole row one radio target
 * for a screen reader — label and control announced together — instead of an
 * unlabelled button sitting next to some text.
 */
@Composable
private fun SimRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = checked, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = checked, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
