package app.webscreensaver.tv

import org.json.JSONException
import org.json.JSONObject

/** The latest GitHub release, as far as the updater cares. */
data class ReleaseInfo(val tag: String, val apkUrl: String, val apkSize: Long) {

    companion object {
        const val APK_NAME = "web-screensaver.apk"

        /** Parses the GitHub "latest release" API response; null if it has no usable APK. */
        fun parse(json: String): ReleaseInfo? = try {
            val release = JSONObject(json)
            val tag = release.optString("tag_name")
            val assets = release.optJSONArray("assets")
            val apk = assets?.let { list ->
                (0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { it.optString("name") == APK_NAME }
            }
            val url = apk?.optString("browser_download_url").orEmpty()
            if (tag.isEmpty() || apk == null || !url.startsWith("https://")) {
                null
            } else {
                ReleaseInfo(tag, url, apk.optLong("size"))
            }
        } catch (e: JSONException) {
            null
        }
    }
}
