# Smart-Glasses Multi-Point Vehicle Inspection Tool

## Context, Product Rationale, and Detailed Build Prompt

This document provides two things:

1. A practical explanation of **multi-point vehicle inspection**, also called digital vehicle inspection, video MPI, electronic vehicle health check, or multi-part inspection in some discussions.
2. A detailed, reusable prompt for designing and building a **smart-glasses-based inspection tool** that records technician video/audio and turns it into a structured web report with playable proof clips.

---

# Part 1 — Context: What Is Multi-Point Vehicle Inspection?

## 1. What dealerships are doing

A multi-point vehicle inspection is a structured inspection performed by a dealership technician during a service visit. The technician checks the vehicle across several systems, captures evidence, records measurements, and produces a report for the customer, dealership, and sometimes the manufacturer or warranty administrator.

The inspection usually covers areas such as:

- Tires and wheels.
- Brakes.
- Fluids.
- Battery and charging system.
- Filters.
- Lights.
- Wipers.
- Suspension and steering.
- Engine bay.
- Undercarriage.
- Interior and exterior condition.
- Diagnostic trouble codes.
- Recalls, campaigns, or warranty-related items.
- EV or hybrid systems, when relevant.

The inspection is not just a checklist. In modern dealership workflows, it increasingly includes **photos, videos, measurements, customer-facing explanations, approval links, and manufacturer-facing documentation**.

---

## 2. Why dealerships do it

Dealerships perform multi-point inspections for several overlapping reasons.

### 2.1 Customer trust

Customers often cannot see the worn brake pad, damaged tire, leak, dirty filter, or cracked bushing. Video and photo evidence makes the recommendation more credible.

Instead of the advisor saying:

> “You need rear tires.”

The customer can see:

> “Your rear tires are at 3/32 tread depth. Here is a short video showing the measurement.”

This makes the interaction more transparent and reduces the feeling that the dealership is simply upselling.

### 2.2 Repair approvals

A good inspection report helps the customer make a decision faster. The report connects the problem, the proof, the recommendation, the price, and the approval button.

The goal is not merely to show a video. The goal is to convert a technician’s finding into a clear customer decision.

### 2.3 Warranty and manufacturer documentation

When a repair is covered by warranty, the dealership may need to prove to the manufacturer that:

- The problem was real.
- The diagnosis was valid.
- The failure was documented.
- Measurements were taken.
- The repair followed OEM procedure.
- The claim is eligible for reimbursement.

For this audience, the report needs to be more technical and evidence-oriented.

### 2.4 Liability and dispute protection

Inspections create a record of the vehicle’s condition.

This matters when there are disputes such as:

- “That scratch was not there before.”
- “My tire was not damaged when I dropped the vehicle off.”
- “You told me my brakes were fine last time.”
- “I did not approve that repair.”

A time-stamped inspection report with video evidence helps protect both the customer and the dealership.

### 2.5 Retention and future service

Declined work can be saved and revisited later.

Example:

> Customer declined rear tires at 58,240 miles. Rear tires measured 3/32. Recheck at next visit.

This turns the inspection into a long-term service relationship tool, not just a same-day sales tool.

---

## 3. The stakeholders

A good inspection system serves several audiences at once.

### Customer

The customer wants:

- Simple explanations.
- Proof.
- Urgency level.
- Price.
- Clear approve/decline options.
- Confidence that the dealership is being honest.

### Technician

The technician wants:

- Minimal typing.
- No workflow disruption.
- A fast way to document what they already see.
- A way to record measurements and observations without stopping constantly.
- Protection from miscommunication.

### Service advisor

The advisor wants:

- A clean report to send to the customer.
- Technician findings translated into customer-friendly language.
- Evidence clips that support recommendations.
- A fast path to build estimates and capture approvals.
- Status tracking: viewed, approved, declined, waiting.

### Dealership manager

The manager wants:

- Inspection completion rates.
- Video attach rates.
- Approval rates.
- Additional services sold per repair order.
- Declined service value.
- Technician/advisor performance.
- Warranty claim quality.
- Customer satisfaction.

### Manufacturer / OEM / warranty administrator

The manufacturer or warranty administrator wants:

- Technical documentation.
- VIN and mileage.
- Diagnostic evidence.
- Measurements.
- Proof clips or images.
- Labor operation support.
- Warranty eligibility evidence.
- A clean audit trail.

---

## 4. What belongs in a strong inspection report

A strong inspection report should include:

### Vehicle and repair order identity

- VIN.
- Year, make, model, trim.
- Mileage.
- Repair order number.
- Customer name or customer ID.
- Advisor.
- Technician.
- Dealer/store.
- Inspection date and time.

### Customer concern

Examples:

- “Customer states brake squeal when reversing.”
- “Customer requests 30,000-mile service.”
- “Customer reports check engine light.”
- “Customer requests inspection before road trip.”

### Findings

Each finding should include:

- System, such as brakes, tires, fluids, battery, suspension, etc.
- Component.
- Location.
- Technician observation.
- Measurement.
- Severity.
- Recommendation.
- Evidence clip or image.
- Customer-friendly explanation.
- OEM/warranty technical note, when needed.

### Measurements

Typical measurements include:

- Tire tread depth.
- Tire pressure.
- Brake pad thickness.
- Rotor thickness or runout.
- Battery health, voltage, cold cranking amps, or state of health.
- Alignment readings.
- Fluid level or condition.
- Diagnostic trouble codes.
- EV/hybrid battery or coolant loop data.

### Evidence

Evidence may include:

- Short video clip.
- Still image.
- Transcript excerpt.
- Measurement shown in frame.
- Technician voice narration.
- Scan tool report.
- Battery tester report.
- Alignment machine printout.

### Severity

A simple green/yellow/red system works well.

- **Green:** OK now.
- **Yellow:** Monitor or service soon.
- **Red:** Urgent, safety-related, failure-related, or warranty-sensitive.

### Customer approval

For customer-facing reports, each recommendation should be connected to:

