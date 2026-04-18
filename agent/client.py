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


def analyze_image(
    image_source: str,
    system_prompt: str,
    model: str = DEFAULT_MODEL,
    api_key: str | None = None,
) -> str:
    """
    Send an image to OpenRouter and return the diagnostic analysis.

    Args:
        image_source: Local file path or public URL to the image.
        system_prompt:  Fully rendered system prompt (with terminology injected).
        model:          OpenRouter model identifier.
        api_key:        OpenRouter API key. Falls back to OPENROUTER_API_KEY env var.

    Returns:
        Raw text response from the model.
    """
    key = api_key or os.environ.get("OPENROUTER_API_KEY")
    if not key:
        raise ValueError("OPENROUTER_API_KEY is not set.")

    # Build the image content block
    if image_source.startswith("http://") or image_source.startswith("https://"):
        image_block = {"type": "image_url", "image_url": {"url": image_source}}
    else:
        data, media_type = _encode_image(image_source)
        image_block = {
            "type": "image_url",
            "image_url": {"url": f"data:{media_type};base64,{data}"},
        }

    payload = {
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

    headers = {
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
    }

    with httpx.Client(timeout=60) as client:
        response = client.post(OPENROUTER_API_URL, json=payload, headers=headers)
        response.raise_for_status()

    return response.json()["choices"][0]["message"]["content"]
