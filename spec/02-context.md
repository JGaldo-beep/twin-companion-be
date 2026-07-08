# Spec 02 — Contexto Manual + Backend (Fase 2)

## Objetivo

Conectar el muñeco con contexto real de Antonio.
Antonio actualiza su estado desde su app de control.
El muñeco lo muestra proactivamente en el teléfono de su amiga.

**Sin AI todavía.** El muñeco muestra exactamente lo que Antonio escribe — nada más.

---

## Prerequisito

Fase 1 completa y funcionando. El muñeco ya camina por los bordes.

---

## Entregables de esta fase

### Backend (FastAPI + Supabase)
- [ ] `backend/main.py` — App FastAPI con los 2 endpoints
- [ ] `backend/prompts/antonio.txt` — System prompt base de Antonio (para Fase 3)
- [ ] Tabla `context` en Supabase
- [ ] Deploy en Railway o Render

### Android app (muñeco)
- [ ] `ContextRepository.kt` — Llama al backend cada 5 minutos
- [ ] `BubbleView.kt` — Actualizado para mostrar contexto real
- [ ] Lógica proactiva — el muñeco aparece solo cuando hay contexto nuevo

### Control app (Antonio)
- [ ] App separada, misma estructura de proyecto
- [ ] `StatusUpdateActivity.kt` — UI para que Antonio actualice su estado
- [ ] `ControlRepository.kt` — Envía el estado al backend

---

## Base de datos — Supabase

### Tabla: `context`

```sql
create table context (
  id uuid default gen_random_uuid() primary key,
  owner_id text not null default 'antonio',  -- para el futuro multi-usuario
  status text not null,                       -- 'working' | 'available' | 'sleeping' | 'traveling'
  message text,                               -- texto libre que escribe Antonio
  emoji text,                                 -- emoji que elige Antonio
  available_at text,                          -- ej: "después de las 6pm"
  location_city text,                         -- ej: "Bogotá"
  updated_at timestamptz default now()
);

-- Solo hay UN registro activo por owner_id
-- Siempre se hace UPDATE, nunca INSERT adicional
-- Excepto el primero
```

### Registro inicial (seed)

```sql
insert into context (owner_id, status, message, emoji, available_at, location_city)
values ('antonio', 'available', 'Todo bien por acá', '👋', null, 'Bogotá');
```

---

## Backend — FastAPI

### Estructura

```
backend/
├── main.py
├── models.py
├── database.py
├── prompts/
│   └── antonio.txt     ← system prompt para Fase 3
├── requirements.txt
└── .env                ← SUPABASE_URL, SUPABASE_KEY (nunca commitear)
```

### models.py

```python
from pydantic import BaseModel
from typing import Optional

class ContextUpdate(BaseModel):
    status: str          # 'working' | 'available' | 'sleeping' | 'traveling'
    message: str         # texto libre, max 120 caracteres
    emoji: str           # un solo emoji
    available_at: Optional[str] = None   # "después de las 6pm"
    location_city: Optional[str] = None  # "Bogotá"

class ContextResponse(BaseModel):
    status: str
    message: str
    emoji: str
    available_at: Optional[str]
    location_city: Optional[str]
    updated_at: str
    display_text: str    # texto listo para mostrar en el globo — lo construye el backend
```

### main.py — los 2 endpoints

```python
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from models import ContextUpdate, ContextResponse
from database import get_context, update_context

app = FastAPI()

app.add_middleware(CORSMiddleware, allow_origins=["*"])

# ──────────────────────────────────────────────
# GET /context
# Lo llama la app del muñeco cada 5 minutos
# ──────────────────────────────────────────────
@app.get("/context", response_model=ContextResponse)
async def get_antonio_context():
    context = await get_context("antonio")
    if not context:
        raise HTTPException(status_code=404, detail="No context found")

    return ContextResponse(
        **context,
        display_text=build_display_text(context)
    )

# ──────────────────────────────────────────────
# POST /context
# Lo llama la app de control de Antonio
# ──────────────────────────────────────────────
@app.post("/context", response_model=ContextResponse)
async def set_antonio_context(update: ContextUpdate):
    if len(update.message) > 120:
        raise HTTPException(status_code=400, detail="Mensaje muy largo (máx 120 chars)")

    context = await update_context("antonio", update.dict())
    return ContextResponse(
        **context,
        display_text=build_display_text(context)
    )

# ──────────────────────────────────────────────
# Construye el texto del globo según el status
# ──────────────────────────────────────────────
def build_display_text(context: dict) -> str:
    status = context["status"]
    message = context["message"]
    emoji = context["emoji"]
    available_at = context.get("available_at")
    city = context.get("location_city", "")

    if status == "working":
        base = f"{emoji} Antonio está en modo trabajo"
        if available_at:
            base += f" — libre {available_at}"
    elif status == "sleeping":
        base = f"{emoji} Antonio está durmiendo"
    elif status == "traveling":
        base = f"{emoji} Antonio está viajando"
        if city:
            base += f" — ahora en {city}"
    else:  # available
        base = f"{emoji} {message}"

    return base
```

### requirements.txt

```
fastapi==0.111.0
uvicorn==0.29.0
supabase==2.4.0
python-dotenv==1.0.1
pydantic==2.7.0
```

---

## Android app — cambios sobre Fase 1

### ContextRepository.kt

