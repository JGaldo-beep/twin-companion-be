package com.example.twincompanion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class OverlayView(
    context: Context,
    private val service: OverlayService
) : View(context) {

    val spriteEngine = SpriteEngine(context)
    private val paint = Paint().apply { isFilterBitmap = false }

    private var touchStartRawX = 0f
    private var touchStartRawY = 0f
    private var spriteStartX = 0
    private var spriteStartY = 0
    private var isDragging = false
    private val dragThresholdPx = (12 * resources.displayMetrics.density)

    init {
        spriteEngine.onFrameChanged = { postInvalidateOnAnimation() }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(
            spriteEngine.currentBitmap,
            null,
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            paint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartRawX = event.rawX
                touchStartRawY = event.rawY
                val (sx, sy) = service.getCurrentPosition()
                spriteStartX = sx
                spriteStartY = sy
                isDragging = false
                service.startDrag()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchStartRawX
                val dy = event.rawY - touchStartRawY
                if (!isDragging && (abs(dx) > dragThresholdPx || abs(dy) > dragThresholdPx)) {
                    isDragging = true
                }
                if (isDragging) {
                    service.updatePosition(
                        (spriteStartX + dx).toInt(),
                        (spriteStartY + dy).toInt()
                    )
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val (fx, fy) = service.getCurrentPosition()
                service.endDrag(fx, fy)
                if (!isDragging) {
                    showBubble()
                }
                isDragging = false
                return true
            }
        }
        return false
    }

    fun startAnimation() {
        spriteEngine.startWalking(Direction.RIGHT)
    }

    private fun showBubble() {
        // Al tocar: abrir el cuadro para preguntarle a Antonio
        AskInputView.show(context) { question ->
            val (x, y) = service.getCurrentPosition()
            BubbleView.show(context, "Pensando… 🤔", x, y, durationMs = 60000L)
            AskRepository.ask(question) { answer ->
                val text = answer ?: "Uy, no pude responder ahora 😬"
                val (nx, ny) = service.getCurrentPosition()
                BubbleView.show(context, text, nx, ny, durationMs = 9000L)
            }
        }
    }

    /** Globo con el contexto real de Antonio (llega de Firestore). Dura mas. */
    fun showContextBubble(text: String) {
        val (x, y) = service.getCurrentPosition()
        BubbleView.show(context, text, x, y, durationMs = 5000L)
    }
}
