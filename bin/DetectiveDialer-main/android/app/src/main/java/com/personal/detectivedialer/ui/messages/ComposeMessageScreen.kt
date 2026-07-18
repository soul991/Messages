package com.personal.detectivedialer.ui.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.detectivedialer.ui.theme.Brand

/**
 * New-message composer. Also the target of sms:/smsto: SENDTO intents —
 * [initialRecipient] and [initialBody] arrive pre-filled from the intent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeMessageScreen(
    initialRecipient: String,
    initialBody: String,
    onSent: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    var recipient by rememberSaveable { mutableStateOf(initialRecipient) }
    var body by rememberSaveable { mutableStateOf(initialBody) }
    var sending by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New message", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brand.Blue,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it; error = false },
                label = { Text("To (number)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it; error = false },
                label = { Text("Message") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (error) {
                Text(
                    "Send failed — check the number and try again.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                enabled = !sending && recipient.isNotBlank() && body.isNotBlank(),
                onClick = {
                    sending = true
                    val to = recipient.trim()
                    viewModel.send(to, body.trim()) { ok ->
                        sending = false
                        if (ok) onSent(to) else error = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (sending) "Sending…" else "Send")
            }
        }
    }
}
