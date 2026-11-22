package app.webscreensaver.tv

/** Pure decisions for the self-update. */
object UpdateCheck {

    /** True if [latestTag] (e.g. "v1.2.0") is a higher semantic version than [installed] (e.g. "1.1.9"). */
    fun isNewer(latestTag: String, installed: String): Boolean {
        val latest = parse(latestTag) ?: return false
        val current = parse(installed) ?: return false
        for (i in 0 until 3) {
            if (latest[i] != current[i]) return latest[i] > current[i]
        }
        return false
    }

    const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** At most one check per [CHECK_INTERVAL_MS]; a last check "in the future" means the clock changed. */
    fun isDue(lastCheckMillis: Long, now: Long): Boolean =
        lastCheckMillis > now || now - lastCheckMillis >= CHECK_INTERVAL_MS

    /** "v1.2.3" or "1.2.3" (a "-suffix" is ignored) → [1, 2, 3]; anything else → null. */
    private fun parse(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").substringBefore('-')
        val parts = core.split('.')
        if (parts.size != 3) return null
        return parts.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
    }
}
