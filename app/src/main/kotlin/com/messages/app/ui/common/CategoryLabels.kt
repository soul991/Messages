package com.messages.app.ui.common

import androidx.annotation.StringRes
import com.messages.app.R

/**
 * V2-36. Maps a stored category id to the string resource that names it.
 *
 * The ids ("INBOX", "SPAM", …) are storage, not copy. Several screens used to
 * show them by lowercasing and re-capitalising the id, which produces English
 * in every locale — and in Turkish produces "Inbox" → "ınbox", because
 * `lowercase()` without a locale argument follows the device's. One mapping,
 * used everywhere, is also what keeps the tab, the verdict header and the rule
 * list saying the same word for the same bucket.
 */
@StringRes
fun categoryLabelRes(category: String): Int = when (category) {
    "SPAM" -> R.string.category_spam
    "PROMOTIONS" -> R.string.category_promotions
    "TRANSACTIONS" -> R.string.category_transactions
    "REVIEW" -> R.string.category_review
    "BLOCKED" -> R.string.category_blocked
    else -> R.string.category_inbox
}