```kotlin
// Responsabilidades:
// 1. Llamar GET /context cada 5 minutos
// 2. Cachear el último contexto en memoria
// 3. Notificar al OverlayService cuando hay contexto nuevo

data class AntoniContext(
    val status: String,
    val message: String,
    val emoji: String,
    val displayText: String,
    val updatedAt: String
)

class ContextRepository(private val service: OverlayService) {

    private val BASE_URL = "https://tu-backend.railway.app"  // cambiar en deploy
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdatedAt: String = ""

    fun startPolling() {
        fetchContext()
        scheduleNextPoll()
    }

    private fun scheduleNextPoll() {
        handler.postDelayed({
            fetchContext()
            scheduleNextPoll()
        }, 5 * 60 * 1000L)  // cada 5 minutos
    }

    private fun fetchContext() {
        // Llamada en background thread
        Thread {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/context")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@Thread
                val context = parseContext(body)

                // Solo notificar si el contexto cambió
                if (context.updatedAt != lastUpdatedAt) {
                    lastUpdatedAt = context.updatedAt
                    handler.post { service.onContextUpdated(context) }
                }
            } catch (e: Exception) {
                // Silencioso — si falla el network, el muñeco sigue caminando sin texto
            }
        }.start()
    }
}
```

### Cambios en OverlayService.kt

```kotlin
// Agregar en onStartCommand:
private val contextRepository = ContextRepository(this)

override fun onStartCommand(...): Int {
    startForeground(NOTIFICATION_ID, buildNotification())
    setupOverlay()
    contextRepository.startPolling()  // ← NUEVO en Fase 2
    return START_STICKY
}

// Nuevo método que recibe contexto actualizado:
fun onContextUpdated(context: AntonioContext) {
    overlayView.showContextBubble(context.displayText)
    // El muñeco para de caminar 2 segundos, "saluda", y muestra el globo
}
```

### Cambios en BubbleView.kt

```kotlin
// Ya no es texto hardcodeado
// Recibe el displayText del backend

// Comportamiento en Fase 2:
// - Aparece automáticamente cuando hay contexto nuevo
// - Muestra el displayText del backend
// - Tiene un botón pequeño "Preguntar" (prepara Fase 3 — sin funcionalidad aún)
// - Desaparece en 5 segundos si no hay interacción
```

---

## Control app — app de Antonio

### Estructura

```
control-app/
└── app/src/main/
    ├── StatusUpdateActivity.kt   ← pantalla principal
    ├── ControlRepository.kt      ← llama POST /context
    └── res/layout/
        └── activity_status.xml
```

### UI de StatusUpdateActivity

```
┌─────────────────────────────────────┐
│  🟢 ¿Qué estás haciendo, Antonio?  │
│                                     │
│  [ Trabajando 🎧 ]  [ Disponible 👋 ] │
│  [ Durmiendo 😴 ]  [ Viajando ✈️ ]  │
│                                     │
│  Mensaje (opcional):                │
│  ┌─────────────────────────────┐   │
│  │ Ej: "Grabando un video"     │   │
│  └─────────────────────────────┘   │
│                                     │
│  Disponible a partir de: ________  │
│                                     │
│         [ Actualizar ]              │
│                                     │
│  Último update: hace 23 minutos    │
└─────────────────────────────────────┘
```

### StatusUpdateActivity.kt

```kotlin
// Comportamiento:
// 1. Al abrir muestra el estado actual (GET /context)
// 2. El usuario elige status con botones grandes (no dropdown)
// 3. Puede agregar mensaje libre (EditText, max 120 chars, contador visible)
// 4. Botón "Actualizar" → POST /context → toast de confirmación
// 5. Muestra timestamp del último update

// Estados disponibles (hardcodeados en Fase 2):
val estados = listOf(
    Estado("working", "Trabajando", "🎧"),
    Estado("available", "Disponible", "👋"),
    Estado("sleeping", "Durmiendo", "😴"),
    Estado("traveling", "Viajando", "✈️")
)
```

---

## Deploy del backend

### Railway (recomendado — gratis para empezar)

```bash
# 1. Instalar Railway CLI
npm install -g @railway/cli

# 2. Login
railway login

# 3. Desde la carpeta backend/
railway init
railway up

# 4. Variables de entorno en Railway dashboard:
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_KEY=eyJxxx...
```

### .env local (para desarrollo)

```
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_KEY=eyJxxx...
```

---

## Criterios de aceptación — Fase 2 completa cuando:

- [ ] Antonio abre su app, cambia status a "Trabajando 🎧", agrega mensaje "Grabando un video"
- [ ] En máximo 5 minutos, el muñeco en el teléfono de su amiga muestra el globo con ese texto
- [ ] Si Antonio no actualiza nada, el muñeco sigue caminando sin globo (no falla)
- [ ] Si el backend está caído, el muñeco sigue funcionando (fallo silencioso)
- [ ] El backend responde en menos de 500ms en condiciones normales
- [ ] El contexto sobrevive reinicios del backend (está en Supabase, no en memoria)

---

## Notas para Claude Code

- El backend va en una carpeta separada `backend/` — no dentro del proyecto Android
- `build_display_text()` vive en el backend, NO en el Android — el cliente solo muestra lo que recibe
- El polling de 5 minutos es intencional — no hacer WebSockets en Fase 2
- La control app es una app Android separada — mismo repo, diferente módulo
- No implementar autenticación en Fase 2 — el endpoint es público (la URL es el secreto)
- El campo `available_at` es texto libre — no un TimePicker — Antonio escribe "después de las 6"
- `antonio.txt` puede estar vacío en Fase 2 — se usa en Fase 3
