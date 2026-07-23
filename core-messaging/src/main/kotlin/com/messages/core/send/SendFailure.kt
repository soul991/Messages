package com.messages.core.send

/**
 * Human-readable reasons for SMS send failures. The raw code is the
 * `resultCode` the platform delivers to the sent-status PendingIntent — the
 * `android.telephony.SmsManager.RESULT_*` constants. Values are duplicated
 * here as literals (verified against the platform-35 android.jar) so this
 * stays pure Kotlin and JVM-testable; they are stable public API.
 *
 * Wording rule: carriers do NOT report insufficient balance as a distinct
 * code — it arrives as GENERIC_FAILURE — so the generic wording mentions
 * balance only as one possibility and never claims to know the cause.
 */
object SendFailure {

    /** Synthetic code: SmsManager threw before the radio ever saw the message
     *  (no SIM for the subscription, malformed destination, …). Negative so it
     *  can never collide with a platform result code (0+) — note the platform
     *  reports success as Activity.RESULT_OK = -1, which never reaches
     *  markFailed. */
    const val LOCAL_SEND_ERROR = -2

    // SmsManager.RESULT_* (platform values)
    const val RESULT_ERROR_GENERIC_FAILURE = 1
    const val RESULT_ERROR_RADIO_OFF = 2
    const val RESULT_ERROR_NULL_PDU = 3
    const val RESULT_ERROR_NO_SERVICE = 4
    const val RESULT_ERROR_LIMIT_EXCEEDED = 5
    const val RESULT_ERROR_FDN_CHECK_FAILURE = 6
    const val RESULT_ERROR_SHORT_CODE_NOT_ALLOWED = 7
    const val RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED = 8
    const val RESULT_RADIO_NOT_AVAILABLE = 9
    const val RESULT_NETWORK_REJECT = 10
    const val RESULT_NETWORK_ERROR = 17
    const val RESULT_INVALID_SMSC_ADDRESS = 19
    const val RESULT_SMS_BLOCKED_DURING_EMERGENCY = 29
    const val RESULT_SMS_SEND_RETRY_FAILED = 30
    const val RESULT_NO_DEFAULT_SMS_APP = 32
    const val RESULT_RIL_RADIO_NOT_AVAILABLE = 100
    const val RESULT_RIL_SMS_SEND_FAIL_RETRY = 101
    const val RESULT_RIL_NETWORK_REJECT = 102
    const val RESULT_RIL_NO_MEMORY = 105
    const val RESULT_RIL_REQUEST_RATE_LIMITED = 106
    const val RESULT_RIL_INVALID_SMS_FORMAT = 107
    const val RESULT_RIL_ENCODING_ERR = 109
    const val RESULT_RIL_INVALID_SMSC_ADDRESS = 110
    const val RESULT_RIL_MODEM_ERR = 111
    const val RESULT_RIL_NETWORK_ERR = 112
    const val RESULT_RIL_NETWORK_NOT_READY = 116
    const val RESULT_RIL_CANCELLED = 119
    const val RESULT_RIL_SIM_ABSENT = 120
    const val RESULT_RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED = 121
    const val RESULT_RIL_ACCESS_BARRED = 122
    const val RESULT_RIL_BLOCKED_DUE_TO_CALL = 123
    const val RESULT_RIL_NO_NETWORK_FOUND = 135

    private const val GENERIC =
        "Couldn't send — check network coverage or account balance"

    /**
     * One-line reason for a stored failure code. Null code (legacy rows,
     * MMS failures) falls back to the honest generic wording.
     */
    fun reasonFor(code: Int?): String = when (code) {
        null -> GENERIC
        RESULT_ERROR_NO_SERVICE,
        RESULT_RIL_NO_NETWORK_FOUND,
        -> "No network signal"
        RESULT_ERROR_RADIO_OFF,
        RESULT_RADIO_NOT_AVAILABLE,
        RESULT_RIL_RADIO_NOT_AVAILABLE,
        -> "Airplane mode is on or the radio is off"
        RESULT_ERROR_LIMIT_EXCEEDED,
        RESULT_RIL_REQUEST_RATE_LIMITED,
        -> "Sending limit reached — try again later"
        RESULT_RIL_SIM_ABSENT -> "No SIM card"
        RESULT_ERROR_FDN_CHECK_FAILURE -> "Blocked by the SIM's fixed dialling list"
        RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
        RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED,
        -> "Sending to this short code isn't allowed"
        RESULT_NETWORK_REJECT,
        RESULT_RIL_NETWORK_REJECT,
        RESULT_RIL_ACCESS_BARRED,
        -> "The network rejected the message"
        RESULT_NETWORK_ERROR,
        RESULT_RIL_NETWORK_ERR,
        RESULT_RIL_NETWORK_NOT_READY,
        RESULT_RIL_SMS_SEND_FAIL_RETRY,
        RESULT_SMS_SEND_RETRY_FAILED,
        -> "Network problem — try again"
        RESULT_INVALID_SMSC_ADDRESS,
        RESULT_RIL_INVALID_SMSC_ADDRESS,
        -> "Message centre number problem — check SIM settings"
        RESULT_ERROR_NULL_PDU,
        RESULT_RIL_INVALID_SMS_FORMAT,
        RESULT_RIL_ENCODING_ERR,
        -> "Couldn't build the message"
        RESULT_RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED,
        RESULT_RIL_BLOCKED_DUE_TO_CALL,
        -> "Can't send during the current call"
        RESULT_SMS_BLOCKED_DURING_EMERGENCY -> "Sending is blocked during an emergency call"
        RESULT_NO_DEFAULT_SMS_APP -> "Messages isn't the default SMS app"
        RESULT_RIL_MODEM_ERR -> "Phone radio error — try again"
        RESULT_RIL_NO_MEMORY -> "Not enough storage to send"
        RESULT_RIL_CANCELLED -> "Send was cancelled"
        LOCAL_SEND_ERROR -> "Couldn't send — check the SIM and the number"
        RESULT_ERROR_GENERIC_FAILURE -> GENERIC
        else -> GENERIC
    }

    /** Info-sheet debug line: reason plus the raw code when one was stored. */
    fun detailFor(code: Int?): String =
        if (code == null) reasonFor(null) else "${reasonFor(code)} (code $code)"
}
