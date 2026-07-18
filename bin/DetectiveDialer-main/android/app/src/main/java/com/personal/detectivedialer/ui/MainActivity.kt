package com.personal.detectivedialer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.detectivedialer.ui.theme.DetectiveDialerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val launchTarget = parseLaunchTarget(intent)

        setContent {
            val vm: RootViewModel = hiltViewModel()
            val onboarded by vm.onboarded.collectAsState()
            DetectiveDialerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Wait for DataStore before picking a start destination —
                    // rendering with a guessed value briefly showed onboarding
                    // (or skipped it) on every cold start.
                    onboarded?.let { done ->
                        AppNav(
                            startOnboarding = !done,
                            launchTarget = launchTarget,
                        )
                    }
                }
            }
        }
    }

    /**
     * Where should this launch land?
     *  - detectivedialer://call/{id}  → Call Detail (screening notification tap)
     *  - detectivedialer://sms/{sender} → SMS thread (message notification tap)
     *  - ACTION_SENDTO/SEND sms:/smsto:/mms:/mmsto: → composer, pre-filled
     *    (this activity is the default-SMS-app composer entry point)
     */
    private fun parseLaunchTarget(intent: Intent?): LaunchTarget {
        val data = intent?.data
        return when {
            data?.scheme == "detectivedialer" && data.host == "call" ->
                LaunchTarget(callId = data.lastPathSegment)

            data?.scheme == "detectivedialer" && data.host == "sms" ->
                LaunchTarget(smsSender = data.lastPathSegment)

            data?.scheme in SMS_SCHEMES -> LaunchTarget(
                // sms:+919812345678?body=hi → schemeSpecificPart holds the number
                composeRecipient = data?.schemeSpecificPart
                    ?.substringBefore('?')
                    ?.trim()
                    .orEmpty(),
                composeBody = intent?.getStringExtra("sms_body")
                    ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
                    ?: data?.query?.substringAfter("body=", "")?.let { android.net.Uri.decode(it) }
                        .orEmpty(),
            )

            intent?.action == Intent.ACTION_SEND ->
                LaunchTarget(composeRecipient = "", composeBody = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())

            // ACTION_DIAL/ACTION_VIEW on a tel: URI → open the dialpad, pre-filled.
            (intent?.action == Intent.ACTION_DIAL || intent?.action == Intent.ACTION_VIEW) &&
                data?.scheme == "tel" ->
                LaunchTarget(dialNumber = android.net.Uri.decode(data.schemeSpecificPart.orEmpty()))

            intent?.action == Intent.ACTION_DIAL -> LaunchTarget(dialNumber = "")

            else -> LaunchTarget()
        }
    }

    companion object {
        private val SMS_SCHEMES = setOf("sms", "smsto", "mms", "mmsto")
    }
}
