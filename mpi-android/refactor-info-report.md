You are working on an existing mobile app called Capture and Report / MPI.

The app lets a mechanic or technician start a capture session using smart glasses or a mobile camera. When the capture session ends, the app should generate a structured multi-point inspection report from the captured photos, videos, and transcript.

Your task is to refactor and improve the existing “Infer & Report” tab.

The current tab already has these sections:

1. Technical Snapshot
2. Concise Diagnosis
3. Inspection Report

Keep the first two sections mostly as they are, but improve the content and layout of everything that follows.

Do not rebuild the whole app. Do not change the Capture tab or Settings tab unless absolutely necessary. Focus on the Infer & Report tab UI, the inspection report data model, and the backend logic needed to create a structured MPI report after a capture session ends.

The app already uses OpenRouter / Gemini for inference. Continue using the existing model route where possible. Do not hard-code API keys. Use the existing networking/config pattern in the project.

The goal is to create a demo-ready multi-point inspection report that looks and behaves like a dealership MPI / courtesy inspection workflow, but optimized for a smartphone screen.

Core product goal:

After a capture session ends, the user should open “Infer & Report” and see a structured, auto-filled multi-point inspection report. The report should include:
- A compact technical snapshot
- A concise diagnosis / summary
- A multi-section inspection checklist
- Green / yellow / red status per inspection item
- Measurements where relevant, such as tire tread depth, tire pressure, brake pad thickness, battery voltage, etc.
- Expandable rows for comments and supporting evidence
- Automatically attached photos or video clips from the session
- A final narrative “story” describing the vehicle condition and items needing attention

Important UX principle:

This is for mechanics in a workshop. The UI must be compact, fast, readable, and not bulky. Use small but legible font sizes, tight spacing, simple cards, and collapsible sections. Avoid giant blocks of text unless the user expands an item. The default screen should give a quick overview. Details should be available on tap.

Preserve current top-level structure:

At the top of the Infer & Report tab, keep:

SECTION 1 — Technical Snapshot

Keep the same information currently shown, including:
- Capture date
- Mode
- Domain
- Video source
- Audio source
- Speech route

Format as a compact card.

Example:

TECHNICAL SNAPSHOT

Capture date: May 3, 2026 • 18:43
Mode: Read-only
Domain: Car only
Video source: Glasses
Audio source: Glasses
Speech route: OpenRouter

SECTION 2 — Concise Diagnosis

Keep the concise diagnosis card, but make the copy vehicle/MPI-oriented.

This section should contain a short mechanic-facing summary generated from the capture session.

Examples:

- “Vehicle inspection completed. Main attention items: front wiper blades worn, brake fluid condition flagged, front tires measured low at 4/32. No obvious engine bay leaks visible in captured footage.”
- “Capture quality was limited. Several inspection items could not be visually confirmed. Review the unchecked items before sending this report.”
- “No major visible issue detected from the available clips. Tires, fluids, brakes, and undercarriage items require manual confirmation where no clear evidence was captured.”

The diagnosis should avoid overclaiming. If the model did not see something clearly, say that clearly.

SECTION 3 — MPI Checklist / Inspection Report

Replace the current generic Inspection Report area with a compact dealership-style multi-point inspection checklist.

The UI should resemble a simplified MPI / courtesy inspection form:
- Sections are collapsible.
- Each section contains multiple inspection items.
- Each item row has three compact status boxes:
  - Green = OK / pass
  - Yellow = needs attention soon
  - Red = needs immediate attention / failed / unsafe / recommend now
- Add a fourth implicit state:
  - Grey / empty = not observed / not checked / insufficient evidence

Do not make the row too large. Each item should fit in one compact row by default.

Suggested row layout:

[status boxes]  Item label                  [value badge] [evidence icon] [chevron]

Example:

[■ ■ ■] Wiper blades              Red       📎  ˅
[■ ■ ■] LF tire tread              4/32"     📎  ˅
[■ ■ ■] Brake fluid                Yellow    📎  ˅

For the status boxes:
- Use three tiny square buttons or pill buttons.
- Green, yellow/orange, red.
- Only one should be selected at a time.
- The selected state should be visually obvious.
- If no status has been assigned, all boxes should be muted/grey.
- On press, allow the user to override the model’s selected status.

