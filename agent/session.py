"""
Stateful diagnostic session.

Terminology is sent once in the system prompt. Each subsequent frame is appended
to the conversation history so the model can confirm, correct, or refine its prior
diagnosis as new visual evidence arrives.
"""

import os
from typing import AsyncGenerator

import httpx

from agent.client import DEFAULT_MODEL, OPENROUTER_API_URL, stream_completion
from agent.prompts import (
    INITIAL_USER_PROMPT,
    UPDATE_USER_PROMPT,
    build_system_prompt,
    build_conversation_prompt,
)


class DiagnosticSession:
    """
    One session = one continuous stream of frames analyzed in order.
    The model sees its own prior answers and updates them with each new frame.
    """

    def __init__(self, model: str = DEFAULT_MODEL, api_key: str | None = None):
        self.model = model
        self._api_key = api_key or os.environ.get("OPENROUTER_API_KEY")
        if not self._api_key:
            raise ValueError("OPENROUTER_API_KEY is not set.")
        self._system_prompt = build_system_prompt()  # terminology injected once
        self._messages: list[dict] = []              # full conversation history

    def analyze(self, b64_frame: str, media_type: str = "image/jpeg") -> str:
        """
        Submit a new frame. Returns the model's updated diagnosis.
        On the first call the model performs a fresh analysis.
        On every subsequent call it reviews its prior answer and revises it.
        """
        image_block = {
            "type": "image_url",
            "image_url": {"url": f"data:{media_type};base64,{b64_frame}"},
        }
        user_text = INITIAL_USER_PROMPT if not self._messages else UPDATE_USER_PROMPT

        self._messages.append({
            "role": "user",
            "content": [image_block, {"type": "text", "text": user_text}],
        })

        payload = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": self._system_prompt},
                *self._messages,
            ],
        }
        headers = {
            "Authorization": f"Bearer {self._api_key}",
            "Content-Type": "application/json",
        }

        with httpx.Client(timeout=60) as client:
            response = client.post(OPENROUTER_API_URL, json=payload, headers=headers)
            response.raise_for_status()

        result = response.json()["choices"][0]["message"]["content"]
        self._messages.append({"role": "assistant", "content": result})
        return result

    # ------------------------------------------------------------------
    @property
    def frame_count(self) -> int:
        return sum(1 for m in self._messages if m["role"] == "user")

    @property
    def latest_diagnosis(self) -> str | None:
        for m in reversed(self._messages):
            if m["role"] == "assistant":
                return m["content"]
        return None


class ConversationSession:
    """
    Real-time conversation session for voice Q&A with Hank.
    Supports text-only queries or text + optional image.
    Streams responses for low-latency TTS on glasses.
    """

    def __init__(self, model: str = DEFAULT_MODEL, api_key: str | None = None):
        self.model = model
        self._api_key = api_key or os.environ.get("OPENROUTER_API_KEY")
        if not self._api_key:
            raise ValueError("OPENROUTER_API_KEY is not set.")
        self._system_prompt = build_conversation_prompt()
        self._messages: list[dict] = []

    async def query(
        self,
        text: str,
        b64_frame: str | None = None,
        media_type: str = "image/jpeg",
    ) -> AsyncGenerator[str, None]:
        """
        Process a text query with optional image frame.
        Streams response chunks for real-time TTS.
        Maintains conversation history (trimmed to last 20 turns).
        """
        # Build user message
        if b64_frame:
            user_content = [
                {
                    "type": "image_url",
                    "image_url": {"url": f"data:{media_type};base64,{b64_frame}"},
                },
                {"type": "text", "text": text},
            ]
        else:
            user_content = text

        self._messages.append({"role": "user", "content": user_content})

        # Stream response
        full_response = ""
        async for chunk in stream_completion(
            self._messages,
            self._system_prompt,
            self.model,
            self._api_key,
        ):
            full_response += chunk
            yield chunk

        # Store response in history
        self._messages.append({"role": "assistant", "content": full_response})

        # Trim history to last 20 turns (40 messages)
        if len(self._messages) > 40:
            self._messages = self._messages[-40:]
