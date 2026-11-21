package id.bulwark.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import id.bulwark.R

/**
 * Full-screen, opaque warning shown above any app via SYSTEM_ALERT_WINDOW.
 *
 * - Exactly one warning on screen; removed on button press, when the user leaves the app to
 *   HOME/RECENTS (window focus lost, plus ACTION_CLOSE_SYSTEM_DIALOGS as a fallback on OEMs that
 *   still send it), after [OVERLAY_TIMEOUT_MILLIS] without interaction, and when the service stops.
 * - "Kembali ke aman" and the system Back key -> dismiss + open the Bulwark home screen (handled by
 *   the caller). "Lanjutkan dengan risiko sendiri" -> dismiss + allowlist (handled by the caller).
 * - [show] takes an [onShown] callback so the caller records the link event only after the warning
 *   is really on screen (one overlay = one event = +1 counter).
 *
 * Built with plain Views so it can run from the VpnService without a Compose lifecycle owner.
 */
class OverlayController(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager: WindowManager by lazy { context.getSystemService(WindowManager::class.java) }

    private var overlayView: View? = null
    private var overlayDomain: String? = null
    private var closedCallback: ((reason: String) -> Unit)? = null
    private var receiverRegistered = false
    private var timeoutRunnable: Runnable? = null

    private val appSwitchReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS && overlayView != null) {
                // Deprecated on newer Android; kept as a fallback for OEMs (e.g. Infinix) that
                // still broadcast it. Semantically the user left the app.
                dismissInternal("focus_lost")
            }
        }
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun show(
        domain: String,
        onShown: () -> Unit,
        onStaySafe: () -> Unit,
        onProceed: () -> Unit,
        onClosed: (reason: String) -> Unit,
    ) {
        WarningBridge.onShown = onShown
        WarningBridge.onStaySafe = onStaySafe
        WarningBridge.onProceed = onProceed
        WarningBridge.onClosed = onClosed
        val intent = Intent(context, BlockWarningActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(BlockWarningActivity.EXTRA_DOMAIN, domain)
        mainHandler.post {
            runCatching { context.startActivity(intent) }
                .onFailure { e ->
                    Log.w(TAG, "overlay failed to start domain=$domain: ${e.javaClass.simpleName}: ${e.message}")
                    WarningBridge.reset()
                    onClosed("add_failed")
                }
        }
    }

    fun showFileBlocked(fileName: String) {
        mainHandler.post {
            if (!canDrawOverlays()) {
                Log.w(TAG, "overlay failed: SYSTEM_ALERT_WINDOW not granted file=$fileName")
                return@post
            }
            clearExisting()
            val view = buildFileBlocked(fileName)
            try {
                windowManager.addView(view, fullScreenParams())
                overlayView = view
                overlayDomain = fileName
                closedCallback = null
                registerAppSwitchReceiver()
                scheduleTimeout(fileName, view)
                watchWindowFocus(fileName, view)
                Log.i(TAG, "overlay shown file=$fileName")
            } catch (e: Exception) {
                Log.w(TAG, "overlay failed file=$fileName: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    fun dismiss(reason: String = "dismiss") {
        mainHandler.post { WarningBridge.finish(reason) }
    }

    fun isShowing(): Boolean = WarningBridge.isAttached()

    private fun dismissInternal(reason: String) {
        val callback = closedCallback
        closedCallback = null
        val domain = overlayDomain
        removeView()
        unregisterAppSwitchReceiver()
        Log.i(TAG, "overlay removed domain=${domain ?: "-"} reason=$reason")
        callback?.invoke(reason)
    }

    private fun clearExisting() {
        val callback = closedCallback
        closedCallback = null
        removeView()
        unregisterAppSwitchReceiver()
        // A warning that gets replaced must still release the gate (otherwise it stays "visible"
        // forever and every later warning is suppressed).
        callback?.invoke("replaced")
    }

    private fun removeView() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        overlayView?.let { view -> runCatching { windowManager.removeView(view) } }
        overlayView = null
        overlayDomain = null
    }

    /** Safety net: never leave an opaque full-screen warning hanging for good. */
    private fun scheduleTimeout(label: String, view: View) {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            if (overlayView === view) {
                Log.i(TAG, "overlay timeout label=$label afterMs=$OVERLAY_TIMEOUT_MILLIS")
                dismissInternal("timeout")
            }
        }
        timeoutRunnable = runnable
        mainHandler.postDelayed(runnable, OVERLAY_TIMEOUT_MILLIS)
    }

    /**
     * The overlay window is focusable and opaque: when the user goes to HOME/RECENTS it loses window
     * focus, which is the reliable signal on OEMs that no longer send ACTION_CLOSE_SYSTEM_DIALOGS.
     */
    private fun watchWindowFocus(label: String, view: View) {
        var hadFocus = false
        view.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                hadFocus = true
            } else if (hadFocus && overlayView === view) {
                Log.i(TAG, "overlay lost window focus label=$label")
                dismissInternal("focus_lost")
            }
        }
    }

    private fun registerAppSwitchReceiver() {
        if (receiverRegistered) return
        runCatching {
            val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(appSwitchReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(appSwitchReceiver, filter)
            }
            receiverRegistered = true
        }.onFailure { Log.w(TAG, "app-switch receiver failed: ${it.message}") }
    }

    private fun unregisterAppSwitchReceiver() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(appSwitchReceiver) }
        receiverRegistered = false
    }

    private fun fullScreenParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )

    private fun buildLinkWarning(domain: String, onStaySafe: () -> Unit, onProceed: () -> Unit): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
            // Focusable opaque window: required for the Back key and for the focus-lost signal.
            isFocusableInTouchMode = true
            requestFocus()
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismissInternal("button_safe")
                    onStaySafe()
                    true
                } else {
                    false
                }
            }
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(64), dp(24), dp(32))
        }

        column.addView(text(context.getString(R.string.overlay_title), 26f, Typeface.BOLD, NAVY))
        column.addView(
            text(domain, 20f, Typeface.BOLD, NAVY).apply {
                setPadding(0, dp(16), 0, 0)
            },
        )
        column.addView(
            text(context.getString(R.string.warning_explanation), 15f, Typeface.NORMAL, SECONDARY).apply {
                setPadding(0, dp(12), 0, 0)
            },
        )

        // Push the actions to the bottom of the screen.
        column.addView(View(context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        column.addView(
            pillButton(context.getString(R.string.overlay_stay_safe), primary = true) {
                dismissInternal("button_safe")
                onStaySafe()
            },
        )
        column.addView(
            pillButton(context.getString(R.string.overlay_proceed), primary = false) {
                dismissInternal("button_continue")
                onProceed()
            }.apply {
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

    private fun buildFileBlocked(fileName: String): View {
        val root = FrameLayout(context).apply { setBackgroundColor(Color.WHITE) }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(64), dp(24), dp(32))
        }

        column.addView(text(context.getString(R.string.overlay_file_title), 26f, Typeface.BOLD, NAVY))
        column.addView(
            text(fileName, 20f, Typeface.BOLD, NAVY).apply {
                setPadding(0, dp(16), 0, 0)
            },
        )
        column.addView(
            text(context.getString(R.string.overlay_file_hint), 15f, Typeface.NORMAL, SECONDARY).apply {
                setPadding(0, dp(12), 0, 0)
            },
        )
        column.addView(View(context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(
            pillButton(context.getString(R.string.overlay_file_dismiss), primary = true) {
                dismissInternal("file_dismiss")
            },
        )

        root.addView(
            column,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        return root
    }

    private fun text(content: String, sizeSp: Float, style: Int, color: Int): TextView =
        TextView(context).apply {
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
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(if (primary) Color.WHITE else NAVY)
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

    companion object {
        private const val TAG = "BulwarkDns"
        private const val OVERLAY_TIMEOUT_MILLIS = 60_000L
        private val NAVY = Color.parseColor("#0B1B4D")
        private val SECONDARY = Color.parseColor("#5A6275")
        private val LINE = Color.parseColor("#E3E6F0")
        private val GRADIENT_START = Color.parseColor("#2456E8")
        private val GRADIENT_END = Color.parseColor("#6A3FE0")
    }
}
