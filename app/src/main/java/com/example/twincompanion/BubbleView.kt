package com.example.twincompanion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Nube de dialogo que flota SOBRE el muneco y lo sigue mientras camina,
 * dando la impresion de que el lo esta diciendo/pensando.
 */
object BubbleView {

    private var wm: WindowManager? = null
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var removalRunnable: Runnable? = null

    private var density = 1f
    private var screenW = 0

    fun show(context: Context, text: String, anchorX: Int, anchorY: Int, durationMs: Long = 3000L) {
        density = context.resources.displayMetrics.density
        screenW = context.resources.displayMetrics.widthPixels
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = windowManager

        // Limpia cualquier nube anterior
        removalRunnable?.let { handler.removeCallbacks(it) }
        view?.let { old -> runCatching { windowManager.removeView(old) } }

        val tv = TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#1B1B1B"))
            background = CloudBubbleDrawable(density)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            setPadding(dp(26), dp(22), dp(26), dp(34))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 14f
            setLineSpacing(0f, 1.1f)
            maxWidth = (screenW * 0.72f).toInt()
            alpha = 0f
        }

        // Medimos para poder posicionar la nube respecto al muneco
        tv.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        position(lp, anchorX, anchorY, tv.measuredWidth, tv.measuredHeight)

        windowManager.addView(tv, lp)
        view = tv
        params = lp

        tv.animate().alpha(1f).setDuration(200).start()

        val r = Runnable {
            tv.animate().alpha(0f).setDuration(300)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        runCatching { windowManager.removeView(tv) }
                        if (view == tv) {
                            view = null
                            params = null
                        }
                    }
                }).start()
        }
        removalRunnable = r
        handler.postDelayed(r, durationMs)
    }

    /** Reubica la nube para que siga al muneco (llamado en cada paso). */
    fun updateAnchor(anchorX: Int, anchorY: Int) {
        val v = view ?: return
        val lp = params ?: return
        val windowManager = wm ?: return
        val w = if (v.width > 0) v.width else v.measuredWidth
        val h = if (v.height > 0) v.height else v.measuredHeight
        position(lp, anchorX, anchorY, w, h)
        runCatching { windowManager.updateViewLayout(v, lp) }
    }

    private fun position(lp: WindowManager.LayoutParams, anchorX: Int, anchorY: Int, w: Int, h: Int) {
        val spritePx = SPRITE_SIZE_DP * density
        val centerX = anchorX + spritePx / 2f
        // Los puntitos estan ~32% del ancho: alineamos eso con el centro del muneco
        var x = (centerX - 0.32f * w).toInt()
        // La nube flota arriba de la cabeza; los puntitos bajan hacia el
        var y = (anchorY - h + dp(24)).toInt()

        val margin = dp(6)
        x = x.coerceIn(margin, (screenW - w - margin).coerceAtLeast(margin))
        y = y.coerceAtLeast(margin)
        lp.x = x
        lp.y = y
    }

    private fun dp(v: Int) = (v * density).toInt()
}
