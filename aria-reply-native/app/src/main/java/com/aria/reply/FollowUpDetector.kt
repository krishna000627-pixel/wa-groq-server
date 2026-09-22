package com.aria.reply

/**
 * Scans an Aria reply for commitment phrases and extracts a short description
 * of the promise so it can be persisted as a follow-up task.
 */
object FollowUpDetector {

    /**
     * Returns a non-null commitment description if [replyText] contains a commitment,
     * or null if no follow-up is needed.
     */
    fun detect(replyText: String): String? {
        val text = replyText.trim()
        for (pattern in AriaStore.COMMITMENT_PATTERNS) {
            val match = pattern.find(text) ?: continue
            // Grab the sentence containing the match (up to ~120 chars)
            val start = text.lastIndexOf('.', match.range.first).let { if (it < 0) 0 else it + 1 }
            val end   = text.indexOf('.', match.range.last).let { if (it < 0) text.length else minOf(it + 1, text.length) }
            val sentence = text.substring(start, end).trim().take(120)
            return sentence.ifBlank { text.take(120) }
        }
        return null
    }
}
