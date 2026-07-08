package com.example.twincompanion.control

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class StatusUpdateActivity : Activity() {

    private data class Estado(val key: String, val label: String, val emoji: String)

    private val estados = listOf(
        Estado("working", "Trabajando", "🎧"),
        Estado("available", "Disponible", "👋"),
        Estado("sleeping", "Durmiendo", "😴"),
        Estado("traveling", "Viajando", "✈️")
    )

    private var selected: Estado = estados[1]
    private val estadoButtons = mutableMapOf<String, Button>()
    private lateinit var messageInput: EditText
    private lateinit var counter: TextView
    private lateinit var statusLine: TextView
    private lateinit var calendarLine: TextView
    private lateinit var locationLine: TextView

    private var calendarText: String? = null
    private var locationText: String? = null

    private val accent = Color.parseColor("#FF6B35")
    private val idleBg = Color.parseColor("#EEEEEE")

    private val AUTO_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        refreshSelection()
        ensureAutoSources()
    }

    override fun onResume() {
        super.onResume()
        refreshAuto()
        QuickStatus.post(this, null)
    }

    private fun ensureAutoSources() {
        val needed = mutableListOf<String>()
        if (!CalendarReader.hasPermission(this)) needed.add(android.Manifest.permission.READ_CALENDAR)
        if (!LocationReader.hasPermission(this)) needed.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) refreshAuto()
        else requestPermissions(needed.toTypedArray(), AUTO_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUTO_PERMISSION_CODE) {
            refreshAuto()
            QuickStatus.post(this, null)
        }
    }

    /** Lee calendario + ubicacion y los sincroniza al backend sin pisar el estado manual. */
    private fun refreshAuto() {
        thread {
            val cal = if (CalendarReader.hasPermission(this)) CalendarReader.read(this) else null
            val loc = if (LocationReader.hasPermission(this)) LocationReader.read(this) else null
            calendarText = cal
            locationText = loc
            runOnUiThread {
                calendarLine.text = "📅 " + (cal ?: "Sin acceso al calendario")
                locationLine.text = "📍 " + (loc ?: "Sin acceso a la ubicacion")
            }
            if (!cal.isNullOrBlank() || !loc.isNullOrBlank()) {
                ControlRepository.syncAuto(calendar = cal, locationCity = loc)
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "🟢 ¿Qué estás haciendo, Antonio?"
            textSize = 22f
            setTextColor(Color.parseColor("#1B1B1B"))
            setPadding(0, 0, 0, dp(24))
        })

        // Grid 2x2 de estados
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        estados.chunked(2).forEach { fila ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fila.forEach { estado ->
                val btn = Button(this).apply {
                    text = "${estado.label} ${estado.emoji}"
                    isAllCaps = false
                    setOnClickListener {
                        selected = estado
                        refreshSelection()
                    }
                }
                estadoButtons[estado.key] = btn
                row.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            grid.addView(row)
        }
        root.addView(grid)

        root.addView(TextView(this).apply {
            text = "Mensaje (opcional):"
            setPadding(0, dp(24), 0, dp(8))
            setTextColor(Color.parseColor("#555555"))
        })

        messageInput = EditText(this).apply {
            hint = "Ej: Grabando un video"
            setText("")
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                    if ((s?.length ?: 0) > 120) {
                        messageInput.setText(s?.subSequence(0, 120))
                        messageInput.setSelection(120)
                    }
                    updateCounter()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(messageInput)

        counter = TextView(this).apply {
            text = "0/120"
            gravity = Gravity.END
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
        }
        root.addView(counter)

        calendarLine = TextView(this).apply {
            text = "📅 Leyendo calendario…"
            setPadding(0, dp(20), 0, 0)
            textSize = 14f
            setTextColor(Color.parseColor("#444444"))
        }
        root.addView(calendarLine)

        locationLine = TextView(this).apply {
            text = "📍 Leyendo ubicación…"
            setPadding(0, dp(8), 0, 0)
            textSize = 14f
            setTextColor(Color.parseColor("#444444"))
        }
        root.addView(locationLine)

        root.addView(Button(this).apply {
            text = "📍 Guardar lugar actual (Casa, Oficina…)"
            isAllCaps = false
            textSize = 13f
            setOnClickListener { savePlaceDialog() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(10), 0, 0)
        })

        root.addView(Button(this).apply {
            text = "Actualizar"
            isAllCaps = false
            textSize = 16f
            setOnClickListener { actualizar() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(24), 0, 0)
        })

        statusLine = TextView(this).apply {
            text = ""
            setPadding(0, dp(20), 0, 0)
            setTextColor(Color.parseColor("#777777"))
            gravity = Gravity.CENTER
        }
        root.addView(statusLine)

        root.addView(Button(this).apply {
            text = "🧠 Memoria del clon"
            isAllCaps = false
            textSize = 14f
            setOnClickListener {
                startActivity(android.content.Intent(this@StatusUpdateActivity, MemoryActivity::class.java))
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(16), 0, 0)
        })

        return root
    }

    private fun refreshSelection() {
        estadoButtons.forEach { (key, btn) ->
            val isSel = key == selected.key
            btn.setBackgroundColor(if (isSel) accent else idleBg)
            btn.setTextColor(if (isSel) Color.WHITE else Color.parseColor("#333333"))
        }
    }

    private fun updateCounter() {
        counter.text = "${messageInput.text.length}/120"
    }

    private fun savePlaceDialog() {
        val loc = LocationReader.lastRaw
        if (loc == null) {
            Toast.makeText(this, "Aún no tengo tu ubicación, esperá un momento", Toast.LENGTH_SHORT).show()
            refreshAuto()
            return
        }
        val opciones = arrayOf("Casa 🏠", "Oficina 💼", "Otro…")
        android.app.AlertDialog.Builder(this)
            .setTitle("Guardar este lugar como…")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> doSavePlace("Casa", loc)
                    1 -> doSavePlace("Oficina", loc)
                    2 -> askPlaceName(loc)
                }
            }
            .show()
    }

    private fun askPlaceName(loc: android.location.Location) {
        val input = EditText(this).apply { hint = "Ej: Gimnasio, Café…" }
        android.app.AlertDialog.Builder(this)
            .setTitle("Nombre del lugar")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) doSavePlace(name, loc)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun doSavePlace(name: String, loc: android.location.Location) {
        PlacesStore.save(this, name, loc.latitude, loc.longitude)
        Toast.makeText(this, "Lugar guardado: $name", Toast.LENGTH_SHORT).show()
        refreshAuto()  // ahora deberia mostrar el nombre
    }

    private fun actualizar() {
        val message = messageInput.text.toString().trim()
        statusLine.text = "Enviando…"
        thread {
            val result = ControlRepository.postContext(
                status = selected.key,
                emoji = selected.emoji,
                message = message,
                locationCity = locationText,
                calendar = calendarText
            )
            runOnUiThread {
                if (result.ok) {
                    statusLine.text = "✅ Actualizado: ${result.displayText}"
                    Toast.makeText(this, "Estado actualizado", Toast.LENGTH_SHORT).show()
                } else {
                    statusLine.text = "❌ ${result.error}"
                    Toast.makeText(this, "Error al actualizar", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
