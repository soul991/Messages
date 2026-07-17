package com.personal.detectivedialer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A number that should be rejected silently. */
@Entity(tableName = "blocked_numbers", indices = [Index(value = ["number"], unique = true)])
data class BlockedNumber(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    /** "user" or "ai_learned" */
    val source: String = "user",
    val addedAt: Long = System.currentTimeMillis(),
    val reason: String = "",
)

/** A number that is allowed to ring through (contacts + manually saved). */
@Entity(tableName = "allowed_numbers", indices = [Index(value = ["number"], unique = true)])
data class AllowedNumber(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val label: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

/** A screened call summary, mirrored from the backend / built locally. */
@Entity(tableName = "call_log")
data class CallLogEntry(
    @PrimaryKey val id: String,
    val number: String,
    /** Network-verified caller name (CNAP), when the carrier provided one. */
    val callerName: String = "",
    val timestamp: Long,
    /** AI verdict: SPAM | DELIVERY | VENDOR | PERSONAL | URGENT | UNKNOWN | ALLOW | REJECT. */
    val category: String,
    /**
     * Policy action taken for this call: RING | VOICEMAIL | REJECT (blank when the
     * row predates screening or came from a legacy backend). Orthogonal to
     * [category] (the label) — the call-log UI badges the exception cases from this.
     */
    val action: String = "",
    val summary: String = "",
    val transcript: String = "",
    val recordingUrl: String = "",
    val duration: Int = 0,
    val confidence: Float = 0f,
    /**
     * Call disposition, orthogonal to [category] (the AI verdict). Written by the
     * telecom layer when a real call ends. Blank on rows that only ever came from
     * pre-ring screening / backend push and never rang through our InCallService.
     * INCOMING | OUTGOING | MISSED | REJECTED.
     */
    val type: String = TYPE_NONE,
) {
    companion object {
        const val TYPE_NONE = ""
        const val TYPE_INCOMING = "INCOMING"
        const val TYPE_OUTGOING = "OUTGOING"
        const val TYPE_MISSED = "MISSED"
        const val TYPE_REJECTED = "REJECTED"
    }
}
