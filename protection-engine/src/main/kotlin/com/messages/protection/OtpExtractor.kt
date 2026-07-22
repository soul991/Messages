package com.messages.protection

/**
 * Extracts the OTP digits from a message the engine has already labeled
 * [ProtectedLabel.OTP]. Pure and shared by the chat copy chip and the
 * notification copy action so the two can never disagree on the code.
 *
 * The heuristic is deliberately simple: the first standalone 4–8 digit run.
 * Word boundaries keep account fragments like "XX1234" from matching, and
 * the engine label gate keeps this from ever running on non-OTP messages.
 */
object OtpExtractor {

    private val OTP = Regex("""\b(\d{4,8})\b""")

    fun extract(body: String): String? = OTP.find(body)?.groupValues?.get(1)
}
