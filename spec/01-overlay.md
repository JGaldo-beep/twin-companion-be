# Spec 01 — Android Overlay (Fase 1)

## Objetivo

Construir el overlay funcional del muñeco pixel art en Android.
Al final de esta fase, el muñeco de Antonio debe aparecer en pantalla,
caminar por los bordes, y responder a toques básicos.

**Sin AI. Sin backend. Sin contexto.** Solo el muñeco vivo en pantalla.

---

## Entregables de esta fase

- [ ] `OverlayService.kt` — Foreground service que gestiona el overlay
- [ ] `SpriteEngine.kt` — Motor de animación de sprite sheets LPC
- [ ] `OverlayView.kt` — View personalizada que dibuja el muñeco
- [ ] `WalkingBehavior.kt` — Lógica de movimiento por los bordes
- [ ] `BubbleView.kt` — Globo de diálogo simple (texto hardcodeado en Fase 1)
- [ ] `MainActivity.kt` — Solo pide permisos y lanza el service
- [ ] `AndroidManifest.xml` — Con todos los permisos necesarios
- [ ] `antonio_walk.png` — Sprite sheet LPC en `res/drawable`

---

## Arquitectura del overlay

```
MainActivity
    └── Pide permiso SYSTEM_ALERT_WINDOW
    └── Inicia OverlayService

OverlayService (ForegroundService)
    └── OverlayView (dibujada sobre WindowManager)
            └── SpriteEngine (decide qué frame dibujar)
            └── WalkingBehavior (decide dónde moverse)
            └── BubbleView (globo de texto, aparece al tocar)
```

---

## OverlayService.kt

```kotlin
// Responsabilidades:
// 1. Crear la notificación del foreground service
// 2. Inicializar WindowManager
// 3. Crear y agregar OverlayView al WindowManager
// 4. Arrancar el loop de animación
// 5. Limpiar al destruirse

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private lateinit var params: WindowManager.LayoutParams

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        setupOverlay()
        return START_STICKY  // Se reinicia si el sistema lo mata
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = OverlayView(this)

        params = WindowManager.LayoutParams(
            SPRITE_SIZE_PX,           // width
            SPRITE_SIZE_PX,           // height
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        windowManager.addView(overlayView, params)
        overlayView.startAnimation()
    }

    // Actualiza posición del muñeco (llamado por WalkingBehavior)
    fun updatePosition(x: Int, y: Int) {
        params.x = x
        params.y = y
        windowManager.updateViewLayout(overlayView, params)
    }
}
```

---

## SpriteEngine.kt

```kotlin
// Responsabilidades:
// 1. Cargar el sprite sheet LPC
// 2. Cortar el frame correcto según dirección y tiempo
// 3. Exponer el Bitmap del frame actual

// Constantes LPC estándar
const val FRAME_WIDTH = 64
const val FRAME_HEIGHT = 64
const val FRAMES_PER_ROW = 9
const val FRAME_DURATION_MS = 150L

// Filas del sprite sheet LPC
enum class Direction(val row: Int) {
    DOWN(0),
    LEFT(1),
    RIGHT(2),
    UP(3),
    IDLE(4)  // si existe en el sprite sheet
}

class SpriteEngine(context: Context) {

    private val spriteSheet: Bitmap = BitmapFactory.decodeResource(
        context.resources, R.drawable.antonio_walk
    )

    private var currentFrame = 0
    private var currentDirection = Direction.RIGHT
    private val handler = Handler(Looper.getMainLooper())

    // Bitmap del frame actual — OverlayView lo dibuja
    var currentBitmap: Bitmap = getFrame(Direction.RIGHT, 0)
        private set

    fun startWalking(direction: Direction) {
        currentDirection = direction
        currentFrame = 0
        scheduleNextFrame()
    }

    fun stopWalking() {
        handler.removeCallbacksAndMessages(null)
        currentBitmap = getFrame(Direction.DOWN, 0)  // idle mirando al frente
    }

    private fun scheduleNextFrame() {
        handler.postDelayed({
            currentFrame = (currentFrame + 1) % FRAMES_PER_ROW
            currentBitmap = getFrame(currentDirection, currentFrame)
            scheduleNextFrame()
        }, FRAME_DURATION_MS)
    }

    private fun getFrame(direction: Direction, frame: Int): Bitmap {
        return Bitmap.createBitmap(
            spriteSheet,
            frame * FRAME_WIDTH,
            direction.row * FRAME_HEIGHT,
            FRAME_WIDTH,
            FRAME_HEIGHT
        )
    }
}
```

---

## WalkingBehavior.kt

