package app.webscreensaver.tv

import android.service.dreams.DreamService

/** The system screensaver ("Daydream"). Not interactive: any key ends it. */
class SlideshowDream : DreamService() {

    private var slideshow: Slideshow? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val show = Slideshow(this, SettingsStore(this).load(), getString(R.string.hint_no_urls_dream))
        slideshow = show
        setContentView(show.view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        slideshow?.start()
    }

    override fun onDreamingStopped() {
        slideshow?.destroy()
        slideshow = null
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        slideshow?.destroy()
        slideshow = null
        super.onDetachedFromWindow()
    }
}
