# Dev Notes: Client Orchestration Improvement Plan

This document explains how I think the current client orchestration works, what is good about it, what is risky about it, and how I would improve it.

It is written for a smart junior developer who can read Kotlin and Swift, but who may not yet have a strong feel for where product logic should live in a real-time multimodal app.

The goal is not to say "the current code is bad." The goal is to make the next round of changes easier, safer, and faster.

## What This Repo Is Doing

At a high level, `Hank & Daisy` is trying to do this:

1. Connect to Meta glasses through the DAT SDK.
2. Stream camera frames and audio.
3. Listen for user speech.
4. Decide when a frame or question is worth sending to the model.
5. Send the right context to Hank.
6. Speak the answer back quickly.
7. Save the session and connect it to repair workflow data.

That is a real-time system with several moving parts:

- device/session lifecycle
- camera stream lifecycle
- frame processing
- voice recognition
- barge-in handling
- LLM transport
- conversation memory
- UI state
- workflow state like repair orders and saved chats

When all of those concerns live too close together, the app can still work, but it becomes hard to reason about. Small changes start causing surprising regressions.

## Quick Opinion on the Current Orchestration

The Android app is already a strong prototype. It is event-driven, it has meaningful safety checks, and it clearly reflects the product idea.

The main issue is concentration of responsibility. `StreamViewModel` currently does too many jobs at once:

- DAT session setup and teardown
- stream creation
- frame handling and presentation
- local voice loop coordination
- barge-in behavior
- autonomous observation logic
- LLM request timing
- conversation history
- audio/TTS interaction

That is normal in an early version. It is also the main reason the orchestration feels heavy.

The biggest architectural inconsistency across the repo is that the clients do not all talk to Hank the same way:

- Android calls OpenRouter directly through `GeminiService`.
- iOS documentation describes a WebSocket agent flow, but the Swift client currently calls OpenRouter directly.
- The web app talks to a local Node proxy.
- There is also a Python agent server.

This means the behavior, prompt rules, error handling, and performance optimizations will drift unless we deliberately unify them.

## Why This Matters

In a normal CRUD app, you can get away with fat view models for a while.

In a real-time assistant, orchestration quality directly affects:

- latency
- battery usage
- API cost
- correctness
- how interruptible the assistant feels
- how often the app sends pointless requests

Example: if the user is looking at the same blurry engine bay for five seconds, we do not want to:

- keep copying bitmaps
- keep compressing JPEGs
- keep sending nearly identical frames
- keep paying for repeated model calls
- keep speaking redundant advice

A good orchestration layer is not just about code cleanliness. It is also a performance system.

## Current Strengths

Before talking about changes, it is worth naming what is already good.

### 1. The app already uses stateful, event-ish flows

The Android app is not built as nested callbacks. `WearablesViewModel`, `SessionViewModel`, and `StreamViewModel` use flows, jobs, and explicit state updates.

This is a good foundation because it means we can split responsibilities later without rewriting the whole app.

### 2. The current code already includes basic gating

There are already checks that suppress autonomous analysis when it is obviously the wrong time:

- while the app is already analyzing
- while the user is speaking
- while Hank is speaking
- when there is no conversation yet
- when the last turn happened too recently

Conceptually, that is the right instinct. The next step is to move this from scattered guard clauses into named modules.

### 3. Session teardown is treated seriously

The streaming code clearly reflects pain from real session lifecycle bugs, and that is a good thing. In wearables work, teardown bugs are usually where "works on my phone" demos turn into flaky products.

The code already shows awareness that you cannot casually start a new stream while the old one is still shutting down.

## Main Problems to Fix

### 1. `StreamViewModel` Is Too Busy

### Context

Right now `StreamViewModel` acts like a conductor, an engine, and half the orchestra.

That makes it easy to add one more behavior quickly, but harder to understand the overall system. When a class owns transport, business policy, UI-facing state, and scheduling, each new feature increases coupling.

### Why This Is Relevant

When too much state is centralized:

- bugs become hard to isolate
- tests become hard to write
- it becomes unclear where new logic belongs
- "fast fixes" tend to pile onto the same class

That slows development over time even if the first version shipped quickly.

