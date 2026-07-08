# Firebase — Twin Companion (Fase 2)

Acá Firebase se usa **solo como base de datos en tiempo real** (Firestore).
El cerebro es el FastAPI de [`../backend`](../backend). Sin Cloud Functions,
sin plan Blaze: **plan Spark gratis, sin tarjeta.**

```
FastAPI ──(firebase-admin)──► Firestore context/antonio ──realtime──► App del muñeco
```

## Qué hay que hacer

1. Crear proyecto en https://console.firebase.google.com y activar **Firestore**
2. **Para el backend:** Project Settings → Service accounts → Generate new
   private key → guardar como `backend/serviceAccount.json`
3. **Para la app del muñeco:** Add app → Android → package
   `com.example.twincompanion` → descargar `google-services.json` →
   ponerlo en `app/google-services.json`
4. Desplegar las reglas:
   ```bash
   npm install -g firebase-tools
   firebase login
   cd firebase
   firebase use --add
   firebase deploy --only firestore:rules
   ```

## Reglas ([firestore.rules](firestore.rules))

- El muñeco (app de la amiga) puede **leer** `context/antonio`.
- Nadie escribe directo: solo el backend FastAPI vía Admin SDK (se salta las reglas).

## Documento `context/antonio`

| Campo              | Tipo      | Ejemplo                 |
|--------------------|-----------|-------------------------|
| status             | string    | working / available / sleeping / traveling |
| message            | string    | "Grabando un video"     |
| emoji              | string    | "🎧"                    |
| available_at       | string?   | "despues de las 6"      |
| location_city      | string?   | "Bogota"                |
| display_text       | string    | (lo arma el FastAPI)    |
| updated_at         | timestamp | server time             |
| updated_at_millis  | number    | para detectar cambios   |
