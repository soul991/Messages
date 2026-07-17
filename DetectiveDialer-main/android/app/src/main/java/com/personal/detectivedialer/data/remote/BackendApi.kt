package com.personal.detectivedialer.data.remote

import com.personal.detectivedialer.data.screening.ScreeningRulesResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// ── Wire models ─────────────────────────────────────────────────────────
//
// Serialized with Moshi's KotlinJsonAdapterFactory (reflection). Do NOT add
// @JsonClass(generateAdapter = true) here: that annotation makes the factory
// defer to codegen adapters this project doesn't generate (no moshi codegen
// KSP), which crashes every request with "Unable to create adapter".
// Proguard keeps this package (see proguard-rules.pro) so reflection works
// in release builds too.

data class TurnDto(
    val role: String = "",
    val text: String = "",
)

data class CallDto(
    val id: String = "",
    val from: String = "",
    /** Network-verified caller name (CNAP), recorded by /screen when present. */
    val callerId: String = "",
    val startedAt: Long = 0,
    val endedAt: Long = 0,
    val duration: Int = 0,
    val category: String = "UNKNOWN",
    /** Policy action (RING | VOICEMAIL | REJECT); blank on a legacy backend. */
    val action: String = "",
    val confidence: Float = 0f,
    val summary: String = "",
    val recordingUrl: String = "",
    val turns: List<TurnDto> = emptyList(),
)

data class CallsResponse(val calls: List<CallDto> = emptyList())

data class NumberBody(
    val number: String,
    val label: String? = null,
    val reason: String? = null,
    val source: String? = null,
)

data class BlockedDto(val number: String = "", val source: String = "", val reason: String = "", val addedAt: Long = 0)

data class AllowedDto(val number: String = "", val label: String = "", val addedAt: Long = 0)

data class BlocklistResponse(val blocked: List<BlockedDto> = emptyList())

data class AllowlistResponse(val allowed: List<AllowedDto> = emptyList())

data class OkResponse(val ok: Boolean = true)

data class DeviceBody(val token: String)

// ── On-device screening ──────────────────────────────────────────────────

data class ScreenRequest(
    val phoneNumber: String,
    val callerId: String? = null,
    val timestamp: Long = 0,
)

data class ScreenResponse(
    val decision: String = "ALLOW",
    /**
     * Policy action the app branches on: RING | VOICEMAIL | REJECT. Kept separate
     * from [decision] (the human-facing label). Blank on a legacy backend — the
     * app derives a safe default from [decision] in that case.
     */
    val action: String = "",
    val reason: String = "",
    val confidence: Float = 0f,
)

// ── SMS content screening (opt-in fallback) ──────────────────────────────

data class ScreenSmsRequest(
    val sender: String,
    val text: String,
)

data class ScreenSmsResponse(
    val verdict: String = "OK",
    val reason: String = "",
    val confidence: Float = 0f,
)

// ── Retrofit interface ──────────────────────────────────────────────────

interface BackendApi {
    /**
     * Lightweight health ping used to warm the backend + the OkHttp connection
     * pool before a real /screen call. Returns raw bytes so no DTO/parsing is
     * needed and it works against the backend's open /healthz endpoint.
     */
    @GET("healthz")
    suspend fun health(): okhttp3.ResponseBody

    @POST("screen")
    suspend fun screen(@Body body: ScreenRequest): ScreenResponse

    @POST("screen-sms")
    suspend fun screenSms(@Body body: ScreenSmsRequest): ScreenSmsResponse

    @POST("api/device")
    suspend fun registerDevice(@Body body: DeviceBody): OkResponse

    @GET("api/screening-rules")
    suspend fun getScreeningRules(): ScreeningRulesResponse

    @GET("api/calls")
    suspend fun getCalls(): CallsResponse

    @GET("api/call/{id}")
    suspend fun getCall(@Path("id") id: String): CallDto

    @GET("api/blocklist")
    suspend fun getBlocklist(): BlocklistResponse

    @POST("api/blocklist")
    suspend fun addBlock(@Body body: NumberBody): BlockedDto

    @DELETE("api/blocklist/{number}")
    suspend fun removeBlock(@Path("number") number: String): OkResponse

    @GET("api/allowlist")
    suspend fun getAllowlist(): AllowlistResponse

    @POST("api/allowlist")
    suspend fun addAllow(@Body body: NumberBody): AllowedDto

    @DELETE("api/allowlist/{number}")
    suspend fun removeAllow(@Path("number") number: String): OkResponse
}