Each checklist item should be expandable.

When expanded, show:
1. Auto-generated comment
2. Technician note field
3. Supporting evidence thumbnails
4. Video clip preview if available
5. Timestamp or source reference
6. Confidence / needs review indicator if useful

The expanded detail should be compact.

Example expanded item:

WIPER BLADES — RED

Auto comment:
“Both front wiper blades appear worn/streaking. Recommend replacement.”

Technician note:
[ editable text field ]

Evidence:
[thumbnail image] [video clip 00:13–00:19]

Source:
Captured from glasses video • 00:13–00:19

Confidence:
High

The comment tree does not need to be complex for the first demo. Implement it as:
- Auto comment
- Technician note
- Advisor/customer wording

Example:

Auto comment:
“Front wiper blades appear worn.”

Technician note:
“Both front blades streaking.”

Advisor/customer wording:
“Recommend replacing both front wiper blades to improve visibility during rain.”

SECTION 4 — Final Inspection Story

At the bottom of the checklist, add an open text/narrative section.

Title: Inspection Story

This is a concise narrative summary of everything going on with the vehicle. It should be readable by a service advisor or customer.

It should:
- Summarize overall condition
- Highlight all yellow/red items
- Mention measurements where relevant
- Avoid mentioning items that were not observed unless needed
- Include cautious language when the model confidence is low
- Not hallucinate repairs or parts that are not supported by evidence

Example:

“Courtesy inspection completed. Most exterior lighting and basic safety checks appear OK from the available footage. The front wiper blades were flagged red and should be replaced. Front tire tread was recorded at 4/32, which is approaching replacement range and should be monitored or quoted depending on dealership policy. Brake fluid and coolant condition were flagged for review based on captured visuals, but technician confirmation is recommended before final customer approval.”

This text field should be editable.

The user should be able to manually edit:
- Item status
- Measurement values
- Comments
- Final story

MPI checklist content:

Use this checklist as the default for the demo. Store it as a structured constant/config file, not hard-coded directly into the UI components.

The checklist should have these sections:

1. Exterior / Lights / Safety

Items:
- Warning lights
- Headlights
- High beam headlights
- Parking lamps
- Brake and reverse lights
- License plate lights
- Turn signals
- Horn
- Wiper blades
- Windshield washer spray
- Windshield condition
- Exterior body damage
- Mirrors
- Door operation

2. Interior / Cabin

Items:
- Interior lights
- Seat belts
- HVAC operation
- Cabin air filter
- Dashboard indicators
- Parking brake
- Clutch operation, if applicable
- Brake pedal feel
- Steering wheel controls

3. Under Hood / Fluids

Items:
- Engine oil level
- Engine oil condition
- Coolant / antifreeze
- Brake fluid
- Power steering fluid
- Transmission fluid
- Windshield washer fluid
- Engine air filter
- Battery condition
- Battery terminals / cables
- Belts
- Radiator hoses
- Visible leaks
- Fuel system visual check

4. Tires / Wheels / Brakes

Items with measurements:
- Left front tire tread depth, unit: /32 inch
- Right front tire tread depth, unit: /32 inch
- Left rear tire tread depth, unit: /32 inch
- Right rear tire tread depth, unit: /32 inch
- Left front tire pressure, unit: PSI
- Right front tire pressure, unit: PSI
- Left rear tire pressure, unit: PSI
- Right rear tire pressure, unit: PSI
- Left front brake pad thickness, unit: mm
- Right front brake pad thickness, unit: mm
- Left rear brake pad thickness, unit: mm
- Right rear brake pad thickness, unit: mm

Other items:
- Tire wear pattern
- Sidewall condition
- Wheel damage
- Brake rotor condition
- Brake lines / hoses

5. Under Vehicle / Suspension

Items:
- Engine oil leak
- Transmission fluid leak
- Coolant leak
- Exhaust system
- Shocks / struts
- Suspension components
- Tie rod ends
- Ball joints
- Steering rack / gear
- CV boots
- Drive shaft boots
- Underbody damage
- Parking brake cable
- Fasteners / missing shields

6. Road Test / Symptoms