### Example

Suppose we want to add "do not analyze frames that are too dark." If there is no clean boundary, the change might get added:

- inside the frame collector
- inside `autonomousObservation()`
- inside the LLM client
- inside the prompt builder

All four would technically work. Only one of them is the right architectural home.

### Suggested Improvement

Split responsibilities into focused coordinators:

- `StreamCoordinator`
- `PerceptionCoordinator`
- `ConversationCoordinator`
- `AudioCoordinator`
- `InferenceClient`

These do not need to be fancy. They just need clear ownership.

### Intended Behavior Improvement

After the split, when we add a new frame filter, the team should naturally know:

"This belongs in perception, before inference."

That kind of clarity is what makes a codebase feel fast to work in.

### 2. Client Policy Is Mixed Together With Transport and UI

### Context

Some product behavior is currently expressed as prompt text, some as Kotlin guards, some as audio-state logic, and some as transport choices.

This is common early on, but it makes the assistant's behavior hard to reason about as one coherent system.

### Why This Is Relevant

If the same policy is partially enforced in multiple places, you get subtle drift.

Example:

- Android may suppress a redundant request locally.
- iOS may send it anyway.
- the web app may structure the prompt differently.

Now the product "Hank" is not one assistant anymore. It is three cousins with similar vibes.

### Suggested Improvement

Move toward one canonical inference behavior layer, ideally on the backend side, while keeping cheap local gating on-device.

The client should still decide:

- when capture is happening
- whether a local event is worth escalating
- how to render/speak the response

But it should own less of the assistant's deeper reasoning rules.

### Intended Behavior Improvement

When product asks for "Hank should only advance one repair step at a time," that should be a single behavior rule that applies to Android, iOS, and web, not three separate rewrites.

### 3. The Repo Uses Too Many Different Hank Paths

### Context

Today there are multiple ways to talk to the model:

- Android direct to OpenRouter
- Swift direct to OpenRouter
- Node proxy to OpenAI
- Python agent server

Each path has slightly different prompts, failure modes, and optimization opportunities.

### Why This Is Relevant

This is a development speed problem even before it becomes a runtime problem.

Every time we improve:

- request shaping
- retry behavior
- caching
- moderation/filtering
- logging
- prompt versioning

we have to make the same conceptual change several times.

### Suggested Improvement

Pick one primary Hank backend path.

My recommendation:

- keep cheap filters on-device
- route final model calls through one backend service

That backend can be Python or Node, but the system should clearly prefer one.

### Intended Behavior Improvement

If we later add "drop image inference when the frame is too blurry," Android and iOS can still do a local blur check, but the final request format, prompt version, and logging behavior should be shared.

## Proposed Module Additions

The rest of this document describes the modules I would add, where they should sit, and why they matter.

### 1. `FrameGate`

### What It Is

`FrameGate` is a lightweight local module that decides whether a frame is worth further processing.

This module should run before expensive work like:

- full bitmap copy
- JPEG compression
- LLM upload
- autonomous analysis

### Why This Is Relevant

This is probably the single highest-value performance improvement in the current architecture.

In a camera system, the cheapest frame is the one you never process.

If two frames are nearly identical, or one is obviously unusable, sending them downstream wastes:

- CPU
- memory bandwidth
- battery
- model requests
- latency budget

### What It Should Filter

- near-duplicate frames
- very blurry frames
- very dark or overexposed frames
- frames that arrive too quickly after the last accepted frame
- unstable frames during camera movement

### Example

Imagine the user is walking from the toolbox back to the car.

Without `FrameGate`:

- the app sees lots of motion
- scene-change logic may keep waking up
- bad frames keep reaching later stages

With `FrameGate`:

- highly unstable frames are dropped early
- later logic only sees candidate frames once the camera stabilizes

That makes the system feel calmer and faster.

### Where To Add It

Best insertion point: immediately after raw frame receipt, before the system starts heavier frame work in the stream path.

Conceptually:

`DAT video frame -> FrameGate -> presentation/perception`

### Implementation Notes

Start simple. Do not begin with ML.

Version 1 can use:

