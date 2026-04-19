#!/usr/bin/env python3
"""
Meta Glasses Live Stream
========================
Captures the iPhone Mirroring window (showing Meta View app) and serves frames as:
  - MJPEG stream at http://localhost:5050/stream  (drop into any AI pipeline / browser)
  - Latest JPEG at  http://localhost:5050/frame   (single grab)
  - Raw numpy array via shared memory (for local Python pipelines)

Usage:
  python3 stream_glasses.py              # auto-detects iPhone Mirroring window
  python3 stream_glasses.py --fps 10     # set capture rate
  python3 stream_glasses.py --region 0,0,1280,720  # manual crop x,y,w,h
  python3 stream_glasses.py --port 5050
"""

import io
import sys
import time
import threading
import argparse
from datetime import datetime
from pathlib import Path

import mss
import mss.tools
from PIL import Image
from flask import Flask, Response, jsonify

try:
    from Quartz import (
        CGWindowListCopyWindowInfo,
        kCGWindowListOptionOnScreenOnly,
        kCGNullWindowID,
    )
    HAS_QUARTZ = True
except ImportError:
    HAS_QUARTZ = False

# ── Config ────────────────────────────────────────────────────────────────────

app = Flask(__name__)
latest_frame: bytes = b""
frame_lock = threading.Lock()
capture_region: dict | None = None  # {left, top, width, height}
frame_count = 0
start_time = time.time()


# ── Window Detection ──────────────────────────────────────────────────────────

IPHONE_MIRRORING_TITLES = [
    "iPhone Mirroring",
    "iPhone",
    "Meta View",
]


def find_iphone_mirroring_window() -> dict | None:
    """Returns {left, top, width, height} of the iPhone Mirroring window."""
    if not HAS_QUARTZ:
        print("Quartz not available — use --region x,y,w,h to set capture area manually.")
        return None

    windows = CGWindowListCopyWindowInfo(kCGWindowListOptionOnScreenOnly, kCGNullWindowID)
    for w in windows:
        name = w.get("kCGWindowName", "") or ""
        owner = w.get("kCGWindowOwnerName", "") or ""
        for title in IPHONE_MIRRORING_TITLES:
            if title.lower() in name.lower() or title.lower() in owner.lower():
                bounds = w.get("kCGWindowBounds", {})
                return {
                    "left":   int(bounds.get("X", 0)),
                    "top":    int(bounds.get("Y", 0)),
                    "width":  int(bounds.get("Width", 400)),
                    "height": int(bounds.get("Height", 800)),
                }
    return None


def list_windows():
    if not HAS_QUARTZ:
        print("Quartz not available.")
        return
    windows = CGWindowListCopyWindowInfo(kCGWindowListOptionOnScreenOnly, kCGNullWindowID)
    seen = set()
    for w in windows:
        owner = w.get("kCGWindowOwnerName", "")
        if owner and owner not in seen:
            seen.add(owner)
            print(f"  {owner}")


# ── Capture Loop ──────────────────────────────────────────────────────────────

def capture_loop(fps: int, region: dict | None):
    global latest_frame, capture_region, frame_count
    interval = 1.0 / fps

    with mss.mss() as sct:
        while True:
            t0 = time.time()

            # Re-detect window every 30 frames (handles resize/move)
            if frame_count % 30 == 0:
                detected = find_iphone_mirroring_window()
                if detected:
                    capture_region = detected
                elif region:
                    capture_region = region

            if not capture_region:
                # Full screen fallback
                mon = sct.monitors[1]
                capture_region = {"left": mon["left"], "top": mon["top"],
                                  "width": mon["width"], "height": mon["height"]}

            try:
                screenshot = sct.grab(capture_region)
                img = Image.frombytes("RGB", screenshot.size, screenshot.bgra, "raw", "BGRX")

                buf = io.BytesIO()
                img.save(buf, format="JPEG", quality=85)
                jpeg = buf.getvalue()

                with frame_lock:
                    latest_frame = jpeg
                frame_count += 1

            except Exception as e:
                print(f"Capture error: {e}", flush=True)

            elapsed = time.time() - t0
            sleep = interval - elapsed
            if sleep > 0:
                time.sleep(sleep)


# ── Flask Routes ──────────────────────────────────────────────────────────────

def generate_mjpeg():
    while True:
        with frame_lock:
            frame = latest_frame
        if frame:
            yield (
                b"--frame\r\n"
                b"Content-Type: image/jpeg\r\n\r\n" + frame + b"\r\n"
            )
        time.sleep(0.01)


@app.route("/stream")
def stream():
    """MJPEG stream — open in browser or use in OpenCV: cv2.VideoCapture('http://localhost:5050/stream')"""
    return Response(generate_mjpeg(), mimetype="multipart/x-mixed-replace; boundary=frame")


@app.route("/frame")
def frame():
    """Single JPEG frame."""
    with frame_lock:
        data = latest_frame
    return Response(data, mimetype="image/jpeg")


@app.route("/status")
def status():
    elapsed = time.time() - start_time
    return jsonify({
        "fps": round(frame_count / elapsed, 1) if elapsed > 0 else 0,
        "frames": frame_count,
        "region": capture_region,
        "stream_url": "http://localhost:5050/stream",
        "frame_url":  "http://localhost:5050/frame",
    })


@app.route("/")
def index():
    return """
    <html><body style="background:#111;color:#eee;font-family:monospace;padding:2em">
    <h2>Meta Glasses Live Stream</h2>
    <img src="/stream" style="max-width:100%;border:1px solid #444">
    <pre style="margin-top:1em">
Stream URL (MJPEG): http://localhost:5050/stream
Single frame:       http://localhost:5050/frame
Status:             http://localhost:5050/status

OpenCV:
  cap = cv2.VideoCapture('http://localhost:5050/stream')

Requests:
  import requests
  from PIL import Image
  import io
  img = Image.open(io.BytesIO(requests.get('http://localhost:5050/frame').content))
    </pre>
    </body></html>
    """


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fps",    type=int, default=15, help="Capture FPS (default 15)")
    parser.add_argument("--port",   type=int, default=5050)
    parser.add_argument("--region", type=str, default=None, help="x,y,w,h — manual crop")
    parser.add_argument("--list-windows", action="store_true")
    args = parser.parse_args()

    if args.list_windows:
        print("Open windows:")
        list_windows()
        return

    region = None
    if args.region:
        x, y, w, h = map(int, args.region.split(","))
        region = {"left": x, "top": y, "width": w, "height": h}

    # Detect window
    detected = find_iphone_mirroring_window()
    if detected:
        print(f"Found iPhone Mirroring window: {detected}")
    else:
        print("iPhone Mirroring window not detected yet — will retry while running.")
        print("Open iPhone Mirroring and launch Meta View on your phone.")
        if not region:
            print("Or specify --region x,y,w,h for a manual crop.")

    # Start capture thread
    t = threading.Thread(target=capture_loop, args=(args.fps, region), daemon=True)
    t.start()

    print(f"\nStreaming at http://localhost:{args.port}/stream")
    print(f"Open in browser or use with OpenCV / requests in your AI pipeline.\n")
    app.run(host="0.0.0.0", port=args.port, threaded=True)


if __name__ == "__main__":
    main()
