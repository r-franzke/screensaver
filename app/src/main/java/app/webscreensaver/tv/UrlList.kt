package app.webscreensaver.tv

/** A line of the URL text field that is not an accepted URL. [lineNumber] is 1-based. */
data class InvalidLine(val lineNumber: Int, val text: String)

data class UrlParseResult(val urls: List<String>, val invalidLines: List<InvalidLine>) {
    val isValid: Boolean get() = invalidLines.isEmpty()
}

/** Parsing and validation of the "one URL per line" settings field. Pure Kotlin, no Android types. */
object UrlList {

    private val schemes = listOf("http://", "https://")

    fun parse(text: String): UrlParseResult {
        val urls = mutableListOf<String>()
        val invalid = mutableListOf<InvalidLine>()
        text.lines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed
            if (isValidUrl(line)) urls += line else invalid += InvalidLine(index + 1, line)
        }
        return UrlParseResult(urls, invalid)
    }

    /** Accepts only absolute http(s) URLs with a host and without whitespace. */
    fun isValidUrl(url: String): Boolean {
        val scheme = schemes.firstOrNull { url.startsWith(it, ignoreCase = true) } ?: return false
        if (url.any { it.isWhitespace() }) return false
        val rest = url.substring(scheme.length)
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        val hostAndPort = authority.substringAfterLast('@')
        val host = if (hostAndPort.startsWith("[")) {
            hostAndPort.substringAfter('[').substringBefore(']') // IPv6 literal
        } else {
            hostAndPort.substringBefore(':')
        }
        return host.isNotEmpty()
    }

    fun serialize(urls: List<String>): String = urls.joinToString("\n")

    /** Reads back what [serialize] stored; tolerant of anything else that might be in storage. */
    fun deserialize(stored: String?): List<String> = stored?.let { parse(it).urls } ?: emptyList()
}
