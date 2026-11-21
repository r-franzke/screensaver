package app.webscreensaver.tv

import android.content.Context

/** Persists the [FailureLog] so the settings can show errors after the screensaver has ended. */
class FailureStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): FailureLog = FailureLog.deserialize(prefs.getString(KEY_LOG, null))

    fun recordFailure(url: String, failure: PageFailure) {
        val log = load()
        log.record(url, failure)
        save(log)
    }

    /** Writes only if the page had an entry, so successful loads normally cost no disk write. */
    fun recordSuccess(url: String) {
        val log = load()
        if (log.clear(url)) save(log)
    }

    fun retainOnly(pages: List<PageEntry>) {
        val log = load()
        if (log.retainOnly(pages)) save(log)
    }

    private fun save(log: FailureLog) {
        prefs.edit().putString(KEY_LOG, log.serialize()).apply()
    }

    private companion object {
        const val PREFS_NAME = "status"
        const val KEY_LOG = "failures"
    }
}