- Price.
- Parts and labor summary.
- Estimated completion time.
- Approve button.
- Decline button.
- Ask-a-question option.
- Approval timestamp.
- Report/estimate version.

---

## 5. Why current workflows are clumsy

The current workflow in many dealerships is awkward because it often relies on technicians using phones, manually taking photos, typing notes, and sending media through systems that are not deeply connected to the repair order.

### 5.1 Orphaned photos and videos

A photo is only useful if it is attached to the right:

- VIN.
- Repair order.
- Customer.
- Technician.
- Service line.
- Finding.
- Measurement.
- Timestamp.

Without this metadata, the dealership ends up with a pile of random media files.

### 5.2 Technicians do not want to type

Technicians work in noisy, dirty, time-sensitive service bays. They may be wearing gloves, holding tools, standing under a lift, or working around oil, grease, and poor lighting.

Stopping to unlock a phone, take a photo, type a note, upload media, and tag it correctly is slow and annoying.

### 5.3 Measurements are structured data

A note like:

> “Rear tires low.”

is weak.

A useful finding is:

> “Rear tires measured 3/32 tread depth with visible outer-edge wear. Recommend replacing rear tires and performing alignment.”

The system needs to capture units, thresholds, location, evidence, and recommendation. Manual typing makes this inconsistent.

### 5.4 Different stakeholders need different language

The customer needs simple language.

The OEM or warranty administrator needs technical documentation.

The technician should not have to write both versions manually.

### 5.5 Advisors become the bottleneck

The advisor often has to:

- Review technician notes.
- Interpret photos.
- Build the estimate.
- Contact the customer.
- Explain urgency.
- Capture approval.
- Update the repair order.
- Follow up with the technician.

A poor inspection tool simply moves the work from the technician to the advisor. A good tool reduces work for both.

### 5.6 Approval needs an audit trail

If a customer approves work, the dealership needs to know exactly:

- What was approved.
- What price was shown.
- Which estimate version was approved.
- When approval happened.
- Who approved it.
- Through which communication channel.

Random text messages and manual notes are not enough for a clean, defensible workflow.

---

## 6. The smart-glasses opportunity

Smart glasses can make the inspection workflow much smoother because they allow the technician to record hands-free from their point of view.

The technician can work naturally and speak naturally:

> “Front left brake pad is about 2 millimeters. Recommend front brake replacement.”

The system can then:

1. Record the inspection.
2. Transcribe the audio.
3. Detect automotive terms, measurements, and severity clues.
4. Find the relevant timestamp.
5. Cut a short evidence clip.
6. Generate a finding card.
7. Produce a web report.
8. Let the advisor review and send it to the customer.

The fastest practical MVP is not full visual AI. The fastest path is:

> **Use technician narration plus time-coded video evidence to generate structured findings and proof clips.**

Visual AI can be added later for measurement assistance, quality control, redaction, and component detection.

---

# Part 2 — Detailed Build Prompt

Copy and paste the following prompt into a product/engineering AI, give it to a development team, or use it as the foundation for a product requirements document.

---

# Master Prompt: Build a Smart-Glasses Multi-Point Vehicle Inspection Tool

You are a senior product architect, automotive fixed-operations workflow expert, UX designer, AI product lead, and full-stack technical architect.

Design a complete product for a **smart-glasses-based multi-point vehicle inspection tool** for car dealerships.

The system should allow a mechanic or technician to wear smart glasses during a vehicle inspection, record video and audio hands-free, upload the recording through an Android app, automatically process the video, extract relevant inspection evidence, and generate a web-based inspection report with embedded video proof clips.

The final output should be a detailed, shareable web page that multiple stakeholders can access, including:

1. The dealership service advisor.
2. The technician.
3. The customer/client.
4. The dealership manager.
5. The manufacturer/OEM or warranty administrator, when relevant.

The report should not merely be a video archive. It should be a structured, evidence-backed inspection report with conclusions, measurements, recommendations, severity ratings, and direct playable clips showing the proof for each finding.

---

## 1. Product Vision

Build a system that transforms a technician’s normal inspection workflow into a structured digital report without forcing the technician to manually type everything.

The core product promise is:

> “The technician performs the inspection naturally while wearing smart glasses. The system records what they see and say, then converts that video into a clear, structured report with proof clips, recommendations, and stakeholder-specific summaries.”

The product should solve these dealership problems:

- Manual photo-taking is clumsy.
- Technicians do not want to stop work to type notes.
- Service advisors waste time translating technician comments into customer-friendly explanations.
- Customers distrust repair recommendations without proof.
- OEM/warranty documentation often lacks clear evidence.
- Photos and videos are often not attached correctly to the VIN, repair order, or line item.
- Inspection quality is inconsistent across technicians.
- Approvals are delayed because customers need explanation.
- Declined work is not always tracked properly for future follow-up.

The product should make the inspection process more trustworthy, faster, better documented, and easier to monetize.

---

## 2. Core Use Case

A customer brings a vehicle to a dealership for service.

The service advisor creates or opens a repair order.

The technician receives the job and puts on smart glasses.

The smart glasses record the inspection from the technician’s point of view. The technician can optionally speak naturally while working, for example:

> “Front left tire is at 3/32, outer edge wear visible. Recommend two front tires and alignment.”

The Android app manages recording, metadata, upload, and status.

The backend processes the video and audio.

The system automatically creates an inspection report with:

- Vehicle identity.
- Inspection checklist.
- Findings.
- Severity levels.
- Measurements.
- Technician comments.
- Customer-friendly explanations.
- OEM/warranty-friendly technical notes.
- Embedded short video clips showing each issue.
- Still thumbnails.
- Suggested recommendations.
- Estimated urgency.
- Optional pricing and approval sections.

The customer receives a web link and can open the report, watch specific proof clips, and approve or decline recommended work.

---

## 3. Key Product Principle

Do not force users to watch a long inspection video.

The raw video may be 5, 10, or 20 minutes long, but the final report should extract the useful moments.

For each finding, the web page should provide:

