package com.messages.app.report

/**
 * Pure formatting for carrier spam reports (Phase 4 item 17); JVM-testable.
 *
 * India (TRAI, researched 2026-07): forward the UCC to short code 1909 in the
 * format `<message text>, <sender number or header>, dd/mm/yy` (per
 * trai.gov.in FAQ). Complaints within 3 days of receipt are actionable
 * ("complaints"); older ones still help detection ("reports").
 *
 * Global (GSMA): forward the message text to 7726 (SPAM); the carrier replies
 * asking for the sender separately.
 */
object CarrierReportFormat {

    const val TRAI_SHORT_CODE = "1909"
    const val GSMA_SHORT_CODE = "7726"

    private const val THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000L

    /** Official TRAI 1909 complaint body: `<text>, <sender>, dd/mm/yy`. */
    fun traiComplaint(body: String, sender: String, receivedAtMillis: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = receivedAtMillis }
        val dd = "%02d".format(cal.get(java.util.Calendar.DAY_OF_MONTH))
        val mm = "%02d".format(cal.get(java.util.Calendar.MONTH) + 1)
        val yy = "%02d".format(cal.get(java.util.Calendar.YEAR) % 100)
        return "${body.trim()}, ${sender.trim()}, $dd/$mm/$yy"
    }

    /** 7726 expects just the message text; sender is asked for in a follow-up. */
    fun gsmaReport(body: String): String = body.trim()

    /** TRAI treats ≤3-day-old complaints as actionable; older ones as reports. */
    fun withinTraiComplaintWindow(receivedAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis - receivedAtMillis <= THREE_DAYS_MS
}