- a tiny grayscale thumbnail
- average brightness
- edge sharpness estimate
- timestamp cooldown

Simple heuristics are enough to remove a lot of junk.

### 2. `SceneRelevanceScorer`

### What It Is

A local module that decides whether a stable frame is relevant enough to justify a model call.

This is different from `FrameGate`.

- `FrameGate` answers: "Is this frame technically usable?"
- `SceneRelevanceScorer` answers: "Even if usable, is it worth sending right now?"

### Why This Is Relevant

The current code already avoids obvious bad times to analyze, but it does not yet have a named concept for "this is not meaningfully new."

That matters because many technically valid frames are still operationally useless.

### Example

Suppose Hank just said:

"Move closer to the belt tensioner and let me know when you're there."

If the next stable frame still shows the whole engine bay from far away, the frame is not useless in a camera sense, but it is not relevant to the conversational goal.

That should usually not trigger a new inference call.

### What It Should Consider

- similarity to last analyzed frame
- whether the scene meaningfully changed
- whether the conversation is waiting for a specific visual confirmation
- whether enough time passed since the last relevant visual check

### Where To Add It

Right before autonomous analysis requests.

Conceptually:

`stable accepted frame -> SceneRelevanceScorer -> autonomousObservation() -> backend`

### Intended Behavior Improvement

Hank speaks less often, but when he does speak it feels more useful.

That is the target. Not silence for its own sake. Better timing.

### 3. `SpeechIntentFilter`

### What It Is

A local module that sits between speech recognition output and model dispatch.

Its job is to distinguish:

- real user questions
- local control commands
- filler/partial speech
- accidental microphone noise

### Why This Is Relevant

Speech recognition is noisy. If every recognized string becomes a model request, the system feels eager in the wrong way.

You want the assistant to feel responsive, not jumpy.

### Example

These should probably stay local:

- "stop"
- "repeat that"
- "go back"
- "hold on"

These should probably become model turns:

- "what am I looking at here"
- "does this belt look cracked"
- "what should I test next"

These may need suppression:

- "uh"
- "wait"
- partial repeated fragments from interruption

### Where To Add It

Between `VoiceCommandManager` output and the logic that creates a user turn or model request.

Conceptually:

`speech recognizer -> SpeechIntentFilter -> local action OR model turn`

### Intended Behavior Improvement

The model gets fewer junk requests, and the assistant feels more deliberate.

This also improves latency because local commands can execute instantly without waiting on a network round trip.

### 4. `TurnDebouncer`

### What It Is

A small module that suppresses duplicate or near-duplicate turns within a short window.

### Why This Is Relevant

In voice systems, the same idea often appears multiple times:

- the user repeats themselves
- speech recognition emits slightly different versions
- barge-in restarts the listen loop and catches a partial repeat

Without debouncing, the model may answer the same question twice.

### Example

The recognizer might emit:

- "what is this connector"
- "what's this connector"

within two seconds while the user is still repositioning.

Those are functionally the same request. A debouncer can collapse them.

### Where To Add It

After `SpeechIntentFilter`, before the model request is created.

### Intended Behavior Improvement

The conversation feels less repetitive, and you spend less money on near-identical calls.

### 5. `PromptContextBuilder`

### What It Is

A module that assembles the model input in a predictable way.

It should answer:

- which conversation turns are included
- whether an image is attached
- whether this is a user turn or an autonomous visual check
- what mode the assistant is in

### Why This Is Relevant

Right now prompt composition is partly embedded in transport clients and partly implicit in calling code.

That makes it easy for small changes to accidentally alter assistant behavior.

### Example

An autonomous observation should probably not be built the same way as a direct user question.

These are different intentions:

- User turn: "Do you see a leak?"
- Autonomous visual check: "The user moved the camera; only react if there is a meaningful change relevant to the current step."

A dedicated context builder makes that difference explicit.

### Where To Add It

Immediately before the transport layer.

Conceptually:

`Conversation state + event type + optional frame -> PromptContextBuilder -> backend payload`

### Intended Behavior Improvement

Prompt structure becomes inspectable, testable, and consistent across clients.

That makes future tuning much safer.

### 6. `InferenceScheduler`

### What It Is

