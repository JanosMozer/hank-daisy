import { useCallback, useEffect, useRef, useState } from "react";
import {
  MAX_FILE_BYTES,
  fileToVisionDataUrl,
} from "../lib/hankImage";
import type { WorkOrder } from "../data/jobs";

const MAX_PHOTOS = 6;

type Props = {
  open: boolean;
  onClose: () => void;
  job: WorkOrder;
  technicianName: string;
  onTechnicianNameChange: (name: string) => void;
  bayNotes: string;
  repairStartedAt: Date | null;
  /** Pre-built from Diagnose / Repair / Verify checklists + bay notes */
  autoSummary: string;
};

export function ClosureReportModal({
  open,
  onClose,
  job,
  technicianName,
  onTechnicianNameChange,
  bayNotes,
  repairStartedAt,
  autoSummary,
}: Props) {
  const [solution, setSolution] = useState(autoSummary);
  const [photos, setPhotos] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const started = repairStartedAt ?? new Date();

  useEffect(() => {
    if (open) {
      setSolution(autoSummary);
      setPhotos([]);
      setError(null);
    }
  }, [open, autoSummary]);

  const addPhotos = useCallback(async (files: FileList | File[]) => {
    setError(null);
    const next: string[] = [];
    for (const file of Array.from(files)) {
      if (file.size > MAX_FILE_BYTES) {
        setError("A file is too large.");
        continue;
      }
      try {
        next.push(await fileToVisionDataUrl(file));
      } catch {
        setError("Could not read an image.");
      }
    }
    if (!next.length) return;
    setPhotos((prev) => {
      const room = MAX_PHOTOS - prev.length;
      if (room <= 0) {
        setError(`Maximum ${MAX_PHOTOS} photos.`);
        return prev;
      }
      const slice = next.slice(0, room);
      if (next.length > room) setError(`Only ${MAX_PHOTOS} photos allowed.`);
      return [...prev, ...slice];
    });
  }, []);

  const removePhoto = (i: number) => {
    setPhotos((p) => p.filter((_, j) => j !== i));
    setError(null);
  };

  const generatePdf = async () => {
    const body = solution.trim() || autoSummary.trim();
    if (!body) {
      setError("Nothing to print — complete checklists or bay notes first.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const { downloadClosurePdf } = await import("../lib/closurePdf");
      const completedAt = new Date();
      const dtcsSummary =
        job.dtcs.map((d) => `${d.code}: ${d.description}${d.pending ? " (pending)" : ""}`).join("\n") ||
        "None listed";

      await downloadClosurePdf({
        technicianName,
        ro: job.ro,
        bay: job.bay,
        vin: job.vin,
        vehicle: job.vehicle,
        concern: job.concern,
        writerNotes: job.writerNotes,
        bayNotes,
        dtcsSummary,
        repairStartedAt: started,
        repairCompletedAt: completedAt,
        solutionText: body,
        images: photos,
      });
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : "PDF failed.");
    } finally {
      setBusy(false);
    }
  };

  if (!open) return null;

  return (
    <div
      className="closure-backdrop"
      role="dialog"
      aria-labelledby="closure-title"
      aria-modal="true"
      onClick={(e) => {
        if (e.target === e.currentTarget && !busy) onClose();
      }}
    >
      <div className="closure-modal" onClick={(e) => e.stopPropagation()}>
        <header className="closure-modal-head">
          <div>
            <h2 id="closure-title" className="closure-title">
              Closure report & PDF
            </h2>
            <p className="closure-sub">RO {job.ro} · summary is auto-built from your workflow</p>
          </div>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => !busy && onClose()}>
            Close
          </button>
        </header>

        <div className="closure-body">
          <label className="closure-field">
            <span className="closure-label">Technician name</span>
            <input
              type="text"
              className="tech-input"
              value={technicianName}
              onChange={(e) => onTechnicianNameChange(e.target.value)}
              placeholder="Your name (printed on PDF)"
              disabled={busy}
            />
          </label>

          <div className="closure-timing">
            <div>
              <span className="closure-mini-label">Repair clock started</span>
              <p className="closure-mini-val">{started.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}</p>
            </div>
            <div>
              <span className="closure-mini-label">PDF completed time</span>
              <p className="closure-mini-val muted">This download (or last auto-download after Verify)</p>
            </div>
          </div>

          <label className="closure-field">
            <span className="closure-label">Work performed (auto-generated)</span>
            <p className="closure-hint">
              Pulled from Diagnose / Repair / Verify checklists and bay notes. Edit if you need to add detail.
            </p>
            <textarea
              className="tech-textarea closure-auto-textarea"
              rows={12}
              value={solution}
              onChange={(e) => setSolution(e.target.value)}
              placeholder="Fills automatically from the workflow…"
              disabled={busy}
            />
          </label>

          <div className="closure-photos-block">
            <span className="closure-label">Photos (optional)</span>
            <div className="closure-photo-actions">
              <input
                ref={fileRef}
                type="file"
                accept="image/*"
                multiple
                className="hank-file-input"
                onChange={(e) => {
                  const f = e.target.files;
                  if (f?.length) void addPhotos(f);
                  e.target.value = "";
                }}
              />
              <button
                type="button"
                className="hank-attach-btn"
                onClick={() => fileRef.current?.click()}
                disabled={busy || photos.length >= MAX_PHOTOS}
              >
                Add photos ({photos.length}/{MAX_PHOTOS})
              </button>
            </div>
            {photos.length > 0 && (
              <div className="hank-pending-images closure-photo-grid">
                {photos.map((src, i) => (
                  <div key={i} className="hank-pending-slot">
                    <img src={src} alt="" className="hank-thumb" />
                    <button
                      type="button"
                      className="hank-remove-img"
                      onClick={() => removePhoto(i)}
                      aria-label="Remove photo"
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {error && <p className="hank-attach-error">{error}</p>}
        </div>

        <footer className="closure-footer">
          <button type="button" className="btn btn-ghost" onClick={() => !busy && onClose()} disabled={busy}>
            Cancel
          </button>
          <button type="button" className="btn" onClick={() => void generatePdf()} disabled={busy}>
            {busy ? "Building PDF…" : "Download PDF"}
          </button>
        </footer>
      </div>
    </div>
  );
}
