package com.example.twincompanion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Nube de dialogo dibujada en canvas (sin imagenes — pesa nada, nitida a cualquier dpi).
 * Borde ondulado tipo nube, relleno blanco, contorno suave, sombrita y dos puntitos
 * que apuntan hacia el muneco.
 */
class CloudBubbleDrawable(private val density: Float) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(6f * density, 0f, 3f * density, 0x33000000)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2B2B")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeJoin = Paint.Join.ROUND
    }

    private val bump = 9f * density          // radio de cada "bollito" de la nube
    private val shadowPad = 7f * density     // margen para que la sombra no se corte
    private val dotZone = 20f * density      // espacio abajo para los puntitos

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cr = RectF(
            b.left + shadowPad + bump,
            b.top + shadowPad + bump,
            b.right - shadowPad - bump,
            b.bottom - shadowPad - bump - dotZone
        )
        if (cr.right <= cr.left || cr.bottom <= cr.top) return

        val countX = max(2, ((cr.right - cr.left) / (2 * bump)).roundToInt())
        val rX = (cr.right - cr.left) / (2 * countX)
        val countY = max(2, ((cr.bottom - cr.top) / (2 * bump)).roundToInt())
        val rY = (cr.bottom - cr.top) / (2 * countY)

        val path = Path().apply {
            moveTo(cr.left, cr.top)
            for (i in 0 until countX) {
                val cx = cr.left + rX * (2 * i + 1)
                arcTo(RectF(cx - rX, cr.top - rX, cx + rX, cr.top + rX), 180f, 180f, false)
            }
            for (i in 0 until countY) {
                val cy = cr.top + rY * (2 * i + 1)
                arcTo(RectF(cr.right - rY, cy - rY, cr.right + rY, cy + rY), -90f, 180f, false)
            }
            for (i in 0 until countX) {
                val cx = cr.right - rX * (2 * i + 1)
                arcTo(RectF(cx - rX, cr.bottom - rX, cx + rX, cr.bottom + rX), 0f, 180f, false)
            }
            for (i in 0 until countY) {
                val cy = cr.bottom - rY * (2 * i + 1)
                arcTo(RectF(cr.left - rY, cy - rY, cr.left + rY, cy + rY), 90f, 180f, false)
            }
            close()
        }

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)

        // Dos puntitos hacia el muneco (abajo-izquierda)
        val dotX = cr.left + (cr.right - cr.left) * 0.32f
        val baseY = cr.bottom + bump
        drawDot(canvas, dotX, baseY + 6f * density, 6f * density)
        drawDot(canvas, dotX - 9f * density, baseY + 16f * density, 3.5f * density)
    }

    private fun drawDot(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, strokePaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
