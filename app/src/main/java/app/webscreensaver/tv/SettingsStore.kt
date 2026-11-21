package app.webscreensaver.tv

import android.content.Context

data class SlideshowSettings(
    val pages: List<PageEntry>,
    val delayMinutes: Int,
)

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
