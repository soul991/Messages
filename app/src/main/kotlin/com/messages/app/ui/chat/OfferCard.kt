package com.messages.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messages.app.R
import com.messages.designsystem.categoryPalette

/**
 * Structured details extracted from promotional messages.
 */
data class OfferDetails(
    val headline: String,
    val discount: String? = null,
    val rechargeAmount: String? = null,
    val couponCode: String? = null,
    val validity: String? = null,
    val actionUrl: String? = null,
    val actionLabel: String = "View Offer",
)

/**
 * Deterministic extractor for commercial offer details (discounts, recharge plans,
 * promo coupon codes, validity dates, and primary action links).
 */
object OfferExtractor {

    private val DISCOUNT_PERCENT_REGEX = Regex(
        """(?i)\b(?:flat|upto|up to|extra|additional)?\s*(\d{1,2}\s?%)\s*(?:off|discount|cashback)?\b"""
    )
    private val DISCOUNT_AMOUNT_REGEX = Regex(
        """(?i)\b(?:flat|upto|up to|extra|additional)?\s*(?:₹|rs\.?|inr)?\s*(\d{2,4})\s*(?:off|discount|cashback)\b|\b(?:flat|upto|up to)\s*(?:₹|rs\.?|inr)\s*(\d{2,4})\b"""
    )
    private val RECHARGE_REGEX = Regex(
        """(?i)\b(?:recharge\s+(?:with|for|of|plan)?\s*(?:₹|rs\.?|inr)?\s*(\d{2,4})|(?:₹|rs\.?|inr)\s*(\d{2,4})\s*(?:recharge|plan))\b"""
    )
    private val COUPON_REGEX = Regex(
        """(?i)\b(?:use\s+code|coupon(?:\s+code)?|promo(?:\s+code)?|apply\s+code|use\s+promo|code)[\s:]+['"]?([A-Z0-9_-]{3,15})['"]?\b"""
    )
    private val VALIDITY_REGEX = Regex(
        """(?i)\b(?:valid\s+(?:till|until|for|through|today)|validity[\s:]+|expires\s+(?:on|in|at|today)?|ends\s+(?:on|in|at|today|tonight|soon)?|offer\s+valid\s+(?:till|until|for))\s*([^.!\n\r,]{2,25})"""
    )
    private val URL_REGEX = Regex("""https?://[^\s<>"]+""")

