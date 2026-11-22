package app.webscreensaver.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class StartPositionTest {

    private val urls = listOf("https://a.example/", "https://b.example/?token=x", "https://c.example/")

    @Test
    fun `continues with the page after the last shown one and wraps around`() {
        assertEquals(2, StartPosition.next(urls, lastShownKey = UrlKey.of(urls[1])))
        assertEquals(0, StartPosition.next(urls, lastShownKey = UrlKey.of(urls[2])))
    }

    @Test
    fun `starts at the first page when nothing was shown yet or the page was removed`() {
        assertEquals(0, StartPosition.next(urls, lastShownKey = null))
        assertEquals(0, StartPosition.next(urls, lastShownKey = UrlKey.of("https://removed.example/")))
        assertEquals(0, StartPosition.next(emptyList(), lastShownKey = UrlKey.of(urls[0])))
    }

    @Test
    fun `follows the page, not its position, when the list was reordered`() {
        val reordered = listOf(urls[2], urls[0], urls[1])
        assertEquals(2, StartPosition.next(reordered, lastShownKey = UrlKey.of(urls[0])))
    }
}
