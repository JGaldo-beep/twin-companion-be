# Backend — Twin Companion (Fase 2)

FastAPI = el cerebro. Antonio actualiza su estado (`POST /context`); FastAPI
valida, arma `display_text` y lo escribe en **Firestore**. La app del muñeco
escucha Firestore en tiempo real (no usa este backend para leer).

```
Control-app ──POST /context──► FastAPI ──(firebase-admin)──► Firestore context/antonio
                                                                   │ realtime listener
                                                                   ▼
                                                            App del muñeco
```

## 1. Proyecto Firebase (plan gratis Spark, sin tarjeta)

1. https://console.firebase.google.com → **Add project**
2. Activar **Firestore Database** (modo producción)
3. **NO** hace falta activar Cloud Functions ni Blaze

## 2. Credencial del backend (serviceAccount.json)

1. Project Settings → **Service accounts** → **Generate new private key**
2. Guardar el archivo como `backend/serviceAccount.json`
   (ya está en `.gitignore`, nunca se commitea)

## 3. Configurar y correr

```bash
cp .env.example .env          # apunta a ./serviceAccount.json
python -m venv .venv
.venv\Scripts\activate        # Windows
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

Probar:

```bash
curl -X POST http://localhost:8000/context ^
  -H "Content-Type: application/json" ^
  -d "{\"status\":\"working\",\"message\":\"Grabando un video\",\"emoji\":\"🎧\"}"

curl http://localhost:8000/context
```

Docs interactivas: http://localhost:8000/docs
Tras el POST, mirá el documento `context/antonio` en la consola de Firestore.

## 4. Reglas de Firestore

Desplegar las reglas de [`../firebase/firestore.rules`](../firebase/firestore.rules)
(el muñeco puede leer; nadie escribe directo — solo este backend vía Admin SDK).

## 5. Deploy (Railway / Render)

Subir `backend/` y setear la variable de entorno con el contenido del
serviceAccount (o el archivo). Comando de arranque:

```
uvicorn main:app --host 0.0.0.0 --port $PORT
```

## Endpoints

| Método | Ruta       | Quién lo llama              |
|--------|------------|----------------------------|
| POST   | `/context` | Control-app de Antonio     |
| GET    | `/context` | Debug / control-app        |
| GET    | `/health`  | Healthcheck                |

> El muñeco **no** llama a estos endpoints: lee Firestore directo en tiempo real.
