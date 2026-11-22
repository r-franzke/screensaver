package app.webscreensaver.tv

import android.content.Context

data class SlideshowSettings(
    val pages: List<PageEntry>,
    val delayMinutes: Int,
)

/** Remembers which page was shown last (as a URL hash), so the next start continues after it. */
class LastShownStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("position", Context.MODE_PRIVATE)

    fun load(): String? = prefs.getString(KEY, null)

    fun save(url: String) {
        val key = UrlKey.of(url)
        if (key != load()) prefs.edit().putString(KEY, key).apply()
    }

    private companion object {
        const val KEY = "last_shown"
    }
}

/** SharedPreferences-backed settings. The URLs may contain private tokens: never log them. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SlideshowSettings = SlideshowSettings(
        pages = UrlList.deserialize(prefs.getString(KEY_URLS, null)),
        delayMinutes = Delay.sanitize(prefs.getInt(KEY_DELAY, Delay.DEFAULT_MINUTES)),
    )

    fun save(settings: SlideshowSettings) {
        prefs.edit()
            .putString(KEY_URLS, UrlList.serialize(settings.pages))
            .putInt(KEY_DELAY, Delay.sanitize(settings.delayMinutes))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_URLS = "urls"
        const val KEY_DELAY = "delay_minutes"
    }
}
