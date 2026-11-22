package app.webscreensaver.tv

/**
 * Order in which pages are shown. Pages that failed to load are skipped for [retryAfterMs]
 * after the failure, then become eligible again (so they are retried). Pure Kotlin, takes the
 * current time as a parameter so it can be tested without a clock.
 */
class Rotation(val size: Int, private val retryAfterMs: Long = RETRY_AFTER_MS) {

    private val failedAt = arrayOfNulls<Long>(size)

    fun markFailed(index: Int, now: Long) {
        failedAt[index] = now
    }

    fun markOk(index: Int) {
        failedAt[index] = null
    }

    fun hasFailed(index: Int): Boolean = failedAt[index] != null

    /** True if the page did not fail recently and may be shown/preloaded. */
    fun isAvailable(index: Int, now: Long): Boolean {
        val failed = failedAt[index] ?: return true
        return now - failed >= retryAfterMs
    }

    /**
     * The next page after [from] in direction [step] (+1 forward, -1 backward), wrapping around.
     * With [skipUnavailable], recently failed pages are skipped. Returns null if there is no
     * other page to go to (single URL, or all others failed), meaning: stay on [from].
     */
    fun next(from: Int, now: Long, step: Int = 1, skipUnavailable: Boolean = true): Int? {
        if (size <= 1) return null
        val direction = if (step < 0) -1 else 1
        for (offset in 1 until size) {
            val candidate = Math.floorMod(from + direction * offset, size)
            if (!skipUnavailable || isAvailable(candidate, now)) return candidate
        }
        return null
    }

    /** Whether any page other than [index] could be shown right now. */
    fun hasOtherAvailable(index: Int, now: Long): Boolean = next(index, now) != null

    companion object {
        const val RETRY_AFTER_MS = 60_000L
    }
}
