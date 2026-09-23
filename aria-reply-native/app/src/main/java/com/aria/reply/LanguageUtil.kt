package com.aria.reply

/**
 * Lightweight heuristic to tag the incoming message's language/script so it can be
 * passed to the model explicitly — relying on the model to infer this silently is
 * less reliable than just telling it, especially for Hinglish (Hindi in Roman script).
 */
object LanguageUtil {

    private val DEVANAGARI = Regex("[\\u0900-\\u097F]")

    // Common Hindi words written in Roman script — enough to flag Hinglish, not meant
    // to be exhaustive.
    private val HINGLISH_WORDS = setOf(
        "hai", "hain", "ho", "kya", "kyu", "kyun", "kaise", "kaisi", "kab", "kahan",
        "raha", "rahi", "rahe", "tha", "thi", "the", "nahi", "nahin", "haan", "acha",
        "accha", "theek", "thik", "bata", "batao", "bhai", "yaar", "matlab", "abhi",
        "milte", "milenge", "karo", "karenge", "kar", "chal", "chalo", "aa", "aaja",
        "aaraha", "ruka", "rukja", "padh", "padhai", "khatam", "wala", "wale", "waali",
        "mein", "main", "tum", "aap", "hum", "kuch", "kaam", "dena", "dunga", "dedo"
    )

    enum class Detected(val label: String) {
        HINDI("Hindi (Devanagari script)"),
        HINGLISH("Hinglish (Hindi written in Roman letters)"),
        ENGLISH("English")
    }

    fun detect(text: String): Detected {
        if (DEVANAGARI.containsMatchIn(text)) return Detected.HINDI
        val words = text.lowercase().split(Regex("[^a-z']+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return Detected.ENGLISH
        val hits = words.count { it in HINGLISH_WORDS }
        // Require at least 2 hits (or 1 hit in a very short message) to avoid false
        // positives on genuinely English text that happens to share a short word.
        val threshold = if (words.size <= 3) 1 else 2
        return if (hits >= threshold) Detected.HINGLISH else Detected.ENGLISH
    }
}
