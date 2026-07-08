package com.example.twincompanion

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

data class AntonioContext(
    val status: String,
    val message: String,
    val emoji: String,
    val displayText: String,
    val updatedAtMillis: Long
)

/**
 * Escucha el documento context/antonio en Firestore EN TIEMPO REAL.
 * Cuando Antonio actualiza su estado (via el backend), este listener se dispara
 * solo — sin polling — y notifica al OverlayService.
 */
class ContextRepository(
    private val onContext: (AntonioContext) -> Unit
) {
    private val db = FirebaseFirestore.getInstance()
    private var registration: ListenerRegistration? = null
    private var lastSeenMillis: Long = -1L

    fun startListening() {
        registration = db.collection("context").document(OWNER_ID)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error escuchando contexto", error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val displayText = snapshot.getString("display_text") ?: return@addSnapshotListener
                val updatedAt = snapshot.getLong("updated_at_millis") ?: 0L

                // Solo reaccionar si es contexto realmente nuevo
                if (updatedAt <= lastSeenMillis) return@addSnapshotListener
                val isFirstLoad = lastSeenMillis == -1L
                lastSeenMillis = updatedAt

                // En el primer arranque no interrumpimos: solo guardamos el estado.
                if (isFirstLoad) return@addSnapshotListener

                onContext(
                    AntonioContext(
                        status = snapshot.getString("status") ?: "available",
                        message = snapshot.getString("message") ?: "",
                        emoji = snapshot.getString("emoji") ?: "",
                        displayText = displayText,
                        updatedAtMillis = updatedAt
                    )
                )
            }
    }

    fun stopListening() {
        registration?.remove()
        registration = null
    }

    companion object {
        private const val TAG = "ContextRepository"
        private const val OWNER_ID = "antonio"
    }
}
