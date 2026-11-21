package app.webscreensaver.tv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max

/**
 * Shown while the first page loads (and when nothing is configured): a calm dark gradient with the
 * app logo, name, three pulsing dots and a status line. Animations run only while it is visible.
 */
class WaitingScreen(context: Context) : FrameLayout(context) {

    private val gradient = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        colors = intArrayOf(0xFF1C2A3D.toInt(), 0xFF0E1520.toInt(), 0xFF07090D.toInt())
        setGradientCenter(0.5f, 0.42f)
    }

    private val dots = List(3) {
        View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ACCENT)
            }
            alpha = 0.25f
        }
    }

    private val dotRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        dots.forEach { dot ->
            addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { setMargins(dp(6), 0, dp(6), 0) })
        }
    }

    private val message = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(0xFF9AAEC4.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setLineSpacing(0f, 1.25f)
    }

    private var pulse: Animator? = null
    private var fadeOut: Animator? = null

    init {
        background = gradient
        isFocusable = false

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(48), dp(48), dp(48), dp(48))
        }
        column.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            alpha = 0.9f
        }, LinearLayout.LayoutParams(dp(112), dp(112)))
        column.addView(TextView(context).apply {
            setText(R.string.app_name)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            letterSpacing = 0.02f
        }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        column.addView(dotRow, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(10)).apply {
            topMargin = dp(28)
            bottomMargin = dp(24)
        })
        column.addView(message, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(column, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient.gradientRadius = max(w, h) * 0.75f
    }

    /** Waiting for a page: dots pulse. */
    fun showLoading(text: String) {
        message.text = text
        dotRow.visibility = View.VISIBLE
        startPulse()
    }

    /** Static state (nothing configured, or no page could be loaded): no animation, no CPU. */
    fun showMessage(text: String) {
        message.text = text
        dotRow.visibility = View.INVISIBLE
        stopPulse()
    }

    val isShowing: Boolean get() = visibility == View.VISIBLE && fadeOut == null

    /** Fades away to reveal the page underneath, then stops all animation. */
    fun hide(durationMs: Long) {
        if (visibility != View.VISIBLE || fadeOut != null) return
        fadeOut = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0f).apply {
            duration = durationMs
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = View.GONE
                    alpha = 1f
                    fadeOut = null
                    stopPulse()
                }
            })
            start()
        }
    }

    fun release() {
        fadeOut?.cancel()
        stopPulse()
    }

    private fun startPulse() {
        if (pulse != null) return
        pulse = AnimatorSet().apply {
            playTogether(dots.mapIndexed { i, dot ->
                ObjectAnimator.ofPropertyValuesHolder(
                    dot,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0.25f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 0.8f, 1.15f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.8f, 1.15f),
                ).apply {
                    duration = 600
                    startDelay = i * 200L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                }
            })
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        dots.forEach {
            it.alpha = 0.25f
            it.scaleX = 1f
            it.scaleY = 1f
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val ACCENT = 0xFF64B5F6.toInt()
    }
}
