package com.messages.app.ui.drivebackup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.messages.app.R
import com.messages.app.drive.DriveBackup
import com.messages.core.backup.MasterKeyVault

/**
 * V2-5 / V2-46 — the user-facing half of user-held backup keys.
 *
 * The screen's job here is almost entirely copy. Both custody modes encrypt
 * backups identically; what the user is actually choosing is who holds the key,
 * and that is a trade they can only make if both sides are stated plainly. So
 * account mode is described by what it costs (account access is backup access)
 * rather than being flagged as the insecure option, and the recovery code is
 * described by what it costs too: lose it and the backups are gone, with no one
 * to appeal to.
 */

/** Which key-custody operation the dialog is walking through. */
enum class KeyFlowKind { ENABLE, CHANGE, DISABLE, UNLOCK }

/** Where in that walk we are. Not every kind visits every step. */
enum class KeyFlowStep {
    /** Prove you hold the current secret (skipped when this device has the key). */
    CURRENT,

    /** Recovery code or password. */
    METHOD,

    /** Show the generated code and make the user acknowledge it. */
    CODE,

    /** Type a password twice. */
    PASSWORD,

    /** Last chance before handing the key back to the account. */
    CONFIRM_DISABLE,
}

/**
 * All of the dialog's state in one value, so the ViewModel owns it and it
 * survives recomposition and rotation without a pile of `remember`s holding
 * secrets.
 */
data class KeyFlowState(
    val kind: KeyFlowKind,
    val step: KeyFlowStep,
    /** What the existing vault expects, so the prompt says "code" or "password". */
    val currentMethod: String? = null,
    val current: String = "",
    val newMethod: String? = null,
    /** Display form of the freshly generated code — grouped, dash-separated. */
    val generated: String? = null,
    val password: String = "",
    val passwordRepeat: String = "",
    val error: String? = null,
    val busy: Boolean = false,
)