Items:
- Pulls / pulsation
- Balance / noise
- Brake noise
- Steering noise
- Engine noise
- Transmission shift quality
- Warning lights during operation
- General drivability concern

7. Needs Technician Review

Items:
- Unclear visual evidence
- Customer concern not verified
- Additional diagnostic time recommended
- Manual confirmation required

Measurements and status rules:

Some checklist items are simple status-only:
- Green / yellow / red / unknown

Some checklist items require a numeric measurement:
- Tire tread depth: integer or decimal, unit “/32”
- Tire pressure: integer, unit “PSI”
- Brake pad thickness: integer or decimal, unit “mm”
- Battery voltage: decimal, unit “V”
- Fluid level: low / ok / high, plus status
- Fluid condition: ok / dirty / contaminated / unknown, plus status

The UI should show measurement badges inline.

Example:
- “LF tire tread — 4/32”
- “RF tire pressure — 32 PSI”
- “Battery voltage — 12.4 V”
- “LF brake pad — 5 mm”

Use sensible demo thresholds, but make them configurable:

Tire tread:
- Green: >= 6/32
- Yellow: 4/32 to 5/32
- Red: <= 3/32

Brake pad thickness:
- Green: >= 6 mm
- Yellow: 4–5 mm
- Red: <= 3 mm

Battery voltage:
- Green: >= 12.4 V engine off
- Yellow: 12.1–12.3 V
- Red: < 12.1 V

Do not present these thresholds as universal truth. They are demo defaults and should be editable/configurable.

Evidence handling:

Each checklist item may have supporting evidence.

Evidence can be:
- Image
- Video clip
- Transcript snippet
- Audio transcript quote
- Manually added note

Each evidence object should include:
- id
- type: image | video | transcript | note
- uri
- thumbnailUri if applicable
- startTimeMs
- endTimeMs
- caption
- checklistItemIds
- confidence
- source: glasses | phone | transcript | manual

The expanded item row should show evidence thumbnails.

For video evidence:
- Show thumbnail
- Show short duration
- Show timestamp range
- Tapping opens playback or whatever preview mechanism already exists in the app

For image evidence:
- Show thumbnail
- Tapping opens larger image preview

Backend / inference logic:

When a capture session ends, run a multi-step report-generation pipeline.

Do not attempt to analyze everything in one giant model call. Use a staged process:

STEP 1 — Capture session closes

The app receives or creates a media manifest for the completed session.

The manifest should include:
- sessionId
- startTime
- endTime
- photos
- video clips
- transcript segments
- capture source
- audio source
- metadata already used in Technical Snapshot

Example structure:

{
  "sessionId": "session_123",
  "captureDate": "2026-05-03T18:43:00Z",
  "mode": "read-only",
  "domain": "car",
  "videoSource": "glasses",
  "audioSource": "glasses",
  "speechRoute": "OpenRouter",
  "media": [
    {
      "id": "clip_001",
      "type": "video",
      "uri": "...",
      "thumbnailUri": "...",
      "startTimeMs": 0,
      "endTimeMs": 18000
    },
    {
      "id": "photo_001",
      "type": "image",
      "uri": "..."
    }
  ],
  "transcriptSegments": [
    {
      "id": "transcript_001",
      "startTimeMs": 2500,
      "endTimeMs": 7200,
      "text": "Both front wipers are torn. Mark red."
    }
  ]
}

STEP 2 — Preprocess media

For video:
- Generate thumbnails
- Segment longer videos into short clips if needed
- Sample frames for VLM analysis
- Preserve timestamps
- Avoid huge payloads
- Prefer multiple small analysis calls over one massive call

For transcript:
- Align transcript segments to timestamps
- Use transcript as an important signal, especially when the tech says measurements out loud

Example:
“Left front tire is 4/32” should populate LF tire tread = 4/32 even if the VLM cannot visually read the tread gauge.

STEP 3 — VLM indexing pass

Send images/clips/frames to Gemini through OpenRouter.

The goal of the indexing pass is not to write the final report. The goal is to identify what is visible in each media item.

For each media item, ask the VLM to return structured JSON only.

The VLM should identify:
- Vehicle area visible
- Components visible
- Relevant checklist item IDs
- Observed condition
- Possible measurements
- Confidence
- Suggested evidence caption
- Whether the media is useful or irrelevant

