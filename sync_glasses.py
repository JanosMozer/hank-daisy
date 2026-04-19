#!/usr/bin/env python3
"""
Meta Glasses Media Sync
Detects Oakley Meta / Ray-Ban Meta glasses connected via USB,
copies new photos and videos to ~/Pictures/MetaGlasses, and opens the folder.
"""

import os
import sys
import shutil
import subprocess
from datetime import datetime
from pathlib import Path

DEST_DIR = Path.home() / "Pictures" / "MetaGlasses"

# Volume name patterns the Meta glasses appear as on Mac
GLASSES_VOLUME_NAMES = [
    "Meta",
    "OAK",
    "OAKLEY",
    "Ray-Ban",
    "RAYBAN",
    "META GLASSES",
    "Smart Glasses",
    "GLASSES",
]

MEDIA_EXTENSIONS = {".jpg", ".jpeg", ".png", ".mp4", ".mov", ".heic", ".aac", ".m4a"}


def find_glasses_volume() -> Path | None:
    volumes = Path("/Volumes")
    for vol in volumes.iterdir():
        if vol.name == "Macintosh HD" or vol.name.startswith("."):
            continue
        name_upper = vol.name.upper()
        for pattern in GLASSES_VOLUME_NAMES:
            if pattern.upper() in name_upper:
                return vol
    return None


def find_media_files(volume: Path) -> list[Path]:
    media = []
    for root, _, files in os.walk(volume):
        for f in files:
            if Path(f).suffix.lower() in MEDIA_EXTENSIONS:
                media.append(Path(root) / f)
    return media


def sync_media(volume: Path) -> tuple[int, int]:
    DEST_DIR.mkdir(parents=True, exist_ok=True)
    files = find_media_files(volume)
    copied = 0
    skipped = 0

    for src in files:
        # Preserve original filename, add date prefix if needed
        dest = DEST_DIR / src.name
        if dest.exists() and dest.stat().st_size == src.stat().st_size:
            skipped += 1
            continue
        # Avoid collision by adding counter suffix
        if dest.exists():
            stem = src.stem
            suffix = src.suffix
            for i in range(1, 1000):
                dest = DEST_DIR / f"{stem}_{i}{suffix}"
                if not dest.exists():
                    break
        shutil.copy2(src, dest)
        copied += 1
        print(f"  Copied: {src.name} → {dest.name}")

    return copied, skipped


def open_folder(path: Path):
    subprocess.run(["open", str(path)], check=False)


def notify(title: str, message: str):
    script = f'display notification "{message}" with title "{title}"'
    subprocess.run(["osascript", "-e", script], check=False)


def main():
    print(f"[{datetime.now():%H:%M:%S}] Scanning for Meta glasses...")

    volume = find_glasses_volume()
    if not volume:
        print("No Meta glasses volume found in /Volumes.")
        print("\nMounted volumes:")
        for v in sorted(Path("/Volumes").iterdir()):
            if not v.name.startswith("."):
                print(f"  {v.name}")
        print("\nIf your glasses are connected and not detected, run:")
        print("  python3 sync_glasses.py --volume 'YourVolumeName'")
        sys.exit(1)

    print(f"Found glasses volume: {volume}")
    copied, skipped = sync_media(volume)

    msg = f"{copied} new files copied, {skipped} already synced → {DEST_DIR}"
    print(f"\n{msg}")
    notify("Meta Glasses Sync", msg)

    if copied > 0:
        open_folder(DEST_DIR)
    elif "--open" in sys.argv:
        open_folder(DEST_DIR)


if __name__ == "__main__":
    # Allow overriding the volume name via CLI arg
    for i, arg in enumerate(sys.argv[1:], 1):
        if arg == "--volume" and i + 1 < len(sys.argv):
            GLASSES_VOLUME_NAMES.insert(0, sys.argv[i + 1])
        elif arg == "--dest" and i + 1 < len(sys.argv):
            DEST_DIR = Path(sys.argv[i + 1])

    main()
