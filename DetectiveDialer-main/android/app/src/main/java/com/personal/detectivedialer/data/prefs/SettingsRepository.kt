package com.personal.detectivedialer.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

/** User-facing settings backed by Jetpack DataStore. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Settings(
        val ownerName: String = "",
        val homeAddress: String = "",
        val gateCode: String = "",
        val language: String = "auto",
        val personaTone: String = "friendly",
        val backendUrl: String = "",
        val apiKey: String = "",
        val fcmToken: String = "",
        val onboardingDone: Boolean = false,
        /** Policy for unknown international callers: OFF | SILENCE | REJECT. */
        val internationalPolicy: String = "SILENCE",
        /** Cached JSON of the backend's screening-rules table (offline default when blank). */
        val screeningRulesJson: String = "",
        // SMS: which TRAI suffix classes go to the Blocked folder.
        val smsBlockPromotional: Boolean = true,
        val smsBlockService: Boolean = false,
        val smsBlockTransactional: Boolean = false,
        val smsBlockGovernment: Boolean = false,
        /** Opt-in: POST suffix-less senders' message text to /screen-sms. */
        val smsContentFallback: Boolean = false,
        /** Internal file path of the incoming-call wallpaper (blank = default). */
        val incomingWallpaperPath: String = "",
        /** Scrim opacity over the wallpaper, 0–100 (higher = darker/more readable). */
        val incomingScrimPercent: Int = 45,
    )

    private object Keys {
        val OWNER_NAME = stringPreferencesKey("owner_name")
        val HOME_ADDRESS = stringPreferencesKey("home_address")
        val GATE_CODE = stringPreferencesKey("gate_code")
        val LANGUAGE = stringPreferencesKey("language")
        val PERSONA_TONE = stringPreferencesKey("persona_tone")
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val API_KEY = stringPreferencesKey("api_key")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val INTERNATIONAL_POLICY = stringPreferencesKey("international_policy")
        val SCREENING_RULES_JSON = stringPreferencesKey("screening_rules_json")
        val SMS_BLOCK_PROMOTIONAL = booleanPreferencesKey("sms_block_promotional")
        val SMS_BLOCK_SERVICE = booleanPreferencesKey("sms_block_service")
        val SMS_BLOCK_TRANSACTIONAL = booleanPreferencesKey("sms_block_transactional")
        val SMS_BLOCK_GOVERNMENT = booleanPreferencesKey("sms_block_government")
        val SMS_CONTENT_FALLBACK = booleanPreferencesKey("sms_content_fallback")
        val INCOMING_WALLPAPER_PATH = stringPreferencesKey("incoming_wallpaper_path")
        val INCOMING_SCRIM_PERCENT = intPreferencesKey("incoming_scrim_percent")
    }

    private fun fromPreferences(p: androidx.datastore.preferences.core.Preferences) = Settings(
        ownerName = p[Keys.OWNER_NAME] ?: "",
        homeAddress = p[Keys.HOME_ADDRESS] ?: "",
        gateCode = p[Keys.GATE_CODE] ?: "",
        language = p[Keys.LANGUAGE] ?: "auto",
        personaTone = p[Keys.PERSONA_TONE] ?: "friendly",
        backendUrl = p[Keys.BACKEND_URL] ?: "",
        apiKey = p[Keys.API_KEY] ?: "",
        fcmToken = p[Keys.FCM_TOKEN] ?: "",
        // Pre-flag installs configured a backend URL through the old onboarding;
        // treat them as onboarded so an update doesn't bounce them back to it.
        onboardingDone = p[Keys.ONBOARDING_DONE] ?: (p[Keys.BACKEND_URL].orEmpty().isNotBlank()),
        internationalPolicy = p[Keys.INTERNATIONAL_POLICY] ?: "SILENCE",
        screeningRulesJson = p[Keys.SCREENING_RULES_JSON] ?: "",
        smsBlockPromotional = p[Keys.SMS_BLOCK_PROMOTIONAL] ?: true,
        smsBlockService = p[Keys.SMS_BLOCK_SERVICE] ?: false,
        smsBlockTransactional = p[Keys.SMS_BLOCK_TRANSACTIONAL] ?: false,
        smsBlockGovernment = p[Keys.SMS_BLOCK_GOVERNMENT] ?: false,
        smsContentFallback = p[Keys.SMS_CONTENT_FALLBACK] ?: false,
        incomingWallpaperPath = p[Keys.INCOMING_WALLPAPER_PATH] ?: "",
        incomingScrimPercent = p[Keys.INCOMING_SCRIM_PERCENT] ?: 45,
    )

    val settings: Flow<Settings> = context.dataStore.data.map(::fromPreferences)

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val next = transform(fromPreferences(p))
            p[Keys.OWNER_NAME] = next.ownerName
            p[Keys.HOME_ADDRESS] = next.homeAddress
            p[Keys.GATE_CODE] = next.gateCode
            p[Keys.LANGUAGE] = next.language
            p[Keys.PERSONA_TONE] = next.personaTone
            p[Keys.BACKEND_URL] = next.backendUrl
            p[Keys.API_KEY] = next.apiKey
            p[Keys.FCM_TOKEN] = next.fcmToken
            p[Keys.ONBOARDING_DONE] = next.onboardingDone
            p[Keys.INTERNATIONAL_POLICY] = next.internationalPolicy
            p[Keys.SCREENING_RULES_JSON] = next.screeningRulesJson
            p[Keys.SMS_BLOCK_PROMOTIONAL] = next.smsBlockPromotional
            p[Keys.SMS_BLOCK_SERVICE] = next.smsBlockService
            p[Keys.SMS_BLOCK_TRANSACTIONAL] = next.smsBlockTransactional
            p[Keys.SMS_BLOCK_GOVERNMENT] = next.smsBlockGovernment
            p[Keys.SMS_CONTENT_FALLBACK] = next.smsContentFallback
            p[Keys.INCOMING_WALLPAPER_PATH] = next.incomingWallpaperPath
            p[Keys.INCOMING_SCRIM_PERCENT] = next.incomingScrimPercent
        }
    }

    suspend fun setFcmToken(token: String) = update { it.copy(fcmToken = token) }

    suspend fun setOnboardingDone() = update { it.copy(onboardingDone = true) }
}
