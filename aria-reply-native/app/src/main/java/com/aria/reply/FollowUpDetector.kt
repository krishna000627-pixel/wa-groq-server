package com.aria.reply

/**
 * Scans an Aria reply for commitment phrases and extracts a short description
 * of the promise so it can be persisted as a follow-up task.
 */
object FollowUpDetector {

    /**
     * Primary path: the system prompt instructs the model to emit a trailing
     * `[[FOLLOWUP: ...]]` tag when (and only when) it makes a commitment. This strips
     * that tag out of the visible reply and returns the commitment text alongside it.
     * Falls back to regex-based [detect] on the cleaned text if no tag is present, so
     * a custom/overridden system prompt still gets best-effort detection.
     *
     * @return Pair(replyTextWithTagRemoved, commitmentOrNull)
     */
    fun extractAndStrip(replyText: String): Pair<String, String?> {
        val match = AriaStore.FOLLOWUP_TAG_PATTERN.find(replyText)
        if (match != null) {
            val cleaned = replyText.replace(match.value, "").trimEnd()
            val commitment = match.groupValues.getOrNull(1)?.trim()?.take(160)
            return cleaned to commitment.takeUnless { it.isNullOrBlank() }
        }
        return replyText to detect(replyText)
    }

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