Important:
The VLM must not hallucinate. If it cannot see the component clearly, it must return unknown / insufficient evidence.

Suggested VLM indexing prompt:

“You are analyzing media from an automotive multi-point inspection capture session.

Your job is to identify visible vehicle components and map them to a predefined MPI checklist.

Do not guess. Do not infer hidden conditions. Only report what is visible or explicitly stated in the transcript.

Return JSON only.

Checklist item IDs:
[insert checklist item IDs and labels here]

Media metadata:
[insert media id, timestamp, transcript if nearby]

Analyze the provided image/video frames.

Return:

{
  "mediaId": string,
  "isVehicleRelated": boolean,
  "vehicleArea": "exterior" | "interior" | "under_hood" | "under_vehicle" | "tires_brakes" | "road_test" | "unknown",
  "visibleComponents": string[],
  "observations": [
    {
      "checklistItemId": string,
      "status": "green" | "yellow" | "red" | "unknown",
      "measurementValue": number | null,
      "measurementUnit": string | null,
      "condition": string,
      "comment": string,
      "confidence": number,
      "evidenceCaption": string,
      "startTimeMs": number | null,
      "endTimeMs": number | null
    }
  ],
  "irrelevantReason": string | null,
  "needsTechnicianReview": boolean
}

Rules:
- Use green only when the visible evidence supports OK condition.
- Use yellow when the item needs attention soon or should be monitored.
- Use red when it appears failed, unsafe, worn beyond threshold, leaking, damaged, or explicitly marked by the technician.
- Use unknown when the item is not visible or cannot be assessed.
- If a measurement is spoken in the transcript, extract it exactly.
- If a measurement is not clearly visible or spoken, leave it null.
- Do not invent tire tread, brake pad, voltage, or PSI values.
- Keep comments short and mechanic-facing.
- Confidence must be between 0 and 1.
”

STEP 4 — Transcript extraction pass

Separately parse the transcript for explicit mechanic statements.

The transcript is often more reliable than the image for:
- Tire tread values
- Tire pressure
- Brake pad thickness
- Fluid comments
- “Mark this red/yellow/green”
- Recommendations
- Customer complaint
- Technician notes

Suggested transcript extraction prompt:

“You are extracting structured multi-point inspection findings from a mechanic’s spoken transcript.

Return JSON only.

Do not invent values. Extract only what is explicitly stated.

Map findings to the checklist item IDs.

Transcript:
[insert transcript]

Checklist:
[insert checklist item IDs]

Return:

{
  "findings": [
    {
      "checklistItemId": string,
      "status": "green" | "yellow" | "red" | "unknown",
      "measurementValue": number | null,
      "measurementUnit": string | null,
      "rawText": string,
      "comment": string,
      "confidence": number
    }
  ],
  "generalNotes": string[],
  "customerConcern": string | null
}

Rules:
- Phrases like 'mark red', 'failed', 'replace now', 'unsafe', 'leaking badly' imply red.
- Phrases like 'recommend soon', 'monitor', 'getting low', 'borderline' imply yellow.
- Phrases like 'good', 'okay', 'passes', 'no issue' imply green.
- Measurements must be copied exactly.
- If uncertain, use unknown and lower confidence.
”

STEP 5 — Merge observations into checklist draft

Create a report synthesis function that merges:
- VLM media observations
- Transcript findings
- Existing manual data, if any
- Default checklist structure

Merge rules:
- Transcript explicit values should usually override weak visual guesses.
- High-confidence VLM observations can pre-fill status.
- Low-confidence observations should not auto-select green/yellow/red. Instead mark the item as “needs review.”
- Red beats yellow, yellow beats green when multiple reliable observations conflict.
- Preserve all evidence, but rank the best evidence first.
- Attach each media item to the most relevant checklist items.
- Irrelevant media should not be attached.

Confidence thresholds:
- confidence >= 0.75: allow auto-fill
- 0.45 <= confidence < 0.75: mark as needs review
- confidence < 0.45: do not use for auto-fill, but optionally keep as low-confidence evidence hidden by default

STEP 6 — Generate comments and final story

