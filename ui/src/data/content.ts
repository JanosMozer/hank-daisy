export type FlowNode = {
  id: string;
  title: string;
  short: string;
  detail: string;
  pressure?: string;
};

export const diagnosisFlow: FlowNode[] = [
  {
    id: "intake",
    title: "Customer intake",
    short: "Symptoms in plain language hit the service lane.",
    detail:
      "Drivers describe shudders, smells, and “sometimes” noises. None of that is structured data yet.",
    pressure: "Drive-through pace: writers compress the story into a headline.",
  },
  {
    id: "ro",
    title: "Repair order (RO)",
    short: "The RO becomes the only handoff to the bay.",
    detail:
      "Whatever the writer captured is what the tech sees—often hours later, with no way to ask follow-ups.",
    pressure: "Vague ROs force the tech to rediscover context with billable diagnostic time.",
  },
  {
    id: "bay",
    title: "Bay assignment",
    short: "Throughput beats certainty.",
    detail:
      "The shop is tuned for speed. Diagnosis is slow, uncertain detective work on a moving system.",
    pressure: "Flat-rate pay rewards finished jobs, not careful hypothesis testing.",
  },
  {
    id: "scan",
    title: "Scan tool & codes",
    short: "Codes are clues, not verdicts.",
    detail:
      "A code names where a monitor saw a symptom. Root cause can be upstream, adjacent, or intermittent.",
    pressure: "Jumping from code → parts is fast; it is also how mis-repairs happen.",
  },
  {
    id: "tsb",
    title: "TSBs & OEM info",
    short: "Official knowledge is incomplete and hard to search.",
    detail:
      "Publication lag, sparse coverage for rare failures, and keyword-only search bury known fixes.",
    pressure: "Senior techs carry a mental index; juniors burn hours re-learning.",
  },
  {
    id: "repro",
    title: "Reproduction",
    short: "Intermittent faults may not appear in the shop.",
    detail:
      "Load, temperature, vibration, and time all matter. Short test drives miss the failure mode.",
    pressure: "“No problem found” sends the car back; the loop restarts.",
  },
  {
    id: "verify",
    title: "Verification",
    short: "The step most often skipped under time pressure.",
    detail:
      "Clearing codes without a verification drive that matches real use invites comebacks.",
    pressure: "Comebacks eat labor, frustrate techs, and hit CSI scores tied to OEM incentives.",
  },
];

export type CodeWalkStep = {
  title: string;
  body: string;
  pitfall: string;
};

export const unknownCodeWalkthrough: CodeWalkStep[] = [
  {
    title: "1. Read the code in context",
    body: "Note pending vs stored, monitor type, and any companion codes. One code often pulls others along for the ride.",
    pitfall: "Treating the first code as “the” problem without reading the full stack.",
  },
  {
    title: "2. Pull freeze frame / live data",
    body: "Capture RPM, load, temps, fuel trims, and voltages at the trip when the fault set. That narrows the operating region.",
    pitfall: "Skipping data because “the code already says catalyst” or similar.",
  },
  {
    title: "3. OEM + TSB sweep for VIN",
    body: "Search by VIN and symptom family, not just the P-code string. Known calibrations and revised parts hide here.",
    pitfall: "Keyword mismatch: “hesitation” vs “rough idle” can miss the same bulletin.",
  },
  {
    title: "4. Reproduce on purpose",
    body: "Match customer conditions: cold soak, hot restart, grade, cruise vs tip-in. Use a co-pilot for safety.",
    pitfall: "Parking-lot idle checks when the fault only appears under highway load.",
  },
  {
    title: "5. Hypothesis + targeted tests",
    body: "Pick the smallest test that falsifies a theory: vacuum, fuel pressure, scope a sensor, compare bank-to-bank.",
    pitfall: "Parts roulette—fast under flat-rate, expensive for the customer, wrong often enough to matter.",
  },
  {
    title: "6. Document, then verify",
    body: "Write what you tested and saw. Verify with the same drive profile that originally failed—then clear monitors.",
    pitfall: "Clearing codes and releasing without a verification drive that stresses the original fault.",
  },
];

export const glassesScenario = {
  vin: "1FTFW1E86PFA12345",
  vehicle: "2023 F-150 3.5L EcoBoost",
  code: "P0299",
  label: "Turbo / Supercharger Underboost",
  rpm: 2140,
  boost: 4.2,
  targetBoost: 12.0,
  trims: { short: 2.1, long: -1.4 },
  tips: [
    "TSB 23-2347: wastegate actuator harness chafe on early builds—inspect left frame rail clip.",
    "iATN pattern: charge pipe crack at intercooler outlet—soapy water while holding 15 psi shop air.",
    "Verify MAP vs BARO at key-on; skewed BARO can false-trigger underboost logic.",
  ],
};
