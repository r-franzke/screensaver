package app.webscreensaver.tv

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Self-update from the GitHub releases of [BuildConfig.UPDATE_REPO].
 *
 * Checks at most once a day while the slideshow runs (never in the background otherwise), downloads
 * a newer `web-screensaver.apk` into private storage, and installs it the next time the app is
 * opened. Android shows its own confirmation and only accepts an APK signed with the same key.
 */
object Updater {

    enum class State { NEVER_CHECKED, UP_TO_DATE, DOWNLOADED, FAILED }

    data class Status(val state: State, val lastCheckMillis: Long, val version: String)

    val isAvailable: Boolean get() = BuildConfig.UPDATE_REPO.isNotEmpty()

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "updater").apply { isDaemon = true } }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var installing = false

    /** Called when the slideshow starts: checks if a day has passed since the last check. */
    fun checkIfDue(context: Context) {
        if (!isAvailable) return
        val app = context.applicationContext
        if (!UpdateCheck.isDue(prefs(app).getLong(KEY_LAST_CHECK, 0), System.currentTimeMillis())) return
        executor.execute { check(app) }
    }

    /** "Check for updates" button. [onDone] runs on the main thread. */
    fun checkNow(context: Context, onDone: () -> Unit) {
        if (!isAvailable) return
        val app = context.applicationContext
        executor.execute {
            check(app)
            mainHandler.post(onDone)
        }
    }

    fun status(context: Context): Status {
        val p = prefs(context)
        val state = State.entries.firstOrNull { it.name == p.getString(KEY_STATE, null) } ?: State.NEVER_CHECKED
        return Status(state, p.getLong(KEY_LAST_CHECK, 0), p.getString(KEY_VERSION, null) ?: "")
    }

    /** Called when the app is opened: installs a downloaded newer version (Android asks to confirm). */
    fun installPendingUpdate(context: Context) {
        if (!isAvailable || installing) return
        val app = context.applicationContext
        val tag = prefs(app).getString(KEY_PENDING_TAG, null)
        val apk = apkFile(app)
        if (tag == null || !apk.exists() || !UpdateCheck.isNewer(tag, BuildConfig.VERSION_NAME)) {
            discardDownload(app) // nothing pending, or already installed
            return
        }
        installing = true
        executor.execute {
            try {
                commitInstall(app, apk)
            } catch (e: IOException) {
                installing = false
            } catch (e: SecurityException) {
                installing = false
            }
        }
    }

    internal fun onInstallFinished(context: Context, success: Boolean) {
        installing = false
        if (success) discardDownload(context.applicationContext)
    }

    private fun check(app: Context) {
        val now = System.currentTimeMillis()
        val result = try {
            val release = ReleaseInfo.parse(httpGetText(latestReleaseUrl()))
            when {
                release == null -> State.FAILED to ""
                !UpdateCheck.isNewer(release.tag, BuildConfig.VERSION_NAME) -> {
                    discardDownload(app)
                    State.UP_TO_DATE to BuildConfig.VERSION_NAME
                }
                else -> {
                    download(app, release)
                    State.DOWNLOADED to release.tag.removePrefix("v")
                }
            }
        } catch (e: IOException) {
            State.FAILED to ""
        }
        prefs(app).edit()
            .putLong(KEY_LAST_CHECK, now)
            .putString(KEY_STATE, result.first.name)
            .putString(KEY_VERSION, result.second)
            .apply()
    }

    private fun download(app: Context, release: ReleaseInfo) {
        val target = apkFile(app)
        if (prefs(app).getString(KEY_PENDING_TAG, null) == release.tag && target.exists()) return
        val dir = target.parentFile!!
        dir.mkdirs()
        val partial = File(dir, "$APK_FILE.part")
        val connection = open(release.apkUrl)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
        if (release.apkSize > 0 && partial.length() != release.apkSize) {
            partial.delete()
            throw IOException("Incomplete download")
        }
        if (!partial.renameTo(target)) throw IOException("Could not store download")
        prefs(app).edit().putString(KEY_PENDING_TAG, release.tag).apply()
    }

    private fun commitInstall(app: Context, apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Silent where Android allows it (newer versions); otherwise Android asks to confirm.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(APK_FILE, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callback = PendingIntent.getBroadcast(
                app,
                sessionId,
                Intent(app, InstallResultReceiver::class.java).setPackage(app.packageName),
                flags,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun discardDownload(app: Context) {
        apkFile(app).delete()
        prefs(app).edit().remove(KEY_PENDING_TAG).apply()
    }

    private fun latestReleaseUrl() = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

    private fun httpGetText(url: String): String {
        val connection = open(url)
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true // GitHub redirects downloads to its asset host (https only)
            setRequestProperty("User-Agent", "web-screensaver-updater")
        }

    private fun apkFile(app: Context) = File(File(app.noBackupFilesDir, "update"), APK_FILE)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "updater"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_STATE = "state"
    private const val KEY_VERSION = "version"
    private const val KEY_PENDING_TAG = "pending_tag"
    private const val APK_FILE = "update.apk"
}
