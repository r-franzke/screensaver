package app.webscreensaver.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlListTest {

    @Test
    fun `parses one url per line and ignores empty lines and surrounding spaces`() {
        val result = UrlList.parse("https://a.example/\n\n   \n  http://b.example/path?x=1#top  \r\nhttps://c.example\n")
        assertTrue(result.isValid)
        assertEquals(listOf("https://a.example/", "http://b.example/path?x=1#top", "https://c.example"), result.urls)
    }

    @Test
    fun `empty text gives empty valid list`() {
        val result = UrlList.parse("  \n\n")
        assertTrue(result.isValid)
        assertTrue(result.urls.isEmpty())
    }

    @Test
    fun `reports invalid lines with 1-based line numbers`() {
        val result = UrlList.parse("https://ok.example\nftp://files.example\n\nexample.com\nhttps://\njavascript:alert(1)")
        assertFalse(result.isValid)
        assertEquals(
            listOf(
                InvalidLine(2, "ftp://files.example"),
                InvalidLine(4, "example.com"),
                InvalidLine(5, "https://"),
                InvalidLine(6, "javascript:alert(1)"),
            ),
            result.invalidLines,
        )
        assertEquals(listOf("https://ok.example"), result.urls)
    }

    @Test
    fun `accepts http and https in any letter case, with port, credentials and ip`() {
        listOf(
            "HTTPS://EXAMPLE.COM",
            "http://localhost:8080/dash",
            "https://user:pass@host.example/",
            "http://192.168.1.10/",
            "https://[::1]:8443/",
            "https://host.example?token=abc",
        ).forEach { assertTrue(it, UrlList.isValidUrl(it)) }
    }

    @Test
    fun `rejects other schemes, missing host and whitespace`() {
        listOf(
            "file:///sdcard/x.html",
            "about:blank",
            "https:/missing-slash.example",
            "https:///path",
            "http://?q",
            "https://exa mple.com",
            "www.example.com",
            "",
        ).forEach { assertFalse(it, UrlList.isValidUrl(it)) }
    }

    @Test
    fun `serialize and deserialize round trip`() {
        val pages = listOf(PageEntry("https://a.example/?t=1", reloadBeforeShowing = true), PageEntry("http://b.example"))
        assertEquals("! https://a.example/?t=1\nhttp://b.example", UrlList.serialize(pages))
        assertEquals(pages, UrlList.deserialize(UrlList.serialize(pages)))
        assertEquals(emptyList<PageEntry>(), UrlList.deserialize(null))
        assertEquals(emptyList<PageEntry>(), UrlList.deserialize(""))
    }
}

class ReloadMarkerTest {

    @Test
    fun `leading exclamation mark marks a page for reload, other pages keep running`() {
        val result = UrlList.parse("! https://clock.example/\nhttps://dash.example/\n!https://news.example/")
        assertTrue(result.isValid)
        assertEquals(
            listOf(
                PageEntry("https://clock.example/", reloadBeforeShowing = true),
                PageEntry("https://dash.example/", reloadBeforeShowing = false),
                PageEntry("https://news.example/", reloadBeforeShowing = true),
            ),
            result.pages,
        )
    }

    @Test
    fun `marker without a valid url or other prefixes are invalid`() {
        val result = UrlList.parse("!\n! ftp://x.example\n? https://a.example\nreload https://b.example")
        assertEquals(listOf(1, 2, 3, 4), result.invalidLines.map { it.lineNumber })
    }
}
