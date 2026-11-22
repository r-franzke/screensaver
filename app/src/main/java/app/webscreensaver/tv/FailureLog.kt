package app.webscreensaver.tv

import android.webkit.WebViewClient

enum class FailureKind {
    CONNECTION, HTTP, CERTIFICATE, OTHER;

    companion object {
        /** Maps a WebViewClient.ERROR_* code from onReceivedError. */
        fun fromWebViewError(errorCode: Int): FailureKind = when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_PROXY_AUTHENTICATION,
            WebViewClient.ERROR_IO,
            -> CONNECTION
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> CERTIFICATE
            else -> OTHER
        }
    }
}

data class PageFailure(val kind: FailureKind, val httpStatus: Int = 0, val atMillis: Long)

/** One line of the status list in the settings. Shows the host only, never the full URL (tokens). */
data class StatusRow(val lineNumber: Int, val host: String, val failure: PageFailure)

/**
 * Last load failure per page, shown in the settings. Pages are identified by a hash of their URL,
 * so the stored log never contains a URL or token.
 */
class FailureLog {

    private val entries = LinkedHashMap<String, PageFailure>()

    fun record(url: String, failure: PageFailure) {
        entries[key(url)] = failure
    }

    /** Returns true if there was an entry, i.e. the log changed and needs saving. */
    fun clear(url: String): Boolean = entries.remove(key(url)) != null

    fun statusFor(pages: List<PageEntry>): List<StatusRow> = pages.mapIndexedNotNull { index, page ->
        entries[key(page.url)]?.let { StatusRow(index + 1, UrlList.host(page.url) ?: "", it) }
    }

    /** Removes entries of pages that are no longer configured. Returns true if something changed. */
    fun retainOnly(pages: List<PageEntry>): Boolean {
        val keep = pages.map { key(it.url) }.toSet()
        return entries.keys.retainAll(keep)
    }

    /** One line per entry: `hash;kind;httpStatus;atMillis`. */
    fun serialize(): String = entries.entries.joinToString("\n") { (hash, f) ->
        "$hash;${f.kind.name};${f.httpStatus};${f.atMillis}"
    }

    private fun key(url: String): String = UrlKey.of(url)

    companion object {
        fun deserialize(stored: String?): FailureLog {
            val log = FailureLog()
            stored?.lines()?.forEach { line ->
                val parts = line.split(';')
                if (parts.size != 4) return@forEach
                val kind = FailureKind.entries.firstOrNull { it.name == parts[1] } ?: return@forEach
                val status = parts[2].toIntOrNull() ?: return@forEach
                val at = parts[3].toLongOrNull() ?: return@forEach
                log.entries[parts[0]] = PageFailure(kind, status, at)
            }
            return log
        }
    }
}