A module that decides when a model call is allowed to start.

This is not the same as content filtering. It is request scheduling.

### Why This Is Relevant

Even when a request is valid, it may not be the right moment to send it.

For example:

- the user is still speaking
- Hank just started TTS
- another inference call is already in flight
- the current request is lower priority than a new user question

Without scheduling, the system can feel clogged or out of order.

### Example

If an autonomous visual check is queued, but the user suddenly asks:

"Wait, is that connector burnt?"

the system should prioritize the direct user question over the background observation.

### What It Should Support

- priority rules
- cancellation of stale background tasks
- cooldown windows
- single-flight behavior for expensive calls

### Where To Add It

Between request creation and transport.

Conceptually:

`candidate inference request -> InferenceScheduler -> backend`

### Intended Behavior Improvement

The assistant feels more interruptible and more human because urgent user turns win over background chatter.

### 7. Event Types or a Reducer-Style State Machine

### What It Is

A more explicit orchestration layer built around named events and state transitions.

This does not need to be a huge framework. It can just be a clear internal model.

### Why This Is Relevant

Right now some transitions are encoded in separate coroutines and state collectors. That works, but the overall behavior graph is hard to hold in your head.

Named events make the system easier to debug.

### Example Event List

- `FrameReceived`
- `FrameAccepted`
- `SceneSettled`
- `WakeWordDetected`
- `UserQuestionReady`
- `UserCommandDetected`
- `InferenceStarted`
- `InferenceFinished`
- `TtsStarted`
- `TtsEnded`
- `SessionStopping`

### Why This Helps a Junior Developer

When you are new to a codebase, event names are easier to reason about than "this collector launches that job which updates this flag."

The system becomes easier to trace in logs and easier to test with fake inputs.

### Intended Behavior Improvement

You can answer questions like:

"Why did Hank speak just now?"

by inspecting a clean event chain rather than guessing which coroutine branch fired.

## Suggested Coordinator Split

Here is a practical split that keeps the code understandable without over-engineering it.

### 1. `StreamCoordinator`

Owns:

- DAT device session
- DAT stream start/stop
- terminal state handling
- teardown sequencing

Why relevant:

The session lifecycle is a domain of its own. It should not share ownership with conversation policy if we want to avoid fragile restart bugs.

### 2. `PerceptionCoordinator`

Owns:

- frame receipt
- `FrameGate`
- `SceneRelevanceScorer`
- stable-frame sampling for autonomy

Why relevant:

Visual signal quality and visual relevance are not the same as UI state or transport state. Keeping them together gives the team one place to improve camera-side performance.

### 3. `AudioCoordinator`

Owns:

- wake word flow
- manual listen flow
- `SpeechIntentFilter`
- barge-in behavior
- TTS interaction rules

Why relevant:

The audio loop is timing-sensitive and very easy to break by accident. Grouping the audio responsibilities helps preserve the interruptible feel of the product.

### 4. `ConversationCoordinator`

Owns:

- turn history
- user vs autonomous turn type
- `TurnDebouncer`
- session memory window
- request intent creation

Why relevant:

Conversation state is not the same thing as transport state. The app should be able to reason about "what the conversation wants next" even if the backend transport changes.

### 5. `InferenceClient`

Owns:

- backend transport
- serialization
- retry behavior
- provider-specific details

Why relevant:

Transport details should be boring. If they are mixed with product logic, changing providers or request formats becomes much harder than it needs to be.

## Current File Touchpoints

This section maps the conceptual changes above to the files that currently matter most.

These are not permanent homes for every feature. They are the places you will likely touch first while refactoring.

### Android Files

### `android/app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/stream/StreamViewModel.kt`

This is the main orchestration hotspot today.

Why this file matters:

- it owns stream lifecycle
- it owns a lot of inference timing
- it owns audio loop coordination
- it owns autonomous observation

Relevant changes here:

- insert `FrameGate` near the frame handling path
- insert `SceneRelevanceScorer` before autonomous analysis
- move conversation/inference/audio responsibilities out over time

Conceptual reason:

This file is where the system currently feels "thick." Refactoring it gradually is the clearest way to reduce coupling without losing the working behavior.

