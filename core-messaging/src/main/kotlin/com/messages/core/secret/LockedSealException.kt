package com.messages.core.secret

/**
 * V2-6b — locked text could not be sealed at ANY grade: the Keystore content
 * key is unavailable AND the software fallback key could not be minted or
 * used. This never means "store plaintext"; callers on the intake path degrade
 * to a tombstone row (empty text, provider copy kept), and user-initiated
 * paths surface the failure.
 */
open class LockedSealException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * A user-initiated locked-space write refused while the content key is
 * unavailable. Unlike arriving mail — which nobody can refuse and which is
 * therefore held at the degraded pending grade — the user is present to see
 * this error, so the honest answer is to decline rather than degrade.
 */
class LockedWriteBlockedException :
    LockedSealException("Locked space is read-only: content key unavailable")
