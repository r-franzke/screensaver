package app.webscreensaver.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DelayTest {

    @Test
    fun `default is 5 minutes and bounds are 1 to 120`() {
        assertEquals(5, Delay.DEFAULT_MINUTES)
        assertEquals(1, Delay.MIN_MINUTES)
        assertEquals(120, Delay.MAX_MINUTES)
    }

    @Test
    fun `parses whole numbers within bounds`() {
        assertEquals(1, Delay.parse("1"))
        assertEquals(5, Delay.parse(" 5 "))
        assertEquals(120, Delay.parse("120"))
    }

    @Test
    fun `rejects out of bounds and non numbers`() {
        listOf("0", "-1", "121", "1000", "", " ", "abc", "1.5", "5min").forEach { assertNull(it, Delay.parse(it)) }
    }

    @Test
    fun `sanitize falls back to default`() {
        assertEquals(7, Delay.sanitize(7))
        assertEquals(5, Delay.sanitize(0))
        assertEquals(5, Delay.sanitize(500))
    }

    @Test
    fun `converts minutes to milliseconds`() {
        assertEquals(60_000L, Delay.toMillis(1))
        assertEquals(7_200_000L, Delay.toMillis(120))
    }

    @Test
    fun `next page starts loading 30 seconds before the switch`() {
        assertEquals(270_000L, Delay.preloadAfterMillis(5))
        assertEquals(30_000L, Delay.preloadAfterMillis(1))
        assertEquals(7_170_000L, Delay.preloadAfterMillis(120))
    }
}
