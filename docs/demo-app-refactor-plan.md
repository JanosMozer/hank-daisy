# Demo App Refactor Plan

## Purpose

This note locks the demo-first refactor plan before implementation starts.

The main goal is to turn the current app shell into a focused demo and evaluation tool for:

- glasses-based demo mode
- phone-only demo mode
- configurable audio pipeline experiments
- configurable video pipeline experiments

This refactor should simplify the user-facing app while making the underlying capture and speech pipeline more modular.

## Current State

The current Android shell still reflects a broader product shape:

- `Convos`
- `Chats`
- `Orders`
- `Settings`

Relevant current structures:

- `AppTab` still defines legacy navigation tabs.
- `SessionViewModel` owns shell state and persisted settings.
- `AppSettings` is still compact and product-oriented.
- `StreamViewModel` and `PhoneCameraStreamViewModel` already split glasses and phone streaming.
- `VoiceCommandManager` still relies on Android `SpeechRecognizer`, which limits mic routing and front-end processing control.

The branch already contains demo-oriented narrowing, but the architecture is not yet aligned with the intended evaluation workflow.

## Product Decision

Use **three** top-level tabs, not four:

- `Demo`
- `Pipeline`
- `Settings`

This is the first major simplification.

`Glasses Demo` and `Phone Demo` should not be separate top-level tabs. They are the same demo experience with different capture/output routes. Splitting them at the top level would duplicate UI, navigation, and state.

Instead:

- `Demo` contains a mode selector: `Glasses` or `Phone`
- `Pipeline` contains experimental controls for audio/video routing
- `Settings` contains non-pipeline app settings

## Why This Is Better

This reduces duplication and keeps the mental model clean:

- one place to run the demo
- one place to configure the pipeline
- one place for general app behavior

It also matches the real architecture more closely:

- demo mode selects the runtime environment
- pipeline config selects how media is captured and processed
- settings controls theme, accessibility, domain, and other app-level behavior

## Non-Goals

This refactor should not try to solve everything at once.

Not in scope for the first pass:

- full backend unification across all clients
- a polished benchmarking dashboard
- implementation of every candidate denoiser or STT model
- broad cleanup of unrelated screens outside the demo shell

The first refactor should focus on structure, settings, routing, and compatibility boundaries.

## Information Architecture

### 1. `Demo`

Primary runtime surface.

Contains:

- mode selector: `Glasses` / `Phone`
- connect/start controls
- live video preview
- active transcript / conversation state
- push-to-talk or wake-word state
- current audio/video route summary
- access to the same live assist experience regardless of mode

Behavior:

- `Glasses` mode uses DAT session + glasses camera + glasses-preferred audio route where possible
- `Phone` mode uses CameraX + phone mic + phone speaker

The screen layout should be shared as much as possible. The source adapters should differ, not the whole UI.

### 2. `Pipeline`

Experimental control panel for input and inference routing.

This tab should be split internally into sections:

- `Audio`
- `Video`
- optional `Advanced`

This avoids one giant flat control screen.

### 3. `Settings`

General app behavior only.

Keep here:

- theme
- text scale
- high contrast
- haptics
- work domain
- demo commentary mode
- help/about/debug items that are not pipeline-stage controls

Remove from here:

- speech route selection
- mic route selection
- video FPS tuning
- model-specific pipeline controls

Those belong in `Pipeline`.

## Naming Decisions

Top-level tabs:

- `Demo`
- `Pipeline`
- `Settings`

Internal labels:

- `Glasses`
- `Phone`
- `Audio`
- `Video`
- `Advanced`

Avoid long labels like `Audio and video input settings`. They are accurate but too heavy for a repeated navigation surface.

## State Model

The app should separate three layers of state:

- shell/navigation state
- runtime/demo state
- persisted configuration state

### Shell State

This is the navigation layer.

Proposed shape:

```kotlin
data class AppShellState(
    val currentTab: TopLevelTab = TopLevelTab.DEMO,
    val demoMode: DemoMode = DemoMode.GLASSES,
    val pipelineSection: PipelineSection = PipelineSection.AUDIO,
    val overlay: AppOverlay? = null,
)

enum class TopLevelTab { DEMO, PIPELINE, SETTINGS }
enum class DemoMode { GLASSES, PHONE }
enum class PipelineSection { AUDIO, VIDEO, ADVANCED }
```

Notes:

- `demoMode` belongs to shell/runtime state, not buried in general settings
- `PipelineSection` is UI navigation state, not business state

### Runtime Demo State

This is the active live-session layer.

Proposed shape:

```kotlin
data class DemoRuntimeState(
    val isStreaming: Boolean = false,
    val isListening: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isAssistantSpeaking: Boolean = false,
    val activeCaptureRoute: CaptureRouteSummary? = null,
    val activeAudioPipeline: ActiveAudioPipelineSummary? = null,
    val activeVideoPipeline: ActiveVideoPipelineSummary? = null,
    val lastTranscript: String? = null,
    val lastError: String? = null,
)
```

This should be derived from the active demo engine, not manually duplicated across unrelated screens.

### Persisted Configuration State

This is the most important part of the refactor.

The current `AppSettings` model is too shallow for the intended pipeline experimentation. It should be split.

Proposed top-level settings model:

```kotlin
data class AppConfig(
    val demo: DemoConfig = DemoConfig(),
    val audio: AudioPipelineConfig = AudioPipelineConfig(),
    val video: VideoPipelineConfig = VideoPipelineConfig(),
    val general: GeneralSettings = GeneralSettings(),
)
```

## Settings Schema

### 1. Demo Config

```kotlin
data class DemoConfig(
    val defaultMode: DemoMode = DemoMode.GLASSES,
    val autoResumeListeningAfterTts: Boolean = true,
)
```

This is intentionally small.

### 2. Audio Pipeline Config

The audio chain needs explicit stages.

```kotlin
data class AudioPipelineConfig(
    val capture: AudioCaptureConfig = AudioCaptureConfig(),
    val enhancement: AudioEnhancementConfig = AudioEnhancementConfig(),
    val wakeWord: WakeWordConfig = WakeWordConfig(),
    val intent: SpeechIntentConfig = SpeechIntentConfig(),
    val vad: VadConfig = VadConfig(),
    val transcription: TranscriptionConfig = TranscriptionConfig(),
)
```

#### 2.1 Capture

```kotlin
data class AudioCaptureConfig(
    val inputMode: AudioInputMode = AudioInputMode.ANDROID_SPEECH_RECOGNIZER,
    val preferredDevice: PreferredMicDevice = PreferredMicDevice.SYSTEM_DEFAULT,
    val sampleRateHz: Int = 16000,
)

enum class AudioInputMode {
    ANDROID_SPEECH_RECOGNIZER,
    RAW_AUDIO_RECORD,
}

enum class PreferredMicDevice {
    SYSTEM_DEFAULT,
    BUILT_IN_MIC,
    WIRED_HEADSET,
    USB_MIC,
    BLUETOOTH_MIC,
}
```

Important constraint:

- if `inputMode = ANDROID_SPEECH_RECOGNIZER`, the app does not truly control most downstream audio stages

This must be reflected in the UI.

#### 2.2 Enhancement

Do not model everything as one generic AI filter.

Use explicit toggles and route types:

```kotlin
data class AudioEnhancementConfig(
    val noiseSuppression: NoiseSuppressionMode = NoiseSuppressionMode.NONE,
    val echoCancellation: EchoCancellationMode = EchoCancellationMode.SYSTEM,
    val automaticGainControl: Boolean = false,
)

enum class NoiseSuppressionMode {
    NONE,
    SYSTEM,
    RNNOISE,
    DEEP_FILTER_NET,
    REMOTE_PREPROCESS,
}

enum class EchoCancellationMode {
    NONE,
    SYSTEM,
    MODEL_BASED,
}
```

This is deliberately narrower than a huge model catalog.

For the first phase, the UI should support only the modes that are actually implemented. The schema can still anticipate future routes.

#### 2.3 Wake Word

```kotlin
data class WakeWordConfig(
    val enabled: Boolean = true,
    val phrase: String = "Hey Hank",
    val route: WakeWordRoute = WakeWordRoute.BUILT_IN,
)

enum class WakeWordRoute {
    BUILT_IN,
    LOCAL_KEYWORD_MODEL,
    DISABLED,
}
```

Keep the first version simple.

Changing the wake phrase is useful, but arbitrary custom wake words may not be equally supported by every route. The UI should communicate that.

#### 2.4 Speech Intent

This is not the same thing as STT.

```kotlin
data class SpeechIntentConfig(
    val route: SpeechIntentRoute = SpeechIntentRoute.RULE_BASED,
    val minQueryLength: Int = 2,
    val duplicateWindowMs: Long = 2500L,
    val localCommandsEnabled: Boolean = true,
)

enum class SpeechIntentRoute {
    RULE_BASED,
    LOCAL_CLASSIFIER,
}
```

This stage should absorb:

- local commands like stop/repeat/hold on
- filler suppression
- duplicate suppression

