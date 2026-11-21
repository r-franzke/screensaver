package app.webscreensaver.tv

import android.content.Context

data class SlideshowSettings(
    val urls: List<String>,
    val delayMinutes: Int,
    val reloadEachTime: Boolean,
)

/** SharedPreferences-backed settings. The URLs may contain private tokens: never log them. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SlideshowSettings = SlideshowSettings(
        urls = UrlList.deserialize(prefs.getString(KEY_URLS, null)),
        delayMinutes = Delay.sanitize(prefs.getInt(KEY_DELAY, Delay.DEFAULT_MINUTES)),
        reloadEachTime = prefs.getBoolean(KEY_RELOAD, true),
    )

    fun save(settings: SlideshowSettings) {
        prefs.edit()
            .putString(KEY_URLS, UrlList.serialize(settings.urls))
            .putInt(KEY_DELAY, Delay.sanitize(settings.delayMinutes))
            .putBoolean(KEY_RELOAD, settings.reloadEachTime)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_URLS = "urls"
        const val KEY_DELAY = "delay_minutes"
        const val KEY_RELOAD = "reload_each_time"
    }
}
