package com.example.twincompanion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var walkingBehavior: WalkingBehavior
    private var contextRepository: ContextRepository? = null

    private var overlayAdded = false
    private var spriteSizePx = 0
    private var minX = 0
    private var maxX = 0
    private var minY = 0
    private var maxY = 0

    private val notificationId = 1
    private val channelId = "twin_overlay"

    companion object {
        const val ACTION_PROACTIVE = "com.example.twincompanion.PROACTIVE"
        const val EXTRA_TEXT = "text"
        var isRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        createNotificationChannel()
        startForeground(notificationId, buildNotification())

        if (!overlayAdded) {
            setupOverlay()
        }

        // Aviso proactivo (push FCM): el muñeco aparece y lo dice
        if (intent?.action == ACTION_PROACTIVE) {
            val text = intent.getStringExtra(EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                overlayView.postDelayed({ showProactive(text) }, 400)
            }
        }
        return START_STICKY
    }

    private fun showProactive(text: String) {
        if (!overlayAdded) return
        walkingBehavior.greet()
        val (x, y) = getCurrentPosition()
        BubbleView.show(this, text, x, y, durationMs = 9000L)
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val (screenWidth, screenHeight) = getScreenSize()
        spriteSizePx = dpToPx(SPRITE_SIZE_DP)
        val sideMargin = dpToPx(8)
        val topSafeMargin = dpToPx(56)
        val bottomMargin = dpToPx(16)

        minX = sideMargin
        maxX = (screenWidth - spriteSizePx - sideMargin).coerceAtLeast(minX)
        minY = topSafeMargin
        maxY = (screenHeight - spriteSizePx - bottomMargin).coerceAtLeast(minY)

        overlayView = OverlayView(this, this)

        params = WindowManager.LayoutParams(
            spriteSizePx,
            spriteSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = minX
            y = maxY
        }

        windowManager.addView(overlayView, params)
        overlayAdded = true

        walkingBehavior = WalkingBehavior(
            service = this,
            spriteEngine = overlayView.spriteEngine,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY
        )
        walkingBehavior.start()

        // Escucha el contexto de Antonio en tiempo real (Firestore)
        contextRepository = ContextRepository { context ->
            onContextUpdated(context)
        }.also { it.startListening() }

        // Registra el token FCM para recibir avisos proactivos
        registerFcmToken()
    }

    private fun registerFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> AskRepository.registerDevice(token) }
    }

    /** Llega contexto nuevo: el muneco se detiene, saluda y muestra el globo. */
    private fun onContextUpdated(context: AntonioContext) {
        walkingBehavior.greet()
        overlayView.showContextBubble(context.displayText)
    }

    fun updatePosition(x: Int, y: Int) {
        params.x = x.coerceIn(minX, maxX)
        params.y = y.coerceIn(minY, maxY)
        windowManager.updateViewLayout(overlayView, params)
        // Si hay una nube visible, que siga al muneco
        BubbleView.updateAnchor(params.x, params.y)
    }

    fun getCurrentPosition(): Pair<Int, Int> = Pair(params.x, params.y)

    fun startDrag() {
        if (::walkingBehavior.isInitialized) walkingBehavior.startDrag()
    }

    fun endDrag(x: Int, y: Int) {
        if (::walkingBehavior.isInitialized) walkingBehavior.endDrag(x, y)
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
        AskInputView.dismiss()
        contextRepository?.stopListening()
        if (::walkingBehavior.isInitialized) walkingBehavior.stop()
        if (::overlayView.isInitialized) {
            overlayView.spriteEngine.stop()
            runCatching { windowManager.removeView(overlayView) }
        }
        overlayAdded = false
    }

    private fun getScreenSize(): Pair<Int, Int> {
        val dm = resources.displayMetrics
        return Pair(dm.widthPixels, dm.heightPixels)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Twin Companion",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Antonio esta activo en tu pantalla" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, channelId)
            .setContentTitle("Antonio esta aqui")
            .setContentText("Tu twin digital esta activo")
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .build()
    }
}