This is cheaper and safer than throwing every transcript at the model.

#### 2.5 VAD

```kotlin
data class VadConfig(
    val route: VadRoute = VadRoute.NONE,
    val threshold: Float = 0.5f,
    val minSpeechMs: Long = 120L,
)

enum class VadRoute {
    NONE,
    SYSTEM,
    SILERO,
}
```

Important design note:

- store the threshold as a normalized value such as `0.0f..1.0f`
- the UI can still render a friendlier `0..100` slider if desired

Do not store display-scale values in the config model.

#### 2.6 Transcription

```kotlin
data class TranscriptionConfig(
    val route: TranscriptionRoute = TranscriptionRoute.ANDROID,
    val remoteModelId: String? = null,
    val localModelId: String? = null,
    val emitPartials: Boolean = true,
)

enum class TranscriptionRoute {
    ANDROID,
    OPENROUTER_OPENAI_AUDIO,
    LOCAL_MODEL,
}
```

This is where route comparison should happen.

Do not encode denoising choice into the STT route itself. Keep stages separate.

### 3. Video Pipeline Config

Start simple.

```kotlin
data class VideoPipelineConfig(
    val sourceFps: Int = 24,
    val modelSendFps: Int = 3,
    val videoQuality: VideoQualityPreset = VideoQualityPreset.MEDIUM,
)

enum class VideoQualityPreset {
    LOW,
    MEDIUM,
    HIGH,
}
```

If needed later, add:

- scene-change gating
- JPEG quality
- upload resolution

Those are phase-two additions, not first-pass requirements.

### 4. General Settings

This absorbs the existing app-level settings:

```kotlin
data class GeneralSettings(
    val workDomain: WorkDomain = WorkDomain.CAR,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val textScale: TextScale = TextScale.NORMAL,
    val highContrast: Boolean = false,
    val hapticFeedback: Boolean = true,
    val demoCommentaryMode: Boolean = false,
)
```

## Compatibility Rules

The app must not present impossible combinations as if they are valid.

Examples:

- `ANDROID_SPEECH_RECOGNIZER` should disable manual mic selection and most enhancement stages
- some enhancement routes should require `RAW_AUDIO_RECORD`
- some local STT routes may require fixed sample rates
- some remote STT routes may ignore local VAD because server-side endpointing is used

This should be enforced by a derived capabilities layer:

```kotlin
data class AudioPipelineCapabilities(
    val canSelectMic: Boolean,
    val canUseNoiseSuppression: Boolean,
    val canUseEchoCancellation: Boolean,
    val canUseWakeWordModel: Boolean,
    val canUseCustomVad: Boolean,
)
```

The UI should render unavailable controls as disabled with an explanation, not hide the model entirely.

## Presets

The UI should provide a small number of named presets before exposing every stage.

Recommended starting presets:

- `Fastest`
- `Balanced`
- `Noisy Shop`
- `Local Only`
- `Remote Best Accuracy`

Example mapping:

- `Fastest`: Android recognizer, minimal processing
- `Balanced`: raw capture + light preprocessing + remote STT
- `Noisy Shop`: raw capture + denoiser + strong VAD + remote STT
- `Local Only`: raw capture + local STT
- `Remote Best Accuracy`: raw capture + optional denoise + remote STT

This makes demos usable while still supporting detailed tuning.

## Architecture Changes

### 1. Shell Simplification

Refactor navigation around:

- `DemoScreen`
- `PipelineScreen`
- `SettingsScreen`

Replace legacy tab logic in:

- `AppTab`
- `HankDaisyScaffold`
- `SessionViewModel`

### 2. Configuration Model Split

Replace the current `AppSettings` with a richer persisted configuration model.

Migration requirements:

- old saved prefs must still load
- missing fields must default safely
- old `captureMode` and `speechRecognitionRoute` must map into the new schema

### 3. Demo Engine Abstraction

Create a common abstraction for the two demo runtimes:

```kotlin
interface DemoEngine {
    val runtimeState: StateFlow<DemoRuntimeState>
    fun start()
    fun stop()
    fun askHank()
}
```

Implementations:

- `GlassesDemoEngine`
- `PhoneDemoEngine`

These should wrap the current `StreamViewModel` and `PhoneCameraStreamViewModel` behavior instead of duplicating UI logic.

### 4. Audio Pipeline Abstraction

Introduce an explicit audio pipeline boundary:

```kotlin
interface SpeechPipeline {
    val state: StateFlow<SpeechPipelineState>
    fun start()
    fun stop()
}
```

Candidate implementations:

