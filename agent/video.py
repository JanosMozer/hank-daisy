"""
Video stream watcher.

Runs continuously: idles until a video stream becomes available on the given source,
samples one frame per second (wall-clock), saves each snapshot + analysis to
records/<timestamp>/{frame.jpg, analysis.txt}, then returns to idle.

Three threads per stream session:
  - Capture  : drains the camera buffer continuously so it never stalls.
  - Sampler  : grabs the latest frame every second and enqueues it.
  - Analysis : works through the queue sequentially (session memory requires order).

If analysis is slower than 1 fps the queue absorbs the backlog up to `queue_depth`
frames; anything beyond that is dropped with a warning.

Usage:
    python -m agent.video <source_url_or_path>
"""

import argparse
import base64
import logging
import signal
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from queue import Empty, Full, Queue

import cv2

from agent.client import DEFAULT_MODEL
from agent.session import DiagnosticSession

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger(__name__)

_running = True


def _on_signal(sig, frame):
    global _running
    log.info("Signal received — finishing current frame then stopping.")
    _running = False


def _encode_frame(frame) -> bytes:
    ok, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 85])
    if not ok:
        raise RuntimeError("Failed to JPEG-encode frame")
    return buf.tobytes()


def _save_record(session_dir: Path, frame_idx: int, frame_bytes: bytes, analysis: str) -> Path:
    """Save one frame + analysis to session_dir/frame_NNNN/"""
    entry = session_dir / f"frame_{frame_idx:04d}"
    entry.mkdir(parents=True, exist_ok=True)
    (entry / "frame.jpg").write_bytes(frame_bytes)
    (entry / "analysis.txt").write_text(analysis, encoding="utf-8")
    return entry


def _try_open(source: str) -> cv2.VideoCapture | None:
    cap = cv2.VideoCapture(source)
    if not cap.isOpened():
        cap.release()
        return None
    ok, _ = cap.read()
    if not ok:
        cap.release()
        return None
    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    return cap


# ---------------------------------------------------------------------------
# Per-stream worker threads
# ---------------------------------------------------------------------------

def _capture_thread(cap: cv2.VideoCapture, latest_frame: list, lock: threading.Lock,
                    stream_done: threading.Event) -> None:
    """Drain the camera buffer continuously. Stores only the most recent frame."""
    while not stream_done.is_set():
        ok, frame = cap.read()
        if not ok:
            stream_done.set()
            break
        with lock:
            latest_frame[0] = frame


def _analysis_thread(queue: Queue, session: DiagnosticSession,
                     session_dir: Path, stream_done: threading.Event) -> None:
    """
    Consume frames from the queue one at a time and analyze them sequentially.
    Sequential order is required so the session conversation stays coherent.
    Keeps draining the queue even after the stream ends.
    """
    frame_idx = 1
    while True:
        try:
            frame_bytes = queue.get(timeout=1.0)
        except Empty:
            if stream_done.is_set():
                break
            continue

        try:
            b64 = base64.standard_b64encode(frame_bytes).decode()
            analysis = session.analyze(b64)
            record = _save_record(session_dir, frame_idx, frame_bytes, analysis)
            log.info("[frame %d] Saved → %s", frame_idx, record)
            frame_idx += 1
        except Exception:
            log.exception("Analysis failed — skipping frame.")
        finally:
            queue.task_done()


def _process_stream(cap: cv2.VideoCapture, session: DiagnosticSession,
                    records_dir: Path, queue_depth: int = 8) -> None:
    """
    Run capture + analysis threads for the lifetime of one stream connection.
    Creates a timestamped session directory and returns when the stream ends or _running is cleared.
    """
    session_ts = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S_%f")[:-3]
    session_dir = records_dir / f"session_{session_ts}"
    session_dir.mkdir(parents=True, exist_ok=True)
    log.info("Session dir: %s", session_dir)

    latest_frame: list = [None]
    lock = threading.Lock()
    stream_done = threading.Event()
    queue: Queue = Queue(maxsize=queue_depth)

    t_capture = threading.Thread(target=_capture_thread,
                                 args=(cap, latest_frame, lock, stream_done),
                                 daemon=True)
    t_analysis = threading.Thread(target=_analysis_thread,
                                  args=(queue, session, session_dir, stream_done))
    t_capture.start()
    t_analysis.start()

    last_sample = time.monotonic()
    while not stream_done.is_set() and _running:
        now = time.monotonic()
        if now - last_sample >= 1.0:
            last_sample = now
            with lock:
                frame = latest_frame[0]
            if frame is not None:
                try:
                    queue.put_nowait(_encode_frame(frame))
                except Full:
                    log.warning(
                        "Analysis queue full (%d pending) — frame dropped. "
                        "API is slower than 1 fps.",
                        queue_depth,
                    )
        time.sleep(0.02)

    # Signal analysis thread to finish remaining work then exit
    stream_done.set()
    t_capture.join()
    t_analysis.join()


# ---------------------------------------------------------------------------
# Public entry point
# ---------------------------------------------------------------------------

def watch_stream(
    source: str,
    records_dir: str = "records",
    poll_interval: float = 2.0,
    queue_depth: int = 8,
    model: str = DEFAULT_MODEL,
    api_key: str | None = None,
) -> None:
    """
    Run continuously: idle until video appears on `source`, sample 1 frame/sec,
    save records/<timestamp>/{frame.jpg, analysis.txt}, then idle again.

    A new DiagnosticSession (fresh memory) is created each time the stream connects.
    Terminology is sent only once per session via the system prompt.

    If analysis takes longer than 1 s, up to `queue_depth` frames are buffered;
    excess samples are dropped rather than blocking frame capture.

    Ctrl+C or SIGTERM stops cleanly after the current analysis finishes.
    """
    global _running
    _running = True
    signal.signal(signal.SIGINT, _on_signal)
    signal.signal(signal.SIGTERM, _on_signal)

    out = Path(records_dir)
    log.info("Watching %s  [idle]", source)

    while _running:
        cap = _try_open(source)
        if cap is None:
            time.sleep(poll_interval)
            continue

        fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
        log.info("Stream active (%.1f fps) — starting new diagnostic session.", fps)

        session = DiagnosticSession(model=model, api_key=api_key)
        try:
            _process_stream(cap, session, out, queue_depth=queue_depth)
        finally:
            cap.release()

        log.info("Session closed (%d frame(s) analyzed).", session.frame_count)
        if _running:
            log.info("[idle]")
            time.sleep(poll_interval)

    log.info("Stopped.")


if __name__ == "__main__":
    from dotenv import load_dotenv
    load_dotenv()

    parser = argparse.ArgumentParser(description="Watch a video stream and analyze frames")
    parser.add_argument("source", help="RTSP/HTTP stream URL or local file path")
    parser.add_argument("--records", default="records", help="Directory to save results (default: records/)")
    parser.add_argument("--poll", type=float, default=2.0, help="Seconds between idle checks (default: 2)")
    parser.add_argument("--queue-depth", type=int, default=8, help="Max frames buffered ahead of analysis (default: 8)")
    parser.add_argument("--model", default=DEFAULT_MODEL, help="OpenRouter model ID")
    args = parser.parse_args()

    watch_stream(
        args.source,
        records_dir=args.records,
        poll_interval=args.poll,
        queue_depth=args.queue_depth,
        model=args.model,
    )
