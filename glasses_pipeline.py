#!/usr/bin/env python3
"""
Smart Glasses AI Pipeline
=========================
Receives frames (from stream_glasses.py or any source), deduplicates them,
maintains a persistent world state, fetches relevant documentation in parallel,
and sends everything to an AI model via OpenRouter (free tier).

Recommended free model: google/gemini-2.0-flash-exp:free
Fallback:               meta-llama/llama-3.2-11b-vision-instruct:free

Usage:
    export OPENROUTER_API_KEY=sk-or-...   # get free key at openrouter.ai

    # Feed from stream server (stream_glasses.py must be running):
    python3 glasses_pipeline.py --source http://localhost:5050/frame

    # Feed from a folder of images (for testing):
    python3 glasses_pipeline.py --source ./test_frames/

    # Feed a single image:
    python3 glasses_pipeline.py --source image.jpg --once

    # With car documentation context:
    python3 glasses_pipeline.py --source http://localhost:5050/frame --car "BMW 3 Series E46 2003" --task "replacing air filter"

    # Use a different model:
    python3 glasses_pipeline.py --model meta-llama/llama-3.2-11b-vision-instruct:free
"""

import io
import os
import sys
import json
import time
import base64
import hashlib
import argparse
import threading
import traceback
from pathlib import Path
from datetime import datetime, timedelta
from typing import Optional
from queue import Queue, Empty
from dataclasses import dataclass, field, asdict

import imagehash
import requests
from PIL import Image
from openai import OpenAI

OPENROUTER_BASE = "https://openrouter.ai/api/v1"
# Free vision models on OpenRouter (as of 2026-04):
#   google/gemma-4-31b-it:free          — best (262k ctx, image+video) but often rate-limited
#   google/gemma-3-27b-it:free          — strong (131k ctx) but rate-limited at peak
#   nvidia/nemotron-nano-12b-v2-vl:free — reliable, 128k ctx, image+video
DEFAULT_MODEL   = "nvidia/nemotron-nano-12b-v2-vl:free"
FALLBACK_MODEL  = "google/gemma-3-12b-it:free"

# ── Data structures ───────────────────────────────────────────────────────────

@dataclass
class Frame:
    image: Image.Image
    timestamp: datetime
    phash: str
    source: str
    change_score: float = 0.0  # 0=identical, 1=completely different


@dataclass
class WorldState:
    """What the AI knows — persists across frames even when not visible."""
    session_start: str = ""
    task: str = ""
    car: str = ""

    # Structural knowledge
    items_observed: list = field(default_factory=list)
    current_location: str = ""        # e.g. "engine bay, near air filter"

    # Action history (last 20 actions)
    actions_taken: list = field(default_factory=list)

    # Current state — what's true right now (missing screws, open panels, etc.)
    current_state: dict = field(default_factory=dict)

    # Running summary of last few minutes
    recent_summary: str = ""

    last_updated: str = ""

    def to_json(self) -> str:
        return json.dumps(asdict(self), indent=2)

    def update_from_ai(self, patch: dict):
        for key, val in patch.items():
            if hasattr(self, key):
                setattr(self, key, val)
        self.last_updated = datetime.now().isoformat()


# ── Frame deduplication ───────────────────────────────────────────────────────

class FrameDeduplicator:
    """
    Skips frames that are too similar to the last processed one.
    But always processes a frame if enough time has passed (even if scene looks the same)
    so Claude can confirm ongoing state.
    """
    SIMILARITY_THRESHOLD = 8    # pHash hamming distance — lower = stricter dedup
    MAX_SKIP_SECONDS     = 30   # always process after this many seconds even if no change

    def __init__(self):
        self.last_hash: Optional[imagehash.ImageHash] = None
        self.last_processed: Optional[datetime] = None
        self.skip_count = 0

    def compute_hash(self, img: Image.Image) -> imagehash.ImageHash:
        return imagehash.phash(img)

    def should_process(self, img: Image.Image) -> tuple[bool, float]:
        """Returns (should_process, change_score 0-1)."""
        h = self.compute_hash(img)

        # First frame always processes
        if self.last_hash is None:
            self.last_hash = h
            self.last_processed = datetime.now()
            return True, 1.0

        distance = self.last_hash - h
        change_score = min(distance / 64.0, 1.0)  # normalize to 0-1

        # Force process if too much time has elapsed (confirms ongoing state)
        time_since = (datetime.now() - self.last_processed).total_seconds()
        if time_since >= self.MAX_SKIP_SECONDS:
            self.last_hash = h
            self.last_processed = datetime.now()
            return True, change_score

        # Skip if too similar
        if distance < self.SIMILARITY_THRESHOLD:
            self.skip_count += 1
            return False, change_score

        # Meaningful change detected
        self.last_hash = h
        self.last_processed = datetime.now()
        self.skip_count = 0
        return True, change_score


# ── Documentation fetcher (runs in parallel) ─────────────────────────────────

