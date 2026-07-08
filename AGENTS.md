# AGENTS.md — Digital Twin Companion

## Qué es este proyecto

Una app Android que muestra un avatar pixel art de **Antonio Galdo** como overlay flotante
en el teléfono de otra persona. El muñeco camina por los bordes de la pantalla, reacciona
a toques, y muestra contexto real de la vida de Antonio — qué está haciendo, cómo está,
qué diría.

Inspirado en Oneko (gato que sigue el mouse en Chrome) y Pixel Agents (personajes RPG
pixel art conectados a agentes AI).

---

## Arquitectura general

```
android-app/        → App del muñeco (instala la persona que quiere tener el twin)
control-app/        → App de Antonio para actualizar su estado/contexto
backend/            → FastAPI + Supabase — contexto y AI brain
specs/              → Especificaciones por módulo
```

---

## Stack tecnológico

| Capa | Tecnología | Notas |
|---|---|---|
| Android overlay | Kotlin + WindowManager | TYPE_APPLICATION_OVERLAY |
| Animación | Sprite sheets LPC (64x64px) | Frame-by-frame manual, sin librerías |
| Backend | FastAPI (Python) | Desplegado en Railway o Render |
| Base de datos | Supabase | Contexto de Antonio en tiempo real |
| AI | Codex API (Codex-sonnet-4-20250514) | Brain del muñeco |
| Push | Firebase FCM | Para despertar el overlay proactivamente |
| Voz (fase 2) | ElevenLabs | Clonar voz de Antonio |

---

## Reglas de desarrollo

### Android
- **Minimum SDK: 26** (Android 8.0) — requerido para TYPE_APPLICATION_OVERLAY estable
- **Target SDK: 35**
- El overlay corre en un `ForegroundService` — NUNCA en Activity
- Usar `WindowManager.LayoutParams` para posicionar el muñeco
- La animación usa un `Handler` con `postDelayed` — NO usar animators de Android
- Todo el estado del muñeco vive en el Service, no en Activities
- Kotlin coroutines para llamadas al backend (no threads manuales)

### Sprite sheets
- Formato LPC estándar: cada frame es **64x64px**
- Fila 0: caminar hacia abajo (sur)
- Fila 1: caminar hacia izquierda (oeste)
- Fila 2: caminar hacia derecha (este)
- Fila 3: caminar hacia arriba (norte)
- Fila 4: idle / saludar (si existe)
- Cada fila tiene **9 frames** (columnas 0–8)
- El sprite sheet completo: 576px ancho × variable alto

### Backend
- Endpoints RESTful, siempre responden JSON
- El contexto de Antonio se guarda en Supabase tabla `context`
- Codex API se llama SOLO cuando el usuario pregunta algo — no en cada frame
- System prompt de Antonio vive en `backend/prompts/antonio.txt` — NO hardcodeado

### Naming
- Clases Android: PascalCase (`OverlayService`, `SpriteEngine`)
- Funciones Kotlin: camelCase (`startWalking()`, `showBubble()`)
- Endpoints backend: snake_case (`/get_context`, `/ask_antonio`)
- Archivos de sprite: `antonio_walk.png`, `antonio_idle.png`

---

## Fases del proyecto

```
Fase 1 — Overlay MVP         El muñeco aparece y camina. Sin AI.
Fase 2 — Contexto manual     Antonio actualiza su estado. Muñeco lo muestra.
Fase 3 — AI brain            Codex responde preguntas sobre Antonio.
Fase 4 — Fuentes reales      Calendario, ubicación, redes.
Fase 5 — Voz                 ElevenLabs clone de voz de Antonio.
```

**Nunca mezclar fases en un mismo PR.** Cada fase debe funcionar de forma independiente.

---

## Permisos Android requeridos

```xml
<!-- Overlay -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>

<!-- Foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>

<!-- Internet (para backend) -->
<uses-permission android:name="android.permission.INTERNET"/>

<!-- Notificaciones (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

---

## Lo que Codex NO debe hacer

- No usar Jetpack Compose para el overlay — solo Views clásicas
- No usar librerías de animación (Lottie, etc.) — el sprite engine es manual
- No hardcodear el contexto de Antonio en el código
- No crear Activities para el overlay — todo en Service
- No usar SharedPreferences para el contexto — usar Supabase
- No generar tests unitarios en Fase 1 — MVP primero, tests después

---

## Contexto del dueño del twin

**Antonio Galdo** — Boliviano en Colombia. Head of Product. Indie hacker en construcción.
Su twin debe reflejar su personalidad: directo, activo, honesto, con humor natural.
El system prompt del AI brain está en `backend/prompts/antonio.txt`.
