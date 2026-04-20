export type JobStatus = "queued" | "diag" | "repair" | "verify";

export type DtcRow = {
  code: string;
  description: string;
  pending: boolean;
};

export type WorkOrder = {
  id: string;
  ro: string;
  bay: string;
  vin: string;
  vehicle: string;
  concern: string;
  writerNotes: string;
  status: JobStatus;
  dtcs: DtcRow[];
};

export const MOCK_JOBS: WorkOrder[] = [
  {
    id: "job-1",
    ro: "48291",
    bay: "4",
    vin: "1FTFW1E86PFA12345",
    vehicle: "2023 F-150 3.5L EcoBoost",
    concern: "Power loss under load; whistle under hood when accelerating uphill.",
    writerNotes:
      "Customer states worse when warm. Started ~2 weeks ago. No recent repairs. No aftermarket tune.",
    status: "diag",
    dtcs: [
      { code: "P0299", description: "Turbocharger/Supercharger Underboost", pending: true },
      { code: "P0106", description: "MAP/Barometric Pressure Circuit Range", pending: false },
    ],
  },
  {
    id: "job-2",
    ro: "48304",
    bay: "2",
    vin: "1G1ZD5ST7PF987654",
    vehicle: "2023 Chevrolet Malibu 1.5L Turbo",
    concern: "Rough idle cold only; MIL intermittent.",
    writerNotes: "CEL not always on. Customer leaving vehicle for day — try cold soak repro.",
    status: "queued",
    dtcs: [{ code: "P0506", description: "Idle Air Control RPM Lower Than Expected", pending: false }],
  },
  {
    id: "job-3",
    ro: "48288",
    bay: "1",
    vin: "3MW5U7J09M8A112233",
    vehicle: "2021 BMW 330i",
    concern: "Lane assist warning after windshield replacement (GlassCo).",
    writerNotes: "Needs ADAS calibration per insurer. Photo calibration required.",
    status: "repair",
    dtcs: [],
  },
];

export type RepairStep = { id: string; title: string; detail: string };

/** Illustrative steps — always follow OEM procedure in production. */
export const REPAIR_STEPS_P0299: RepairStep[] = [
  {
    id: "r1",
    title: "Visual + boost leak check (intercooler, charge pipes, BOV)",
    detail: "Pressure test to ~15 psi; spray soapy water on couplers cold and hot.",
  },
  {
    id: "r2",
    title: "TSB harness inspection (if applicable to VIN)",
    detail: "Wastegate actuator harness — follow frame rail clip routing; look for rub-through.",
  },
  {
    id: "r3",
    title: "Commanded vs actual boost + MAP/BARO sanity",
    detail: "Key-on BARO plausible; compare MAP at idle WOT snapshot to spec.",
  },
  {
    id: "r4",
    title: "Document findings + photos for RO",
    detail: "Attach photos of chafed harness or cracked boot before replacing parts.",
  },
];

export type VerifyItem = { id: string; label: string };

export const VERIFY_ITEMS_DEFAULT: VerifyItem[] = [
  { id: "v1", label: "Reproduced original complaint before fix (note conditions)" },
  { id: "v2", label: "After repair: same drive profile — load, grade, temperature" },
  { id: "v3", label: "Pending codes cleared; monitors complete per OEM (not just MIL off)" },
  { id: "v4", label: "Scan for new codes after drive; freeze frame saved if anything sets" },
];

export const GENERIC_REPAIR_STEPS: RepairStep[] = [
  {
    id: "g1",
    title: "Confirm OEM procedure for primary concern",
    detail: "Use factory service info for this VIN — torque, sequence, special tools.",
  },
  {
    id: "g2",
    title: "Torque-critical fasteners + witness marks",
    detail: "Mark staged fasteners after torque; photo if shop policy requires.",
  },
  {
    id: "g3",
    title: "Clear faults only after verification plan exists",
    detail: "Avoid clearing monitors before post-repair drive plan is agreed.",
  },
];

export const ADAS_CALIBRATION_STEPS: RepairStep[] = [
  {
    id: "a1",
    title: "Pre-scan + alignment check per OEM",
    detail: "Some procedures require thrust line / alignment in spec before calibration.",
  },
  {
    id: "a2",
    title: "Target setup + lighting conditions",
    detail: "Follow OEM target placement; document bay floor level and glare issues.",
  },
  {
    id: "a3",
    title: "Post-calibration validation drive",
    detail: "Confirm lane centering / ACC behavior per checklist; store confirmation codes.",
  },
];

export function repairStepsForJob(job: WorkOrder): RepairStep[] {
  if (job.dtcs.some((d) => d.code === "P0299")) return REPAIR_STEPS_P0299;
  const t = `${job.concern} ${job.writerNotes}`.toLowerCase();
  if (t.includes("adas") || t.includes("lane") || t.includes("windshield")) {
    return ADAS_CALIBRATION_STEPS;
  }
  return GENERIC_REPAIR_STEPS;
}
