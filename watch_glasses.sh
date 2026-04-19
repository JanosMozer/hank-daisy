#!/bin/bash
# Polls every 5 seconds for Meta glasses USB connection and triggers sync.
# Run this manually or via the LaunchAgent.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SYNC_SCRIPT="$SCRIPT_DIR/sync_glasses.py"
LOG_FILE="$SCRIPT_DIR/sync.log"
LAST_SYNCED=""

echo "[$(date '+%H:%M:%S')] Watching for Meta glasses..."

while true; do
    # Check if any Meta-related volume appeared
    VOLUME=$(ls /Volumes/ 2>/dev/null | grep -iE 'meta|oakley|oak|ray.ban|glasses' | head -1)

    if [[ -n "$VOLUME" && "$VOLUME" != "$LAST_SYNCED" ]]; then
        echo "[$(date '+%H:%M:%S')] Detected: $VOLUME — syncing..."
        python3 "$SYNC_SCRIPT" >> "$LOG_FILE" 2>&1
        LAST_SYNCED="$VOLUME"
        echo "[$(date '+%H:%M:%S')] Sync done. Waiting for disconnect..."
    elif [[ -z "$VOLUME" && -n "$LAST_SYNCED" ]]; then
        echo "[$(date '+%H:%M:%S')] Glasses disconnected."
        LAST_SYNCED=""
    fi

    sleep 5
done
