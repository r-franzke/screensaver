package app.webscreensaver.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationTest {

    @Test
    fun `goes forward in order and wraps around`() {
        val r = Rotation(3)
        assertEquals(1, r.next(0, now = 0))
        assertEquals(2, r.next(1, now = 0))
        assertEquals(0, r.next(2, now = 0))
    }

    @Test
    fun `goes backward and wraps around`() {
        val r = Rotation(3)
        assertEquals(2, r.next(0, now = 0, step = -1))
        assertEquals(0, r.next(1, now = 0, step = -1))
    }

    @Test
    fun `single page or empty list stays`() {
        assertNull(Rotation(1).next(0, now = 0))
        assertNull(Rotation(1).next(0, now = 0, step = -1))
        assertNull(Rotation(0).next(0, now = 0))
    }

    @Test
    fun `skips recently failed pages`() {
        val r = Rotation(4, retryAfterMs = 60_000)
        r.markFailed(1, now = 1_000)
        r.markFailed(2, now = 1_000)
        assertEquals(3, r.next(0, now = 2_000))
        assertEquals(3, r.next(0, now = 2_000, step = -1))
        assertEquals(0, r.next(3, now = 2_000))
    }

    @Test
    fun `failed page becomes eligible again after the retry interval`() {
        val r = Rotation(3, retryAfterMs = 60_000)
        r.markFailed(1, now = 0)
        assertFalse(r.isAvailable(1, now = 59_999))
        assertEquals(2, r.next(0, now = 59_999))
        assertTrue(r.isAvailable(1, now = 60_000))
        assertEquals(1, r.next(0, now = 60_000))
    }

    @Test
    fun `stays when all other pages failed`() {
        val r = Rotation(3)
        r.markFailed(1, now = 0)
        r.markFailed(2, now = 0)
        assertNull(r.next(0, now = 10))
        assertFalse(r.hasOtherAvailable(0, now = 10))
    }

    @Test
    fun `failed current page does not block moving on`() {
        val r = Rotation(3)
        r.markFailed(0, now = 0)
        assertEquals(1, r.next(0, now = 10))
        assertTrue(r.hasOtherAvailable(0, now = 10))
    }

    @Test
    fun `successful load clears the failure`() {
        val r = Rotation(2)
        r.markFailed(1, now = 0)
        assertNull(r.next(0, now = 10))
        r.markOk(1)
        assertFalse(r.hasFailed(1))
        assertEquals(1, r.next(0, now = 10))
    }

    @Test
    fun `manual navigation can include failed pages`() {
        val r = Rotation(3)
        r.markFailed(1, now = 0)
        assertEquals(1, r.next(0, now = 10, skipUnavailable = false))
        assertEquals(2, r.next(0, now = 10, skipUnavailable = true))
    }
}
