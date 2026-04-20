import type { HankMessage, HankVehicleContext } from "./hankTypes";

function ctxHeader(c: HankVehicleContext | null): string {
  if (!c) return "[No RO selected — generic bay advice]\n\n";
  const codes = c.dtcs.map((d) => `${d.code} (${d.pending ? "pending" : "stored"})`).join(", ") || "none listed";
  return `[Context: RO ${c.ro} · Bay ${c.bay} · ${c.vehicle} · VIN ${c.vin}]\n[DTCs: ${codes}]\n[Concern: ${c.concern}]\n\n`;
}

export function mockHankReply(
  messages: HankMessage[],
  context: HankVehicleContext | null
): string {
  const last = [...messages].reverse().find((m) => m.role === "user");
  const head = ctxHeader(context);

  if (last?.images && last.images.length > 0) {
    return (
      head +
      `I can’t see photos without the live vision API. Start the Hank server (\`npm run hank-server\`) with OPENAI_API_KEY and a vision-capable model (e.g. gpt-4o-mini or gpt-4o).\n\n` +
      `Until then, for useful bay photos:\n` +
      `1. Steady light; fill the frame with the component.\n` +
      `2. Show connector locks, wire colors, and any fluid trail to its source.\n` +
      `3. Add context (wide shot + close-up).\n\n` +
      `Verify against OEM SI for this VIN before turning wrenches.`
    );
  }

  const q = (last?.content ?? "").toLowerCase();

  if (/torque|spec|ft-?lb|nm/.test(q)) {
    return (
      head +
      `1. Torque values are vehicle- and revision-specific — pull the fastener ID from OEM SI for this exact VIN and model year.\n` +
      `2. Note torque sequence (many cylinder heads / oil pans / wheels are pattern-tighten).\n` +
      `3. Use a calibrated torque wrench; replace stretch bolts if the procedure says one-time-use.\n` +
      `4. Witness-mark critical steering/suspension fasteners per shop policy.\n\n` +
      `Verify against OEM SI for this VIN before turning wrenches.`
    );
  }

  if (/p0299|underboost|boost|turbo|wastegate|charge pipe|intercooler/.test(q)) {
    return (
      head +
      `Underboost-style logic (generic — confirm with OEM tests for your platform):\n\n` +
      `1. Confirm freeze frame / live data: commanded vs actual boost, MAP/BARO sanity, related pending codes.\n` +
      `2. Inspect charge-air path under boost: couplers, intercooler, bypass valve operation — leak test hot and cold if intermittent.\n` +
      `3. Check wastegate / bypass control: vacuum/electrical command vs actual position; harness rub per any applicable TSB.\n` +
      `4. Avoid parts roulette: document baseline measurements before replacing turbo-related assemblies.\n` +
      `5. Verification drive: reproduce customer load profile (grade, rpm range), then confirm monitors.\n\n` +
      `Verify against OEM SI for this VIN before turning wrenches.`
    );
  }

  if (/adas|calibrat|camera|lane|radar|windshield|target/.test(q)) {
    return (
      head +
      `ADAS / calibration outline:\n\n` +
      `1. Confirm OEM prerequisites: alignment within spec, tire size, tire pressure, suspension integrity.\n` +
      `2. Follow OEM target setup and environmental rules (lighting, distance, floor level).\n` +
      `3. Complete static/dynamic calibration per SI; store confirmation codes or screenshots per shop policy.\n` +
      `4. Road verify: lane centering / ACC behavior per checklist — do not rely on dash lights alone.\n\n` +
      `Verify against OEM SI for this VIN before turning wrenches.`
    );
  }

  if (/intermittent|cannot duplicate|npf|no problem/.test(q)) {
    return (
      head +
      `Intermittent strategy:\n\n` +
      `1. Nail operating conditions from the RO (temp, load, grade, duration, cold vs hot).\n` +
      `2. Prefer pending codes + freeze frame over cleared-memory snapshots.\n` +
      `3. Use logging if available; extend test drive with a second tech for safety if needed.\n` +
      `4. Document “not duplicated today” honestly — attach what you tested and invite customer-defined repro.\n\n` +
      `Verify against OEM SI for this VIN before turning wrenches.`
    );
  }

  if (/tsb|bulletin|recall|campaign/.test(q)) {
    return (
      head +
      `TSB workflow:\n\n` +
      `1. Search OEM portal by VIN first (not just keyword), then symptom family + code.\n` +
      `2. Read applicability (build dates, option codes) before ordering parts.\n` +
      `3. Cross-check labor op codes if warranty/pay matters.\n` +
      `4. If nothing publishes yet, log findings for engineering — intermittent early failures exist pre-bulletin.\n\n` +
      `Verify against OEM SI for this VIN before turning wrenches.`
    );
  }

  return (
    head +
    `Here is a safe default diagnostic cadence for most concerns:\n\n` +
    `1. Restate the customer symptom in testable terms; confirm against RO — ask writer if critical detail is missing.\n` +
    `2. Scan all modules relevant to the symptom family; note pending vs stored and companion codes.\n` +
    `3. Pull freeze frame / live data at the fault conditions; narrow electrical vs mechanical vs calibration.\n` +
    `4. Choose the smallest test that falsifies each hypothesis (pressure, voltage, comparison bank-to-bank).\n` +
    `5. Document evidence on the RO; verify with the same drive profile before release.\n\n` +
    `For your exact question: "${last?.content ?? ""}" — pair this cadence with OEM SI wiring diagrams and specs for this VIN.\n\n` +
    `Verify against OEM SI for this VIN before turning wrenches.`
  );
}