### `android/app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/stream/GeminiService.kt`

This is the Android transport client today.

Why this file matters:

- request payload shape lives here
- prompt composition partly lives here
- provider details live here

Relevant changes here:

- eventually shrink this into a more boring transport layer
- move prompt assembly to `PromptContextBuilder`
- make it easier to swap direct provider calls for a canonical backend

Conceptual reason:

Transport should not quietly become the place where product logic accumulates. If it does, changing providers later becomes more dangerous than it should be.

### `android/app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/chat/ChatOnlyViewModel.kt`

This is useful because it shows a second conversation path without DAT streaming.

Why this file matters:

- it has voice behavior
- it has image attachment behavior
- it has turn creation logic

Relevant changes here:

- reuse `SpeechIntentFilter`
- reuse `TurnDebouncer`
- eventually reuse the same `ConversationCoordinator` ideas as the streaming path

Conceptual reason:

If a smart fix is valid for both stream mode and chat-only mode, that is a good sign it belongs in a shared module instead of one screen-specific class.

### iOS Files

### `ios/HankDaisy/HankDaisy/HankDaisy/Agent/AgentClient.swift`

This is the Swift-side inference client.

Why this file matters:

- it currently talks directly to OpenRouter
- it carries prompt and request logic
- it does not fully match the iOS README architecture description

Relevant changes here:

- either align it with the documented backend path
- or update the docs and make the direct path an intentional choice
- eventually keep this thin if the backend becomes canonical

Conceptual reason:

When docs and code disagree, future developers lose time before they even begin the real task. Fixing the mismatch is a productivity improvement.

### Backend/Shared Files

### `agent/server.py`

This is the Python WebSocket agent server.

Why this file matters:

- it is already a candidate canonical backend
- it has conversation session boundaries
- it streams results back incrementally

Relevant changes here:

- decide whether this becomes the main backend path
- if yes, centralize prompt versioning and inference policy here

Conceptual reason:

A backend is most valuable when it is clearly "the place where shared assistant behavior lives," not just one more optional path.

### `ui/server/hank-proxy.mjs`

This is the web HTTP proxy.

Why this file matters:

- it already keeps provider keys off the browser
- it already acts like a backend boundary
- it currently represents a second backend path

Relevant changes here:

- either fold its responsibility into the canonical backend
- or deliberately make it the canonical backend

Conceptual reason:

Having two server-side gateways is fine for experiments, but not ideal as a long-term product shape unless they clearly have different responsibilities.

## Where To Put the Fastest Filters

If the goal is to make the system faster, the best improvements are the ones that remove work early.

### Priority 1: Before Bitmap/Model Work

Add `FrameGate` as early as possible.

Why:

This cuts downstream load before the expensive parts begin.

Expected wins:

- fewer frame copies
- fewer compressions
- fewer model calls
- lower CPU/battery usage

### Priority 2: Before Autonomous Inference

Add `SceneRelevanceScorer`.

Why:

Not every stable frame deserves a model turn.

Expected wins:

- fewer irrelevant visual checks
- less Hank chatter
- lower latency for important turns

### Priority 3: Before Speech Turns Become Model Turns

Add `SpeechIntentFilter` and `TurnDebouncer`.

Why:

A lot of speech noise can be removed before it costs a request.

Expected wins:

- fewer junk requests
- faster reaction to local commands
- less repetition

### Priority 4: Before Transport

Add `InferenceScheduler`.

Why:

Once content is valid, timing still matters.

Expected wins:

- fewer stale background requests
- better prioritization
- smoother interruptions

## Concrete Refactor Order

This is the order I would actually do the work in.

### Phase 1: Add Filters Without Big Structural Change

1. Add `FrameGate`
2. Add `SpeechIntentFilter`
3. Add `TurnDebouncer`
4. Add lightweight logging around suppressed events

Why this order:

These changes improve performance quickly and do not require a big rewrite first.

### Phase 2: Extract Focused Coordinators

1. Extract `InferenceClient`
2. Extract `ConversationCoordinator`
3. Extract `AudioCoordinator`
4. Extract `PerceptionCoordinator`
5. Leave `StreamCoordinator` as the final shape of the remaining stream lifecycle code

