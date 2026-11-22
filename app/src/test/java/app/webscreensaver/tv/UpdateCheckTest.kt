package app.webscreensaver.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun `a higher release tag is newer than the installed version`() {
        assertTrue(UpdateCheck.isNewer(latestTag = "v1.0.1", installed = "1.0.0"))
        assertTrue(UpdateCheck.isNewer(latestTag = "v1.1.0", installed = "1.0.9"))
        assertTrue(UpdateCheck.isNewer(latestTag = "v2.0.0", installed = "1.12.3"))
        assertTrue(UpdateCheck.isNewer(latestTag = "v1.10.0", installed = "1.9.0"))
    }

    @Test
    fun `same or older release is not newer`() {
        assertFalse(UpdateCheck.isNewer(latestTag = "v1.0.0", installed = "1.0.0"))
        assertFalse(UpdateCheck.isNewer(latestTag = "1.0.0", installed = "1.0.1"))
        assertFalse(UpdateCheck.isNewer(latestTag = "v1.9.0", installed = "1.10.0"))
    }

    @Test
    fun `unparseable versions never trigger an update`() {
        assertFalse(UpdateCheck.isNewer(latestTag = "nightly", installed = "1.0.0"))
        assertFalse(UpdateCheck.isNewer(latestTag = "v1.0", installed = "1.0.0"))
        assertFalse(UpdateCheck.isNewer(latestTag = "v2.0.0", installed = "garbage"))
    }

    @Test
    fun `checks at most once every 24 hours`() {
        val day = 24 * 60 * 60 * 1000L
        val last = 1_700_000_000_000L
        assertTrue(UpdateCheck.isDue(lastCheckMillis = 0, now = last))
        assertFalse(UpdateCheck.isDue(lastCheckMillis = last, now = last + day - 1))
        assertTrue(UpdateCheck.isDue(lastCheckMillis = last, now = last + day))
    }

    @Test
    fun `a last check in the future (clock was changed) counts as due`() {
        assertTrue(UpdateCheck.isDue(lastCheckMillis = 2_000_000_000_000L, now = 1_700_000_000_000L))
    }
}
