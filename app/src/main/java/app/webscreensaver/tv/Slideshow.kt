package app.webscreensaver.tv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Fullscreen web page slideshow, used by both [SlideshowDream] and [SlideshowActivity].
 *
 * The visible page is shown in one WebView while the next one is preloaded in another; after the
 * delay the preloaded page is cross-faded in. With "reload each time" off, every page gets its own
 * WebView that keeps running in the background and is only reloaded after an error.
 *
 * Never log URLs: they may contain private tokens.
 */
class Slideshow(
    private val context: Context,
    private val settings: SlideshowSettings,
    emptyHint: String,
) {
    private enum class Status { IDLE, LOADING, LOADED, FAILED }

    private inner class Page {
        var webView: WebView = createWebView(this)
        var index = -1
        var status = Status.IDLE

        /** Loaded ahead of time for the next show (reload mode): may be shown without reloading. */
        var fresh = false
    }

    private val urls = settings.urls
    private val reloadEachTime = settings.reloadEachTime
    private val delayMs = Delay.toMillis(settings.delayMinutes)
    private val rotation = Rotation(urls.size)
    private val handler = Handler(Looper.getMainLooper())

    private val pagesLayer = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
    private val errorOverlay = createOverlay()
    private val hintView = createHint(emptyHint)

    /** The view to put on screen. */
    val view: FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        addView(pagesLayer, matchParent())
        val overlayParams = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END)
        val margin = dp(32)
        overlayParams.setMargins(margin, margin, margin, margin)
        addView(errorOverlay, overlayParams)
        addView(hintView, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    }

    val hasUrls: Boolean get() = urls.isNotEmpty()

    /** Reload mode: the two alternating pages. Keep-running mode: one page per URL index. */
    private val pool = mutableListOf<Page>()
    private val pagesByIndex = HashMap<Int, Page>()

    private var visible: Page? = null
    private var pending: Page? = null
    private var fade: Animator? = null
    private var started = false
    private var destroyed = false

    private val advanceRunnable = Runnable { advanceAutomatically() }
    private val retryRunnable = Runnable { retryVisible() }
    private val readyTimeoutRunnable = Runnable { pending?.let { reveal(it) } }

    fun start() {
        if (started || destroyed) return
        started = true
        if (urls.isEmpty()) {
            hintView.visibility = View.VISIBLE
            return
        }
        showIndex(0)
    }

    /** D-pad right. */
    fun next() = jump(+1)

    /** D-pad left. */
    fun previous() = jump(-1)

    fun destroy() {
        if (destroyed) return
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        fade?.cancel()
        fade = null
        val all = (pool + pagesByIndex.values).distinct()
        pool.clear()
        pagesByIndex.clear()
        visible = null
        pending = null
        pagesLayer.removeAllViews()
        all.forEach { destroyWebView(it.webView) }
    }

    // --- Rotation -----------------------------------------------------------------------------

    private fun now() = SystemClock.elapsedRealtime()

    private fun baseIndex(): Int = pending?.index ?: visible?.index ?: 0

    private fun jump(step: Int) {
        if (!started || destroyed || urls.size <= 1) return
        val target = rotation.next(baseIndex(), now(), step, skipUnavailable = false) ?: return
        showIndex(target)
    }

    private fun advanceAutomatically() {
        val current = visible?.index ?: return
        val target = rotation.next(current, now())
        if (target == null) {
            // Nothing else to show right now (all other pages failed recently): stay, check later.
            scheduleAdvance()
            return
        }
        showIndex(target)
    }

    private fun scheduleAdvance() {
        handler.removeCallbacks(advanceRunnable)
        if (urls.size > 1) handler.postDelayed(advanceRunnable, delayMs)
    }

    /** Starts switching to [index]; the page is revealed once it has loaded (or after a timeout). */
    private fun showIndex(index: Int) {
        if (destroyed) return
        handler.removeCallbacks(advanceRunnable)
        handler.removeCallbacks(readyTimeoutRunnable)
        finishFade(preloadAfterwards = false)

        val page = obtain(index)
        if (page === visible) {
            pending = null
            reveal(page)
            return
        }
        pending = page
        when (page.status) {
            Status.LOADED -> reveal(page)
            Status.FAILED -> onPendingFailed(page)
            else -> handler.postDelayed(readyTimeoutRunnable, READY_TIMEOUT_MS)
        }
    }

    /** Returns a page that shows (or is loading) [index], starting a load if needed. */
    private fun obtain(index: Int): Page {
        if (!reloadEachTime) {
            val page = pagesByIndex.getOrPut(index) { addPage() }
            if (page.status == Status.IDLE || page.status == Status.FAILED) load(page, index)
            return page
        }
        while (pool.size < 2) pool += addPage()
        val hidden = pool.first { it !== visible }
        if (hidden.index == index && hidden.fresh && hidden.status != Status.FAILED) return hidden
        load(hidden, index)
        return hidden
    }

    private fun preloadNext() {
        if (destroyed) return
        val current = visible?.index ?: return
        val next = rotation.next(current, now()) ?: return
        obtain(next)
    }

    // --- Showing ------------------------------------------------------------------------------

    private fun reveal(page: Page) {
        if (destroyed) return
        handler.removeCallbacks(readyTimeoutRunnable)
        if (pending === page) pending = null
        val old = visible
        visible = page
        page.fresh = false

        val webView = page.webView
        webView.visibility = View.VISIBLE
        webView.bringToFront()
        if (old == null || old === page) {
            webView.alpha = 1f
            preloadNext()
        } else {
            webView.alpha = 0f
            fade = ObjectAnimator.ofFloat(webView, View.ALPHA, 0f, 1f).apply {
                duration = FADE_MS
                addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (fade === animation) fade = null
                        old.webView.visibility = View.INVISIBLE
                        webView.alpha = 1f
                        if (!cancelled) preloadNext()
                    }
                })
                start()
            }
        }
        updateOverlay()
        scheduleAdvance()
        if (page.status == Status.FAILED) onVisibleFailed()
    }

    /** Ends a running cross-fade immediately. */
    private fun finishFade(preloadAfterwards: Boolean) {
        val running = fade ?: return
        fade = null
        if (preloadAfterwards) running.end() else running.cancel()
    }

    // --- Loading and errors -------------------------------------------------------------------

    private fun load(page: Page, index: Int) {
        page.index = index
        page.status = Status.LOADING
        page.fresh = true
        page.webView.loadUrl(urls[index])
    }

    private fun onLoaded(page: Page) {
        rotation.markOk(page.index)
        when {
            page === pending -> reveal(page)
            page === visible -> {
                handler.removeCallbacks(retryRunnable)
                updateOverlay()
            }
        }
    }

    private fun onFailed(page: Page) {
        rotation.markFailed(page.index, now())
        when {
            page === pending -> onPendingFailed(page)
            page === visible -> onVisibleFailed()
            // A failed preload is simply skipped by the rotation for a minute.
        }
    }

    private fun onPendingFailed(page: Page) {
        val other = rotation.next(page.index, now())
        if (other != null && other != visible?.index) {
            showIndex(other)
        } else if (visible != null && visible?.status != Status.FAILED) {
            // Keep showing the working page instead of an error.
            pending = null
            handler.removeCallbacks(readyTimeoutRunnable)
            scheduleAdvance()
        } else {
            reveal(page)
        }
    }

    private fun onVisibleFailed() {
        val current = visible ?: return
        val other = rotation.next(current.index, now())
        if (other != null) {
            updateOverlay()
            showIndex(other)
            return
        }
        updateOverlay()
        handler.removeCallbacks(retryRunnable)
        handler.postDelayed(retryRunnable, Rotation.RETRY_AFTER_MS)
    }

    private fun retryVisible() {
        val page = visible ?: return
        if (page.status != Status.FAILED) return
        load(page, page.index)
    }

    private fun fail(page: Page) {
        if (page.status == Status.FAILED) return
        page.status = Status.FAILED
        page.fresh = false
        // Replace the WebView's own (white) error page with black.
        page.webView.stopLoading()
        page.webView.loadUrl(BLANK)
        onFailed(page)
    }

    private fun updateOverlay() {
        errorOverlay.visibility = if (visible?.status == Status.FAILED) View.VISIBLE else View.GONE
    }

    /** The renderer crashed (e.g. out of memory): replace the WebView instead of crashing the app. */
    private fun recreate(page: Page) {
        if (destroyed) return
        val oldView = page.webView
        val position = pagesLayer.indexOfChild(oldView)
        val newView = createWebView(page)
        newView.visibility = oldView.visibility
        pagesLayer.removeView(oldView)
        pagesLayer.addView(newView, if (position >= 0) position else pagesLayer.childCount, matchParent())
        page.webView = newView
        destroyWebView(oldView)
        if (page === visible || page === pending) {
            load(page, page.index)
        } else {
            page.status = Status.IDLE
            page.fresh = false
        }
    }

    // --- Views --------------------------------------------------------------------------------

    private fun addPage(): Page {
        val page = Page()
        pagesLayer.addView(page.webView, 0, matchParent())
        return page
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(page: Page): WebView = WebView(context).apply {
        setBackgroundColor(Color.BLACK)
        visibility = View.INVISIBLE
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        // Keys go to the activity (D-pad, Menu), not to the page.
        isFocusable = false
        isFocusableInTouchMode = false
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webChromeClient = object : WebChromeClient() {
            // Avoid the grey play-button placeholder for videos.
            override fun getDefaultVideoPoster(): Bitmap =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        webViewClient = PageClient(page)
    }

    private inner class PageClient(private val page: Page) : WebViewClient() {

        private fun isCurrent(view: WebView) = !destroyed && view === page.webView

        // Links stay inside the WebView.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

        override fun onPageFinished(view: WebView, url: String?) {
            if (!isCurrent(view) || url == BLANK) return
            if (page.status == Status.LOADING) {
                page.status = Status.LOADED
                onLoaded(page)
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (isCurrent(view) && request.isForMainFrame) fail(page)
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (isCurrent(view) && request.isForMainFrame && errorResponse.statusCode >= 400) fail(page)
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            if (isCurrent(view) && page.status == Status.LOADING && page.index >= 0 &&
                error.url == urls[page.index]
            ) {
                fail(page)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (view === page.webView) handler.post { recreate(page) }
            return true
        }
    }

    private fun destroyWebView(webView: WebView) {
        webView.stopLoading()
        (webView.parent as? FrameLayout)?.removeView(webView)
        webView.destroy()
    }

    private fun createOverlay(): TextView = TextView(context).apply {
        setText(R.string.error_retrying)
        setTextColor(0xCCFFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        background = GradientDrawable().apply {
            setColor(0x99000000.toInt())
            cornerRadius = dp(8).toFloat()
        }
        visibility = View.GONE
    }

    private fun createHint(text: String): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        setPadding(dp(48), dp(48), dp(48), dp(48))
        visibility = View.GONE
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun matchParent() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )

    private companion object {
        const val BLANK = "about:blank"
        const val FADE_MS = 1_000L
        const val READY_TIMEOUT_MS = 20_000L
        const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
    }
}