class DocFetcher:
    """
    Fetches relevant documentation for the current task/car.
    Caches results so it doesn't refetch the same thing repeatedly.
    Runs in a background thread so it doesn't block the main vision call.
    """

    def __init__(self, car: str = "", task: str = "", client: "OpenAI" = None, model: str = DEFAULT_MODEL):
        self.car = car
        self.task = task
        self._cache: dict[str, str] = {}
        self._latest_docs: str = ""
        self._lock = threading.Lock()
        self._client = client
        self._model = model

    def fetch_for_context(self, observation: str):
        """Non-blocking — spawns background thread."""
        t = threading.Thread(target=self._fetch, args=(observation,), daemon=True)
        t.start()

    def _fetch(self, observation: str):
        cache_key = hashlib.md5(f"{self.car}{observation}".encode()).hexdigest()[:8]
        if cache_key in self._cache:
            return

        if not self.car:
            return

        try:
            resp = self._client.chat.completions.create(
                model=self._model,
                max_tokens=512,
                messages=[
                    {
                        "role": "system",
                        "content": (
                            f"You are a technical documentation expert for {self.car}. "
                            f"The user is working on: {self.task or 'general maintenance'}. "
                            "Given what they are currently observing, provide the most relevant "
                            "technical notes, torque specs, warnings, or procedure steps. "
                            "Be concise — max 200 words. Focus only on what's directly relevant."
                        )
                    },
                    {
                        "role": "user",
                        "content": f"Currently observing: {observation}\n\nWhat's most relevant from the manual?"
                    }
                ]
            )
            docs = resp.choices[0].message.content
            self._cache[cache_key] = docs
            with self._lock:
                self._latest_docs = docs
            print(f"[docs] Fetched: {docs[:80]}...")
        except Exception as e:
            print(f"[docs] Error: {e}")

    def get_latest(self) -> str:
        with self._lock:
            return self._latest_docs


# ── Claude vision engine ──────────────────────────────────────────────────────

SYSTEM_PROMPT = """\
You are an AI assistant embedded in smart glasses, helping someone work on a task in the real world.

Your job:
1. OBSERVE — describe what you see in the image, especially changes from before
2. TRACK — maintain the world state (what exists, what's missing, what was done)
3. ADVISE — give relevant guidance based on what you see + documentation

Critical rules:
- If the world state says a screw is missing, assume it's still missing even if you can't see it clearly
- Small details in context override what's (not) visible in the current frame
- When someone takes an action, record it as permanent until you see evidence it's reversed
- Be brief and useful — this is heads-up display context, not an essay

Always respond in this JSON format:
{
  "observation": "what I see in this frame (1-2 sentences)",
  "changes": "what changed since last frame (or 'no significant change')",
  "advice": "relevant guidance or warning (empty string if nothing important)",
  "world_state_patch": {
    "current_location": "...",
    "items_observed": [...],
    "actions_taken": [...],
    "current_state": {...},
    "recent_summary": "..."
  }
}
"""


class VisionEngine:
    def __init__(self, world_state: WorldState, doc_fetcher: DocFetcher, client: "OpenAI", model: str):
        self.client = client
        self.world_state = world_state
        self.doc_fetcher = doc_fetcher
        self.conversation: list[dict] = []
        self.model = model

    def _image_to_data_url(self, img: Image.Image) -> str:
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        b64 = base64.standard_b64encode(buf.getvalue()).decode()
        return f"data:image/png;base64,{b64}"

    def process_frame(self, frame: Frame) -> dict:
        docs = self.doc_fetcher.get_latest()

        change_label = (
            "SIGNIFICANT CHANGE DETECTED" if frame.change_score > 0.3
            else "Minor change" if frame.change_score > 0.05
            else "Periodic check (scene similar to before)"
        )

        context_block = (
            f"World state (what you know is true):\n{self.world_state.to_json()}\n\n"
            + (f"Relevant documentation:\n{docs}\n\n" if docs else "")
            + f"Frame info: {change_label} (change score: {frame.change_score:.2f})\n"
            + f"Timestamp: {frame.timestamp.strftime('%H:%M:%S')}"
        )

        # Keep last 4 turns for continuity
        if len(self.conversation) > 8:
            self.conversation = self.conversation[-4:]

        user_content = [
            {"type": "text",      "text": context_block},
            {"type": "image_url", "image_url": {"url": self._image_to_data_url(frame.image)}},
            {"type": "text",      "text": "What do you see? Update the world state if anything changed."},
        ]

        self.conversation.append({"role": "user", "content": user_content})

        resp = self.client.chat.completions.create(
            model=self.model,
            max_tokens=1500,  # reasoning models need extra tokens for thinking
            messages=[{"role": "system", "content": SYSTEM_PROMPT}] + self.conversation,
        )

        msg = resp.choices[0].message
        # Reasoning models (e.g. nemotron) put thinking in .reasoning, answer in .content
        raw = msg.content or getattr(msg, "reasoning", None) or ""
        self.conversation.append({"role": "assistant", "content": raw})

        # Parse JSON — models sometimes wrap it in ```json ... ```
        try:
            if "```" in raw:
                raw = raw.split("```")[1].lstrip("json").strip()
            result = json.loads(raw)
        except json.JSONDecodeError:
            result = {"observation": raw, "changes": "", "advice": "", "world_state_patch": {}}

        if patch := result.get("world_state_patch"):
            self.world_state.update_from_ai(patch)

        if obs := result.get("observation"):
            self.doc_fetcher.fetch_for_context(obs)

        return result


