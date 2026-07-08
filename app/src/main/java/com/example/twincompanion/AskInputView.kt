package com.example.twincompanion

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout

/**
 * Cuadro de texto flotante para preguntarle a Antonio.
 * Va ARRIBA de la pantalla (el teclado aparece abajo), asi no tapa al muneco.
 */
object AskInputView {

    private var wm: WindowManager? = null
    private var view: View? = null

    fun show(context: Context, onSubmit: (String) -> Unit) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        dismiss()

        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val screenW = context.resources.displayMetrics.widthPixels

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#2B2B2B"))
            }
            isFocusableInTouchMode = true
        }

        val input = EditText(context).apply {
            hint = "Preguntale a Antonio…"
            setHintTextColor(Color.parseColor("#999999"))
            setTextColor(Color.parseColor("#1B1B1B"))
            textSize = 15f
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = null
        }

        val send = Button(context).apply {
            text = "Enviar"
            isAllCaps = false
            textSize = 14f
        }

        bar.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(send, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        fun submit() {
            val q = input.text.toString().trim()
            imm.hideSoftInputFromWindow(input.windowToken, 0)
            dismiss()
            if (q.isNotEmpty()) onSubmit(q)
        }

        send.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                submit(); true
            } else false
        }
        // Back cierra
        bar.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                imm.hideSoftInputFromWindow(input.windowToken, 0); dismiss(); true
            } else false
        }
        // Tocar afuera cierra
        bar.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                imm.hideSoftInputFromWindow(input.windowToken, 0); dismiss(); true
            } else false
        }

        val sideMargin = dp(12)
        val params = WindowManager.LayoutParams(
            screenW - sideMargin * 2,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // SIN FLAG_NOT_FOCUSABLE -> puede recibir teclado
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = sideMargin
            y = dp(48)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        windowManager.addView(bar, params)
        wm = windowManager
        view = bar

        input.requestFocus()
        bar.postDelayed({ imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }, 120)
    }

    fun dismiss() {
        view?.let { runCatching { wm?.removeView(it) } }
        view = null
    }
}
