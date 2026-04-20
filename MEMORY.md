# Hank Daisy Development Notes

## Latest Changes: Dynamic Agent with Real-Time Streaming (April 2026)

### What Changed

Made the agent dynamic to accept text queries (with or without camera frames) from the glasses and respond in real-time using automotive terminology. Previously, the agent only processed video frames in batch mode. Now it's a WebSocket server that streams responses sentence-by-sentence for smooth voice interaction.

### Key Directories

- `agent/` - Python backend: diagnostic session engine, OpenRouter client, conversational system prompts, WebSocket server
- `samples/CameraAccess/app/` - Android app: DAT SDK integration, voice commands, glasses audio I/O, chat UI
- `samples/CameraAccess/app/src/main/java/.../stream/` - Core streaming logic: StreamViewModel (orchestrator), GeminiService (old, kept for autonomous observation), HankAgentClient (new WebSocket client), GlassesAudioManager (TTS), VoiceCommandManager (wake word detection)

### Files Modified

Python side:
- `agent/prompts.py` - Added CONVERSATION_SYSTEM_PROMPT for conversational mode (vs structured diagnostic lists)
- `agent/client.py` - Added async stream_completion() for OpenRouter SSE streaming
- `agent/session.py` - Added ConversationSession class supporting text-only and text+image queries with streaming
- `agent/server.py` - NEW: WebSocket server on port 8765
- `requirements.txt` - Added websockets>=12.0

Android side:
- `samples/CameraAccess/app/src/main/java/.../stream/HankAgentClient.kt` - NEW: OkHttp WebSocket client
- `samples/CameraAccess/app/src/main/java/.../stream/StreamViewModel.kt` - Integrated HankAgentClient, sentence-by-sentence streaming TTS
- `samples/CameraAccess/app/src/main/java/.../stream/GlassesAudioManager.kt` - Added useQueueAdd parameter for queue control
- `samples/CameraAccess/app/build.gradle.kts` - Added HANK_AGENT_URL BuildConfig field
- `local.properties` - NEW: Configuration template for hank_agent_url

### How It Works

Voice transcription from glasses goes to StreamViewModel.analyzeWithQuestion() which sends text (and optional current frame) to HankAgentClient WebSocket. Python agent receives it, processes through ConversationSession using OpenRouter streaming, sends chunks back. Android accumulates chunks, speaks completed sentences immediately (first sentence QUEUE_FLUSHes, subsequent QUEUE_ADD for smooth TTS).

### Running the Agent

Start: `python3 -m agent.server`
Configure Android: set hank_agent_url in local.properties (ws://10.0.2.2:8765 for emulator, or ws://YOUR_IP:8765 for device)

### Backward Compatibility

Autonomous observation (automatic scene-triggered comments) still uses GeminiService directly. Only user voice queries go through HankAgent.

### Dependencies

Python: httpx, opencv-python, python-dotenv, websockets
Android: OkHttp 4.12.0 (already in build.gradle), DAT SDK
