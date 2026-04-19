import base64
import os
from pathlib import Path

import httpx

OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"
DEFAULT_MODEL = "google/gemini-2.5-pro-preview"


def _encode_image(image_path: str) -> tuple[str, str]:
    """Return (base64_data, media_type) for a local image file."""
    path = Path(image_path)
    ext = path.suffix.lower()
    media_types = {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".webp": "image/webp",
        ".gif": "image/gif",
    }
    media_type = media_types.get(ext, "image/jpeg")
    data = base64.standard_b64encode(path.read_bytes()).decode("utf-8")
    return data, media_type


def _post(payload: dict, api_key: str) -> str:
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    with httpx.Client(timeout=60) as client:
        response = client.post(OPENROUTER_API_URL, json=payload, headers=headers)
        response.raise_for_status()
    return response.json()["choices"][0]["message"]["content"]


def _build_payload(image_block: dict, system_prompt: str, model: str) -> dict:
    return {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {
                "role": "user",
                "content": [
                    image_block,
                    {"type": "text", "text": "Perform a full diagnostic analysis."},
                ],
            },
        ],
    }


def analyze_image(
    image_source: str,
    system_prompt: str,
    model: str = DEFAULT_MODEL,
    api_key: str | None = None,
) -> str:
    """Send an image file or URL to OpenRouter and return the diagnostic analysis."""
    key = api_key or os.environ.get("OPENROUTER_API_KEY")
    if not key:
        raise ValueError("OPENROUTER_API_KEY is not set.")

    if image_source.startswith("http://") or image_source.startswith("https://"):
        image_block = {"type": "image_url", "image_url": {"url": image_source}}
    else:
        data, media_type = _encode_image(image_source)
        image_block = {"type": "image_url", "image_url": {"url": f"data:{media_type};base64,{data}"}}

    return _post(_build_payload(image_block, system_prompt, model), key)


def analyze_frame(
    b64_data: str,
    media_type: str,
    system_prompt: str,
    model: str = DEFAULT_MODEL,
    api_key: str | None = None,
) -> str:
    """Send a base64-encoded raw frame to OpenRouter and return the diagnostic analysis."""
    key = api_key or os.environ.get("OPENROUTER_API_KEY")
    if not key:
        raise ValueError("OPENROUTER_API_KEY is not set.")

    image_block = {"type": "image_url", "image_url": {"url": f"data:{media_type};base64,{b64_data}"}}
    return _post(_build_payload(image_block, system_prompt, model), key)
