from pydantic import BaseModel, Field
from typing import Optional


class ContextUpdate(BaseModel):
    status: str = Field(..., description="working | available | sleeping | traveling")
    message: str = Field("", max_length=120)
    emoji: str = ""
    available_at: Optional[str] = None   # ej: "despues de las 6pm"
    location_city: Optional[str] = None  # ej: "Bogota"
    calendar: Optional[str] = None       # fuente automatica: ej "En reunion hasta las 16:00"


class DeviceToken(BaseModel):
    token: str


class MemoryItem(BaseModel):
    fact: str = Field(..., max_length=300)


class MemoryList(BaseModel):
    facts: list[str]


class AskRequest(BaseModel):
    question: str = Field(..., max_length=500)


class AskResponse(BaseModel):
    answer: str


class ContextResponse(BaseModel):
    status: str
    message: str
    emoji: str
    available_at: Optional[str] = None
    location_city: Optional[str] = None
    calendar: Optional[str] = None
    updated_at: str
    display_text: str  # texto listo para el globo — lo construye el backend