- A short title.
- The affected vehicle area.
- The severity.
- The measurement, if available.
- A short explanation.
- A recommended action.
- A playable video extract, ideally 5–30 seconds long.
- A still image or thumbnail.
- The technician’s relevant spoken comment.
- Confidence level if the system inferred the finding automatically.
- A way for the technician or advisor to edit, confirm, or reject the finding.

The product should feel like a structured inspection report with video evidence, not like a folder full of random clips.

---

# 4. Main System Components

Design the product around four major components:

1. Smart glasses capture layer.
2. Android recording and upload app.
3. Backend video processing and report generation pipeline.
4. Web inspection report for stakeholders.

Each component should be designed in detail.

---

# 5. Smart Glasses Capture Layer

The smart glasses are used as a hands-free recording device.

The goals of the smart glasses layer are:

- Capture the technician’s point of view.
- Capture technician voice comments.
- Minimize disruption to the technician’s workflow.
- Avoid requiring the technician to hold a phone.
- Preserve context around visual evidence.
- Make it easy to start, pause, resume, and stop inspection recording.

## Required Features

The smart glasses should support:

- Start recording.
- Stop recording.
- Pause and resume recording.
- Audio recording.
- Voice markers such as:
  - “Mark this.”
  - “Finding.”
  - “Red item.”
  - “Customer concern confirmed.”
  - “Warranty evidence.”
  - “Tire measurement.”
  - “Brake measurement.”
- Optional short voice commands:
  - “Start inspection.”
  - “End inspection.”
  - “Mark front left tire.”
  - “Mark rear brakes.”
  - “Add customer note.”
  - “Repeat measurement.”
- Visual or audio feedback confirming that recording is active.
- Battery status visibility.
- Connection status to Android app.
- Local buffering if the phone connection is temporarily lost.

## Smart Glasses Design Constraints

The system should account for real dealership conditions:

- Noisy service bays.
- Poor lighting under vehicles.
- Grease, gloves, and awkward hand positions.
- Technicians moving quickly.
- Vehicles on lifts.
- Privacy issues around other customers’ vehicles.
- Background conversations.
- Intermittent Wi-Fi or cellular coverage.
- Long recording sessions.
- Battery limitations.
- Technicians who do not want extra steps.

## Important Smart Glasses UX Rules

The glasses should not require constant interaction.

The technician should be able to complete most inspections with only:

1. Start recording.
2. Speak naturally when something matters.
3. End recording.

Voice markers are helpful, but the system should still work if the technician forgets to use them.

The system should capture continuous video but later reduce it into useful clips.

---

# 6. Android App Recording and Upload Pipeline

The Android app acts as the local control center.

It should connect to the smart glasses, manage job metadata, handle recording state, upload video, and show processing status.

## Android App Core Responsibilities

The Android app should:

- Authenticate the technician.
- Connect to the smart glasses.
- Select or scan the vehicle/repair order.
- Attach all recordings to the correct VIN and repair order.
- Start, pause, resume, and stop recordings.
- Show recording status.
- Show battery and connection status.
- Upload videos to the backend.
- Retry failed uploads automatically.
- Compress or chunk video when needed.
- Preserve video quality enough for inspection evidence.
- Store video temporarily if upload is unavailable.
- Show upload progress.
- Show report generation progress.
- Notify the advisor when the report is ready.
- Allow the technician to review key detected findings.
- Allow the technician to correct obvious errors before customer delivery.

## Repair Order Association

The app must prevent orphaned media.

Before recording starts, the app should require one of the following:

- Scan VIN.
- Scan repair order barcode or QR code.
- Select repair order from DMS integration.
- Manually enter VIN/RO as fallback.
- Match vehicle by appointment list.

Each recording should be associated with:

- Dealer ID.
- Store/location ID.
- Technician ID.
- Advisor ID, if available.
- Repair order number.
- VIN.
- Year/make/model/trim.
- Mileage.
- Customer ID, if available.
- Date/time.
- Inspection type.
- Recording session ID.

## Upload Pipeline Requirements

The upload pipeline should support:

- Chunked video upload.
- Resume after failure.
- Background upload.
- Upload queue.
- Local encryption before upload.
- Server-side encryption after upload.
- Upload status logs.
- Network interruption handling.
- Duplicate upload prevention.
- Maximum file size handling.
- Optional compression profiles.
- Upload prioritization for active repair orders.

## Upload States

Use clear states such as:

- Not started.
- Recording.
- Recording complete.
- Queued for upload.
- Uploading.
- Upload paused.
- Upload failed.
- Upload complete.
- Processing.
- Technician review needed.
- Advisor review needed.
- Report ready.
- Sent to customer.
- Customer viewed.
- Customer approved.
- Customer declined.

## Android App UX

The app should be extremely simple.

Main screens:

1. **Today’s Jobs**
   - List assigned ROs.
   - Vehicle details.
   - Customer concern.
   - Status.

2. **Job Detail**
   - VIN.
   - RO.
   - Mileage.
   - Requested service.
   - Inspection checklist.
   - Start recording button.

3. **Recording Screen**
   - Large recording indicator.
   - Timer.
   - Glasses connection status.
   - Battery status.
   - Pause/resume.
   - Stop inspection.
   - Manual “mark finding” button.

4. **Upload/Processing Screen**
   - Upload progress.
   - Processing progress.
   - Errors.
   - Retry button.

5. **Detected Findings Review**
   - Finding title.
   - Clip preview.
   - Transcript excerpt.
   - Severity.
   - Measurement.
   - Confirm/edit/delete.

---

# 7. Backend Video Processing Pipeline

The backend should transform raw inspection footage into structured inspection data.

The pipeline should include:

1. Video ingestion.
2. Metadata validation.
3. Video storage.
4. Audio transcription.
5. Voice marker detection.
6. Video segmentation.
7. Object/component detection.
8. Measurement extraction where possible.
9. Finding detection.
10. Clip selection.
11. Still image extraction.
12. Report drafting.
13. Technician/advisor review.
14. Web report generation.
15. Stakeholder-specific access control.

---

## 7.1 Video Ingestion

When a video is uploaded, the backend should:

