import os
import time
import asyncio
from typing import Optional

import json

from dotenv import load_dotenv
import firebase_admin
from firebase_admin import credentials, firestore

load_dotenv()

if not firebase_admin._apps:
    _sa_json = os.environ.get("FIREBASE_SERVICE_ACCOUNT")
    _sa_path = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
    if _sa_json:
        # Railway/Render: pega el contenido del serviceAccount.json como variable de entorno
        firebase_admin.initialize_app(credentials.Certificate(json.loads(_sa_json)))
    elif _sa_path:
        firebase_admin.initialize_app(credentials.Certificate(_sa_path))
    else:
        # Application Default Credentials (Cloud Run / gcloud auth)
        firebase_admin.initialize_app()

_db = firestore.client()
COLLECTION = "context"
MEMORY_COLLECTION = "memory"


def _meta_ref():
    return _db.collection("meta").document("proactive")


async def add_token(token: str) -> list:
    def _w():
        ref = _meta_ref()
        snap = ref.get()
        tokens = (snap.to_dict() or {}).get("tokens", []) if snap.exists else []
        if token not in tokens:
            tokens = tokens + [token]
        ref.set({"tokens": tokens}, merge=True)
        return tokens

    return await asyncio.to_thread(_w)


async def get_tokens() -> list:
    def _q():
        snap = _meta_ref().get()
        return (snap.to_dict() or {}).get("tokens", []) if snap.exists else []

    return await asyncio.to_thread(_q)


async def get_last_notified() -> int:
    def _q():
        snap = _meta_ref().get()
        return int((snap.to_dict() or {}).get("last_notified_millis", 0)) if snap.exists else 0

    return await asyncio.to_thread(_q)


async def set_last_notified(millis: int) -> None:
    def _w():
        _meta_ref().set({"last_notified_millis": millis}, merge=True)

    await asyncio.to_thread(_w)


async def get_memory(owner_id: str) -> list:
    def _q():
        snap = _db.collection(MEMORY_COLLECTION).document(owner_id).get()
        data = snap.to_dict() if snap.exists else None
        return (data or {}).get("facts", []) or []

    return await asyncio.to_thread(_q)


async def add_memory(owner_id: str, fact: str) -> list:
    def _w():
        ref = _db.collection(MEMORY_COLLECTION).document(owner_id)
        snap = ref.get()
        facts = (snap.to_dict() or {}).get("facts", []) if snap.exists else []
        if fact not in facts:
            facts = facts + [fact]
        ref.set({"facts": facts})
        return facts

    return await asyncio.to_thread(_w)


async def remove_memory(owner_id: str, fact: str) -> list:
    def _w():
        ref = _db.collection(MEMORY_COLLECTION).document(owner_id)
        snap = ref.get()
        facts = (snap.to_dict() or {}).get("facts", []) if snap.exists else []
        facts = [f for f in facts if f != fact]
        ref.set({"facts": facts})
        return facts

    return await asyncio.to_thread(_w)


async def get_context(owner_id: str) -> Optional[dict]:
    def _query():
        snap = _db.collection(COLLECTION).document(owner_id).get()
        return snap.to_dict() if snap.exists else None

    return await asyncio.to_thread(_query)


async def set_context(owner_id: str, fields: dict, display_text: str) -> dict:
    """Escribe el unico documento context/{owner_id}.

    Guarda display_text ya construido para que el muneco lo lea directo
    desde Firestore sin recalcular nada.
    """
    def _write():
        doc = {
            **fields,
            "owner_id": owner_id,
            "display_text": display_text,
            "updated_at": firestore.SERVER_TIMESTAMP,
            "updated_at_millis": int(time.time() * 1000),
        }
        ref = _db.collection(COLLECTION).document(owner_id)
        ref.set(doc)
        return ref.get().to_dict()

    return await asyncio.to_thread(_write)