- `AndroidRecognizerPipeline`
- `RawAudioRemoteSttPipeline`
- `RawAudioLocalSttPipeline`

This is the key step that enables route comparison.

### 5. Filtering Layer

Implement a post-transcript layer before model dispatch:

- `SpeechIntentFilter`
- `TurnDebouncer`

This should live between transcript generation and `analyzeWithQuestion(...)`.

## Execution Plan

### Phase 0: Planning Lock

Deliverables:

- this note committed to the branch

No feature work starts before this commit exists.

### Phase 1: Shell Refactor

Goals:

- replace legacy tab model with `Demo`, `Pipeline`, `Settings`
- remove top-level emphasis on chats/orders in the shell
- preserve existing deep screens until they can be removed cleanly

Files likely touched:

- `ui/AppTab.kt`
- `ui/HankDaisyScaffold.kt`
- `session/SessionViewModel.kt`

### Phase 2: Config Model Refactor

Goals:

- replace `AppSettings` with `AppConfig`
- add migration from legacy prefs
- split general settings from pipeline settings

Files likely touched:

- `session/AppSettings.kt` or replacement files
- `session/SessionViewModel.kt`
- `ui/SettingsScreen.kt`
- new config model files under `session/` or a dedicated `config/` package

### Phase 3: Pipeline UI

Goals:

- add `PipelineScreen`
- add `Audio` and `Video` sections
- add presets
- add capability-aware enable/disable logic

Files likely touched:

- new `ui/PipelineScreen.kt`
- new `ui/AudioPipelineSection.kt`
- new `ui/VideoPipelineSection.kt`

### Phase 4: Demo Unification

Goals:

- create a shared `DemoScreen`
- switch runtime implementation based on `DemoMode`
- keep shared transcript/chat UI common

Files likely touched:

- new `ui/DemoScreen.kt`
- existing `stream/StreamViewModel.kt`
- existing `stream/PhoneCameraStreamViewModel.kt`

### Phase 5: Speech Pipeline Abstraction

Goals:

- isolate Android recognizer path
- add raw-audio capture path
- formalize remote STT route
- prepare for local STT route

Files likely touched:

- `stream/VoiceCommandManager.kt`
- `stream/OpenRouterSpeechTranscriber.kt`
- new speech pipeline classes

### Phase 6: Filtering and Instrumentation

Goals:

- add `SpeechIntentFilter`
- add duplicate-turn suppression
- log latency and route choices
- log route-specific errors

Metrics to capture:

- end-of-speech to transcript latency
- transcript finalization latency
- model-dispatch latency
- duplicate suppression count
- no-speech suppression count
- route identifier per turn

### Phase 7: Cleanup

Goals:

- remove obsolete shell paths if no longer needed
- reduce temporary compatibility code
- update docs and screenshots

## Evaluation Plan

To compare routes meaningfully, support both:

- live testing
- replay testing

Replay testing should become a first-class goal for later phases.

Ideal comparison flow:

- capture one utterance once
- replay it through multiple route combinations
- compare transcript quality and latency

Without replay, route comparisons will be confounded by different takes and inconsistent background noise.

## Risks

### 1. The pipeline UI becomes a control-room mess

Mitigation:

- use presets first
- hide advanced controls behind section expansion
- disable impossible combinations clearly

### 2. Too many enums with too few implementations

Mitigation:

- keep the schema forward-looking
- only expose implemented routes in the initial UI

### 3. The app implies combinability that does not exist

Mitigation:

- capability model
- route-specific explanations in the UI

### 4. Glasses and phone modes drift apart again

Mitigation:

- one `Demo` tab
- one shared demo UI
- route-specific adapters only below that layer

## Simplifications to Protect

These are deliberate constraints and should not be dropped casually:

- three top-level tabs, not four
- one shared demo surface, not separate demo tabs
- presets before fully manual tuning
- normalized config values in persistence
- explicit compatibility rules
- transcript filtering before model dispatch

## Open Questions

These should be answered during implementation, not before Phase 0:

- which local STT engines are realistic on the target Android devices
- which denoisers are feasible on-device versus remote-only
- whether wake-word customization should be free-form or preset-based
- how much of the current `ChatOnly` path survives in the demo-first shell
- whether `Orders` remains accessible only as a deep/debug path or is removed entirely from the demo branch

## Recommended First Commit After This Note

After this planning commit lands, the first implementation commit should do only the shell simplification:

- new top-level tab model
- `Demo` / `Pipeline` / `Settings`
- no speech pipeline implementation changes yet

That keeps the first code refactor tractable and reviewable.
