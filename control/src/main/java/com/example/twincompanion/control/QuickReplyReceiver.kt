package com.example.twincompanion.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlin.concurrent.thread

/**
 * Recibe la respuesta de la notificacion de captura rapida (texto inline o atajo)
 * y la manda al backend, sin abrir la app.
 */
class QuickReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = when (intent.action) {
            QuickStatus.ACTION_REPLY ->
                RemoteInput.getResultsFromIntent(intent)?.getCharSequence(QuickStatus.KEY_TEXT)?.toString()
            QuickStatus.ACTION_PRESET ->
                intent.getStringExtra(QuickStatus.EXTRA_PRESET)
            else -> null
        }?.trim()

        if (text.isNullOrEmpty()) return

        val appContext = context.applicationContext
        val pending = goAsync()
        thread {
            try {
                ControlRepository.setActivity(text)
            } finally {
                // Repinta la notificacion mostrando lo ultimo enviado
                QuickStatus.post(appContext, text)
                pending.finish()
            }
        }
    }
}
