# MPI Demo App Refactor Plan

## Goal

Refactor `mpi-android` from a multipoint-inspection-first app into a demo-and-evaluation app focused on:

- capture configuration and live session control
- the quality and relevance of Hank's live spoken commentary
- extraction of useful technical details from the last capture session
- generation of a concise review/report surface from captured evidence

This app should be optimized for evaluating:

- scene understanding
- usefulness of read-only commentary
- extraction of relevant structured facts
- post-session summary/report quality

It should not be optimized primarily for audio pipeline experimentation. That remains the focus of the main `android/` app.

## Product Positioning

### Main distinction from `android/`

`mpi-android` should become the app for testing whether Hank:

- sees the right thing
- says the right thing
- extracts the right details
- produces a useful report from the last session

The main `android/` app remains the place to test:

- speech recognition route quality
- denoiser / capture pipeline combinations
- mic routing and low-level audio behavior

### Core product idea

The most important behavior in this refactor is `read-only mode`.

In this mode, Hank is not driven mainly by direct turn-taking with the user. Instead:

- Hank watches the scene continuously
- Hank gives concise technical commentary
- Hank spaces commentary with a configurable pause between spoken units
- the microphone still runs, but user speech is filtered aggressively
- only relevant extracted content enters context
- newly extracted user content affects the next spoken sentence rather than interrupting the current one

This is the main differentiator of the app and should be treated as a first-class system mode, not as a side effect of scene-change commentary.

## Current State

The current `mpi-android` app is still structured around four tabs:

- `Capture`
- `Sessions`
- `Inspections`
- `Settings`

In practice:

- live capture starts from `ConvosScreen`
- saved chats live in `SessionsHomeScreen`
- report-like inspection data lives in `OrdersScreen` and `OrderDetailScreen`
- settings are still light and mostly global

This is a mismatch with the intended product.

The current data model is also still MPI-first:

- `RepairOrder`
- `InspectionFinding`
- `Session`
- saved evidence attached to inspections

The refactor should shift the center of gravity from `order-first` to `capture-session-first`.

## Proposed Information Architecture

Replace the current four-tab shell with three top-level tabs:

- `Capture`
- `Info & Report`
- `Settings`

### Capture

`Capture` becomes the operational home for configuring and running a live session.

Before the session starts, the screen should expose:

- `Video source`
- `Audio source`
- `Phone mic device`
- `Connect glasses`
- `Start chat`

`Video source` options:

- `Phone camera`
- `Glasses camera`

`Audio source` options:

- `Phone mic`
- `Glasses mic`

`Phone mic device` should appear only when `Phone mic` is selected. It should allow choosing from currently available Android input devices such as:

- built-in mic
- wired mic/headset
- USB mic
- Bluetooth mic/headset when Android exposes it as a usable input source

`Connect glasses` should launch or resume the DAT pairing / registration / permission flow whenever glasses are required by either:

- selected video source
- selected audio source

`Start chat` should start the live capture session and the Hank session.

### Live Capture Modes

During a live session, Hank should have two explicit operating modes:

- `Interactive`
- `Read-only`

`Interactive`:

- current voice-driven back-and-forth behavior
- user speech triggers Hank responses directly
- normal follow-up listening behavior
- barge-in remains enabled

`Read-only`:

- Hank watches the scene and comments proactively
- Hank continues extracting information from mic input
- incoming speech does not interrupt the current spoken sentence
- extracted relevant speech updates the context for the next sentence
- commentary cadence is controlled by a configurable pause duration

### Info & Report

`Info & Report` becomes the post-session surface for the most recent completed capture session.

Its mental model should be:

"This is the structured output for the last finished capture session."

When the user enters this tab, the app should either:

- show the last completed session's generated report view, or
- prompt to end/finalize the active session first if capture is still running

Recommended structure:

- `Technical snapshot`
- `Concise diagnosis`
- `Checklist`
- `Inspection report`

`Technical snapshot` should show five to six compact key-value facts, such as:

- `Vehicle / Device`
- `Model / Year`
- `Primary issue`
- `Observed subsystem`
- `Likely fault`
- `Codes / signals`

The values should be generated from the last session's extracted facts and evidence.

`Concise diagnosis` should describe:

- what Hank believes is present
- what evidence supports that belief
- what remains uncertain

`Checklist` should be a short action-oriented list of next inspections or repair steps.

