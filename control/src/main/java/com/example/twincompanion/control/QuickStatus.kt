package com.example.twincompanion.control

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

/**
 * Notificacion fija de "captura rapida": desde la barra de notificaciones, sin abrir
 * la app, Antonio escribe que esta haciendo (o toca un atajo) y se sincroniza al instante.
 */
object QuickStatus {

    const val CHANNEL_ID = "quick_status"
    const val NOTIF_ID = 42
    const val ACTION_REPLY = "com.example.twincompanion.control.REPLY"
    const val ACTION_PRESET = "com.example.twincompanion.control.PRESET"
    const val KEY_TEXT = "quick_text"
    const val EXTRA_PRESET = "preset_text"

    private val PRESETS = listOf("Descansando 😌", "Trabajando 🎧", "Comiendo 🍽️")

    fun post(context: Context, lastActivity: String?) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Captura rápida", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Decí qué estás haciendo sin abrir la app"
            }
        )

        // Campo de texto inline
        val remoteInput = RemoteInput.Builder(KEY_TEXT)
            .setLabel("¿Qué estás haciendo?")
            .build()
        val replyIntent = Intent(context, QuickReplyReceiver::class.java).setAction(ACTION_REPLY)
        val replyPending = PendingIntent.getBroadcast(
            context, 1, replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit, "Decir qué hago", replyPending
        ).addRemoteInput(remoteInput).build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("¿Qué estás haciendo?")
            .setContentText(lastActivity?.let { "Última: $it" } ?: "Tocá para contarle a tu twin")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(replyAction)

        // Atajos de un toque
        PRESETS.forEach { preset ->
            val i = Intent(context, QuickReplyReceiver::class.java)
                .setAction(ACTION_PRESET)
                .putExtra(EXTRA_PRESET, preset)
                .setData(Uri.parse("twin://preset/${preset.hashCode()}"))
            val p = PendingIntent.getBroadcast(
                context, preset.hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(NotificationCompat.Action.Builder(0, preset, p).build())
        }

        nm.notify(NOTIF_ID, builder.build())
    }
}
