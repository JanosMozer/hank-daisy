import base64
import json
import os
from pathlib import Path
from typing import AsyncGenerator

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


async def stream_completion(
    messages: list[dict],
    system_prompt: str,
    model: str = DEFAULT_MODEL,
    api_key: str | None = None,
) -> AsyncGenerator[str, None]:
    """Stream response chunks from OpenRouter using SSE."""
    key = api_key or os.environ.get("OPENROUTER_API_KEY")
    if not key:
        raise ValueError("OPENROUTER_API_KEY is not set.")

    payload = {
        "model": model,
        "messages": [{"role": "system", "content": system_prompt}] + messages,
        "stream": True,
    }

    headers = {
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
    }

    async with httpx.AsyncClient(timeout=120) as client:
        async with client.stream("POST", OPENROUTER_API_URL, json=payload, headers=headers) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if line.startswith("data: "):
                    data = line[6:].strip()
                    if data == "[DONE]":
                        break
                    try:
                        chunk = json.loads(data)
                        delta = chunk.get("choices", [{}])[0].get("delta", {})
                        content = delta.get("content", "")
                        if content:
                            yield content
                    except json.JSONDecodeError:
                        pass