After checklist statuses are populated, generate:
- Concise Diagnosis
- Per-item auto comments
- Advisor/customer wording
- Final Inspection Story

Suggested report synthesis prompt:

“You are generating a concise automotive MPI report from structured inspection findings.

Do not invent new findings. Use only the provided checklist data, measurements, comments, and evidence.

Produce:
1. Concise mechanic-facing diagnosis
2. Per-item comments for items marked yellow or red
3. Advisor/customer-friendly wording for items marked yellow or red
4. Final inspection story

Keep it concise.

Input:
[insert checklist draft with statuses, measurements, comments, evidence captions]

Return JSON only:

{
  "conciseDiagnosis": string,
  "itemCommentUpdates": [
    {
      "checklistItemId": string,
      "autoComment": string,
      "advisorWording": string
    }
  ],
  "inspectionStory": string,
  "needsReviewSummary": string[]
}

Rules:
- Mention all red items.
- Mention important yellow items.
- Mention measurements where available.
- Do not claim a repair is required unless the finding supports it.
- Use “recommend inspection/confirmation” when evidence is weak.
- Avoid long paragraphs.
- Sound like a practical dealership service report.
”

Frontend data model:

Create or adapt types similar to this:

type InspectionStatus = 'green' | 'yellow' | 'red' | 'unknown';

type EvidenceType = 'image' | 'video' | 'transcript' | 'note';

type EvidenceItem = {
  id: string;
  type: EvidenceType;
  uri?: string;
  thumbnailUri?: string;
  startTimeMs?: number;
  endTimeMs?: number;
  caption?: string;
  checklistItemIds: string[];
  confidence?: number;
  source: 'glasses' | 'phone' | 'transcript' | 'manual' | 'vlm';
};

type CommentTree = {
  autoComment?: string;
  technicianNote?: string;
  advisorWording?: string;
};

type InspectionItem = {
  id: string;
  label: string;
  sectionId: string;
  status: InspectionStatus;
  value?: number | string | null;
  unit?: string | null;
  valueType?: 'none' | 'number' | 'text' | 'select';
  possibleUnits?: string[];
  needsReview?: boolean;
  confidence?: number;
  comments: CommentTree;
  evidence: EvidenceItem[];
};

type InspectionSection = {
  id: string;
  title: string;
  description?: string;
  items: InspectionItem[];
  collapsedByDefault?: boolean;
};

type MPIReport = {
  sessionId: string;
  technicalSnapshot: {
    captureDate: string;
    mode: string;
    domain: string;
    videoSource: string;
    audioSource: string;
    speechRoute: string;
  };
  conciseDiagnosis: string;
  sections: InspectionSection[];
  inspectionStory: string;
  generatedAt: string;
  reportStatus: 'empty' | 'processing' | 'ready' | 'needs_review' | 'error';
};

UI components to create or refactor:

1. InferReportScreen
- Top-level screen
- Scroll view
- Contains TechnicalSnapshotCard
- Contains ConciseDiagnosisCard
- Contains MPIReportCard / Checklist
- Contains InspectionStoryCard

2. TechnicalSnapshotCard
- Compact card
- Keep existing content

3. ConciseDiagnosisCard
- Compact card
- Keep existing content style
- Use generated conciseDiagnosis

4. MPISectionAccordion
- Collapsible section
- Header shows section title and summary counts:
  - number green
  - number yellow
  - number red
  - number needs review
- Example:
  “Tires / Wheels / Brakes   6 OK • 2 Attention • 1 Red”

5. MPIItemRow
- Compact row
- Shows status selector
- Item label
- Measurement badge if present
- Evidence icon/count if evidence exists
- Needs review badge if applicable
- Chevron for expand/collapse

6. StatusSelector
- Three tiny buttons: green/yellow/red
- Also supports unknown/no selection
- Allows manual override

7. MeasurementBadge
- Shows value and unit
- Tappable/editable if practical
- Examples:
  “4/32”
  “32 PSI”
  “5 mm”
  “12.4 V”

8. EvidenceStrip
- Horizontal row of thumbnails
- Supports images and videos
- For videos, show duration or timestamp
- Keep compact

9. ExpandedItemDetails
- Shows auto comment
- Technician note field
- Advisor wording
- Evidence strip
- Confidence / source line

