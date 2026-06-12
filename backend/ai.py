import os
from typing import Optional

from dotenv import load_dotenv
from anthropic import Anthropic

load_dotenv()

# Modelo configurable. Sonnet 4.6 es buen balance precio/calidad para Q&A corto.
# Cambialo a "claude-opus-4-8" (mas inteligente) o "claude-haiku-4-5" (mas barato/rapido).
MODEL = os.environ.get("CLAUDE_MODEL", "claude-sonnet-4-6")

_PROMPT_PATH = os.path.join(os.path.dirname(__file__), "prompts", "antonio.txt")

# Lee ANTHROPIC_API_KEY del entorno automaticamente.
_client = Anthropic()


def _load_persona() -> str:
    try:
        with open(_PROMPT_PATH, encoding="utf-8") as f:
            return f.read().strip()
    except FileNotFoundError:
        return "Eres el gemelo digital de Antonio Galdo. Responde en primera persona."


_PERSONA = _load_persona()


def _context_block(ctx: Optional[dict]) -> str:
    if not ctx:
        return "No hay contexto reciente."
    lines = []
    if ctx.get("status"):
        lines.append(f"- Estado: {ctx['status']}")
    if ctx.get("message"):
        lines.append(f"- Mensaje: {ctx['message']}")
    if ctx.get("location_city"):
        lines.append(f"- Ciudad: {ctx['location_city']}")
    if ctx.get("calendar"):
        lines.append(f"- Agenda: {ctx['calendar']}")
    if ctx.get("available_at"):
        lines.append(f"- Disponible: {ctx['available_at']}")
    if ctx.get("display_text"):
        lines.append(f"- Resumen: {ctx['display_text']}")
    return "\n".join(lines) if lines else "No hay contexto reciente."


def ask_antonio(question: str, context: Optional[dict], facts: Optional[list] = None) -> str:
    """Llama a Claude como el gemelo de Antonio, usando su memoria + contexto actual."""
    persona_and_memory = _PERSONA
    if facts:
        persona_and_memory += "\n\nCosas que sabes sobre Antonio (su memoria, usala como propia):\n"
        persona_and_memory += "\n".join(f"- {f}" for f in facts)

    # Persona + memoria son estables -> se cachean. El contexto es volatil -> va despues.
    system = [
        {
            "type": "text",
            "text": persona_and_memory,
            "cache_control": {"type": "ephemeral"},
        },
        {
            "type": "text",
            "text": (
                "Contexto actual de Antonio (usalo para responder; "
                "no inventes datos que no esten aqui):\n" + _context_block(context)
            ),
        },
    ]

    response = _client.messages.create(
        model=MODEL,
        max_tokens=300,
        system=system,
        messages=[{"role": "user", "content": question}],
    )

    return "".join(b.text for b in response.content if b.type == "text").strip()


def proactive_line(change: str, context: Optional[dict], facts: Optional[list] = None) -> str:
    """Redacta un aviso corto que Antonio diria por su cuenta sobre un cambio."""
    persona = _PERSONA
    if facts:
        persona += "\n\nCosas que sabes sobre Antonio:\n" + "\n".join(f"- {f}" for f in facts)
    persona += (
        "\n\nAhora vas a escribir UN aviso muy corto (maximo 12 palabras), en primera "
        "persona, que Antonio diria por su cuenta sobre lo que acaba de pasar. Natural, "
        "con su tono, sin comillas, sin emojis de mas (1 como mucho)."
    )

    user = f"Acaba de pasar esto: {change}.\nContexto actual:\n{_context_block(context)}"

    response = _client.messages.create(
        model=MODEL,
        max_tokens=60,
        system=[{"type": "text", "text": persona, "cache_control": {"type": "ephemeral"}}],
        messages=[{"role": "user", "content": user}],
    )
    return "".join(b.text for b in response.content if b.type == "text").strip()
