package com.messages.app.ui.secret

import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.messages.core.secret.SecretCrypto

/**
 * V2-50. Which credential methods a person using assistive technology can
 * actually operate, and how the chooser says so.
 *
 * V2-38 made the pattern grid reachable — every dot is a semantic node with a
 * label, a state and a click action, and the grid works from a keyboard. That
 * fixed *operability*. It did not make pattern a good choice under a screen
 * reader: entering one still means tracking nine unlabelled positions in a
 * remembered spatial order, with no echo of what you have entered so far,
 * against a 30-second timeout, and with a wrong answer costing a cooldown.
 * A PIN or a password is a text field the platform's own IME, braille display
 * and switch-access scanning already handle.
 *
 * ## Why this recommends rather than restricts
 *
 * Removing pattern when a screen reader is on would be the wrong fix twice
 * over: it decides for the user what they can manage, and it silently changes
 * the set of methods available to someone who may already have a pattern
 * credential and simply turned TalkBack on today. So all three methods stay,
 * always, and the chooser adds one sentence naming the easier ones.
 *
 * ## Why it is conditional on touch exploration
 *
 * Shown unconditionally, "easier with a screen reader" is noise for the large
 * majority who are not using one — and copy that everyone learns to skip is
 * copy nobody reads when it matters. [touchExplorationOn] is the platform's own
 * answer to "is a screen reader driving this UI", so the note appears exactly
 * where it is information.
 *
 * The value is deliberately read at composition rather than cached: TalkBack
 * can be toggled with a shortcut mid-flow, and a cached answer would leave the
 * chooser advising the wrong thing for the rest of the session.
 */
object CredentialAccessibility {

    /**
     * True when the platform reports a touch-exploring accessibility service
     * (TalkBack and equivalents) is active.
     *
     * Fails to `false` rather than throwing: this only decides whether one
     * sentence of advice is shown, and a missing or misbehaving
     * `AccessibilityManager` must not take the credential chooser down with it.
     */
    fun touchExplorationOn(context: Context): Boolean = runCatching {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        am?.isTouchExplorationEnabled == true
    }.getOrDefault(false)

    /**
     * The methods the note names as easier. Text-entry methods, in the order
     * the chooser shows them.
     */
    val RECOMMENDED: List<String> = listOf(SecretCrypto.KIND_PIN, SecretCrypto.KIND_PASSWORD)

    fun isRecommended(kind: String): Boolean = kind in RECOMMENDED
}
