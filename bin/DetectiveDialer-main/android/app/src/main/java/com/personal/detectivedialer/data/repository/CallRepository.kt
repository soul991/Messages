package com.personal.detectivedialer.data.repository

import com.personal.detectivedialer.data.local.AllowedNumber
import com.personal.detectivedialer.data.local.AllowedNumberDao
import com.personal.detectivedialer.data.local.BlockedNumber
import com.personal.detectivedialer.data.local.BlockedNumberDao
import com.personal.detectivedialer.data.local.CallLogDao
import com.personal.detectivedialer.data.local.CallLogEntry
import com.personal.detectivedialer.data.prefs.SettingsRepository
import com.personal.detectivedialer.data.remote.BackendApi
import com.personal.detectivedialer.data.remote.CallDto
import com.personal.detectivedialer.data.remote.DeviceBody
import com.personal.detectivedialer.data.remote.NumberBody
import com.personal.detectivedialer.data.remote.ScreenRequest
import com.personal.detectivedialer.data.remote.ScreenResponse
import com.personal.detectivedialer.data.remote.ScreenSmsRequest
import com.personal.detectivedialer.data.remote.ScreenSmsResponse
import com.personal.detectivedialer.data.screening.PrefixRule
import com.personal.detectivedialer.data.screening.ScreeningMatcher
import com.personal.detectivedialer.data.screening.ScreeningRulesResponse
import com.personal.detectivedialer.di.ScreenClient
import com.personal.detectivedialer.service.ContactsHelper
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for call data. Local Room DB is authoritative for the
 * fast call-screening decision; the backend is synced on demand for history.
 */
