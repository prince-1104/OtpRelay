package com.prince.otprelay.review

/** A locked/background/expired check must not consume the pending presentation. */
internal class ReviewPresentationGate {
    private var presented: String? = null

    fun take(token: String?, resumed: Boolean, unlocked: Boolean, eligible: Boolean): Boolean {
        if (token == null || token == presented || !resumed || !unlocked || !eligible) return false
        presented = token
        return true
    }
}
