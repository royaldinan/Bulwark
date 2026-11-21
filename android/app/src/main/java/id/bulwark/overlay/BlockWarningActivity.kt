package id.bulwark.overlay

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import id.bulwark.R

/**
 * Full-screen, opaque block warning shown as an Activity.
 *
 * Why an Activity: a focusable TYPE_APPLICATION_OVERLAY window keeps window focus after HOME on the
 * demo device (verified: mCurrentFocus stayed on the overlay window), so focus-lost could never
 * dismiss it. An Activity gets [onStop] when the user goes HOME/RECENTS and Back is delivered
 * normally, with no extra permission.
 *
 * Close reasons (logged exactly once): button_safe | button_continue | focus_lost | timeout |
 * service_stop. One visible warning = one link_event (the service records it from [WarningBridge]).
 */
class BlockWarningActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { close("timeout") }
    private var closed = false
    private var domain: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        domain = intent.getStringExtra(EXTRA_DOMAIN).orEmpty()
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        window.setBackgroundDrawableResource(android.R.color.white)
        setContentView(buildWarning(domain))
        WarningBridge.attach(this)
        handler.postDelayed(timeoutRunnable, TIMEOUT_MILLIS)
        Log.i(TAG, "overlay shown domain=$domain activity=true")
        WarningBridge.onShown?.invoke()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        close("button_safe")
    }

    override fun onStop() {
        super.onStop()
        // HOME / RECENTS / screen off: the warning must never stay above the launcher.
        if (!closed) close("focus_lost")
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)
        WarningBridge.detach(this)
        super.onDestroy()
    }

    fun close(reason: String) {
        if (closed) return
        closed = true
        handler.removeCallbacks(timeoutRunnable)
        Log.i(TAG, "overlay removed domain=$domain reason=$reason")

        when (reason) {
            "button_continue" -> WarningBridge.onProceed?.invoke()
            "button_safe" -> WarningBridge.onStaySafe?.invoke()
        }
        val callback = WarningBridge.onClosed
        finishAndRemoveTask()
        callback?.invoke(reason)
    }

    private fun buildWarning(domain: String): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            isFocusableInTouchMode = true
            requestFocus()
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(64), dp(24), dp(32))
        }

        column.addView(text(getString(R.string.overlay_title), 26f, Typeface.BOLD, NAVY))
        column.addView(
            text(domain, 20f, Typeface.BOLD, NAVY).apply { setPadding(0, dp(16), 0, 0) },
        )
        column.addView(
            text(getString(R.string.warning_explanation), 15f, Typeface.NORMAL, SECONDARY).apply {
                setPadding(0, dp(12), 0, 0)
            },
        )
        column.addView(View(this), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(
            pillButton(getString(R.string.overlay_stay_safe), primary = true) { close("button_safe") },
        )
        column.addView(
            pillButton(getString(R.string.overlay_proceed), primary = false) { close("button_continue") }.apply {
                val lp = layoutParams as LinearLayout.LayoutParams
                lp.topMargin = dp(12)
                layoutParams = lp
            },
        )

        root.addView(
            column,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        return root
    }

    private fun text(content: String, sizeSp: Float, style: Int, color: Int): TextView =
        TextView(this).apply {
            text = content
            textSize = sizeSp
            setTextColor(color)
            typeface = Typeface.create(Typeface.DEFAULT, style)
        }

    private fun pillButton(label: String, primary: Boolean, onClick: () -> Unit): TextView {
        val background = GradientDrawable().apply {
            cornerRadius = dp(28f).toFloat()
            if (primary) {
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(GRADIENT_START, GRADIENT_END)
            } else {
                setColor(Color.WHITE)
                setStroke(dp(1), LINE)
            }
        }
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(if (primary) Color.WHITE else NAVY)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            this.background = background
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun dp(value: Float): Int = dp(value.toInt())

    companion object {
        const val EXTRA_DOMAIN = "id.bulwark.extra.DOMAIN"
        private const val TAG = "BulwarkDns"
        private const val TIMEOUT_MILLIS = 60_000L
        private val NAVY = Color.parseColor("#0B1B4D")
        private val SECONDARY = Color.parseColor("#5A6275")
        private val LINE = Color.parseColor("#E3E6F0")
        private val GRADIENT_START = Color.parseColor("#2456E8")
        private val GRADIENT_END = Color.parseColor("#6A3FE0")
    }
}
