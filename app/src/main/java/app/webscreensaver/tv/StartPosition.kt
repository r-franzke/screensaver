package app.webscreensaver.tv

import java.security.MessageDigest

/** Identifies a page by a hash of its URL, so stored data never contains a URL or token. */
object UrlKey {
    fun of(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Where the slideshow starts: after the page shown last time, so every page gets its turn. */
object StartPosition {

    fun next(urls: List<String>, lastShownKey: String?): Int {
        if (urls.isEmpty()) return 0
        val last = urls.indexOfFirst { UrlKey.of(it) == lastShownKey }
        return (last + 1) % urls.size
    }
}
