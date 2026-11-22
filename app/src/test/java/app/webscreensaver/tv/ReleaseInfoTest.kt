package app.webscreensaver.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseInfoTest {

    // Shape of GET https://api.github.com/repos/{owner}/{repo}/releases/latest (trimmed).
    private val latest = """
        {
          "tag_name": "v1.2.0",
          "name": "v1.2.0",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "name": "checksums.txt",
              "size": 120,
              "browser_download_url": "https://github.com/acme/tv/releases/download/v1.2.0/checksums.txt"
            },
            {
              "name": "web-screensaver.apk",
              "size": 65046,
              "browser_download_url": "https://github.com/acme/tv/releases/download/v1.2.0/web-screensaver.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `reads tag and apk download url from the latest release`() {
        assertEquals(
            ReleaseInfo(
                tag = "v1.2.0",
                apkUrl = "https://github.com/acme/tv/releases/download/v1.2.0/web-screensaver.apk",
                apkSize = 65046,
            ),
            ReleaseInfo.parse(latest),
        )
    }

    @Test
    fun `release without the apk asset or with broken json gives null`() {
        assertNull(ReleaseInfo.parse("""{"tag_name": "v1.2.0", "assets": []}"""))
        assertNull(ReleaseInfo.parse("""{"assets": [{"name": "web-screensaver.apk", "browser_download_url": "https://x"}]}"""))
        assertNull(ReleaseInfo.parse("not json"))
        assertNull(ReleaseInfo.parse(""))
    }

    @Test
    fun `only https download urls are accepted`() {
        val insecure = latest.replace("https://github.com/acme/tv/releases/download/v1.2.0/web-screensaver.apk", "http://evil.example/x.apk")
        assertNull(ReleaseInfo.parse(insecure))
    }
}
