package com.example.twincompanion.control

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/**
 * Administra la "memoria" del clon: hechos sobre Antonio que el AI usa siempre.
 * Ej: "Soy boliviano viviendo en Colombia", "Me gusta el indie y el reggaeton".
 */
class MemoryActivity : Activity() {

    private lateinit var listContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        load()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "🧠 Memoria de Antonio"
            textSize = 22f
            setTextColor(Color.parseColor("#1B1B1B"))
        })
        root.addView(TextView(this).apply {
            text = "Cosas que tu clon sabe de vos y usa al responder."
            textSize = 13f
            setTextColor(Color.parseColor("#777777"))
            setPadding(0, dp(4), 0, dp(16))
        })

        val addRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Ej: Me gusta el reggaeton y el indie"
            maxLines = 2
        }
        val addBtn = Button(this).apply {
            text = "Agregar"
            isAllCaps = false
            setOnClickListener { addFact() }
        }
        addRow.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addRow.addView(addBtn)
        root.addView(addRow)

        status = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status)

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(listContainer) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        return root
    }

    private fun load() {
        status.text = "Cargando…"
        thread {
            val facts = ControlRepository.getMemory()
            runOnUiThread { render(facts) }
        }
    }

    private fun addFact() {
        val fact = input.text.toString().trim()
        if (fact.isEmpty()) return
        input.setText("")
        status.text = "Guardando…"
        thread {
            val facts = ControlRepository.addMemory(fact)
            runOnUiThread { render(facts) }
        }
    }

    private fun removeFact(fact: String) {
        status.text = "Borrando…"
        thread {
            val facts = ControlRepository.removeMemory(fact)
            runOnUiThread { render(facts) }
        }
    }

    private fun render(facts: List<String>) {
        status.text = if (facts.isEmpty()) "Todavía no hay nada. Agregá algo sobre vos." else "${facts.size} cosa(s)"
        listContainer.removeAllViews()
        facts.forEach { fact ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            row.addView(TextView(this).apply {
                text = "• $fact"
                textSize = 15f
                setTextColor(Color.parseColor("#1B1B1B"))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "✕"
                isAllCaps = false
                setOnClickListener { removeFact(fact) }
            })
            listContainer.addView(row)
        }
    }
}
