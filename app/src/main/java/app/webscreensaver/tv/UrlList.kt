package app.webscreensaver.tv

/** A line of the URL text field that is not an accepted URL. [lineNumber] is 1-based. */
data class InvalidLine(val lineNumber: Int, val text: String)

/** One configured page. [reloadBeforeShowing] is set by a leading `!` on its line. */
data class PageEntry(val url: String, val reloadBeforeShowing: Boolean = false)

data class UrlParseResult(val pages: List<PageEntry>, val invalidLines: List<InvalidLine>) {
    val isValid: Boolean get() = invalidLines.isEmpty()
    val urls: List<String> get() = pages.map { it.url }
}

/** Parsing and validation of the "one URL per line" settings field. Pure Kotlin, no Android types. */
object UrlList {

    const val RELOAD_MARKER = "!"

    private val schemes = listOf("http://", "https://")

    fun parse(text: String): UrlParseResult {
        val pages = mutableListOf<PageEntry>()
        val invalid = mutableListOf<InvalidLine>()
        text.lines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed
            val reload = line.startsWith(RELOAD_MARKER)
            val url = if (reload) line.removePrefix(RELOAD_MARKER).trim() else line
            if (isValidUrl(url)) pages += PageEntry(url, reload) else invalid += InvalidLine(index + 1, line)
        }
        return UrlParseResult(pages, invalid)
    }

    /** Accepts only absolute http(s) URLs with a host and without whitespace. */
    fun isValidUrl(url: String): Boolean = host(url) != null

    /** The host of an http(s) URL (no credentials, port, path or query), or null if invalid. */
    fun host(url: String): String? {
        val scheme = schemes.firstOrNull { url.startsWith(it, ignoreCase = true) } ?: return null
        if (url.any { it.isWhitespace() }) return null
        val rest = url.substring(scheme.length)
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        val hostAndPort = authority.substringAfterLast('@')
        val host = if (hostAndPort.startsWith("[")) {
            hostAndPort.substringAfter('[').substringBefore(']') // IPv6 literal
        } else {
            hostAndPort.substringBefore(':')
        }
        return host.ifEmpty { null }
    }

    fun serialize(pages: List<PageEntry>): String = pages.joinToString("\n") {
        if (it.reloadBeforeShowing) "$RELOAD_MARKER ${it.url}" else it.url
    }

    /** Reads back what [serialize] stored; tolerant of anything else that might be in storage. */
    fun deserialize(stored: String?): List<PageEntry> = stored?.let { parse(it).pages } ?: emptyList()
}
