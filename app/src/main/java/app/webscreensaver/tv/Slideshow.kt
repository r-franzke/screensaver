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
import android.os.Build
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
 * Every page gets its own WebView, created when first needed, which keeps running in the background
 * while other pages are shown. Pages marked with `!` are loaded fresh shortly before each showing and
 * unloaded after they have been shown. The next page starts loading [Delay.PRELOAD_LEAD_MS] before
 * the switch; the switch is a cross-fade once it has loaded. Under memory pressure, all hidden pages
 * except the next one are closed ([trimMemory]).
 *
 * Nothing runs before [start] or after [destroy]. Never log URLs: they may contain private tokens.
 */
class Slideshow(
    private val context: Context,
    settings: SlideshowSettings,
    private val emptyHint: String,
    private val failureStore: FailureStore,
) {
    private enum class Status { IDLE, LOADING, LOADED, FAILED }

    private inner class Page(val index: Int) {
        val entry: PageEntry get() = entries[index]
        var webView: WebView = createWebView(this)
        var status = Status.IDLE

        /** Loaded since it was last shown (so a `!` page does not need another reload). */
        var fresh = false
    }

    private val entries = settings.pages
    private val delayMinutes = settings.delayMinutes
    private val rotation = Rotation(entries.size)
    private val handler = Handler(Looper.getMainLooper())

    private val pagesLayer = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
    private val waitingScreen = WaitingScreen(context)
    private val errorOverlay = createOverlay()

    /** The view to put on screen. */
    val view: FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        addView(pagesLayer, matchParent())
        addView(waitingScreen, matchParent())
        val overlayParams = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END)
        val margin = dp(32)
        overlayParams.setMargins(margin, margin, margin, margin)
        addView(errorOverlay, overlayParams)
    }

    val hasUrls: Boolean get() = entries.isNotEmpty()

    private val pages = HashMap<Int, Page>()
    private var visible: Page? = null
    private var pending: Page? = null
    private var fade: Animator? = null
    private var started = false
    private var destroyed = false

    private val advanceRunnable = Runnable { advanceAutomatically() }
    private val preloadRunnable = Runnable { preloadNext() }
    private val retryRunnable = Runnable { retryVisible() }
    private val readyTimeoutRunnable = Runnable { pending?.let { reveal(it) } }

    fun start() {
        if (started || destroyed) return
        started = true
        if (entries.isEmpty()) {
            waitingScreen.showMessage(emptyHint)
            return
        }
        waitingScreen.showLoading(loadingText(0))
        showIndex(0)
    }

    /** D-pad right. */
    fun next() = jump(+1)

    /** D-pad left. */
    fun previous() = jump(-1)

    /** Memory is low: close every hidden page except the next one. They are reloaded when needed. */
    fun trimMemory() {
        if (!started || destroyed) return
        val keep = setOfNotNull(visible, pending, nextIndex()?.let { pages[it] })
        pages.values.filter { it !in keep }.forEach { page ->
            pages.remove(page.index)
            destroyWebView(page.webView)
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        fade?.cancel()
        fade = null
        waitingScreen.release()
        val all = pages.values.toList()
        pages.clear()
        visible = null
        pending = null
        all.forEach { destroyWebView(it.webView) }
        pagesLayer.removeAllViews()
    }

    // --- Rotation -----------------------------------------------------------------------------

    private fun now() = SystemClock.elapsedRealtime()

    private fun nextIndex(): Int? = visible?.let { rotation.next(it.index, now()) }

    private fun jump(step: Int) {
        if (!started || destroyed || entries.size <= 1) return
        val base = pending?.index ?: visible?.index ?: return
        val target = rotation.next(base, now(), step, skipUnavailable = false) ?: return
        showIndex(target)
    }

    private fun advanceAutomatically() {
        val target = nextIndex()
        if (target == null) {
            // Nothing else to show right now (all other pages failed recently): stay, check later.
            scheduleTimers()
            return
        }
        showIndex(target)
    }

    private fun scheduleTimers() {
        handler.removeCallbacks(advanceRunnable)
        handler.removeCallbacks(preloadRunnable)
        if (entries.size <= 1) return
        handler.postDelayed(preloadRunnable, Delay.preloadAfterMillis(delayMinutes))
        handler.postDelayed(advanceRunnable, Delay.toMillis(delayMinutes))
    }

    private fun preloadNext() {
        if (destroyed) return
        nextIndex()?.let { prepare(it) }
    }

    /** Starts switching to [index]; the page is revealed once it has loaded (or after a timeout). */
    private fun showIndex(index: Int) {
        if (destroyed) return
        handler.removeCallbacks(advanceRunnable)
        handler.removeCallbacks(preloadRunnable)
        handler.removeCallbacks(readyTimeoutRunnable)
        finishFade()

        val page = prepare(index)
        if (page === visible) {
            pending = null
            reveal(page)
            return
        }
        pending = page
        if (page.status == Status.LOADED) {
            reveal(page)
        } else {
            handler.postDelayed(readyTimeoutRunnable, READY_TIMEOUT_MS)
        }
    }

    /** Returns the page for [index], loading it if it is not loaded (or is a `!` page not yet refreshed). */
    private fun prepare(index: Int): Page {
        val page = pages.getOrPut(index) { addPage(index) }
        val needsLoad = when (page.status) {
            Status.IDLE, Status.FAILED -> true
            Status.LOADING -> false
            Status.LOADED -> page.entry.reloadBeforeShowing && !page.fresh && page !== visible
        }
        if (needsLoad) load(page)
        return page
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
        } else {
            webView.alpha = 0f
            fade = ObjectAnimator.ofFloat(webView, View.ALPHA, 0f, 1f).apply {
                duration = FADE_MS
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (fade === animation) fade = null
                        webView.alpha = 1f
                        if (pages[old.index] === old && old !== visible) hide(old)
                    }
                })
                start()
            }
        }
        if (page.status != Status.FAILED) waitingScreen.hide(FADE_MS)
        updateErrorDisplay()
        scheduleTimers()
        if (page.status == Status.FAILED) onVisibleFailed()
    }

    /** A page that is no longer shown keeps running, unless it is reloaded before each showing anyway. */
    private fun hide(page: Page) {
        page.webView.visibility = View.INVISIBLE
        if (page.entry.reloadBeforeShowing && page.status != Status.LOADING) {
            page.status = Status.IDLE
            page.fresh = false
            page.webView.loadUrl(BLANK)
        }
    }

    /** Ends a running cross-fade immediately (its end handling still runs). */
    private fun finishFade() {
        val running = fade ?: return
        fade = null
        running.end()
    }

    // --- Loading and errors -------------------------------------------------------------------

    private fun load(page: Page) {
        page.status = Status.LOADING
        page.fresh = true
        page.webView.loadUrl(page.entry.url)
    }

    private fun onLoaded(page: Page) {
        rotation.markOk(page.index)
        failureStore.recordSuccess(page.entry.url)
        when {
            page === pending -> reveal(page)
            page === visible -> {
                handler.removeCallbacks(retryRunnable)
                waitingScreen.hide(FADE_MS)
                updateErrorDisplay()
            }
        }
    }

    private fun onFailed(page: Page, failure: PageFailure) {
        rotation.markFailed(page.index, now())
        failureStore.recordFailure(page.entry.url, failure)
        when {
            page === pending -> onPendingFailed(page)
            page === visible -> onVisibleFailed()
            // A failed preload is simply skipped by the rotation for a minute.
        }
    }

    private fun onPendingFailed(page: Page) {
        val other = rotation.next(page.index, now())
        val current = visible
        if (other != null && other != current?.index) {
            showIndex(other)
        } else if (current != null && current.status != Status.FAILED) {
            // Keep showing the working page instead of an error.
            pending = null
            handler.removeCallbacks(readyTimeoutRunnable)
            scheduleTimers()
        } else {
            reveal(page)
        }
    }

    private fun onVisibleFailed() {
        val current = visible ?: return
        val other = rotation.next(current.index, now())
        updateErrorDisplay()
        if (other != null) {
            showIndex(other)
            return
        }
        handler.removeCallbacks(retryRunnable)
        handler.postDelayed(retryRunnable, Rotation.RETRY_AFTER_MS)
    }

    private fun retryVisible() {
        val page = visible ?: return
        if (page.status != Status.FAILED) return
        if (waitingScreen.isShowing) waitingScreen.showLoading(loadingText(page.index))
        load(page)
    }

    private fun fail(page: Page, failure: PageFailure) {
        if (page.status == Status.FAILED) return
        page.status = Status.FAILED
        page.fresh = false
        // Replace the WebView's own (white) error page with black.
        page.webView.stopLoading()
        page.webView.loadUrl(BLANK)
        onFailed(page, failure)
    }

    private fun updateErrorDisplay() {
        val failed = visible?.status == Status.FAILED
        if (failed && waitingScreen.isShowing) {
            // Nothing has been shown yet: say it on the waiting screen instead of a small overlay.
            waitingScreen.showMessage(context.getString(R.string.error_retrying))
            errorOverlay.visibility = View.GONE
        } else {
            errorOverlay.visibility = if (failed) View.VISIBLE else View.GONE
        }
    }

    private fun loadingText(index: Int): String {
        val host = UrlList.host(entries[index].url) ?: ""
        return if (entries.size > 1) {
            context.getString(R.string.loading_page_of, index + 1, entries.size, host)
        } else {
            context.getString(R.string.loading_page, host)
        }
    }

    /** The renderer crashed (e.g. out of memory): replace the WebView instead of crashing the app. */
    private fun recreate(page: Page) {
        if (destroyed || pages[page.index] !== page) return
        val oldView = page.webView
        val position = pagesLayer.indexOfChild(oldView)
        val newView = createWebView(page)
        newView.visibility = oldView.visibility
        destroyWebView(oldView)
        pagesLayer.addView(newView, if (position >= 0) position else pagesLayer.childCount, matchParent())
        page.webView = newView
        if (page === visible || page === pending) {
            load(page)
        } else {
            page.status = Status.IDLE
            page.fresh = false
        }
    }

    // --- Views --------------------------------------------------------------------------------

    private fun addPage(index: Int): Page {
        val page = Page(index)
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
            // Pages are told the user prefers a dark scheme through the app's dark theme
            // (targetSdk 33+); pages without a dark design are not darkened algorithmically.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) isAlgorithmicDarkeningAllowed = false
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

        private fun isCurrent(view: WebView) =
            !destroyed && view === page.webView && pages[page.index] === page

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
            if (isCurrent(view) && request.isForMainFrame) {
                fail(page, PageFailure(FailureKind.fromWebViewError(error.errorCode), atMillis = wallClock()))
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            val status = errorResponse.statusCode
            if (isCurrent(view) && request.isForMainFrame && status >= 400) {
                fail(page, PageFailure(FailureKind.HTTP, httpStatus = status, atMillis = wallClock()))
            }
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            if (isCurrent(view) && page.status == Status.LOADING && error.url == page.entry.url) {
                fail(page, PageFailure(FailureKind.CERTIFICATE, atMillis = wallClock()))
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (view === page.webView) handler.post { recreate(page) }
            return true
        }
    }

    private fun wallClock() = System.currentTimeMillis()

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
