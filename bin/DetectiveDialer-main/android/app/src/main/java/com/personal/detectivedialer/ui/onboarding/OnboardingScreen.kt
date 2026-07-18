package com.personal.detectivedialer.ui.onboarding

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.detectivedialer.data.repository.CallRepository
import com.personal.detectivedialer.ui.settings.SettingsViewModel
import com.personal.detectivedialer.ui.theme.Brand

private const val STEP_WELCOME = 0
private const val STEP_CONNECT = 1
private const val STEP_PERMISSIONS = 2
private const val STEP_BATTERY = 3
private const val LAST_STEP = STEP_BATTERY

/**
 * Paged onboarding: big blue illustration header, one headline + short body
 * per page, full-width CTA, page dots. Steps and persistence logic are
 * identical to the original flow (Welcome → Connect → Permissions → Battery).
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(STEP_WELCOME) }

    // Buffer the connection fields locally; persist on Next (not per keystroke).
    var urlText by rememberSaveable { mutableStateOf("") }
    var apiKeyText by rememberSaveable { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(s.backendUrl, s.apiKey) {
        if (!seeded && (s.backendUrl.isNotBlank() || s.apiKey.isNotBlank())) {
            urlText = s.backendUrl
            apiKeyText = s.apiKey
            seeded = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Illustration(step)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                STEP_WELCOME -> WelcomeStep()
                STEP_CONNECT -> ConnectStep(
                    url = urlText,
                    onUrl = { urlText = it; urlError = null },
                    urlError = urlError,
                    apiKey = apiKeyText,
                    onApiKey = { apiKeyText = it },
                )
                STEP_PERMISSIONS -> PermissionsStep()
                STEP_BATTERY -> BatteryStep(LocalContext.current)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageDots(step)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    if (step == STEP_CONNECT) {
                        val normalized = CallRepository.normalizeBackendUrl(urlText)
                        if (normalized == null) {
                            urlError = "Enter a valid URL, e.g. https://your-app.up.railway.app"
                            return@Button
                        }
                        urlText = normalized
                        val key = apiKeyText.trim()
                        viewModel.update { c -> c.copy(backendUrl = normalized, apiKey = key) }
                        step++
                    } else if (step < LAST_STEP) {
                        step++
                    } else {
                        viewModel.update { c -> c.copy(onboardingDone = true) }
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = step != STEP_CONNECT || urlText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Blue),
            ) {
                Text(
                    when (step) {
                        STEP_WELCOME -> "Get started"
                        LAST_STEP -> "Finish"
                        else -> "Next"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (step > 0) {
                TextButton(onClick = { step-- }) { Text("Back") }
            } else {
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** Big blue gradient header with a step-specific icon "illustration". */
@Composable
private fun Illustration(step: Int) {
    val icon = when (step) {
        STEP_WELCOME -> Icons.Outlined.Shield
        STEP_CONNECT -> Icons.Outlined.Cloud
        STEP_PERMISSIONS -> Icons.Outlined.SupportAgent
        else -> Icons.Outlined.BatteryChargingFull
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Brand.Blue, Brand.BlueDeep)))
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 40.dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@Composable
private fun PageDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(LAST_STEP + 1) { i ->
            val active = i == step
            val width by animateDpAsState(if (active) 20.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (active) Brand.Blue else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                label = "dotColor",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun Headline(title: String, body: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WelcomeStep() {
    Headline(
        "Your AI answers first",
        "Detective Dialer screens unknown callers the moment they ring — right on your phone.",
    )
    Spacer(Modifier.height(4.dp))
    Bullet(Icons.Outlined.Shield, "Unknown callers are checked on-device before your phone rings.")
    Bullet(Icons.Outlined.SupportAgent, "A cloud AI classifies each number as allow, reject, or spam.")
    Bullet(Icons.Default.CheckCircle, "Spam is rejected automatically; you get a clean notification with the reason.")
}

@Composable
private fun Bullet(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Brand.Blue,
            modifier = Modifier.size(22.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ConnectStep(
    url: String,
    onUrl: (String) -> Unit,
    urlError: String?,
    apiKey: String,
    onApiKey: (String) -> Unit,
) {
    Headline(
        "Connect your screening server",
        "Enter the URL of your deployed backend. The app sends unknown numbers there for AI classification.",
    )
    OutlinedTextField(
        value = url,
        onValueChange = onUrl,
        label = { Text("Backend URL") },
        placeholder = { Text("https://your-app.up.railway.app") },
        isError = urlError != null,
        supportingText = { urlError?.let { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKey,
        label = { Text("API key (optional)") },
        supportingText = { Text("Only needed when the backend sets API_KEY") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    // Bumped whenever we return from a permission/role dialog so the ticks refresh.
    var tick by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    val roleHeld = remember(tick) {
        roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }
    val dialerRoleHeld = remember(tick) {
        roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }
    val smsRoleHeld = remember(tick) {
        roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
    }
    val contactsGranted = remember(tick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val notificationsGranted = remember(tick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val smsGranted = remember(tick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { tick++ }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { tick++ }

    Headline(
        "Let the app screen calls",
        "Detective Dialer must be your phone's call screening app, and needs a couple of permissions " +
            "to recognize contacts and show results.",
    )
    StatusCard {
        StatusLine(roleHeld, "Call screening role")
        StatusLine(contactsGranted, "Read contacts (never block people you know)")
        StatusLine(notificationsGranted, "Notifications (screening results)")
        StatusLine(smsGranted, "Read & send SMS (Messages tab)")
        StatusLine(dialerRoleHeld, "Default phone app (in-call screen + dialpad)")
        StatusLine(smsRoleHeld, "Default SMS app (optional — spam SMS filtering)")
    }
    Button(
        onClick = {
            roleManager
                ?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                ?.let { runCatching { roleLauncher.launch(it) } }
        },
        enabled = !roleHeld,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Brand.Blue),
    ) { Text(if (roleHeld) "Call screening enabled ✓" else "Set as call screening app") }
    Button(
        onClick = {
            roleManager
                ?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                ?.let { runCatching { roleLauncher.launch(it) } }
        },
        enabled = !dialerRoleHeld,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Brand.Blue),
    ) { Text(if (dialerRoleHeld) "Default phone app ✓" else "Set as default phone app") }
    OutlinedButton(
        onClick = {
            val wanted = buildList {
                add(Manifest.permission.READ_CONTACTS)
                add(Manifest.permission.READ_SMS)
                add(Manifest.permission.SEND_SMS)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(wanted.toTypedArray())
        },
        enabled = !(contactsGranted && notificationsGranted && smsGranted),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (contactsGranted && notificationsGranted && smsGranted) "Permissions granted ✓"
            else "Grant permissions",
        )
    }

    Text(
        "Optional: make Detective Dialer your SMS app to filter spam texts too. " +
            "TRAI-tagged promotional senders (-P suffix) go to a Blocked folder you " +
            "can always read and restore from — nothing is deleted automatically. " +
            "Your other messages work exactly as before: inbox, threads, and sending.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = {
            roleManager
                ?.createRequestRoleIntent(RoleManager.ROLE_SMS)
                ?.let { runCatching { roleLauncher.launch(it) } }
        },
        enabled = !smsRoleHeld,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (smsRoleHeld) "Default SMS app ✓" else "Make default SMS app") }
}

@Composable
private fun StatusCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (ok) Brand.SafeGreen else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BatteryStep(context: Context) {
    Headline(
        "Keep screening alive",
        "FuntouchOS aggressively kills background apps. Grant these so call screening keeps working:",
    )
    StatusCard {
        StatusLine(true, "Battery usage → Unrestricted")
        StatusLine(true, "Allow background activity")
        StatusLine(true, "Allow auto-start")
    }
    Button(
        onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }.onFailure {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Brand.Blue),
    ) { Text("Open battery settings") }
    OutlinedButton(
        onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Open app info (auto-start)") }
}
