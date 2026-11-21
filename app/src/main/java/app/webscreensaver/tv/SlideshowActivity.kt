package app.webscreensaver.tv

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/**
 * The slideshow as a fullscreen app. Keeps the screen on, so the system screensaver never covers it.
 * Menu or a long press on OK opens the settings; D-pad left/right jumps between pages.
 */
class SlideshowActivity : Activity() {

    private var slideshow: Slideshow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // First launch without any pages: go straight to the settings (only once, not after "back").
        if (savedInstanceState == null && SettingsStore(this).load().pages.isEmpty()) openSettings()
    }

    override fun onStart() {
        super.onStart()
        // Rebuilt on every start so changed settings take effect after returning from the settings.
        val show = Slideshow(
            this,
            SettingsStore(this).load(),
            getString(R.string.hint_no_urls_app),
            FailureStore(this),
        )
        slideshow = show
        setContentView(show.view)
        hideSystemUi()
        show.start()
    }

    override fun onStop() {
        slideshow?.destroy()
        slideshow = null
        super.onStop()
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                openSettings()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                event.startTracking() // enables onKeyLongPress
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                slideshow?.previous()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                slideshow?.next()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (isOkKey(keyCode)) {
            openSettings()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isOkKey(keyCode)) {
            // Without URLs, a short press is enough (remotes without a Menu key).
            if (event.isTracking && !event.isCanceled && slideshow?.hasUrls == false) openSettings()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isOkKey(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION") // still needed below API 35, where edge-to-edge is not enforced
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }
}
