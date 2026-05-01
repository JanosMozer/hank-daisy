# Android Voice Orchestration Hardening

## Goal

Prevent Hank from speaking pipeline/meta language such as `recording`, `audio file`, `transcript`, `transcription`, or `primary speaker` during live use.

## Plan

1. Harden the prompt contract so the main assistant is explicitly forbidden from using pipeline-meta framing.
2. Sanitize recognized speech before it becomes a user turn, regardless of whether the route is Android STT or OpenRouter STT.
3. Add a final assistant-response policy before text is written into chat history or spoken via TTS.
4. Add regression tests around prompt text, preset routing, STT cleanup, and final spoken-output cleanup.

## Runtime Architecture

The active `android/` app has a two-stage agent pipeline plus a final response gate:

1. Speech capture / transcription
   `VoiceCommandManager` owns the live microphone loop.
   `FASTEST` uses Android `SpeechRecognizer`.
   `BALANCED` and the other remote presets use `AudioRecord` plus `OpenRouterSpeechTranscriber`.

2. Speech-turn sanitization
   `SpeechTurnSanitizer` normalizes recognized speech before it becomes the user turn passed to Hank.
   This strips common artifacts like `recording:`, `the audio file says`, and `the transcript says`.

3. Multimodal Hank reasoning
   `GeminiService.analyzeFrame()` sends:
   - system prompt
   - recent text history
   - current user text
   - current live frame

4. Assistant response policy
   `AssistantResponsePolicy` is the last deterministic checkpoint before:
   - assistant text is appended to `conversationHistory`
   - assistant text is shown in the chat overlay
   - assistant text is spoken through TTS

## Implemented Defenses

- `HankPromptFactory` now forbids recording/transcript/speech-recognition language in both normal conversation mode and visual demo mode.
- `OpenRouterSpeechTranscriber` now delegates cleanup to the shared `SpeechTurnSanitizer`.
- `VoiceCommandManager` now sanitizes Android local STT results before wake-word stripping, duplicate suppression, and question handoff.
- `StreamViewModel` and `PhoneCameraStreamViewModel` now route every assistant answer through `AssistantResponsePolicy` before history, UI, or TTS.
- Autonomous unsolicited turns are stricter than direct user Q&A:
  - for direct user turns, a contaminated answer is rewritten or replaced with a neutral fallback
  - for autonomous turns, a fully contaminated answer is dropped instead of being spoken unprompted

## Regression Tests

The added JVM tests cover:

- STT cleanup for recording/transcript prefixes
- preservation of legitimate domain words like `rear speaker`
- final spoken-output cleanup and fallback behavior
- prompt-contract text for both main assistant modes
- preset-to-route mappings for `FASTEST` and `BALANCED`

## Remaining Risks

- Android `SpeechRecognizer` can still hallucinate arbitrary wording that is not covered by the deterministic sanitizer.
- The main assistant still uses raw recent prose history, so a bad but non-banned phrasing can still become stylistically sticky.
- There is no semantic classifier yet; the current guardrail is prompt + regex policy, not full content understanding.
