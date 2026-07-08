package com.example.twincompanion

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        scroll.addView(buildLayout())
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun buildLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 80, 56, 80)
        }

        // ── Header ──────────────────────────────────────────
        val header = TextView(this).apply {
            text = "👾"
            textSize = 56f
            gravity = Gravity.CENTER
        }

        val appName = TextView(this).apply {
            text = "Twin Antonio"
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 6)
        }

        val tagline = TextView(this).apply {
            text = "tu gemelo digital en su teléfono"
            setTextColor(Color.parseColor("#888888"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        // ── Estado ───────────────────────────────────────────
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground("#1A1A1A", 20)
            setPadding(32, 28, 32, 28)
        }

        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(18, 18).also { it.marginEnd = 20 }
            background = dot("#7F8C8D")
        }

        statusText = TextView(this).apply {
            text = "Antonio inactivo"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 15f
            typeface = Typeface.MONOSPACE
        }

        statusCard.addView(statusDot)
        statusCard.addView(statusText)

        // ── Botón principal ──────────────────────────────────
        toggleButton = Button(this).apply {
            text = "Activar"
            textSize = 17f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            background = cardBackground("#2E86DE", 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 24 }
            setPadding(0, 28, 0, 28)
            setOnClickListener { toggle() }
        }

        // ── Separador ────────────────────────────────────────
        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#222222"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.topMargin = 48; it.bottomMargin = 40 }
        }

        // ── Cómo instalar ────────────────────────────────────
        val installTitle = TextView(this).apply {
            text = "Cómo instalar"
            setTextColor(Color.parseColor("#888888"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.15f
            setPadding(0, 0, 0, 20)
        }

        val steps = listOf(
            "1. Generá el APK en Android Studio\n   Build → Build APK(s)",
            "2. Mandá el archivo .apk por WhatsApp\n   o Google Drive",
            "3. La otra persona lo abre en su teléfono\n   y presiona Instalar",
            "4. Si bloquea: Ajustes → Seguridad →\n   Instalar apps desconocidas → activar",
            "5. Abrir la app → dar permiso de overlay\n   → el muñeco aparece ✓"
        )

        root.addView(header)
        root.addView(appName)
        root.addView(tagline)
        root.addView(statusCard)
        root.addView(toggleButton)
        root.addView(divider)
        root.addView(installTitle)

        steps.forEach { step ->
            root.addView(TextView(this).apply {
                text = step
                setTextColor(Color.parseColor("#BBBBBB"))
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, 0, 20)
                setLineSpacing(0f, 1.4f)
            })
        }

        return root
    }

    private fun updateStatus() {
        val running = OverlayService.isRunning
        statusDot.background = dot(if (running) "#27AE60" else "#7F8C8D")
        statusText.text = if (running) "Antonio activo" else "Antonio inactivo"
        toggleButton.text = if (running) "Desactivar" else "Activar"
        toggleButton.background = cardBackground(if (running) "#C0392B" else "#2E86DE", 14)
    }

    private fun toggle() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        if (OverlayService.isRunning) {
            stopService(Intent(this, OverlayService::class.java))
        } else {
            startForegroundService(Intent(this, OverlayService::class.java))
        }
        toggleButton.postDelayed({ updateStatus() }, 350)
    }

    // ── Helpers de drawable ───────────────────────────────────

    private fun cardBackground(hex: String, radiusDp: Int): GradientDrawable {
        val r = (radiusDp * resources.displayMetrics.density)
        return GradientDrawable().apply {
            setColor(Color.parseColor(hex))
            cornerRadius = r
        }
    }

    private fun dot(hex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
        }
    }
}