- Validate user permissions.
- Validate repair order association.
- Store raw video securely.
- Generate a unique video ID.
- Link video to inspection session.
- Extract metadata:
  - Duration.
  - Resolution.
  - Frame rate.
  - Audio channels.
  - Upload timestamp.
  - Device ID.
  - Glasses ID.
- Start processing job.
- Store processing logs.

---

## 7.2 Audio Transcription

The system should transcribe all technician speech.

The transcription should include:

- Full transcript.
- Speaker detection if possible.
- Timestamped words or segments.
- Confidence score.
- Detected automotive terms.
- Measurements.
- Component names.
- Severity words.
- Voice markers.
- Customer concern references.

Important phrases to detect:

- “front left”
- “front right”
- “rear left”
- “rear right”
- “driver side”
- “passenger side”
- “inner”
- “outer”
- “brake pads”
- “rotor”
- “tire”
- “tread”
- “battery”
- “leak”
- “oil leak”
- “coolant leak”
- “alignment”
- “suspension”
- “control arm”
- “bushing”
- “filter”
- “wipers”
- “lights”
- “needs replacement”
- “recommend”
- “urgent”
- “safe”
- “monitor”
- “warranty”
- “customer concern”
- “measurement”
- “two millimeters”
- “three thirty-seconds”
- “below spec”
- “within spec”

The transcript should be used to help generate findings, but the system should not blindly trust the transcript. It should surface uncertain findings for review.

---

## 7.3 Video Segmentation

The raw video should be split into meaningful segments.

Segments may be based on:

- Voice markers.
- Detected silence/speech.
- Component mentions.
- Visual scene changes.
- Vehicle area changes.
- Manual technician marks.
- Checklist sections.
- Time proximity to important words.
- Detected objects or tools.
- Visible measurement devices.

Each segment should have:

- Start timestamp.
- End timestamp.
- Short label.
- Transcript excerpt.
- Detected vehicle area.
- Detected component.
- Candidate finding.
- Confidence score.
- Suggested clip boundaries.

The goal is to extract short useful clips rather than force stakeholders to watch the whole video.

---

## 7.4 Finding Detection

A “finding” is a structured issue or condition discovered during inspection.

Examples:

- Front brake pads worn to 2 mm.
- Rear tires at 3/32.
- Oil leak visible near timing cover.
- Battery failed load test.
- Engine air filter dirty.
- Cabin air filter contaminated.
- Wiper blades streaking.
- Front lower control arm bushing cracked.
- Coolant seepage visible.
- Tire sidewall damage.
- Check engine light present.
- Suspension noise confirmed.
- Undercarriage damage observed.

Each finding should include:

```json
{
  "finding_id": "string",
  "inspection_session_id": "string",
  "repair_order_id": "string",
  "vin": "string",
  "system": "brakes | tires | fluids | battery | suspension | engine | transmission | body | interior | electrical | other",
  "component": "string",
  "location": "front_left | front_right | rear_left | rear_right | front | rear | left | right | center | unknown",
  "severity": "green | yellow | red | unknown",
  "finding_title": "string",
  "technician_observation": "string",
  "customer_friendly_explanation": "string",
  "oem_technical_note": "string",
  "measurement": {
    "value": "number or string",
    "unit": "mm | 32nds | volts | psi | percent | other",
    "source": "spoken | visual | manual | integrated_tool | inferred",
    "confidence": "number"
  },
  "recommendation": "string",
  "why_it_matters": "string",
  "estimated_urgency": "immediate | soon | monitor | no_action",
  "evidence": {
    "video_clip_url": "string",
    "clip_start_time": "number",
    "clip_end_time": "number",
    "thumbnail_url": "string",
    "transcript_excerpt": "string",
    "raw_video_url": "string"
  },
  "review_status": "draft | technician_confirmed | advisor_confirmed | rejected | sent_to_customer",
  "confidence_score": "number"
}
```

---

## 7.5 Severity Classification

Use a simple green/yellow/red system.

### Green

No action needed now.

Examples:

- Brake pads above healthy threshold.
- Tires have healthy tread depth.
- No visible leaks.
- Battery test passed.

### Yellow

Monitor or service soon.

Examples:

- Brake pads approaching replacement threshold.
- Tires nearing low tread.
- Minor seepage.
- Filter moderately dirty.
- Wipers beginning to streak.
- Alignment suggested due to uneven wear.

### Red

Urgent, safety-related, failure-related, warranty-sensitive, or likely to cause further damage.

Examples:

- Brake pads critically low.
- Tire tread dangerously low.
- Exposed cords or sidewall damage.
- Significant fluid leak.
- Battery failed.
- Warning light with drivability concern.
- Suspension component loose or damaged.
- Safety system issue.

The system should allow each dealership or OEM to customize thresholds.

Example tire tread logic:

- Green: 6/32 or above.
- Yellow: 4/32–5/32.
- Red: 3/32 or below.

Example brake pad logic:

- Green: 6 mm or above.
- Yellow: 3–5 mm.
- Red: 2 mm or below.

These thresholds should be configurable because OEMs, dealerships, and jurisdictions may vary.

---

# 8. Web Report Generation

The final report should be a web page, not a PDF-first experience.

The report should be responsive and work on:

- Desktop.
- Tablet.
- Mobile browser.
- Customer phone.
- Advisor workstation.
- Manufacturer review portal.

The report should load quickly, even when there are multiple video clips.

Use embedded video snippets with streaming support.

Do not require users to download files.

---

## 8.1 Report Structure

The report should have the following sections.

---

## A. Header

Show:

- Dealership name and logo.
- Customer name, if appropriate.
- Vehicle year/make/model.
- VIN, partially masked for customer view if desired.
- Mileage.
- Repair order number.
- Inspection date/time.
- Technician name.
- Advisor name.
- Overall inspection status.
- Link expiration date, if applicable.

Example:

> 2021 Toyota Camry
> RO #483920
> Mileage: 42,318
> Inspection completed by: Alex R.
> Advisor: Jamie M.
> Status: 2 urgent items, 3 recommended items, 14 passed items

