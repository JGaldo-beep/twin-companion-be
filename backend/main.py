import asyncio
import time
import os
from datetime import datetime, timezone, timedelta

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from firebase_admin import messaging

from models import (
    ContextUpdate, ContextResponse, AskRequest, AskResponse,
    MemoryItem, MemoryList, DeviceToken,
)
from database import (
    get_context, set_context, get_memory, add_memory, remove_memory,
    add_token, get_tokens, get_last_notified, set_last_notified,
)
from ai import ask_antonio, proactive_line

app = FastAPI(title="Twin Companion — Context API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

OWNER_ID = "antonio"


@app.get("/health")
async def health():
    return {"ok": True}


# ──────────────────────────────────────────────
# GET /context  — lectura directa (debug / control-app).
# El muneco NO usa esto: escucha Firestore en tiempo real.
# ──────────────────────────────────────────────
@app.get("/context", response_model=ContextResponse)
async def get_antonio_context():
    context = await get_context(OWNER_ID)
    if not context:
        raise HTTPException(status_code=404, detail="No context found")
    return _to_response(context)


# ──────────────────────────────────────────────
# POST /context  — lo llama la control-app de Antonio.
# Valida, arma display_text y lo escribe en Firestore.
# ──────────────────────────────────────────────
@app.post("/context", response_model=ContextResponse)
async def set_antonio_context(update: ContextUpdate):
    if len(update.message) > 120:
        raise HTTPException(status_code=400, detail="Mensaje muy largo (max 120 chars)")

    old = await get_context(OWNER_ID)
    fields = update.model_dump()
    display_text = build_display_text(fields)
    context = await set_context(OWNER_ID, fields, display_text)
    await maybe_notify(old, context)
    return _to_response(context)


# ──────────────────────────────────────────────
# Proactividad — el muneco avisa solo cuando cambia algo relevante.
# ──────────────────────────────────────────────
PROACTIVE_MIN_MINUTES = int(os.environ.get("PROACTIVE_MIN_MINUTES", "10"))
QUIET_START = 23  # 11pm
QUIET_END = 8     # 8am


@app.post("/register_device")
async def register_device(d: DeviceToken):
    if d.token.strip():
        await add_token(d.token.strip())
    return {"ok": True}


def _detect_change(old: dict, new: dict):
    if not old:
        return None  # primera vez: no avisamos
    reasons = []
    if old.get("status") != new.get("status"):
        reasons.append(f"cambio de estado de '{old.get('status')}' a '{new.get('status')}'")
    if (new.get("message") or "") != (old.get("message") or "") and new.get("message"):
        reasons.append(f"ahora esta: {new.get('message')}")
    if (new.get("location_city") or "") != (old.get("location_city") or "") and new.get("location_city"):
        reasons.append(f"se movio a: {new.get('location_city')}")
    return "; ".join(reasons) if reasons else None


def _in_quiet_hours() -> bool:
    # Colombia = UTC-5 (sin horario de verano)
    hour = (datetime.now(timezone.utc) - timedelta(hours=5)).hour
    return hour >= QUIET_START or hour < QUIET_END


def _send_push(tokens: list, text: str):
    if not tokens:
        return
    msg = messaging.MulticastMessage(
        tokens=tokens,
        data={"type": "proactive", "text": text},
        android=messaging.AndroidConfig(priority="high"),
    )
    messaging.send_each_for_multicast(msg)


async def maybe_notify(old, new):
    change = _detect_change(old, new)
    if not change or _in_quiet_hours():
        return
    now = int(time.time() * 1000)
    last = await get_last_notified()
    if now - last < PROACTIVE_MIN_MINUTES * 60 * 1000:
        return

    facts = await get_memory(OWNER_ID)
    line = await asyncio.to_thread(proactive_line, change, new, facts)
    tokens = await get_tokens()
    await asyncio.to_thread(_send_push, tokens, line)
    await set_last_notified(now)


# ──────────────────────────────────────────────
# POST /ask_antonio  — el muneco pregunta; Claude responde como Antonio.
# ──────────────────────────────────────────────
@app.post("/ask_antonio", response_model=AskResponse)
async def ask_antonio_endpoint(req: AskRequest):
    question = req.question.strip()
    if not question:
        raise HTTPException(status_code=400, detail="Pregunta vacia")

    context = await get_context(OWNER_ID)
    facts = await get_memory(OWNER_ID)
    # ask_antonio es sincrono (SDK de Anthropic) -> lo corremos en thread.
    answer = await asyncio.to_thread(ask_antonio, question, context, facts)
    return AskResponse(answer=answer)


# ──────────────────────────────────────────────
# Memoria del clon — hechos sobre Antonio que el AI usa siempre.
# ──────────────────────────────────────────────
@app.get("/memory", response_model=MemoryList)
async def get_memory_endpoint():
    return MemoryList(facts=await get_memory(OWNER_ID))


@app.post("/memory", response_model=MemoryList)
async def add_memory_endpoint(item: MemoryItem):
    fact = item.fact.strip()
    if not fact:
        raise HTTPException(status_code=400, detail="Hecho vacio")
    return MemoryList(facts=await add_memory(OWNER_ID, fact))


@app.post("/memory/remove", response_model=MemoryList)
async def remove_memory_endpoint(item: MemoryItem):
    return MemoryList(facts=await remove_memory(OWNER_ID, item.fact))


# ──────────────────────────────────────────────
# Helpers
# ──────────────────────────────────────────────
def _to_response(context: dict) -> ContextResponse:
    return ContextResponse(
        status=context.get("status", "available"),
        message=context.get("message") or "",
        emoji=context.get("emoji") or "",
        available_at=context.get("available_at"),
        location_city=context.get("location_city"),
        calendar=context.get("calendar"),
        updated_at=str(context.get("updated_at_millis", "")),
        display_text=context.get("display_text") or build_display_text(context),
    )


def build_display_text(context: dict) -> str:
    status = context.get("status", "available")
    message = context.get("message") or ""
    emoji = context.get("emoji") or ""
    available_at = context.get("available_at")
    city = context.get("location_city") or ""

    if status == "working":
        base = f"{emoji} Antonio esta en modo trabajo"
        if available_at:
            base += f" — libre {available_at}"
    elif status == "sleeping":
        base = f"{emoji} Antonio esta durmiendo"
    elif status == "traveling":
        base = f"{emoji} Antonio esta viajando"
        if city:
            base += f" — ahora en {city}"
    else:  # available
        base = f"{emoji} {message}".strip()

    return base.strip()
