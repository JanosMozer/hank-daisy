import type { WorkOrder } from "../data/jobs";

export type ClosureSummaryInput = {
  concern: string;
  dtcs: WorkOrder["dtcs"];
  bayNotes: string;
  /** Diagnostic workflow step titles marked done */
  diagStepsDone: string[];
  /** Repair checklist items marked done */
  repairStepsDone: { title: string; detail: string }[];
  /** Verify checklist lines marked done */
  verifyStepsDone: string[];
};

/**
 * Builds the closure “solution / work performed” narrative from what the tech
 * actually checked off in the app plus bay notes — no manual invention.
 */
export function buildAutoClosureSummary(input: ClosureSummaryInput): string {
  const lines: string[] = [];

  lines.push("AUTOMATED WORK SUMMARY (from BayDx workflow)");
  lines.push("");
  lines.push("This section is assembled from Diagnostic / Repair / Verify checklists and bay notes recorded in the app.");

  lines.push("");
  lines.push("--- Customer concern (RO) ---");
  lines.push(input.concern.trim() || "(Not captured)");

  const dtcLine =
    input.dtcs.length > 0
      ? input.dtcs
          .map((d) => `${d.code}${d.pending ? " (pending)" : ""}: ${d.description}`)
          .join("\n")
      : "(No DTCs on RO)";
  lines.push("");
  lines.push("--- DTCs ---");
  lines.push(dtcLine);

  lines.push("");
  lines.push("--- Diagnostic workflow (items checked) ---");
  if (input.diagStepsDone.length === 0) {
    lines.push("(None marked complete in app)");
  } else {
    input.diagStepsDone.forEach((t, i) => lines.push(`${i + 1}. ${t}`));
  }

  lines.push("");
  lines.push("--- Repair execution (items checked) ---");
  if (input.repairStepsDone.length === 0) {
    lines.push("(None marked complete in app)");
  } else {
    input.repairStepsDone.forEach((s, i) => {
      lines.push(`${i + 1}. ${s.title}`);
      if (s.detail?.trim()) {
        lines.push(`   ${s.detail.trim()}`);
      }
    });
  }

  lines.push("");
  lines.push("--- Verification (items checked) ---");
  if (input.verifyStepsDone.length === 0) {
    lines.push("(None marked complete in app)");
  } else {
    input.verifyStepsDone.forEach((t, i) => lines.push(`${i + 1}. ${t}`));
  }

  lines.push("");
  lines.push("--- Bay notes (technician) ---");
  lines.push(input.bayNotes.trim() || "(No bay notes entered)");

  lines.push("");
  lines.push(
    "End of automated summary. Final release decisions remain with the technician and OEM / shop procedures."
  );

  return lines.join("\n");
}