`Inspection report` should display:

- selected evidence images from the session
- short captions grounded in the session context
- optionally a lightweight session timeline later

### Settings

`Settings` should keep current global settings and add the controls needed by the new demo behavior.

Required additions:

- `Domain mode`
- `Speech recognition route`
- `Voice speed`
- `Read-only pause duration`

`Domain mode` options:

- `Car only`
- `General device`

`Car only` should constrain prompts and report generation to dealership / automotive work.

`General device` should widen the domain to cover:

- cars
- bicycles
- general broken devices / equipment

## Proposed State Model

The current model is centered on sessions plus inspection orders. The new model should be centered on capture sessions and report output.

Recommended new core types:

- `TopLevelTab`
- `CaptureConfig`
- `CaptureSourceConfig`
- `HankMode`
- `DomainMode`
- `ReadOnlyConfig`
- `CaptureSessionRecord`
- `ExtractedFact`
- `ReportSnapshot`

### Top-level types

`TopLevelTab`:

- `CAPTURE`
- `INFO_REPORT`
- `SETTINGS`

`HankMode`:

- `INTERACTIVE`
- `READ_ONLY`

`DomainMode`:

- `CAR_ONLY`
- `GENERAL_DEVICE`

### CaptureConfig

`CaptureConfig` should hold:

- selected video source
- selected audio source
- selected phone mic device
- preferred speech recognition route
- selected Hank mode
- read-only timing defaults

### CaptureSessionRecord

`CaptureSessionRecord` should hold:

- session id
- started/ended timestamps
- selected sources
- Hank mode used
- transcript excerpts
- Hank utterances
- extracted facts
- captured evidence
- final `ReportSnapshot`

### ReportSnapshot

`ReportSnapshot` should hold:

- technical header facts
- concise diagnosis text
- checklist items
- representative evidence images and captions

## Read-Only Mode Design

This mode should be modeled as a composition of three behaviors, not as one loose prompt trick.

### 1. Scene commentary engine

Responsible for:

- watching scene changes
- deciding when commentary is useful
- generating short technical spoken units

### 2. Passive speech harvesting

Responsible for:

- continuing STT while Hank is active
- aggressively filtering noise and irrelevant speech
- extracting only content relevant to the current repair context

### 3. Speech scheduling

Responsible for:

- queueing Hank utterances
- enforcing the pause between spoken units
- preventing incoming speech from interrupting the active sentence
- applying new context only to the next scheduled spoken unit

This separation is important. Without it, `read-only mode` will be hard to tune and will collapse back into ad hoc autonomous commentary.

## Source Selection Model

The UI should allow separate selection of video and audio source, but it must not imply that every combination has identical technical support.

The plan should explicitly support:

- phone camera + phone mic
- glasses camera + glasses mic
- glasses camera + phone mic
- phone camera + glasses mic if feasible

The UI should also expose constraints clearly:

- phone mic device selection is only meaningful when the app owns usable mic routing
- some Bluetooth mic routing combinations may vary by Android device behavior
- glasses audio routing may still depend on Android and Bluetooth profile limitations

The user should see valid combinations and practical guidance, not fake universal control.

## Critique of the Proposed Direction

The direction is strong, but there are several risks that should be addressed upfront.

### Risk 1: `Info & Report` is doing two jobs

It is both:

- a review surface
- a report surface

That is acceptable, but only if it is defined clearly as "the structured output of the last completed capture session." Otherwise it will become another overloaded detail screen.

### Risk 2: read-only mode is underspecified if implemented only as prompts

If `read-only mode` is implemented only by changing prompt text around the current stream logic, the behavior will remain brittle.

It needs explicit runtime state:

- mode switch
- commentary cadence
- relevance filtering
- queueing semantics

### Risk 3: old MPI data structures may fight the new product

If `RepairOrder` and MPI inspection screens remain the main organizing concept, the refactor will feel cosmetic rather than structural.

The app should become capture-session-first. Order-like report structure can still exist internally, but it should no longer define the UX.

### Risk 4: navigation and teardown should not be over-coupled

"Opening `Info & Report` means capture ended" is simple, but it may be too rigid.

Safer rule:

- ending capture refreshes `Info & Report`
- opening `Info & Report` during an active session prompts to finalize first

That preserves clarity without making navigation destructive by surprise.

## Recommended Improvements

### Improvement 1: rename `Read-only mode` in the UI