/** The settings block: current custody, and the one or two moves available. */
@Composable
fun KeyProtectionSection(
    state: DriveBackup.KeyState?,
    enabled: Boolean,
    onProtect: () -> Unit,
    onChange: () -> Unit,
    onUnprotect: () -> Unit,
    onUnlock: () -> Unit,
) {
    SectionLabel(stringResource(R.string.drive_section_key_protection))
    Text(
        stringResource(R.string.drive_key_protection_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
    Spacer(Modifier.height(8.dp))

    // Null while the state is still being read from Drive. Showing nothing beats
    // showing "your Google Account" and then flipping it a second later.
    if (state == null) return

    when (state.custody) {
        DriveBackup.KeyCustody.ACCOUNT -> {
            Text(
                stringResource(R.string.drive_key_custody_account),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.drive_key_custody_account_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onProtect, enabled = enabled) {
                Text(stringResource(R.string.drive_key_protect_action))
            }
        }

        DriveBackup.KeyCustody.USER_HELD -> {
            Text(
                stringResource(R.string.drive_key_custody_user_held),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.drive_key_custody_user_held_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(8.dp))
            if (state.access == DriveBackup.KeyAccess.NEEDS_USER_SECRET) {
                Text(
                    stringResource(R.string.drive_key_locked_here),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onUnlock, enabled = enabled) {
                    Text(stringResource(R.string.drive_key_unlock_action))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onChange, enabled = enabled) {
                        Text(stringResource(R.string.drive_key_change_action))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onUnprotect, enabled = enabled) {
                        Text(stringResource(R.string.drive_key_unprotect_action))
                    }
                }
            }
        }
    }
}

/** The multi-step dialog driving [KeyFlowState]. */
@Composable
fun KeyProtectionDialog(
    flow: KeyFlowState,
    onUpdate: (KeyFlowState) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val codeCopied = stringResource(R.string.drive_recovery_code_copied)

    val titleRes = when (flow.step) {
        KeyFlowStep.CURRENT -> when (flow.kind) {
            KeyFlowKind.UNLOCK -> R.string.drive_key_unlock_action
            KeyFlowKind.DISABLE -> R.string.drive_key_unprotect_title
            else -> R.string.drive_key_change_title
        }
        KeyFlowStep.METHOD -> R.string.drive_key_method_title
        KeyFlowStep.CODE -> R.string.drive_recovery_code_title
        KeyFlowStep.PASSWORD -> R.string.drive_recovery_password_label
        KeyFlowStep.CONFIRM_DISABLE -> R.string.drive_key_unprotect_title
    }

    // The confirm button is the only way forward, so its label has to say what
    // the *next* thing is, not a generic OK.
    val confirmRes = when (flow.step) {
        KeyFlowStep.CODE -> R.string.drive_recovery_code_saved
        KeyFlowStep.CONFIRM_DISABLE -> R.string.drive_key_unprotect_action
        KeyFlowStep.CURRENT -> if (flow.kind == KeyFlowKind.UNLOCK) {
            R.string.drive_key_unlock_action
        } else {
            R.string.action_continue
        }
        else -> R.string.action_continue
    }

    AlertDialog(
        onDismissRequest = { if (!flow.busy) onDismiss() },
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                when (flow.step) {
                    KeyFlowStep.CURRENT -> {
                        val isPassword = flow.currentMethod == MasterKeyVault.METHOD_PASSWORD
                        OutlinedTextField(
                            value = flow.current,
                            onValueChange = { onUpdate(flow.copy(current = it, error = null)) },
                            label = {
                                Text(
                                    stringResource(
                                        if (isPassword) R.string.drive_recovery_password_label
                                        else R.string.drive_recovery_code_label,
                                    ),
                                )
                            },
                            visualTransformation = if (isPassword) {
                                PasswordVisualTransformation()
                            } else {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            },
                            singleLine = true,
                            enabled = !flow.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    KeyFlowStep.METHOD -> {
                        Text(
                            stringResource(
                                if (flow.kind == KeyFlowKind.CHANGE) R.string.drive_key_change_body
                                else R.string.drive_key_protect_body,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(12.dp))
                        MethodChoice(
                            selected = flow.newMethod == MasterKeyVault.METHOD_RECOVERY_CODE,
                            title = stringResource(R.string.drive_key_method_code),
                            note = stringResource(R.string.drive_key_method_code_note),
                        ) {
                            onUpdate(flow.copy(newMethod = MasterKeyVault.METHOD_RECOVERY_CODE))
                        }
                        MethodChoice(
                            selected = flow.newMethod == MasterKeyVault.METHOD_PASSWORD,
                            title = stringResource(R.string.drive_key_method_password),
                            note = stringResource(R.string.drive_key_method_password_note),
                        ) {
                            onUpdate(flow.copy(newMethod = MasterKeyVault.METHOD_PASSWORD))
                        }
                    }

                    KeyFlowStep.CODE -> {
                        Text(
                            stringResource(R.string.drive_recovery_code_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                flow.generated.orEmpty(),
                                // Monospace so 8 and B, 5 and S stay distinct on
                                // the one screen where a misread is unrecoverable.
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(flow.generated.orEmpty()))
                                onUpdate(flow.copy(error = codeCopied))
                            },
                            enabled = !flow.busy,
                        ) { Text(stringResource(R.string.drive_recovery_code_copy)) }
                    }

                    KeyFlowStep.PASSWORD -> {
                        Text(
                            stringResource(R.string.drive_key_method_password_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = flow.password,
                            onValueChange = { onUpdate(flow.copy(password = it, error = null)) },
                            label = { Text(stringResource(R.string.drive_recovery_password_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !flow.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = flow.passwordRepeat,
                            onValueChange = { onUpdate(flow.copy(passwordRepeat = it, error = null)) },
                            label = {
                                Text(stringResource(R.string.drive_recovery_password_confirm_label))
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !flow.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    KeyFlowStep.CONFIRM_DISABLE -> {
                        Text(
                            stringResource(R.string.drive_key_unprotect_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                flow.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (flow.busy) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.drive_key_working),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = !flow.busy && when (flow.step) {
                    KeyFlowStep.CURRENT -> flow.current.isNotBlank()
                    KeyFlowStep.METHOD -> flow.newMethod != null
                    KeyFlowStep.PASSWORD ->
                        flow.password.isNotEmpty() && flow.passwordRepeat.isNotEmpty()
                    else -> true
                },
            ) { Text(stringResource(confirmRes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !flow.busy) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun MethodChoice(
    selected: Boolean,
    title: String,
    note: String,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
