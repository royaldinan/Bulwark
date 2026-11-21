package id.bulwark.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import id.bulwark.R

/**
 * Shows the blocking warning above any app via SYSTEM_ALERT_WINDOW.
 *
 * The warning never dismisses itself: the user must choose "Kembali ke aman" or
 * "Lanjutkan dengan risiko sendiri" (PRD Fase 2). Built with plain Views so it can be shown from
 * the VpnService without a Compose lifecycle owner. The Compose overlay screen can replace this
 * later without changing callers.
 */
class OverlayController(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun show(domain: String, onStaySafe: () -> Unit, onProceed: () -> Unit) {
        mainHandler.post {
            if (!canDrawOverlays()) return@post
            removeView()
            val view = buildOverlay(domain, onStaySafe, onProceed)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
            runCatching {
                windowManager().addView(view, params)
                overlayView = view
            }
        }
    }

    fun dismiss() {
        mainHandler.post { removeView() }
    }

    fun isShowing(): Boolean = overlayView != null

    private fun removeView() {
        overlayView?.let { view -> runCatching { windowManager().removeView(view) } }
        overlayView = null
    }

    private fun windowManager(): WindowManager = context.getSystemService(WindowManager::class.java)

    private fun buildOverlay(domain: String, onStaySafe: () -> Unit, onProceed: () -> Unit): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(28f).toFloat()
            }
        }

        card.addView(text(context.getString(R.string.overlay_title), 22f, Typeface.BOLD, Color.parseColor("#0B1B4D")))
        card.addView(text(domain, 16f, Typeface.NORMAL, Color.parseColor("#0B1B4D")).apply {
            setPadding(0, dp(12), 0, 0)
        })
        card.addView(text(context.getString(R.string.overlay_hint), 14f, Typeface.NORMAL, Color.parseColor("#5A6275")).apply {
            setPadding(0, dp(8), 0, dp(20))
        })

        card.addView(pillButton(context.getString(R.string.overlay_stay_safe), isPrimary = true) {
            dismiss()
            onStaySafe()
        })

        card.addView(pillButton(context.getString(R.string.overlay_proceed), isPrimary = false) {
            dismiss()
            onProceed()
        }.apply {
            val lp = layoutParams as LinearLayout.LayoutParams
            lp.topMargin = dp(12)
            layoutParams = lp
        })

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(24)
        }

        root.addView(card, cardParams)
        return root
    }

    private fun text(content: String, sizeSp: Float, style: Int, color: Int): TextView =
        TextView(context).apply {
            text = content
            textSize = sizeSp
            setTextColor(color)
            typeface = Typeface.create(Typeface.DEFAULT, style)
        }

    private fun pillButton(label: String, isPrimary: Boolean, onClick: () -> Unit): TextView {
        val background = GradientDrawable().apply {
            cornerRadius = dp(28f).toFloat()
            if (isPrimary) {
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(Color.parseColor("#2456E8"), Color.parseColor("#6A3FE0"))
            } else {
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#E3E6F0"))
            }
        }
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(if (isPrimary) Color.WHITE else Color.parseColor("#0B1B4D"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            this.background = background
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56),
            )
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    private fun dp(value: Float): Int = dp(value.toInt())
}
