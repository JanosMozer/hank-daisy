import { jsPDF } from "jspdf";

export type ClosurePdfInput = {
  technicianName: string;
  ro: string;
  bay: string;
  vin: string;
  vehicle: string;
  concern: string;
  writerNotes: string;
  bayNotes: string;
  dtcsSummary: string;
  repairStartedAt: Date;
  repairCompletedAt: Date;
  solutionText: string;
  /** JPEG/PNG data URLs */
  images: string[];
};

function fmtLocal(d: Date): string {
  return d.toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

function formatDuration(ms: number): string {
  if (ms < 0) ms = 0;
  const sec = Math.floor(ms / 1000);
  const min = Math.floor(sec / 60);
  const hr = Math.floor(min / 60);
  const m = min % 60;
  const s = sec % 60;
  if (hr > 0) return `${hr}h ${m}m`;
  if (min > 0) return `${min}m ${s}s`;
  return `${s}s`;
}

function stripForPdf(dataUrl: string): { fmt: "JPEG" | "PNG"; data: string } {
  const m = dataUrl.match(/^data:image\/(jpeg|jpg|png);base64,(.+)$/i);
  if (m) {
    return { fmt: m[1].toLowerCase() === "png" ? "PNG" : "JPEG", data: m[2] };
  }
  const comma = dataUrl.indexOf(",");
  if (comma > 0 && dataUrl.startsWith("data:image")) {
    const header = dataUrl.slice(5, comma);
    const isPng = header.includes("png");
    return { fmt: isPng ? "PNG" : "JPEG", data: dataUrl.slice(comma + 1) };
  }
  return { fmt: "JPEG", data: dataUrl };
}

function loadImageSize(src: string): Promise<{ w: number; h: number }> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () =>
      resolve({ w: img.naturalWidth || img.width, h: img.naturalHeight || img.height });
    img.onerror = () => reject(new Error("Image load failed"));
    img.src = src;
  });
}

export async function downloadClosurePdf(input: ClosurePdfInput): Promise<void> {
  const doc = new jsPDF({ unit: "mm", format: "a4" });
  const margin = 14;
  const pageW = doc.internal.pageSize.getWidth();
  const pageH = doc.internal.pageSize.getHeight();
  const maxW = pageW - 2 * margin;
  let y = 16;

  const ensureSpace = (neededMm: number) => {
    if (y + neededMm > pageH - 12) {
      doc.addPage();
      y = 16;
    }
  };

  const addTitle = (text: string) => {
    doc.setFontSize(16);
    doc.setFont("helvetica", "bold");
    doc.text(text, margin, y);
    y += 9;
    doc.setFont("helvetica", "normal");
    doc.setFontSize(10);
  };

  const addLine = (label: string, value: string) => {
    doc.setFontSize(10);
    doc.setFont("helvetica", "bold");
    doc.text(label, margin, y);
    y += 5;
    doc.setFont("helvetica", "normal");
    const lines = doc.splitTextToSize(value || "—", maxW);
    doc.text(lines, margin, y);
    y += lines.length * 4.8 + 5;
    ensureSpace(0);
  };

  const addBlock = (heading: string, body: string) => {
    ensureSpace(16);
    doc.setFontSize(11);
    doc.setFont("helvetica", "bold");
    doc.text(heading, margin, y);
    y += 6;
    doc.setFont("helvetica", "normal");
    doc.setFontSize(10);
    const lines = doc.splitTextToSize(body || "—", maxW);
    doc.text(lines, margin, y);
    y += lines.length * 4.8 + 8;
    ensureSpace(0);
  };

  const durationMs =
    input.repairCompletedAt.getTime() - input.repairStartedAt.getTime();

  addTitle("Repair closure report");
  doc.setFontSize(9);
  doc.setTextColor(100);
  doc.text(`BayDx · Generated ${fmtLocal(input.repairCompletedAt)}`, margin, y);
  doc.setTextColor(0);
  y += 10;

  addLine("Repair order (RO)", input.ro);
  addLine("Bay", input.bay);
  addLine("VIN", input.vin);
  addLine("Vehicle", input.vehicle);
  addLine("Technician", input.technicianName.trim() || "—");
  addLine("Repair started", fmtLocal(input.repairStartedAt));
  addLine("Repair completed", fmtLocal(input.repairCompletedAt));
  addLine("Total time on job", formatDuration(durationMs));

  addBlock("Customer concern", input.concern);
  addBlock("Service writer notes", input.writerNotes);
  if (input.dtcsSummary.trim()) {
    addBlock("DTCs on RO", input.dtcsSummary);
  }
  if (input.bayNotes.trim()) {
    addBlock("Bay notes", input.bayNotes);
  }
  addBlock("Solution / work performed", input.solutionText);

  for (let i = 0; i < input.images.length; i++) {
    const url = input.images[i];
    let dw: number;
    let dh: number;
    try {
      const { w, h } = await loadImageSize(url);
      const ratio = h / w;
      dw = maxW;
      dh = dw * ratio;
      const maxH = 100;
      if (dh > maxH) {
        dh = maxH;
        dw = dh / ratio;
      }
    } catch {
      dw = maxW;
      dh = 60;
    }

    ensureSpace(dh + 14);
    doc.setFontSize(10);
    doc.setFont("helvetica", "bold");
    doc.text(`Photo ${i + 1}`, margin, y);
    y += 5;
    const { fmt, data } = stripForPdf(url);
    try {
      doc.addImage(data, fmt, margin, y, dw, dh);
    } catch {
      doc.setFont("helvetica", "normal");
      doc.text("(Could not embed image)", margin, y);
      y += 8;
      continue;
    }
    y += dh + 10;
  }

  const safeRo = input.ro.replace(/[^\dA-Za-z-]/g, "");
  const stamp = input.repairCompletedAt.toISOString().slice(0, 19).replace(/[:T]/g, "-");
  doc.save(`RO-${safeRo || "report"}-closure-${stamp}.pdf`);
}