Why this order:

It peels off the least DAT-specific responsibilities first and reduces the risk of breaking stream lifecycle code too early.

### Phase 3: Unify Backend Path

1. Choose one canonical Hank backend
2. Update Android/iOS/web to use it consistently
3. Move shared prompt/versioning behavior there
4. Keep only cheap gating on-device

Why this order:

Once the client is cleaner, backend unification becomes easier because the transport boundary is clearer.

### Phase 4: Add Better Testing

Add tests for:

- duplicate speech turn suppression
- frame suppression heuristics
- autonomous observation gating
- priority scheduling
- session teardown/restart sequencing

Why relevant:

Real-time systems become scary when you cannot tell whether an optimization changed behavior. These modules are worth testing because they are mostly deterministic.

## Good Examples of Intended Behavior

These examples make the target behavior more concrete.

### Example A: User Walks Across the Shop

Current risk:

The system sees motion, may keep sampling junk frames, and may eventually do unnecessary visual work.

Improved behavior:

- `FrameGate` rejects unstable walking frames
- `SceneRelevanceScorer` waits for a stable, relevant view
- no model call happens until the camera settles on something meaningful

Why this matters:

The assistant feels patient instead of hyperactive.

### Example B: User Says "Stop, repeat that"

Current risk:

The phrase may become a normal model turn instead of a local control action.

Improved behavior:

- `SpeechIntentFilter` classifies "stop" and "repeat that" as local commands
- TTS is stopped or replayed locally
- no model call is sent

Why this matters:

Local commands should feel immediate.

### Example C: User Repeats the Same Question Twice

Current risk:

The model may answer the same question twice, especially after an interrupt/resume cycle.

Improved behavior:

- `TurnDebouncer` notices near-duplicate intent
- the second request is suppressed or merged

Why this matters:

The app feels smarter without needing a smarter model.

### Example D: Autonomous Observation vs Direct User Question

Current risk:

A background visual check and a real user question compete equally.

Improved behavior:

- `InferenceScheduler` cancels or downgrades the background task
- the direct user question is sent first

Why this matters:

The assistant should always feel like it is listening to the person, not to its own background loops.

## Notes for a Junior Developer

If you are implementing these changes, keep these principles in mind:

#### 1. Do not optimize with complexity first

Start with simple heuristics:

- time windows
- duplicate suppression
- blur thresholds
- brightness checks

These go a long way.

#### 2. Name the decision points

A named module like `FrameGate` is better than one more `if` statement buried in a coroutine.

That does not just improve style. It creates a place where future developers know to look.

#### 3. Separate "is valid" from "is worth it"

This is an important mental model.

- A frame can be valid but not worth sending.
- A speech snippet can be recognized but not worth turning into a model request.

That distinction is the heart of efficient orchestration.

#### 4. Keep transport boring

Provider details, API payloads, and retry handling should feel mechanical.

When transport becomes emotionally significant in the codebase, it usually means too much product logic leaked into it.

#### 5. Prefer observability over guesswork

When you add these modules, log the reasons things are dropped:

- `frame_rejected_blurry`
- `frame_rejected_duplicate`
- `speech_rejected_partial`
- `turn_rejected_duplicate`
- `autonomy_skipped_irrelevant`

That makes tuning much easier than staring at behavior and guessing.

## Recommended First Tickets

If I were turning this into actual development tickets, I would start here:

1. Create `FrameGate` and wire it into the Android stream path.
2. Create `SpeechIntentFilter` for Android voice input.
3. Create `TurnDebouncer` for recent user turns.
4. Extract `InferenceClient` from transport code.
5. Write a short architecture note choosing the canonical Hank backend path.

These five tickets are enough to improve both performance and code clarity without forcing a giant rewrite.

## Final Recommendation

The current orchestration is a good prototype shape, but not yet a good long-term system shape.

The most valuable direction is:

- filter earlier
- name the decisions
- shrink `StreamViewModel`
- unify backend behavior

If we do only one thing first, it should be `FrameGate`.

That is the highest-probability, lowest-drama improvement for both runtime efficiency and future code health.
