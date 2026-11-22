package app.webscreensaver.tv

import android.content.ComponentCallbacks2
import android.service.dreams.DreamService

/** The system screensaver ("Daydream"). Not interactive: any key ends it. */
class SlideshowDream : DreamService() {

    private var slideshow: Slideshow? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val show = Slideshow(
            this,
            SettingsStore(this).load(),
            getString(R.string.hint_no_urls_dream),
            FailureStore(this),
            LastShownStore(this),
        )
        slideshow = show
        setContentView(show.view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        slideshow?.start()
        Updater.checkIfDue(this)
    }

    override fun onDreamingStopped() {
        slideshow?.destroy()
        slideshow = null
        super.onDreamingStopped()
    }

    @Suppress("DEPRECATION") // the RUNNING_* levels are still delivered on Android TV 8/9
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) slideshow?.trimMemory()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        slideshow?.trimMemory()
    }

    override fun onDetachedFromWindow() {
        slideshow?.destroy()
        slideshow = null
        super.onDetachedFromWindow()
    }
}