---

## B. Executive Summary

Give a short plain-language summary.

Example:

> We inspected your vehicle and found two items that need attention now: rear tires are low on tread and front brake pads are near the replacement limit. We also found three items to monitor, including a dirty cabin air filter and early signs of uneven tire wear. The clips below show each finding.

The summary should be automatically generated but editable by the advisor.

---

## C. Priority Items

Show urgent red items first.

Each item should include:

- Title.
- Severity.
- System/component.
- Location.
- Measurement.
- Recommendation.
- Price, if available.
- Approval control, if customer-facing.
- Embedded proof clip.
- Thumbnail.
- Transcript excerpt.
- “Why this matters” explanation.

Example card:

### Red Item: Rear Tires Low on Tread

**Location:** Rear left and rear right
**Measurement:** 3/32 tread depth
**Recommendation:** Replace rear tires. Alignment recommended due to outer-edge wear.
**Why it matters:** Low tread reduces traction, especially in wet conditions.

Embedded clip:

- 12-second video showing technician measuring tread.
- Transcript excerpt: “Rear tires are at three thirty-seconds with outer edge wear.”

Actions:

- Approve.
- Decline.
- Ask a question.
- Save for later.

---

## D. Recommended / Monitor Items

Show yellow items.

These should be less alarming and clearly positioned as “recommended soon” or “monitor.”

Example:

### Yellow Item: Cabin Air Filter Dirty

**Recommendation:** Replace cabin air filter.
**Why it matters:** A dirty cabin filter can reduce airflow and cause odors.

Embedded clip showing the dirty filter.

---

## E. Passed Items

Show green items in a collapsed section.

Do not overwhelm the customer with every passed item unless they want to expand.

Examples:

- Front lights checked.
- Brake fluid level OK.
- No major undercarriage damage visible.
- Battery passed test.
- Wipers OK.

Green items are useful because they increase trust. The report should not only show what is wrong.

---

## F. Full Inspection Checklist

Include a structured checklist grouped by system:

1. Tires and wheels.
2. Brakes.
3. Fluids.
4. Battery and charging.
5. Filters.
6. Lights.
7. Wipers.
8. Suspension and steering.
9. Engine bay.
10. Undercarriage.
11. Interior.
12. Exterior.
13. Diagnostic scan.
14. Recalls or campaigns, if integrated.
15. EV/hybrid systems, if applicable.

Each row should show:

- Item.
- Status.
- Measurement.
- Notes.
- Evidence clip or image.
- Technician confirmation.
- Advisor confirmation.

---

## G. Customer Approval Section

For customer-facing reports, include approval controls.

Each recommended service should have:

- Service title.
- Parts/labor summary.
- Price.
- Tax/shop fees if available.
- Estimated completion time.
- Warranty/customer-pay indicator.
- Approve button.
- Decline button.
- Ask advisor button.

The system should record:

- Exact estimate version approved.
- Date/time.
- Customer identity.
- IP/device info if legally appropriate.
- Communication channel.
- Approved services.
- Declined services.
- Free-text customer comments.

Important: the approval record must be tied to the exact report and estimate version shown to the customer.

---

## H. OEM / Warranty Evidence Section

Create a separate stakeholder view for OEM or warranty users.

This section should include more technical information:

- VIN.
- Mileage.
- In-service date, if available.
- Repair order.
- Complaint/concern.
- Cause.
- Correction.
- Diagnostic trouble codes, if available.
- Scan report attachments.
- Measurements.
- Technician notes.
- Video evidence.
- Still images.
- Part numbers.
- Labor operation codes.
- TSB/recall reference, if available.
- Warranty coverage indicator.
- Part return status, if relevant.

The OEM/warranty view should be less customer-friendly and more audit-friendly.

---

## I. Raw Video Access

The full raw video should be available only to authorized internal users.

Customers should usually see only extracted clips, not the entire raw recording, unless the dealership chooses otherwise.

Raw video access should support:

- Playback.
- Timeline markers.
- Jump to finding.
- Transcript search.
- Download permission controls.
- Audit logs.

---

# 9. Stakeholder-Specific Views

The same inspection should generate different views depending on the stakeholder.

## Customer View

Priorities:

- Clarity.
- Trust.
- Simple language.
- Proof clips.
- Price.
- Approval/decline.
- Urgency.

Avoid:

- Excessive technical jargon.
- Internal notes.
- Warranty codes.
- Technician uncertainty.
- Raw unfiltered video.
- Other customers’ vehicles or private background details.

## Advisor View

Priorities:

- Review and edit findings.
- Build estimate.
- Attach parts/labor.
- Send report.
- Track customer view/approval.
- Chat with customer.
- See declined services.
- Convert findings into repair order lines.

## Technician View

Priorities:

- Minimal typing.
- Confirm detected findings.
- Correct measurements.
- Mark clips.
- Add notes.
- Flag warranty evidence.
- Avoid slowing down work.

## Manager View

Priorities:

- MPI completion rates.
- Video attach rates.
- Approval rates.
- Declined service dollars.
- Advisor performance.
- Technician inspection quality.
- Cycle time.
- Comebacks.
- Warranty denial risk.

## OEM/Warranty View

Priorities:

- Evidence.
- Measurements.
- Claim support.
- Compliance.
- Diagnostic traceability.
- Required documentation.
- Audit trail.

---

# 10. Report UI Requirements

The report web page should be designed around evidence cards.

Each finding card should include:

```text
[Severity badge] [Finding title]

System: Tires
Location: Rear left
Measurement: 3/32
Recommendation: Replace rear tires
Urgency: Immediate

[Embedded video clip]

Technician comment:
“Rear tires are at three thirty-seconds with visible outer-edge wear.”

Why this matters:
Low tread can reduce traction, especially in wet conditions.

Advisor note:
We recommend replacing both rear tires and performing an alignment to reduce uneven wear.

[Approve] [Decline] [Ask a question]
```

The report should include:

- Sticky summary bar.
- Clear red/yellow/green status.
- Embedded video player.
- Expand/collapse sections.
- Mobile-first design.
- Fast load times.
- Accessible text.
- Captions/transcripts for clips.
- Thumbnail previews.
- Timeline markers.
- Print/PDF export as secondary option.
- Audit trail for internal users.
- Secure share links.

---

# 11. Data Model

Design a data model with at least these entities.

## Dealer

- dealer_id
- name
- address
- OEM brands
- settings
- inspection templates
- severity thresholds

## User

- user_id
- name
- role
- dealer_id
- permissions

Roles:

- technician
- advisor
- manager
- warranty_admin
- customer
- OEM_reviewer
- admin

## Vehicle

- vehicle_id
- VIN
- year
- make
- model
- trim
- mileage
- license_plate
- customer_id

## Repair Order

- repair_order_id
- RO number
- dealer_id
- vehicle_id
- customer_id
- advisor_id
- technician_id
- status
- customer concern
- opened_at
- closed_at

## Inspection Session

- inspection_session_id
- repair_order_id
- technician_id
- started_at
- ended_at
- status
- raw_video_ids
- report_id

## Video

- video_id
- inspection_session_id
- storage_url
- duration
- resolution
- uploaded_at
- processing_status
- transcript_id

## Transcript

- transcript_id
- video_id
- full_text
- timestamped_segments
- confidence

## Finding

- finding_id
- inspection_session_id
- system
- component
- location
- severity
- title
- observation
- measurement
- recommendation
- customer_explanation
- OEM_note
- evidence_clip_id
- status
- confidence_score

## Evidence Clip

- clip_id
- finding_id
- raw_video_id
- start_time
- end_time
- clip_url
- thumbnail_url
- transcript_excerpt

## Estimate Item

- estimate_item_id
- finding_id
- repair_order_id
- service_name
- parts
- labor
- price
- taxes
- fees
- warranty_or_customer_pay
- approval_status

## Approval Record

- approval_id
- repair_order_id
- customer_id
- estimate_version_id
- approved_items
- declined_items
- timestamp
- method
- customer_signature_or_confirmation
- audit_metadata

## Report

- report_id
- inspection_session_id
- report_url
- customer_view_status
- advisor_review_status
- OEM_view_status
- created_at
- updated_at
- version

---

# 12. AI Processing Requirements

The AI should help with the following tasks.

## Transcription

Convert technician speech into timestamped text.

## Automotive Entity Extraction

Extract:

- Components.
- Locations.
- Measurements.
- Units.
- Severity words.
- Recommendations.
- Failure descriptions.
- Warranty references.
- Customer concern references.

## Finding Generation

Generate candidate findings from the transcript and video timeline.

## Clip Selection

Choose the most relevant video excerpt for each finding.

The selected clip should:

- Start a few seconds before the key evidence.
- End after the measurement or explanation is complete.
- Avoid unrelated footage.
- Avoid private background content when possible.
- Be short enough for customer viewing.

## Customer Explanation Generation

Rewrite technical comments into simple explanations.

Example technician speech:

> “LF pads are basically cooked, maybe 2 mil, rotor has a lip.”

Customer version:

> “The front left brake pads are worn close to the replacement limit. We recommend replacing the front brake pads and inspecting the rotors.”

## OEM Note Generation

Rewrite the same information into technical documentation.

Example:

> “LF/RF front brake pads measured approximately 2 mm. Rotor wear lip visible. Recommend front brake service per OEM procedure.”

## Severity Recommendation

Suggest green/yellow/red based on:

- Measurement thresholds.
- Technician language.
- Component type.
- Safety relevance.
- Dealer/OEM rules.

## Confidence and Human Review

The AI should never silently finalize uncertain findings.

If confidence is low, mark the item for technician or advisor review.

Examples of low-confidence cases:

- Unclear measurement.
- Audio transcription uncertainty.
- Component visually ambiguous.
- Technician uses vague language.
- No proof clip found.
- Conflicting information.
- Severe recommendation without measurement.

---

# 13. Human Review Workflow

The report should not be sent directly to the customer without review.

Recommended workflow:

1. Technician records inspection.
2. Video uploads.
3. AI processes transcript and footage.
4. AI drafts findings and clips.
5. Technician confirms or corrects findings.
6. Advisor reviews customer-facing language and pricing.
7. Advisor sends report link to customer.
8. Customer views clips and approves/declines.
9. Approved work flows back to the repair order.
10. Declined work is stored for future follow-up.

The product should make review fast.

The review screen should allow:

- Edit title.
- Edit severity.
- Edit measurement.
- Edit recommendation.
- Edit customer explanation.
- Edit OEM note.
- Replace video clip.
- Adjust clip start/end.
- Delete false finding.
- Add missing finding.
- Approve for customer.
- Approve for OEM/warranty package.

---

# 14. Privacy, Security, and Compliance

The system must handle sensitive information carefully.

## Privacy Risks

The raw video may capture:

- Other customers’ vehicles.
- License plates.
- Faces.
- Conversations.
- Documents.
- Shop screens.
- Personal belongings inside vehicles.
- Payment or customer information.

## Required Controls

The system should include:

- Role-based access control.
- Secure signed report links.
- Link expiration.
- Watermarked videos if needed.
- Audit logs for report views.
- Customer-only filtered clips.
- Raw video restricted to internal users.
- Optional face/license-plate blurring.
- Data retention settings.
- Delete/archive policies.
- Encryption in transit.
- Encryption at rest.
- Dealer-level permissions.
- OEM-specific sharing rules.

## Authorization Records

If the customer approves work through the report, the system must store a defensible record of exactly what was approved.

Store:

- Estimate version.
- Approved services.
- Declined services.
- Price shown.
- Timestamp.
- Customer identity.
- Communication method.
- Report version.
- Advisor involved.
- Any customer notes.

---

# 15. Integrations

Design the system so it can integrate with dealership tools.

Potential integrations:

- DMS / repair order system.
- CRM.
- SMS/email messaging.
- Parts catalog.
- Labor time guide.
- OEM warranty portal.
- Payment system.
- Diagnostic scan tools.
- Alignment machine.
- Tire tread measurement tools.
- Battery testers.
- Service scheduling.
- Customer survey/CSI systems.

The MVP does not need all integrations, but the architecture should anticipate them.

Minimum MVP integrations:

- Manual repair order creation or import.
- VIN/RO association.
- SMS or email report sharing.
- Basic estimate item creation.
- Customer approve/decline capture.

---

# 16. MVP Scope

Define a realistic MVP.

## MVP Goal

Create a working demo where a technician records an inspection through smart glasses, uploads the video through Android, and produces a web report with AI-generated findings and playable proof clips.

## MVP Must Include

### Smart Glasses

- Start/stop recording.
- Audio capture.
- Connection to Android app.
- Basic recording status.

### Android App

- Technician login.
- Select or create repair order.
- Attach VIN/RO metadata.
- Start/stop recording.
- Upload video.
- Show upload and processing status.

### Backend

- Store video.
- Transcribe audio.
- Segment video.
- Detect candidate findings from transcript.
- Generate short clips.
- Generate report data.
- Store report.

### Web Report

- Vehicle header.
- Summary.
- Red/yellow/green sections.
- Finding cards.
- Embedded clips.
- Transcript excerpts.
- Recommendation text.
- Advisor review mode.
- Customer view mode.
- Approve/decline buttons as MVP simulation or real capture.

### Human Review

- Advisor can edit findings.
- Advisor can publish report.
- Technician or advisor can correct measurement/severity.

---

# 17. MVP Inspection Categories

Start with a limited but valuable checklist.

Recommended MVP checklist:

1. Tires
   - Tread depth.
   - Uneven wear.
   - Visible damage.

2. Brakes
   - Pad thickness.
   - Rotor condition.
   - Brake fluid comment.

3. Battery
   - Passed/failed test.
   - Voltage or health if spoken.

4. Filters
   - Engine air filter.
   - Cabin air filter.

5. Fluids/leaks
   - Oil leaks.
   - Coolant leaks.
   - Fluid levels.

6. Wipers/lights
   - Basic pass/fail.

7. Suspension/undercarriage
   - Visible damage.
   - Loose/worn components.
   - Leaks.

These are good MVP targets because they are visually explainable and customer-relevant.

---

# 18. Example Generated Report

The system should be able to generate a report like this:

---

## Vehicle Inspection Report

**Vehicle:** 2020 Honda Accord
**Mileage:** 58,240
**Repair Order:** RO-10482
**Inspection Date:** April 24, 2026
**Technician:** Alex R.
**Advisor:** Maria S.

### Summary

We completed a visual inspection of your vehicle. We found one urgent item and two recommended items. The rear tires are low on tread and should be replaced soon. The cabin air filter is dirty, and the front brake pads are approaching the replacement range.

---

### Urgent Item

#### Rear Tires Low on Tread

**Severity:** Red
**Location:** Rear left and rear right
**Measurement:** 3/32
**Recommendation:** Replace rear tires. Consider alignment due to uneven outer-edge wear.

**Why this matters:**
Low tread can reduce wet-weather traction and increase stopping distance.

**Proof:**
Embedded 14-second video clip showing tire tread measurement.

**Technician comment:**
“Rear tires are at three thirty-seconds, and there’s outer-edge wear on both sides.”

**Customer action:**
Approve / Decline / Ask advisor

---

### Recommended Item

#### Cabin Air Filter Dirty

**Severity:** Yellow
**Recommendation:** Replace cabin air filter.

**Why this matters:**
A dirty cabin air filter can reduce airflow and cause odors inside the vehicle.

**Proof:**
Embedded 9-second video clip showing the removed filter.

---

### Monitor Item

#### Front Brake Pads Approaching Replacement

**Severity:** Yellow
**Measurement:** Approximately 4 mm
**Recommendation:** Monitor or replace at next service.

**Proof:**
Embedded 11-second clip showing front brake pad condition.

---

### Passed Items

- Battery visually checked.
- No major oil leak visible.
- Wipers OK.
- Exterior lights OK.
- Coolant level OK.

---

# 19. Technical Architecture

Propose a scalable architecture.

## Frontend

- Web app for reports and review.
- Mobile-responsive customer report.
- Advisor dashboard.
- Technician review screen.

## Android App

- Native Android or React Native.
- Smart glasses SDK integration.
- Local recording/session management.
- Upload manager.
- Offline queue.

## Backend

- API server.
- Authentication service.
- Video ingestion service.
- Object storage.
- Processing queue.
- Transcription service.
- AI extraction service.
- Clip generation service.
- Report generation service.
- Notification service.
- Audit log service.

## Storage

- Raw video storage.
- Clip storage.
- Thumbnail storage.
- Transcript storage.
- Structured inspection database.
- Approval record database.

## Processing

Use asynchronous processing jobs.

Pipeline:

```text
Video uploaded
→ validate metadata
→ store raw video
→ transcribe audio
→ detect voice markers
→ segment video
→ extract candidate findings
→ select evidence clips
→ generate thumbnails
→ generate customer/OEM language
→ create draft report
→ notify technician/advisor
→ human review
→ publish report
```

---

# 20. Report Generation Logic

The system should generate a report from structured findings.

Pseudo-logic:

```text
For each inspection session:
  Load vehicle, RO, technician, advisor, customer data
  Load raw video and transcript
  Detect candidate findings
  Group findings by system
  Assign severity
  Select evidence clip for each finding
  Generate thumbnail
  Generate technician note
  Generate customer explanation
  Generate OEM technical note
  Flag low-confidence findings for review
  Create draft report
  Wait for advisor approval
  Publish stakeholder-specific report views
```

---

# 21. Edge Cases

Design for these cases:

