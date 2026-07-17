package com.personal.detectivedialer.ui

import androidx.compose.ui.graphics.Color

/**
 * Display metadata for each call category.
 * [severe] categories (spam/rejected) get the red treatment everywhere.
 * [showsBadge] marks the non-default outcomes we surface as a call-log badge —
 * modern dialers only flag exceptions (spam / blocked / diverted), never the
 * normal "this call was allowed" case (bug batch #4a).
 */
enum class CategoryUi(
    val key: String,
    val label: String,
    val color: Color,
    val severe: Boolean = false,
    val showsBadge: Boolean = false,
) {
    ALLOW("ALLOW", "Allowed", Color(0xFF2E9E5B)),
    REJECT("REJECT", "Blocked", Color(0xFFE53935), severe = true, showsBadge = true),
    SPAM("SPAM", "Suspected Spam", Color(0xFFE53935), severe = true, showsBadge = true),
    VOICEMAIL("VOICEMAIL", "Voicemail", Color(0xFFF57C00), showsBadge = true),
    DELIVERY("DELIVERY", "Delivery", Color(0xFF0099FF)),
    VENDOR("VENDOR", "Vendor", Color(0xFF7E57C2)),
    PERSONAL("PERSONAL", "Personal", Color(0xFF2E9E5B)),
    URGENT("URGENT", "Urgent", Color(0xFFF57C00)),
    UNKNOWN("UNKNOWN", "Unknown", Color(0xFF78909C));

    companion object {
        fun from(key: String?): CategoryUi =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: UNKNOWN

        /**
         * The badge to show for a call, or null when it's a default/allowed outcome
         * that should carry no badge. Prefers the policy [action] (RING → no badge,
         * VOICEMAIL → Voicemail), then falls back to an exception [category].
         */
        fun badgeFor(category: String?, action: String?): CategoryUi? {
            when (action?.trim()?.uppercase()) {
                "RING" -> return null
                "VOICEMAIL" -> return VOICEMAIL
                "REJECT" -> return REJECT
            }
            return from(category).takeIf { it.showsBadge }
        }
    }
}
