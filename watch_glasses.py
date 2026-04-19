#!/usr/bin/env python3
"""
Polls /Volumes every 5s for Meta glasses and triggers sync.
Designed to run as a LaunchAgent daemon.
"""

import os
import re
import sys
import time
import shutil
import subprocess
from pathlib import Path
from datetime import datetime

SCRIPT_DIR = Path(__file__).parent
DEST_DIR = Path.home() / "Pictures" / "MetaGlasses"
LOG_FILE = SCRIPT_DIR / "sync.log"

GLASSES_PATTERN = re.compile(r"meta|oakley|oak|ray.ban|glasses", re.IGNORECASE)
MEDIA_EXTENSIONS = {".jpg", ".jpeg", ".png", ".mp4", ".mov", ".heic", ".aac", ".m4a"}


def log(msg: str):
    ts = datetime.now().strftime("%H:%M:%S")
    line = f"[{ts}] {msg}"
    print(line, flush=True)
    with open(LOG_FILE, "a") as f:
        f.write(line + "\n")


def find_glasses_volume() -> Path | None:
    for vol in Path("/Volumes").iterdir():
        if vol.name.startswith("."):
            continue
        if GLASSES_PATTERN.search(vol.name):
            return vol
    return None


def sync_media(volume: Path) -> tuple[int, int]:
    DEST_DIR.mkdir(parents=True, exist_ok=True)
    copied, skipped = 0, 0
    for root, _, files in os.walk(volume):
        for fname in files:
            if Path(fname).suffix.lower() not in MEDIA_EXTENSIONS:
                continue
            src = Path(root) / fname
            dest = DEST_DIR / fname
            if dest.exists() and dest.stat().st_size == src.stat().st_size:
                skipped += 1
                continue
            if dest.exists():
                stem, ext = src.stem, src.suffix
                for i in range(1, 9999):
                    dest = DEST_DIR / f"{stem}_{i}{ext}"
                    if not dest.exists():
                        break
            shutil.copy2(src, dest)
            log(f"  Copied: {fname}")
            copied += 1
    return copied, skipped


def notify(title: str, msg: str):
    script = f'display notification "{msg}" with title "{title}"'
    subprocess.run(["osascript", "-e", script], capture_output=True)


def open_folder(path: Path):
    subprocess.run(["open", str(path)], capture_output=True)


def main():
    log("Meta Glasses Watcher started. Scanning /Volumes every 5s...")
    last_synced = ""

    while True:
        volume = find_glasses_volume()

        if volume and volume.name != last_synced:
            log(f"Detected: {volume.name} — syncing to {DEST_DIR}...")
            copied, skipped = sync_media(volume)
            msg = f"{copied} new, {skipped} skipped"
            log(f"Done: {msg}")
            notify("Meta Glasses Sync", f"{msg} → {DEST_DIR.name}/")
            if copied > 0:
                open_folder(DEST_DIR)
            last_synced = volume.name

        elif not volume and last_synced:
            log(f"Glasses disconnected ({last_synced}).")
            last_synced = ""

        time.sleep(5)


if __name__ == "__main__":
    main()