- Technician forgets to speak.
- Technician speaks but camera is not pointed at evidence.
- Audio is noisy.
- Video upload fails.
- Glasses battery dies.
- Wrong repair order selected.
- Multiple videos for one inspection.
- Same finding appears in multiple clips.
- Measurement is unclear.
- AI creates false finding.
- Customer opens report after estimate changed.
- Customer approves only some items.
- Customer forwards report link.
- OEM needs raw evidence.
- Advisor wants to hide internal notes.
- Technician records another customer’s vehicle accidentally.
- Video contains sensitive background content.
- Customer disputes approval.
- Dealership needs report after link expiration.

---

# 22. Success Metrics

Define product success using dealership-relevant metrics.

Track:

- Inspection completion rate.
- Percentage of inspections with video evidence.
- Average time from inspection complete to report ready.
- Average time from report sent to customer viewed.
- Customer view rate.
- Approval rate.
- Approval speed.
- Additional services sold per repair order.
- Declined service value.
- Number of findings per inspection.
- False positive AI findings.
- Technician edit rate.
- Advisor edit rate.
- Warranty documentation acceptance rate.
- Customer satisfaction.
- Comeback rate.
- Report generation failure rate.
- Upload failure rate.

---

# 23. Suggested Improvements Beyond MVP

After the MVP, consider these improvements.

## A. Voice-Guided Inspection

The app can guide the technician through the checklist using audio prompts:

> “Please inspect front left tire.”
> “Please state tread depth.”
> “Please inspect front brakes.”
> “Please show brake pad measurement.”

This would improve consistency but may annoy technicians if too rigid. Make it optional.

## B. Automatic Measurement Capture

Integrate with:

- Digital tire tread depth gauges.
- Brake pad measurement tools.
- Battery testers.
- Alignment machines.
- Scan tools.

This reduces reliance on spoken measurements.

## C. AI Quality Checks

Before the report is sent, the system can warn:

- “No proof clip found for red brake recommendation.”
- “Measurement missing for tire recommendation.”
- “Video is too blurry.”
- “Transcript confidence is low.”
- “Customer-facing explanation uses too much technical language.”
- “Finding marked red but no supporting evidence attached.”

## D. OEM-Specific Templates

Each manufacturer may want different warranty documentation.

Create templates by OEM:

- Required fields.
- Required images.
- Required clips.
- Required measurements.
- Labor operation mapping.
- Warranty claim checklist.

## E. Customer Personalization

Adapt language based on customer type:

- Simple explanation for retail customer.
- Fleet-oriented report for fleet manager.
- Technical report for enthusiast.
- Warranty-first report for OEM.
- Internal sales-oriented report for trade-in appraisal.

## F. Automatic Redaction

Use AI to blur:

- Faces.
- License plates.
- Documents.
- Computer screens.
- Other vehicles’ identifying details.

## G. Smart Timeline

Create a timeline of the entire inspection:

```text
00:00 Vehicle walkaround
01:12 Front tires
02:03 Rear tires
03:20 Front brakes
04:45 Battery
05:30 Filters
06:10 Undercarriage
07:45 Summary
```

Users can jump directly to sections.

## H. Declined Work Follow-Up

When a customer declines work, store it automatically.

Example:

> Customer declined rear tires on April 24, 2026 at 58,240 miles.

At the next visit, show:

> Previously declined: rear tires at 3/32. Recheck required.

## I. Coaching and Training

Use the system to coach technicians and advisors.

Examples:

- Technician forgot measurements.
- Advisor took too long to send report.
- Red items lacked proof clips.
- Customer viewed report but no follow-up occurred.
- Certain advisors have higher approval rates due to better explanations.

---

# 24. Output Required From the Assistant

When responding to this prompt, produce:

1. A complete product requirements document.
2. A user journey for technician, advisor, customer, and OEM/warranty reviewer.
3. A technical architecture.
4. A data model.
5. A backend processing pipeline.
6. A web report wireframe.
7. Android app screens.
8. Smart glasses workflow.
9. AI extraction logic.
10. Human review workflow.
11. Security/privacy model.
12. MVP scope.
13. Future roadmap.
14. Risks and mitigations.
15. Acceptance criteria.

Be specific and practical.

Avoid vague statements like “use AI to analyze video.” Explain exactly what the AI should extract, how findings should be structured, how clips should be chosen, and how humans should review the output.

The final product should feel like a dealership-ready inspection system, not a generic video recording app.

---

# 25. Acceptance Criteria

The MVP is successful if the following can happen end-to-end:

1. A technician selects a repair order in the Android app.
2. The technician starts recording through smart glasses.
3. The technician performs a basic inspection and speaks naturally.
4. The video and audio upload successfully.
5. The backend transcribes the audio.
6. The system detects at least three candidate inspection findings.
7. The system creates short evidence clips for each finding.
8. The system generates a draft report.
9. The advisor can edit the report.
10. The advisor publishes a customer-facing web page.
11. The customer can open the report on a phone.
12. The customer can watch embedded proof clips.
13. The customer can approve or decline recommended services.
14. The approval/decline is stored with timestamp and report version.
15. Internal users can access the raw video and full transcript.
16. Customer users only see the polished report and selected clips.
17. All media remains linked to the correct VIN, RO, technician, and inspection session.

---

# 26. One-Sentence Product North Star

The product should turn a messy hands-free mechanic recording into a clean, trusted, evidence-backed inspection report that helps customers approve work, advisors explain work, technicians avoid typing, and manufacturers verify claims.

---

# 27. Recommended MVP Strategy

For the first version, keep the scope sharp:

> **Record → upload → transcribe → detect findings from speech → create clips → generate report page → human review → send to customer.**

Do not over-invest initially in automatic visual measurement detection. That is harder and more fragile.

The fastest compelling demo is to use the technician’s spoken narration plus time-coded video evidence. For example, when the technician says:

> “Rear tire is at 3/32.”

The system can find that timestamp, cut the surrounding 15 seconds, and create a finding card with:

- Title: Rear tire low on tread.
- Measurement: 3/32.
- Severity: Red or yellow based on configurable threshold.
- Evidence: 15-second clip.
- Technician comment: transcript excerpt.
- Customer explanation.
- Advisor-editable recommendation.

This path creates a credible dealership demo quickly, while leaving room for richer AI, integrations, and measurement automation later.