    fun extract(body: String): OfferDetails? {
        val trimmed = body.trim()
        if (trimmed.length < 8) return null

        val percentMatch = DISCOUNT_PERCENT_REGEX.find(trimmed)
        val amountMatch = DISCOUNT_AMOUNT_REGEX.find(trimmed)
        val discount = when {
            percentMatch != null -> percentMatch.groupValues[1].replace(" ", "")
            amountMatch != null -> {
                val amt = amountMatch.groupValues.filter { it.isNotBlank() }.drop(1).firstOrNull()
                if (amt != null) "₹$amt" else null
            }
            else -> null
        }

        val rechargeMatch = RECHARGE_REGEX.find(trimmed)
        val recharge = rechargeMatch?.let { match ->
            val amt = match.groupValues.filter { it.isNotBlank() }.drop(1).firstOrNull()
            if (amt != null) "₹$amt" else null
        }

        val couponMatch = COUPON_REGEX.find(trimmed)
        val couponCode = couponMatch?.groupValues?.getOrNull(1)?.uppercase()

        val validityMatch = VALIDITY_REGEX.find(trimmed)
        val validity = validityMatch?.let {
            val fullMatch = it.value.trim()
            val cleanTarget = it.groupValues.getOrNull(1)?.trim()?.trimEnd('.', ',') ?: ""
            if (cleanTarget.isNotBlank()) {
                if (fullMatch.startsWith("valid through", ignoreCase = true) || fullMatch.startsWith("validity", ignoreCase = true)) {
                    "Valid till $cleanTarget"
                } else if (fullMatch.startsWith("valid", ignoreCase = true) || fullMatch.startsWith("expires", ignoreCase = true) || fullMatch.startsWith("ends", ignoreCase = true)) {
                    fullMatch.trimEnd('.', ',')
                } else {
                    "Valid till $cleanTarget"
                }
            } else fullMatch.trimEnd('.', ',')
        }

        val urlMatch = URL_REGEX.find(trimmed)
        val actionUrl = urlMatch?.value

        // If no offer signal at all is found, check if it's a generic promo headline
        if (discount == null && recharge == null && couponCode == null && validity == null && actionUrl == null) {
            val promoMatch = Regex("""(?i)\b(mega\s+offer|special\s+offer|exclusive\s+offer|festive\s+offer|flash\s+sale|mega\s+sale|super\s+sale|season\s+sale|great\s+indian\s+festival|big\s+billion|flash\s+deal)\b""").find(trimmed)
            if (promoMatch != null) {
                val foundHeadline = promoMatch.value.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                val actionLabel = when {
                    trimmed.contains("recharge", ignoreCase = true) -> "Recharge Now"
                    trimmed.contains("track", ignoreCase = true) -> "Track Order"
                    trimmed.contains("food", ignoreCase = true) || trimmed.contains("swiggy", ignoreCase = true) || trimmed.contains("zomato", ignoreCase = true) -> "Order Now"
                    else -> "View Offer"
                }
                return OfferDetails(
                    headline = foundHeadline,
                    actionLabel = actionLabel,
                )
            }
            return null
        }

        val headline = when {
            discount != null && recharge != null -> "$discount on $recharge"
            discount != null && couponCode != null -> "$discount OFF with Code $couponCode"
            discount != null -> if (discount.contains("%") || discount.contains("₹")) "$discount OFF" else discount
            recharge != null -> "Recharge $recharge Plan"
            couponCode != null -> "Save with Code $couponCode"
            else -> "Special Offer"
        }

        val actionLabel = when {
            trimmed.contains("recharge", ignoreCase = true) -> "Recharge Now"
            trimmed.contains("track", ignoreCase = true) -> "Track Order"
            trimmed.contains("food", ignoreCase = true) || trimmed.contains("swiggy", ignoreCase = true) || trimmed.contains("zomato", ignoreCase = true) -> "Order Now"
            couponCode != null && actionUrl == null -> "Copy Code"
            else -> "View Offer"
        }

        return OfferDetails(
            headline = headline,
            discount = discount,
            rechargeAmount = recharge,
            couponCode = couponCode,
            validity = validity,
            actionUrl = actionUrl,
            actionLabel = actionLabel,
        )
    }
}

/**
 * Rich Offer Card component rendered in [ChatScreen] for promotional messages.
 */
@Composable
fun OfferCard(
    offer: OfferDetails,
    body: String,
    maxWidth: Dp,
    onCopyCode: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val promoPalette = categoryPalette("PROMOTIONS")
    val cardBg = promoPalette?.container ?: cs.secondaryContainer.copy(alpha = 0.5f)
    val tint = promoPalette?.tint ?: cs.primary
    val border = tint.copy(alpha = 0.35f)
    val cardBorder = if (selected) {
        androidx.compose.foundation.BorderStroke(2.dp, cs.primary)
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, border)
    }

    var codeCopied by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .widthIn(max = maxWidth)
            .padding(top = 4.dp, bottom = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        border = cardBorder,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Header: Category badge & icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(tint.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.LocalOffer,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "PROMOTIONAL OFFER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tint,
                        letterSpacing = 0.8.sp,
                    )
                }

                val badgeText = offer.discount ?: offer.rechargeAmount
                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(tint)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.surface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Main offer headline
            Text(
                text = offer.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )

            if (body.isNotBlank() && body != offer.headline) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }

            // Validity tag if available
            if (offer.validity != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "⏳ ${offer.validity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }

            // Coupon Code Pill
            if (offer.couponCode != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(cs.surface.copy(alpha = 0.7f))
                        .border(1.dp, border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable {
                            onCopyCode(offer.couponCode)
                            codeCopied = true
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CODE: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                        )
                        Text(
                            text = offer.couponCode,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = tint,
                            letterSpacing = 1.sp,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (codeCopied) "Copied!" else "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = tint,
                        )
                    }
                }
            }

            // Action Buttons
            if (offer.actionUrl != null || offer.couponCode != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (offer.actionUrl != null) {
                        Button(
                            onClick = { onOpenUrl(offer.actionUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = tint),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                offer.actionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }

                    if (offer.couponCode != null && offer.actionUrl != null) {
                        OutlinedButton(
                            onClick = {
                                onCopyCode(offer.couponCode)
                                codeCopied = true
                            },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (codeCopied) "Copied" else "Code",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