```kotlin
// Responsabilidades:
// 1. Decidir hacia dónde camina el muñeco
// 2. Caminar por los bordes de la pantalla (como Oneko)
// 3. Parar, sentarse, esperar, y volver a caminar
// 4. Notificar a OverlayService los cambios de posición

// Comportamiento de los bordes:
// - El muñeco solo camina sobre los 4 bordes de la pantalla
// - Al llegar a una esquina, gira 90 grados
// - Aleatoriamente puede parar y mostrar un idle de 2-5 segundos
// - Velocidad: 3px por tick (tick = 16ms → ~60fps)

class WalkingBehavior(
    private val service: OverlayService,
    private val spriteEngine: SpriteEngine,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    private val SPRITE_SIZE = 64
    private val SPEED_PX = 3
    private val WALK_TICK_MS = 16L

    private var x = 0
    private var y = 200
    private var currentEdge = Edge.BOTTOM
    private val handler = Handler(Looper.getMainLooper())

    enum class Edge { TOP, RIGHT, BOTTOM, LEFT }

    fun start() {
        scheduleWalkTick()
    }

    private fun scheduleWalkTick() {
        handler.postDelayed({
            walk()
            scheduleWalkTick()
        }, WALK_TICK_MS)
    }

    private fun walk() {
        when (currentEdge) {
            Edge.BOTTOM -> {
                x += SPEED_PX
                spriteEngine.startWalking(Direction.RIGHT)
                if (x >= screenWidth - SPRITE_SIZE) {
                    x = screenWidth - SPRITE_SIZE
                    currentEdge = Edge.RIGHT
                }
            }
            Edge.RIGHT -> {
                y -= SPEED_PX
                spriteEngine.startWalking(Direction.UP)
                if (y <= 0) {
                    y = 0
                    currentEdge = Edge.TOP
                }
            }
            Edge.TOP -> {
                x -= SPEED_PX
                spriteEngine.startWalking(Direction.LEFT)
                if (x <= 0) {
                    x = 0
                    currentEdge = Edge.LEFT
                }
            }
            Edge.LEFT -> {
                y += SPEED_PX
                spriteEngine.startWalking(Direction.DOWN)
                if (y >= screenHeight - SPRITE_SIZE) {
                    y = screenHeight - SPRITE_SIZE
                    currentEdge = Edge.BOTTOM
                }
            }
        }
        service.updatePosition(x, y)
    }
}
```

---

## OverlayView.kt

```kotlin
// Responsabilidades:
// 1. Dibujar el frame actual del SpriteEngine
// 2. Escalar el sprite de 64px a un tamaño visible (~96dp)
// 3. Manejar el touch (tap → mostrar BubbleView)

class OverlayView(context: Context) : View(context) {

    private val spriteEngine = SpriteEngine(context)
    private val paint = Paint().apply { isFilterBitmap = false }  // sin blur — pixel art nítido

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(
            spriteEngine.currentBitmap,
            null,
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            paint
        )
        invalidate()  // redibuja en cada frame
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            showBubble()
            return true
        }
        return false
    }

    fun startAnimation() {
        spriteEngine.startWalking(Direction.RIGHT)
    }

    private fun showBubble() {
        // Fase 1: texto hardcodeado
        // Fase 2: vendrá del backend
        BubbleView.show(context, "¡Hola! Soy Antonio 👋")
    }
}
```

---

## BubbleView.kt

```kotlin
// En Fase 1: popup simple con texto hardcodeado
// En Fase 2: recibirá texto del backend

// Implementar como WindowManager overlay también
// Aparece encima del muñeco, desaparece en 3 segundos

// Diseño visual:
// - Fondo blanco con bordes redondeados (8dp)
// - Texto negro, fuente monospace (para feeling pixel)
// - Pequeño triángulo apuntando al muñeco
// - Fade in 200ms, visible 3s, fade out 300ms
```

---

## MainActivity.kt

```kotlin
// Responsabilidades MUY limitadas en Fase 1:
// 1. Mostrar pantalla de bienvenida simple
// 2. Pedir permiso SYSTEM_ALERT_WINDOW
// 3. Si tiene permiso → lanzar OverlayService
// 4. Si no → llevar a Settings para habilitarlo

// UI de MainActivity (solo para Fase 1):
// - Fondo negro
// - Texto: "Tu Antonio digital está listo"
// - Botón: "Activar"
// - Si no hay permiso: dialog explicativo
```

---

## AndroidManifest.xml — estructura clave

```xml
<manifest>
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <uses-permission android:name="android.permission.INTERNET"/>

    <application>
        <activity android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <service
            android:name=".OverlayService"
            android:foregroundServiceType="specialUse"
            android:exported="false"/>
    </application>
</manifest>
```

---

## Sprite sheet — instrucciones

1. Ir a: `sanderfrenken.github.io/Universal-LPC-Spritesheet-Character-Generator`
2. Configurar el personaje con el look de Antonio
3. Exportar como PNG
4. Renombrar a `antonio_walk.png`
5. Colocar en `android-app/app/src/main/res/drawable/antonio_walk.png`
6. Verificar que el archivo sea exactamente **576px de ancho** (9 frames × 64px)

---

## Criterios de aceptación — Fase 1 completa cuando:

- [ ] El muñeco aparece en pantalla al abrir la app y dar permiso
- [ ] El muñeco camina continuamente por los 4 bordes de la pantalla
- [ ] El muñeco gira correctamente en cada esquina
- [ ] Al tocar el muñeco aparece un globo de texto
- [ ] El globo desaparece solo después de 3 segundos
- [ ] El muñeco sigue visible sobre cualquier otra app (YouTube, WhatsApp, etc.)
- [ ] El servicio sobrevive si el usuario bloquea la pantalla y la vuelve a encender
- [ ] No hay crashes en Samsung, Pixel, y Xiaomi (los tres más comunes en LATAM)

---

## Notas para Claude Code

- Empezar siempre por `OverlayService.kt` — es el núcleo
- El `SpriteEngine` debe ser completamente independiente del Service
- No optimizar prematuramente — claridad > performance en Fase 1
- Si algo no está en este spec, preguntar antes de inventar
- El tamaño del muñeco en pantalla debe ser ~96dp (no 64px raw — se ve muy pequeño)
- Probar en emulador con API 26 mínimo
