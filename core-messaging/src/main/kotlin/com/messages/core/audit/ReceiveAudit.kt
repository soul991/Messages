package com.messages.core.audit

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic Receive Audit: traces every incoming message at each hop in the receive pipeline:
 * 1. RECEIVED: Intent / broadcast received from system
 * 2. PARSED: PDUs extracted and reassembled into address & body
 * 3. STORED: Written to Telephony provider and local Room database
 * 4. CLASSIFIED: Processed by protection engine with category verdict
 * 5. DISPLAYED: Conversation summary updated, notifications dispatched / UI updated
 */
object ReceiveAudit {
    private const val TAG = "ReceiveAudit"
    private const val MAX_LOG_ENTRIES = 200

    val receivedCount = AtomicLong(0)
    val parsedCount = AtomicLong(0)
    val storedCount = AtomicLong(0)
    val classifiedCount = AtomicLong(0)
    val displayedCount = AtomicLong(0)
    val droppedCount = AtomicLong(0)

    data class AuditEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val stage: Stage,
        val address: String,
        val bodySnippet: String,
        val details: String = "",
    )

    enum class Stage {
        RECEIVED,
        PARSED,
        STORED,
        CLASSIFIED,
        DISPLAYED,
        DROPPED,
    }

    private val auditLog = ConcurrentLinkedQueue<AuditEntry>()

    fun record(stage: Stage, address: String, body: String, details: String = "") {
        when (stage) {
            Stage.RECEIVED -> receivedCount.incrementAndGet()
            Stage.PARSED -> parsedCount.incrementAndGet()
            Stage.STORED -> storedCount.incrementAndGet()
            Stage.CLASSIFIED -> classifiedCount.incrementAndGet()
            Stage.DISPLAYED -> displayedCount.incrementAndGet()
            Stage.DROPPED -> droppedCount.incrementAndGet()
        }

        val snippet = if (body.length > 30) body.take(30) + "…" else body
        val entry = AuditEntry(
            stage = stage,
            address = address,
            bodySnippet = snippet,
            details = details,
        )

        auditLog.offer(entry)
        while (auditLog.size > MAX_LOG_ENTRIES) {
            auditLog.poll()
        }

        Log.d(TAG, "[$stage] addr='$address' snippet='$snippet' details='$details'")
    }

    fun getCounts(): Map<String, Long> = mapOf(
        "received" to receivedCount.get(),
        "parsed" to parsedCount.get(),
        "stored" to storedCount.get(),
        "classified" to classifiedCount.get(),
        "displayed" to displayedCount.get(),
        "dropped" to droppedCount.get(),
    )

    fun getRecentLog(): List<AuditEntry> = auditLog.toList()

    fun resetForTests() {
        receivedCount.set(0)
        parsedCount.set(0)
        storedCount.set(0)
        classifiedCount.set(0)
        displayedCount.set(0)
        droppedCount.set(0)
        auditLog.clear()
    }
}