@Singleton
class CallRepository @Inject constructor(
    private val blockedDao: BlockedNumberDao,
    private val allowedDao: AllowedNumberDao,
    private val callLogDao: CallLogDao,
    private val settings: SettingsRepository,
    private val contacts: ContactsHelper,
    private val okHttp: OkHttpClient,
    @ScreenClient private val screenOkHttp: OkHttpClient,
    private val moshi: Moshi,
) {
    // ── Local lookups (used by CallScreeningService — must be fast) ──────
    suspend fun isBlocked(number: String): Boolean =
        blockedDao.countByNumber(normalize(number)) > 0

    suspend fun isAllowed(number: String): Boolean =
        allowedDao.countByNumber(normalize(number)) > 0

    fun observeBlocked(): Flow<List<BlockedNumber>> = blockedDao.observeAll()
    fun observeAllowed(): Flow<List<AllowedNumber>> = allowedDao.observeAll()
    fun observeCalls(): Flow<List<CallLogEntry>> = callLogDao.observeAll()
    fun observeCall(id: String): Flow<CallLogEntry?> = callLogDao.observeById(id)

    /**
     * Complete history across one or more numbers, newest-first — the unified
     * detail page passes a single unknown number, or all of a saved contact's
     * numbers to merge them into one timeline. Numbers are normalized to match
     * how history rows are stored; blanks and duplicates are dropped.
     */
    fun observeCallsByNumbers(numbers: List<String>): Flow<List<CallLogEntry>> =
        callLogDao.observeByNumbers(numbers.map { normalize(it) }.filter { it.isNotBlank() }.distinct())

    suspend fun getCall(id: String): CallLogEntry? = callLogDao.getById(id)

    /**
     * Ask the backend to classify an unknown caller. Uses the short-timeout
     * client so a slow backend can't blow the CallScreeningService deadline.
     * Returns null when no backend URL is configured or the request fails
     * (caller should fall back to ringing).
     */
    suspend fun screen(
        number: String,
        callerId: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ): ScreenResponse? = runCatching {
        screenApi()?.screen(
            ScreenRequest(
                phoneNumber = normalize(number),
                callerId = callerId,
                timestamp = timestamp,
            ),
        )
    }.getOrNull()

    /**
     * Warm the screening path before a call arrives. Hits /healthz on the
     * short-timeout screen client so its OkHttp connection pool has a live
     * TCP+TLS connection ready — the real /screen call then skips the handshake
     * and answers well inside the ~5s CallScreeningService budget. Complements
     * the backend's own keep-warm (which prevents Railway cold starts).
     * Best-effort: a failure here just means the next /screen pays setup cost.
     */
    suspend fun warmUp(): Boolean = runCatching {
        screenApi()?.health()?.close()
        true
    }.getOrDefault(false)

    /** Register this device's FCM token so the backend can push call summaries. */
    suspend fun registerDevice(token: String): Boolean =
        runCatching { api()?.registerDevice(DeviceBody(token)) }.getOrNull() != null

    /**
     * Optional SMS content classification (short-timeout client — an incoming
     * SMS receiver must not hang). Null when unconfigured or unreachable.
     */
    suspend fun screenSms(sender: String, text: String): ScreenSmsResponse? = runCatching {
        screenApi()?.screenSms(ScreenSmsRequest(sender = sender, text = text))
    }.getOrNull()

    // ── Local screening rules (TRAI prefix tier) ─────────────────────────
    private val rulesAdapter by lazy { moshi.adapter(ScreeningRulesResponse::class.java) }

    /**
     * Active prefix rules for the instant offline tier: the backend-refreshed
     * table when we have one cached, else the built-in TRAI defaults.
     */
    suspend fun screeningRules(): List<PrefixRule> {
        val json = settings.settings.first().screeningRulesJson
        if (json.isBlank()) return ScreeningMatcher.DEFAULT_RULES
        return runCatching { rulesAdapter.fromJson(json)?.rules }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: ScreeningMatcher.DEFAULT_RULES
    }

    /** Pull the rules table from the backend so new rules land without an APK rebuild. */
    suspend fun syncScreeningRules() {
        val resp = runCatching { api()?.getScreeningRules() }.getOrNull() ?: return
        if (resp.rules.isEmpty()) return
        val json = runCatching { rulesAdapter.toJson(resp) }.getOrNull() ?: return
        settings.update { it.copy(screeningRulesJson = json) }
    }

    // ── Mutations (local + best-effort remote sync) ─────────────────────
    suspend fun block(number: String, source: String = "user", reason: String = "") {
        val n = normalize(number)
        blockedDao.insert(BlockedNumber(number = n, source = source, reason = reason))
        runCatching { api()?.addBlock(NumberBody(number = n, reason = reason, source = source)) }
    }

    suspend fun unblock(number: String) {
        val n = normalize(number)
        blockedDao.deleteByNumber(n)
        runCatching { api()?.removeBlock(n) }
    }

    suspend fun allow(number: String, label: String = "") {
        val n = normalize(number)
        allowedDao.insert(AllowedNumber(number = n, label = label))
        runCatching { api()?.addAllow(NumberBody(number = n, label = label)) }
    }

    suspend fun removeAllow(number: String) {
        val n = normalize(number)
        allowedDao.deleteByNumber(n)
        runCatching { api()?.removeAllow(n) }
    }

    suspend fun upsertCall(entry: CallLogEntry) = callLogDao.upsert(entry)

    /**
     * Record the outcome of a real call that rang through our InCallService.
     * Reconciles with the pre-ring screening row for the same number when one
     * exists (within a tight window) so a single physical call is one history
     * row carrying both the AI verdict and the actual disposition; otherwise
     * inserts a fresh row (contacts and all outgoing calls take this path).
     */
    suspend fun logCallOutcome(
        number: String,
        callerName: String,
        type: String,
        timestamp: Long,
        durationSeconds: Int,
    ) {
        val n = normalize(number)
        // The screening row is written seconds before the call rings; match a
        // small window around this call's start so we never merge an unrelated
        // earlier call from the same number.
        val existing = if (n.isNotBlank()) {
            callLogDao.findRecentByNumber(n, timestamp - RECONCILE_WINDOW_MS, timestamp + RECONCILE_WINDOW_MS)
        } else {
            null
        }
        if (existing != null && existing.type.isBlank()) {
            callLogDao.upsert(
                existing.copy(
                    type = type,
                    duration = if (durationSeconds > 0) durationSeconds else existing.duration,
                    // Prefer the freshly resolved name (contact/CNAP settled by call
                    // end); only keep the screening row's name if we resolved none.
                    callerName = callerName.ifBlank { existing.callerName },
                ),
            )
            return
        }
        callLogDao.upsert(
            CallLogEntry(
                id = "call-$n-$timestamp",
                number = n,
                callerName = callerName,
                timestamp = timestamp,
                category = "",
                type = type,
                duration = durationSeconds,
            ),
        )
    }

    /**
     * Re-resolve the display name for a number against the current contacts and
     * stamp it onto every history row for that number. Used after the user saves
     * a previously-unknown number so the call log reflects the new name without an
     * app restart. No-op (returns null) when the number resolves to no contact —
     * we never overwrite an existing name/CNAP with a blank.
     */
    suspend fun refreshCallerName(number: String): String? {
        val n = normalize(number)
        if (n.isBlank()) return null
        val resolved = contacts.displayNameFor(number)?.takeIf { it.isNotBlank() } ?: return null
        callLogDao.updateCallerName(n, resolved)
        return resolved
    }

    /**
     * One-pass backfill: for existing history rows that never resolved a name,
     * try the current contacts again. Only fills blanks — an existing name/CNAP is
     * never touched — so this is safe to run opportunistically (e.g. on refresh)
     * and picks up numbers the user has saved since the call happened. Bounded so
     * a very long history can't stall the contacts provider.
     */
    suspend fun backfillMissingNames(limit: Int = 100) {
        val numbers = runCatching { callLogDao.numbersMissingName(limit) }.getOrNull().orEmpty()
        for (number in numbers) {
            val resolved = contacts.displayNameFor(number)?.takeIf { it.isNotBlank() } ?: continue
            runCatching { callLogDao.updateCallerName(normalize(number), resolved) }
        }
    }

    /** Pull a full call record from the backend and cache it locally. */
    suspend fun refreshCall(id: String): CallLogEntry? {
        val dto = runCatching { api()?.getCall(id) }.getOrNull() ?: return callLogDao.getById(id)
        val entry = dto.toEntry()
        callLogDao.upsert(entry)
        return entry
    }

    /** Sync the whole call history from the backend into Room. */
    suspend fun syncCalls() {
        val resp = runCatching { api()?.getCalls() }.getOrNull() ?: return
        resp.calls.forEach { callLogDao.upsert(it.toEntry()) }
    }

    /**
     * Pull the backend's block/allow lists into Room so server-side changes
     * (e.g. spam auto-blocks) take effect on-device.
     */
    suspend fun syncLists() {
        runCatching { api()?.getBlocklist() }.getOrNull()?.blocked?.forEach {
            if (it.number.isNotBlank()) {
                blockedDao.insert(
                    BlockedNumber(
                        number = normalize(it.number),
                        source = it.source.ifBlank { "server" },
                        reason = it.reason,
                        addedAt = if (it.addedAt > 0) it.addedAt else System.currentTimeMillis(),
                    ),
                )
            }
        }
        runCatching { api()?.getAllowlist() }.getOrNull()?.allowed?.forEach {
            if (it.number.isNotBlank()) {
                allowedDao.insert(
                    AllowedNumber(
                        number = normalize(it.number),
                        label = it.label,
                        addedAt = if (it.addedAt > 0) it.addedAt else System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    // ── Retrofit built lazily from the configured backend URL ───────────
    private val apiLock = Any()
    private var cachedBaseUrl: String? = null
    private var cachedApi: BackendApi? = null
    private var cachedScreenApi: BackendApi? = null

    private suspend fun api(): BackendApi? = apis()?.first
    private suspend fun screenApi(): BackendApi? = apis()?.second

    private suspend fun apis(): Pair<BackendApi, BackendApi>? {
        val configured = settings.settings.first().backendUrl
        val base = normalizeBackendUrl(configured)?.plus("/") ?: return null
        synchronized(apiLock) {
            val api = cachedApi
            val screenApi = cachedScreenApi
            if (base == cachedBaseUrl && api != null && screenApi != null) return Pair(api, screenApi)
            val httpUrl = base.toHttpUrlOrNull() ?: return null
            fun build(client: OkHttpClient): BackendApi = Retrofit.Builder()
                .baseUrl(httpUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(BackendApi::class.java)
            val newApi = build(okHttp)
            val newScreenApi = build(screenOkHttp)
            cachedBaseUrl = base
            cachedApi = newApi
            cachedScreenApi = newScreenApi
            return Pair(newApi, newScreenApi)
        }
    }

    private fun CallDto.toEntry(): CallLogEntry {
        val transcript = turns.joinToString("\n") { "${it.role}: ${it.text}" }
        return CallLogEntry(
            id = id,
            number = from,
            callerName = callerId,
            timestamp = if (startedAt > 0) startedAt else System.currentTimeMillis(),
            category = category,
            action = action,
            summary = summary,
            transcript = transcript,
            recordingUrl = recordingUrl,
            duration = duration,
            confidence = confidence,
        )
    }

    companion object {
        /** Window for matching a call outcome to its pre-ring screening row. */
        private const val RECONCILE_WINDOW_MS = 5 * 60 * 1000L

        /** Matches the backend's normalizeNumber: digits only, `+` kept only as the leading char. */
        fun normalize(raw: String): String =
            raw.replace(Regex("[^\\d+]"), "").replace(Regex("(?!^)\\+"), "")

        /**
         * Canonicalize a user-entered backend URL: trim, default to https://
         * when no scheme is given, and validate. Returns null when the input
         * can't be turned into a usable URL.
         */
        fun normalizeBackendUrl(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme =
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
                else "https://$trimmed"
            return withScheme.toHttpUrlOrNull()?.toString()?.trimEnd('/')
        }
    }
}
