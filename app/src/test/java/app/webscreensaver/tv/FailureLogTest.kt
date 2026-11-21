package app.webscreensaver.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureLogTest {

    private val pages = listOf(
        PageEntry("https://a.example/dash?token=secret"),
        PageEntry("https://user:pw@b.example:8443/x?token=secret", reloadBeforeShowing = true),
        PageEntry("http://192.168.1.5/"),
    )

    @Test
    fun `recorded failure shows up with line number and host only`() {
        val log = FailureLog()
        log.record(pages[1].url, PageFailure(FailureKind.HTTP, httpStatus = 503, atMillis = 1_000))
        assertEquals(
            listOf(StatusRow(lineNumber = 2, host = "b.example", PageFailure(FailureKind.HTTP, 503, 1_000))),
            log.statusFor(pages),
        )
    }

    @Test
    fun `successful load clears the entry and reports a change only if there was one`() {
        val log = FailureLog()
        log.record(pages[0].url, PageFailure(FailureKind.CONNECTION, atMillis = 5))
        assertTrue(log.clear(pages[0].url))
        assertFalse(log.clear(pages[0].url))
        assertEquals(emptyList<StatusRow>(), log.statusFor(pages))
    }

    @Test
    fun `survives saving and restoring without storing urls`() {
        val log = FailureLog()
        log.record(pages[0].url, PageFailure(FailureKind.CERTIFICATE, atMillis = 1_700_000_000_000))
        log.record(pages[2].url, PageFailure(FailureKind.HTTP, httpStatus = 404, atMillis = 42))
        val stored = log.serialize()
        assertFalse(stored.contains("example"))
        assertFalse(stored.contains("secret"))
        assertFalse(stored.contains("192.168"))
        assertEquals(log.statusFor(pages), FailureLog.deserialize(stored).statusFor(pages))
        assertEquals(2, FailureLog.deserialize(stored).statusFor(pages).size)
    }

    @Test
    fun `corrupt or missing stored data gives an empty log`() {
        assertEquals(emptyList<StatusRow>(), FailureLog.deserialize(null).statusFor(pages))
        assertEquals(emptyList<StatusRow>(), FailureLog.deserialize("garbage\nmore;garbage").statusFor(pages))
    }

    @Test
    fun `entries of pages no longer in the list are dropped`() {
        val log = FailureLog()
        log.record(pages[0].url, PageFailure(FailureKind.OTHER, atMillis = 1))
        log.record(pages[1].url, PageFailure(FailureKind.OTHER, atMillis = 2))
        log.retainOnly(listOf(pages[1]))
        assertEquals(listOf(2L), log.statusFor(pages).map { it.failure.atMillis })
        // Line numbers follow the current list.
        assertEquals(listOf(1), log.statusFor(listOf(pages[1])).map { it.lineNumber })
    }

    @Test
    fun `webview error codes map to error types`() {
        // Values as documented for android.webkit.WebViewClient.ERROR_*.
        assertEquals(FailureKind.CONNECTION, FailureKind.fromWebViewError(-2)) // HOST_LOOKUP
        assertEquals(FailureKind.CONNECTION, FailureKind.fromWebViewError(-6)) // CONNECT
        assertEquals(FailureKind.CONNECTION, FailureKind.fromWebViewError(-8)) // TIMEOUT
        assertEquals(FailureKind.CERTIFICATE, FailureKind.fromWebViewError(-11)) // FAILED_SSL_HANDSHAKE
        assertEquals(FailureKind.OTHER, FailureKind.fromWebViewError(-14)) // FILE_NOT_FOUND
        assertEquals(FailureKind.OTHER, FailureKind.fromWebViewError(-1)) // UNKNOWN
    }
}