Internal term can stay, but the user-facing label should be clearer. Recommended candidates:

- `Scene Assist`
- `Commentary Mode`
- `Observe & Guide`

`Read-only` is technically meaningful but not especially intuitive in a product UI.

### Improvement 2: consider `Review & Report` instead of `Info & Report`

`Info & Report` is understandable, but slightly awkward. `Review & Report` better communicates:

- this is where the last session is reviewed
- this is where report output is formed

### Improvement 3: remove alternate hidden session-entry patterns

Capture should start from `Capture`, not from multiple unrelated surfaces. The current chat-only/session split is useful historically, but weakens the new IA.

### Improvement 4: make report generation progressive

Do not wait for a single big "report generation" step if the session data already supports partial output.

Recommended progression:

- immediate extracted fact header
- fast diagnosis draft at session end
- richer report refinement if needed

### Improvement 5: keep domain mode as a prompt-domain switch

`Car only` and `General device` should change prompt scope and report extraction behavior, not fork the entire app into two separate products.

## Implementation Plan

### Phase 1: Navigation and naming reset

Goals:

- replace four-tab shell with three tabs
- remove old top-level MPI framing from the primary UX

Work:

- replace `AppTab` with `CAPTURE`, `INFO_REPORT`, `SETTINGS`
- refactor `HankDaisyScaffold` to route to the new screens
- retire `SessionsHomeScreen` and `OrdersScreen` as top-level tabs
- keep existing secondary screens available only if needed for migration

### Phase 2: New settings model

Goals:

- move from light `AppSettings` to a demo-oriented configuration model

Work:

- replace `AppSettings` with a richer config model
- add `DomainMode`
- add `HankMode` defaults
- add `voiceSpeed`
- add `readOnlyPauseMs`
- keep current theme/accessibility settings

Recommended new store pieces:

- `CaptureConfig`
- `GeneralSettings`
- migration logic from existing stored JSON

### Phase 3: Capture screen redesign

Goals:

- make capture configuration explicit before live session start

Work:

- replace the current `ConvosScreen` with a real `CaptureScreen`
- add video source selector
- add audio source selector
- add phone mic device selector
- add connect-glasses action
- add start-chat action

### Phase 4: Session runtime refactor

Goals:

- support explicit `Interactive` vs `Read-only` behavior during live capture

Work:

- add `HankMode` to stream state
- expose live toggle in stream UI
- separate scene commentary from interactive follow-up logic
- add scheduler for read-only speech cadence
- store extracted relevant speech as deferred context updates

### Phase 5: Report pipeline

Goals:

- generate a structured output from the last completed session

Work:

- introduce `CaptureSessionRecord`
- collect extracted facts during session
- derive `ReportSnapshot` at session end
- build new `InfoReportScreen`

First version of `InfoReportScreen` should contain:

- technical snapshot header
- concise diagnosis
- checklist
- evidence gallery

### Phase 6: Migration away from inspection/order-first surfaces

Goals:

- reduce the app's dependence on MPI-specific objects for primary UX

Work:

- downgrade `RepairOrder` and `InspectionFinding` from core navigation concepts
- keep them only if needed as optional internal metadata
- migrate saved old sessions into the new capture-session model

### Phase 7: Prompt and extraction refinement

Goals:

- tune the relevance and usefulness of both live commentary and report output

Work:

- add dedicated prompts for:
  - interactive troubleshooting
  - read-only scene commentary
  - fact extraction
  - concise diagnosis
  - checklist generation
- keep the extraction path separate from the spoken-commentary path

## Recommended First Deliverable

The first implementation slice should be:

1. Replace the top-level tabs with `Capture / Info & Report / Settings`.
2. Introduce the new persisted config model.
3. Replace `ConvosScreen` with a real pre-session `CaptureScreen`.
4. Add the live `Interactive / Read-only` mode toggle to the stream UI.
5. Stub `Info & Report` with a last-session placeholder model, even before full report generation is complete.

This gives the app the correct structure early without waiting for the full report engine.

## Success Criteria

The refactor is successful when:

- the app clearly reads as a demo/evaluation tool rather than an MPI record app
- capture setup is explicit and understandable
- read-only mode is a coherent runtime behavior, not a prompt hack
- the last session produces a useful structured review/report surface
- the user can evaluate both live guidance quality and post-session extraction quality in one flow
