# Hank Daisy: System Architecture & Context Memory

This document is the source of truth for understanding the `agent/` and `samples/HankDaisy/` directories, their interplay, and the system's architecture. Use this file as context when orienting new AI agent sessions to the `hank-daisy` repository.

## 1. System Structure
Hank Daisy is a real-time, multimodal automotive diagnostic assistant utilizing a hybrid architecture that splits its heavy lifting between local hardware abstraction (an Android companion app) and remote python logic (telemetry & data parsing). 

It is designed to receive live video frames alongside verbal queries, interpret automotive diagnostics, and parse LLM output responses dynamically into structured Markdown steps, checklists, and gauges.

## 2. The Glasses SDK (`samples/HankDaisy/`)
The Android application acts as both the hardware abstraction layer (interfacing directly with Meta Wearables via the DAT SDK) and a standalone edge client that controls the LLM UX.
- **`StreamViewModel.kt`**: The core orchestrator. Manages connection sessions using the DAT SDK, captures microphone audio events, and sequences when autonomous images are grabbed or manual requests ("Ask Hank") are executed.
- **`GeminiService.kt`**: An API wrapper for OpenRouter (OpenAI-compatible endpoints). It directly queries large vision models in real-time using complex structural prompts so it can process both "User Voice Queries" and autonomous background scans.
- **`LiveStreamServer.kt`**: Spins up a local TCP Server (port `8080`) that continuously broadcasts the incoming YUV-converted video frames from the glasses as raw bitmaps for optional external ingestion.
- **`VoiceCommandManager.kt` & `GlassesAudioManager.kt`**: Handles local transcription, wake word detection, cut-off interrupts when the user speaks over the TTS, and audio playback.
- **`SceneChangeWatcher.kt`**: Triggers passive frame analysis. When the user rests their gaze on a specific object/area (determined by motion settling), it snapshots the frame autonomously and prompts `GeminiService` to offer preemptive diagnostic advice.

## 3. The Python Agent (`agent/`)
This acts as a supplemental remote backend designed for persistent observation, independent server-client interaction, remote processing, and logging.
- **`video.py`**: A passive recording service launched via `python -m agent.video <source>`. It hooks securely into the Android app's exposed `LiveStreamServer.kt` TCP stream. It captures the full video stream as an `.mp4`, samples 1 frame per second, hits OpenRouter via Python to log sequential state, and documents everything cleanly in structured `records/session_<timestamp>/` folders (a structure leveraged by the web dashboard).
- **`server.py`**: A standalone continuous WebSocket server running on port `8765`. It waits for connections receiving JSON payloads (`{"text": "...", "frame": "base64..."}`) and streams back diagnostic conversational Markdown chunks (`"type": "chunk"`). It exists to easily bind any secondary applications to Hank's core engine instead of locking everything into the Android app's boundaries.
- **`session.py`**: The agent dialogue backend. Groups API logic in two ways:
  - `DiagnosticSession`: Handles consecutive frame-by-frame analysis workflows building up sequential diagnosis.
  - `ConversationSession`: Standard memory-capped (40 turns) LLM conversational wrapper providing chat interfaces capability.
- **`prompts.py`**: Contains the definitive diagnostic guidelines on generating Markdown (warning panels, diagnostic procedural arrays, and pass/fail gauge formatting schemas).

## 4. Communication Pathways
1. **Primary On-Device Interaction Pathway**: 
   User issues a voice request -> Mic transcodes speech to text via `VoiceCommandManager.kt` -> `StreamViewModel.kt` pairs it with the most recent HD camera frame from the DAT connection -> Sent to `GeminiService.kt` -> TTS reads answer over the Glasses frames.
2. **Dashboard Review & Persistence Pathway**:
   Glasses streams live raw RGB bitmaps out to localhost port `8080` by `LiveStreamServer.kt` -> The Python worker `python -m agent.video` consumes this port -> Snapshots frames independently -> Analyzes them via APIs -> Logs them directly into the `/records` system for historical web dashboard tracking.
