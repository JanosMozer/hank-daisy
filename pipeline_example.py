#!/usr/bin/env python3
"""
Example: consume the glasses stream in an AI pipeline.
Run stream_glasses.py first, then run this.
"""

import io
import time
import requests
from PIL import Image

STREAM_URL = "http://localhost:5050/frame"


def get_frame() -> Image.Image:
    resp = requests.get(STREAM_URL, timeout=2)
    return Image.open(io.BytesIO(resp.content)).convert("RGB")


def run_pipeline():
    print("Reading frames from glasses stream. Ctrl+C to stop.")
    while True:
        img = get_frame()
        # ── Drop your AI model call here ──────────────────────────
        # e.g. with Claude:
        #   import anthropic, base64
        #   client = anthropic.Anthropic()
        #   buf = io.BytesIO(); img.save(buf, "JPEG"); b64 = base64.b64encode(buf.getvalue()).decode()
        #   result = client.messages.create(
        #       model="claude-opus-4-7",
        #       max_tokens=256,
        #       messages=[{"role": "user", "content": [
        #           {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": b64}},
        #           {"type": "text", "text": "What do you see?"}
        #       ]}]
        #   )
        #   print(result.content[0].text)
        # ─────────────────────────────────────────────────────────
        print(f"Frame: {img.size}  — insert your model call here")
        time.sleep(1)


if __name__ == "__main__":
    run_pipeline()
