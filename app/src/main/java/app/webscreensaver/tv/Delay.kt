package app.webscreensaver.tv

/** How long each page stays on screen, in whole minutes. */
object Delay {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 120
    const val DEFAULT_MINUTES = 5

    /** Returns the delay in minutes, or null if [text] is not a whole number within bounds. */
    fun parse(text: String): Int? {
        val value = text.trim().toIntOrNull() ?: return null
        return value.takeIf { isValid(it) }
    }

    fun isValid(minutes: Int): Boolean = minutes in MIN_MINUTES..MAX_MINUTES

    /** Anything stored that is out of bounds falls back to the default. */
    fun sanitize(minutes: Int): Int = if (isValid(minutes)) minutes else DEFAULT_MINUTES

    fun toMillis(minutes: Int): Long = minutes * 60_000L

    /** How long the next page gets to load before the switch. */
    const val PRELOAD_LEAD_MS = 30_000L

    /** When, after a page appeared, the next page starts loading. */
    fun preloadAfterMillis(minutes: Int): Long = (toMillis(minutes) - PRELOAD_LEAD_MS).coerceAtLeast(0)
}