10. InspectionStoryCard
- Text area
- Editable
- Generated from report synthesis

Visual design:

Use the existing app design language:
- White cards
- Rounded corners
- Light grey background
- Dark primary text
- Muted section labels
- Small uppercase labels
- Teal/green accent if already used in the app
- Avoid heavy borders
- Use compact spacing

Suggested typography:
- Screen title: existing style
- Card titles: 11–13px uppercase muted
- Item labels: 13–14px
- Details/comments: 12–13px
- Evidence captions: 11–12px

Default collapsed behavior:
- Technical Snapshot: expanded
- Concise Diagnosis: expanded
- MPI sections: expanded only for sections with red/yellow/needs review items; otherwise collapsed
- Individual items: collapsed
- Inspection Story: expanded

Section summaries:

Each section header should show a compact summary.

Example:

Exterior / Lights / Safety
8 OK • 1 Attention • 1 Red

Under Hood / Fluids
3 OK • 2 Review

Tires / Wheels / Brakes
4 Measurements • 2 Attention

Loading states:

After capture ends, the Infer & Report tab may show:

“Generating inspection report...”
- Indexing captured media
- Extracting spoken findings
- Matching evidence to checklist
- Building report

Show progress if the app already supports it. Otherwise use a simple loading card.

Error states:

If VLM analysis fails:
- Still show the default checklist
- Set reportStatus = error or needs_review
- Show message:
  “Automatic report generation failed. You can still complete the checklist manually.”

If media is irrelevant:
- Example: camera pointed at desk, wall, or floor
- Concise diagnosis should say:
  “Capture did not contain enough vehicle evidence to complete the inspection. Point the camera at the vehicle areas and try another capture.”
- Checklist remains mostly unknown/grey
- No hallucinated green checks

Important safety and trust rules:

- Never mark an item green just because it was not seen.
- Unknown is better than fake OK.
- Never invent measurements.
- Only insert media evidence when the media actually supports that checklist item.
- If confidence is low, mark needs review.
- Keep all model-generated output editable by the technician.
- Avoid definitive safety claims unless there is strong evidence or explicit technician statement.

Demo behavior:

For the first demo, it is acceptable to:
- Use a fixed checklist
- Use generated mock/default checklist sections
- Use local report state
- Avoid DMS integration
- Avoid Shop-Ware / Honda VRC / Xtime integration
- Use simple local thumbnails
- Use a simplified video preview
- Use editable generated text rather than a fully finalized customer report

But the UI should feel like a real dealership MPI workflow.

Acceptance criteria:

1. The Infer & Report tab still shows Technical Snapshot and Concise Diagnosis at the top.
2. Below that, it shows a compact MPI checklist with collapsible sections.
3. Each checklist item has green/yellow/red status controls.
4. Items can be unknown / unchecked.
5. Tire tread, tire pressure, brake pad, and battery items support numeric values and units.
6. Each item can expand to show:
   - Auto comment
   - Technician note
   - Advisor wording
   - Evidence images/videos/transcript snippets
7. The report can be generated from completed capture session media.
8. Media is indexed with Gemini/OpenRouter and mapped to checklist item IDs.
9. Transcript findings are extracted and merged into the report.
10. The app does not hallucinate statuses or measurements when evidence is missing.
11. The final Inspection Story is generated and editable.
12. The UI remains compact enough for a smartphone screen.
13. Sections with red/yellow/needs-review items are easy to spot.
14. The app gracefully handles irrelevant captures, failed model calls, and empty media.

Implementation order:

1. Create the checklist config and data types.
2. Build the MPI checklist UI with static demo data.
3. Add expandable item rows and evidence thumbnails.
4. Add editable comments and final story field.
5. Create the report-generation service skeleton.
6. Add transcript extraction.
7. Add VLM media indexing via existing Gemini/OpenRouter route.
8. Add merge logic.
9. Add final report synthesis.
10. Connect completed capture sessions to generated reports.
11. Add loading/error/needs-review states.
12. Polish mobile spacing and typography.

Do not over-engineer. The first version should prove the workflow:

Capture session ends → model indexes media/transcript → checklist auto-fills → evidence attaches → mechanic reviews → report story is produced.