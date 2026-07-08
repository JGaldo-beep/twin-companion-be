package com.example.twincompanion

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recibe los avisos proactivos (push FCM) y se los pasa al OverlayService
 * para que el muñeco aparezca y lo diga en su nube.
 */
class TwinMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        AskRepository.registerDevice(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val text = message.data["text"] ?: return
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_PROACTIVE
            putExtra(OverlayService.EXTRA_TEXT, text)
        }
        startForegroundService(intent)
    }
}