# ── Frame sources ─────────────────────────────────────────────────────────────

def frames_from_http(url: str, fps: float = 1.0):
    """Poll the stream_glasses.py /frame endpoint."""
    interval = 1.0 / fps
    while True:
        try:
            resp = requests.get(url, timeout=5)
            img = Image.open(io.BytesIO(resp.content)).convert("RGB")
            yield img
        except Exception as e:
            print(f"[source] HTTP error: {e}")
        time.sleep(interval)


def frames_from_folder(folder: str, fps: float = 1.0):
    """Read all images from a folder, sorted by name. Good for testing."""
    p = Path(folder)
    files = sorted(p.glob("*.jpg")) + sorted(p.glob("*.jpeg")) + sorted(p.glob("*.png"))
    for f in files:
        yield Image.open(f).convert("RGB")
        time.sleep(1.0 / fps)


def frames_from_file(path: str):
    """Single image — for one-shot testing."""
    yield Image.open(path).convert("RGB")


# ── Main pipeline ─────────────────────────────────────────────────────────────

def run_pipeline(args):
    print(f"\n{'='*60}")
    print("Smart Glasses AI Pipeline")
    print(f"{'='*60}")
    print(f"Source:  {args.source}")
    print(f"Model:   {args.model}")
    print(f"Car:     {args.car or '(not set)'}")
    print(f"Task:    {args.task or '(not set)'}")
    print(f"{'='*60}\n")

    client = OpenAI(
        api_key=os.environ["OPENROUTER_API_KEY"],
        base_url=OPENROUTER_BASE,
    )

    world_state = WorldState(
        session_start=datetime.now().isoformat(),
        car=args.car,
        task=args.task,
    )
    dedup = FrameDeduplicator()
    doc_fetcher = DocFetcher(car=args.car, task=args.task, client=client, model=args.model)
    engine = VisionEngine(world_state, doc_fetcher, client=client, model=args.model)

    # Pick frame source
    source = args.source
    if source.startswith("http"):
        frame_gen = frames_from_http(source, fps=args.fps)
    elif Path(source).is_dir():
        frame_gen = frames_from_folder(source, fps=args.fps)
    else:
        frame_gen = frames_from_file(source)

    frame_num = 0
    for img in frame_gen:
        should_process, change_score = dedup.should_process(img)

        if not should_process:
            print(f"  [skip] frame {frame_num} — too similar (skipped {dedup.skip_count}x)")
            frame_num += 1
            if args.once:
                break
            continue

        frame = Frame(
            image=img,
            timestamp=datetime.now(),
            phash=str(imagehash.phash(img)),
            source=source,
            change_score=change_score,
        )

        print(f"\n[{frame.timestamp:%H:%M:%S}] Frame {frame_num} — change score: {change_score:.2f}")

        try:
            result = engine.process_frame(frame)  # type: ignore

            print(f"  OBSERVATION: {result.get('observation', '')}")
            if result.get('changes') and result['changes'] != 'no significant change':
                print(f"  CHANGES:     {result['changes']}")
            if result.get('advice'):
                print(f"  ADVICE:      {result['advice']}")

            # Show updated world state summary
            print(f"  STATE:       {world_state.recent_summary or world_state.current_state}")

        except Exception as e:
            print(f"  [error] {e}")
            traceback.print_exc()

        frame_num += 1
        if args.once:
            break

    print("\n[pipeline] Done.")
    print("\nFinal world state:")
    print(world_state.to_json())


# ── Entry point ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default="http://localhost:5050/frame",
                        help="Frame source: URL, folder path, or image file")
    parser.add_argument("--fps",  type=float, default=1.0, help="Frames per second to request (default 1)")
    parser.add_argument("--car",  default="", help='e.g. "BMW 3 Series E46 2003"')
    parser.add_argument("--task", default="", help='e.g. "replacing air filter"')
    parser.add_argument("--model", default=DEFAULT_MODEL,
                        help=f"OpenRouter model ID (default: {DEFAULT_MODEL})")
    parser.add_argument("--once", action="store_true", help="Process one frame and exit")
    args = parser.parse_args()

    if not os.environ.get("OPENROUTER_API_KEY"):
        print("Error: set OPENROUTER_API_KEY environment variable")
        print("Get a free key at https://openrouter.ai")
        sys.exit(1)

    run_pipeline(args)
