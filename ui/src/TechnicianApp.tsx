import { useEffect, useMemo, useRef, useState } from "react";
import { ClosureReportModal } from "./components/ClosureReportModal";
import { HankAdvisor } from "./components/HankAdvisor";
import { GlassesHUD, type HudVehicleContext } from "./components/GlassesHUD";
import type { HankVehicleContext as HankCtx } from "./lib/hankTypes";
import { unknownCodeWalkthrough } from "./data/content";
import {
  MOCK_JOBS,
  VERIFY_ITEMS_DEFAULT,
  repairStepsForJob,
  type WorkOrder,
} from "./data/jobs";
import { buildAutoClosureSummary } from "./lib/closureSummary";

const STORAGE_KEY = "baydiag.tech.v1";

type Stored = {
  diag: Record<string, boolean>;
  repair: Record<string, boolean>;
  verify: Record<string, boolean>;
  notes: Record<string, string>;
  technicianName: string;
  /** ISO timestamps — clock starts first time you open this RO */
  jobRepairStartedAt: Record<string, string>;
};

function loadStored(): Stored {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw)
      return {
        diag: {},
        repair: {},
        verify: {},
        notes: {},
        technicianName: "",
        jobRepairStartedAt: {},
      };
    const p = JSON.parse(raw) as Stored;
    return {
      diag: p.diag ?? {},
      repair: p.repair ?? {},
      verify: p.verify ?? {},
      notes: p.notes ?? {},
      technicianName: p.technicianName ?? "",
      jobRepairStartedAt: p.jobRepairStartedAt ?? {},
    };
  } catch {
    return {
      diag: {},
      repair: {},
      verify: {},
      notes: {},
      technicianName: "",
      jobRepairStartedAt: {},
    };
  }
}

function saveStored(s: Stored) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(s));
}

const SECTION_IDS = ["job-overview", "job-diag", "job-repair", "job-verify", "job-closure"] as const;

export function TechnicianApp() {
  const [jobs, setJobs] = useState<WorkOrder[]>(() => [...MOCK_JOBS]);
  const [activeId, setActiveId] = useState<string | null>(MOCK_JOBS[0]?.id ?? null);
  const [diagIdx, setDiagIdx] = useState(0);
  const [hudOpen, setHudOpen] = useState(false);
  const [closureOpen, setClosureOpen] = useState(false);
  const [pdfToast, setPdfToast] = useState(false);
  const [activeSection, setActiveSection] = useState<string>("job-overview");
  const [clockTick, setClockTick] = useState(() => Date.now());
  const workScrollRef = useRef<HTMLElement>(null);
  const prevVerifyRef = useRef(0);
  const [stored, setStored] = useState<Stored>(() =>
    typeof window !== "undefined"
      ? loadStored()
      : {
          diag: {},
          repair: {},
          verify: {},
          notes: {},
          technicianName: "",
          jobRepairStartedAt: {},
        }
  );

  useEffect(() => {
    setStored(loadStored());
  }, []);

  useEffect(() => {
    const id = window.setInterval(() => setClockTick(Date.now()), 20000);
    return () => window.clearInterval(id);
  }, []);

  const active = useMemo(
    () => jobs.find((j) => j.id === activeId) ?? null,
    [jobs, activeId]
  );

  useEffect(() => {
    if (!active) return;
    setStored((prev) => {
      if (prev.jobRepairStartedAt[active.id]) return prev;
      const next = {
        ...prev,
        jobRepairStartedAt: {
          ...prev.jobRepairStartedAt,
          [active.id]: new Date().toISOString(),
        },
      };
      saveStored(next);
      return next;
    });
  }, [active?.id]);

  const repairSteps = useMemo(() => (active ? repairStepsForJob(active) : []), [active]);

  const repairStartedAt = useMemo(() => {
    if (!active) return null;
    const iso = stored.jobRepairStartedAt[active.id];
    return iso ? new Date(iso) : null;
  }, [active, stored.jobRepairStartedAt]);

  const elapsedMs =
    repairStartedAt != null ? Math.max(0, clockTick - repairStartedAt.getTime()) : 0;

  const formatElapsed = (ms: number) => {
    const sec = Math.floor(ms / 1000);
    const min = Math.floor(sec / 60);
    const hr = Math.floor(min / 60);
    const m = min % 60;
    const s = sec % 60;
    if (hr > 0) return `${hr}h ${m}m`;
    if (min > 0) return `${min}m ${s}s`;
    return `${s}s`;
  };

  const setTechnicianName = (name: string) => {
    setStored((prev) => {
      const next = { ...prev, technicianName: name };
      saveStored(next);
      return next;
    });
  };

  useEffect(() => {
    setActiveSection("job-overview");
  }, [activeId]);

  useEffect(() => {
    if (!activeId || !active) return;
    requestAnimationFrame(() => {
      document.getElementById("job-overview")?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  }, [activeId, active]);

  useEffect(() => {
    const root = workScrollRef.current;
    if (!root || !active) return;

    const obs = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((e) => e.isIntersecting)
          .sort((a, b) => (b.intersectionRatio ?? 0) - (a.intersectionRatio ?? 0))[0];
        if (visible?.target?.id) setActiveSection(visible.target.id);
      },
      { root, rootMargin: "-12% 0px -55% 0px", threshold: [0.08, 0.15, 0.25] }
    );

    SECTION_IDS.forEach((id) => {
      const el = document.getElementById(id);
      if (el) obs.observe(el);
    });

    return () => obs.disconnect();
  }, [active?.id]);

  const techNotes = active ? stored.notes[active.id] ?? "" : "";

  const setTechNotes = (text: string) => {
    if (!active) return;
    setStored((prev) => {
      const next = { ...prev, notes: { ...prev.notes, [active.id]: text } };
      saveStored(next);
      return next;
    });
  };

  const jobId = active?.id ?? "";

  const diagKey = (i: number) => `${jobId}|d|${i}`;
  const repairKey = (id: string) => `${jobId}|r|${id}`;
  const verifyKey = (id: string) => `${jobId}|v|${id}`;

  const toggleDiag = (i: number) => {
    if (!active) return;
    const k = `${active.id}|d|${i}`;
    setStored((prev) => {
      const next = {
        ...prev,
        diag: { ...prev.diag, [k]: !prev.diag[k] },
      };
      saveStored(next);
      return next;
    });
  };

  const toggleRepair = (rid: string) => {
    if (!active) return;
    const k = `${active.id}|r|${rid}`;
    setStored((prev) => {
      const next = {
        ...prev,
        repair: { ...prev.repair, [k]: !prev.repair[k] },
      };
      saveStored(next);
      return next;
    });
  };

  const toggleVerify = (vid: string) => {
    if (!active) return;
    const k = `${active.id}|v|${vid}`;
    setStored((prev) => {
      const next = {
        ...prev,
        verify: { ...prev.verify, [k]: !prev.verify[k] },
      };
      saveStored(next);
      return next;
    });
  };

  const diagDoneCount = unknownCodeWalkthrough.filter(
    (_, i) => stored.diag[diagKey(i)]
  ).length;

  const repairDoneCount = repairSteps.filter((s) => stored.repair[repairKey(s.id)]).length;

  const verifyDoneCount = VERIFY_ITEMS_DEFAULT.filter(
    (v) => stored.verify[verifyKey(v.id)]
  ).length;
  const totalChecklistItems =
    unknownCodeWalkthrough.length + Math.max(repairSteps.length, 1) + VERIFY_ITEMS_DEFAULT.length;
  const completedChecklistItems = diagDoneCount + repairDoneCount + verifyDoneCount;
  const progressPercent =
    totalChecklistItems > 0 ? Math.round((completedChecklistItems / totalChecklistItems) * 100) : 0;

  const autoClosureText = useMemo(() => {
    if (!active) return "";
    const diagTitles = unknownCodeWalkthrough
      .map((step, i) => (stored.diag[diagKey(i)] ? step.title : null))
      .filter((t): t is string => t != null);
    const repairDone = repairSteps
      .filter((s) => stored.repair[repairKey(s.id)])
      .map((s) => ({ title: s.title, detail: s.detail }));
    const verifyLabels = VERIFY_ITEMS_DEFAULT.filter((v) =>
      stored.verify[verifyKey(v.id)]
    ).map((v) => v.label);

    return buildAutoClosureSummary({
      concern: active.concern,
      dtcs: active.dtcs,
      bayNotes: techNotes,
      diagStepsDone: diagTitles,
      repairStepsDone: repairDone,
      verifyStepsDone: verifyLabels,
    });
  }, [active, stored, techNotes, repairSteps]);

  useEffect(() => {
    prevVerifyRef.current = verifyDoneCount;
  }, [activeId]);

  useEffect(() => {
    const prev = prevVerifyRef.current;
    prevVerifyRef.current = verifyDoneCount;

    if (!active) return;
    const total = VERIFY_ITEMS_DEFAULT.length;
    if (verifyDoneCount !== total || prev >= total) return;

    const solution = buildAutoClosureSummary({
      concern: active.concern,
      dtcs: active.dtcs,
      bayNotes: techNotes,
      diagStepsDone: unknownCodeWalkthrough
        .map((step, i) => (stored.diag[`${active.id}|d|${i}`] ? step.title : null))
        .filter((t): t is string => t != null),
      repairStepsDone: repairSteps
        .filter((s) => stored.repair[`${active.id}|r|${s.id}`])
        .map((s) => ({ title: s.title, detail: s.detail })),
      verifyStepsDone: VERIFY_ITEMS_DEFAULT.filter((v) =>
        stored.verify[`${active.id}|v|${v.id}`]
      ).map((v) => v.label),
    });

    const iso = stored.jobRepairStartedAt[active.id];
    const started = iso ? new Date(iso) : new Date();
    const dtcsSummary =
      active.dtcs
        .map((d) => `${d.code}: ${d.description}${d.pending ? " (pending)" : ""}`)
        .join("\n") || "None listed";

    void (async () => {
      try {
        const { downloadClosurePdf } = await import("./lib/closurePdf");
        await downloadClosurePdf({
          technicianName: stored.technicianName,
          ro: active.ro,
          bay: active.bay,
          vin: active.vin,
          vehicle: active.vehicle,
          concern: active.concern,
          writerNotes: active.writerNotes,
          bayNotes: techNotes,
          dtcsSummary,
          repairStartedAt: started,
          repairCompletedAt: new Date(),
          solutionText: solution,
          images: [],
        });
        setPdfToast(true);
        window.setTimeout(() => setPdfToast(false), 7000);
      } catch {
        /* PDF may fail in restricted environments */
      }
    })();
  }, [verifyDoneCount, active, stored, techNotes, repairSteps]);

  const primaryDtc = active?.dtcs[0];

  const hankVehicleContext: HankCtx | null = active
    ? {
        ro: active.ro,
        bay: active.bay,
        vin: active.vin,
        vehicle: active.vehicle,
        concern: active.concern,
        writerNotes: active.writerNotes,
        dtcs: active.dtcs,
      }
    : null;

  const hudContext: HudVehicleContext | undefined =
    active && primaryDtc
      ? {
          vin: active.vin,
          vehicle: active.vehicle,
          code: primaryDtc.code,
          label: primaryDtc.description,
          bay: active.bay,
          pinnedNote: active.concern.slice(0, 120),
        }
      : active
        ? {
            vin: active.vin,
            vehicle: active.vehicle,
            code: "—",
            label: "No code logged",
            bay: active.bay,
            pinnedNote: active.concern.slice(0, 120),
          }
        : undefined;

  const resetJobProgress = () => {
    if (!active) return;
    const id = active.id;
    setStored((prev) => {
      const nextDiag = { ...prev.diag };
      const nextRepair = { ...prev.repair };
      const nextVerify = { ...prev.verify };
      Object.keys(nextDiag).forEach((k) => {
        if (k.startsWith(`${id}|`)) delete nextDiag[k];
      });
      Object.keys(nextRepair).forEach((k) => {
        if (k.startsWith(`${id}|`)) delete nextRepair[k];
      });
      Object.keys(nextVerify).forEach((k) => {
        if (k.startsWith(`${id}|`)) delete nextVerify[k];
      });
      const next = { ...prev, diag: nextDiag, repair: nextRepair, verify: nextVerify };
      saveStored(next);
      return next;
    });
  };

  const [newCode, setNewCode] = useState("");
  const [newDesc, setNewDesc] = useState("");

  const addDtc = () => {
    if (!active || !newCode.trim()) return;
    const code = newCode.trim().toUpperCase();
    const description = newDesc.trim() || "Description pending";
    setJobs((prev) =>
      prev.map((j) =>
        j.id === active.id
          ? {
              ...j,
              dtcs: [...j.dtcs, { code, description, pending: true }],
            }
          : j
      )
    );
    setNewCode("");
    setNewDesc("");
  };

  const jumpTo = (id: (typeof SECTION_IDS)[number]) => {
    document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  if (hudOpen && active && hudContext) {
    return <GlassesHUD context={hudContext} onExit={() => setHudOpen(false)} />;
  }

  return (
    <div className="tech-app-frame">
      {closureOpen && active && (
        <ClosureReportModal
          open
          onClose={() => setClosureOpen(false)}
          job={active}
          technicianName={stored.technicianName}
          onTechnicianNameChange={setTechnicianName}
          bayNotes={techNotes}
          repairStartedAt={repairStartedAt}
          autoSummary={autoClosureText}
        />
      )}

      {pdfToast && (
        <div className="pdf-toast" role="status">
          Closure PDF downloaded — check your Downloads folder to email or upload.
        </div>
      )}

      <div className="tech-layout">
        <aside className="tech-queue" aria-label="Repair order queue">
          <div className="tech-queue-brand-block">
            <span className="tech-brand-mark">Hank</span>
            <span className="tech-brand-tag">Copilot</span>
            <span className="tech-brand-ai">for technicians</span>
          </div>
          <div className="tech-queue-head">
            <h2>Open ROs</h2>
            <span className="badge">{jobs.length}</span>
          </div>
          <ul className="tech-queue-list">
            {jobs.map((j) => (
              <li key={j.id}>
                <button
                  type="button"
                  className={`queue-card ${j.id === activeId ? "active" : ""}`}
                  onClick={() => {
                    setActiveId(j.id);
                    setDiagIdx(0);
                  }}
                >
                  <div className="queue-card-top">
                    <span className="mono">RO {j.ro}</span>
                    <span className="bay-pill">Bay {j.bay}</span>
                  </div>
                  <div className="queue-vehicle">{j.vehicle}</div>
                  <div className="queue-meta">
                    {j.dtcs.length ? (
                      <span className="mono">{j.dtcs.map((d) => d.code).join(", ")}</span>
                    ) : (
                      <span className="muted-small">No codes on RO</span>
                    )}
                  </div>
                </button>
              </li>
            ))}
          </ul>
        </aside>

        <div className="tech-main-area">
          <div className="tech-hank-column">
            <HankAdvisor
              key={activeId ?? "none"}
              variant="embedded"
              context={hankVehicleContext}
            />
          </div>

          <div className="tech-checklist-column tech-work-column">
          {!active ? (
            <div className="tech-empty-main">
              <div className="panel empty-bay">
                <div className="empty-bay-visual" aria-hidden />
                <h2>Select a repair order</h2>
                <p className="lead">
                  Choose an RO in the queue. Hank stays open in the center so you can ask questions anytime.
                </p>
              </div>
            </div>
          ) : (
            <>
              <header className="tech-context-header">
                <div className="tech-context-primary">
                  <div className="tech-context-titles">
                    <span className="tech-context-ro mono">RO {active.ro}</span>
                    <span className="bay-pill">{active.bay}</span>
                    <span className={`status-pill status-${active.status}`}>{active.status}</span>
                  </div>
                  <p className="tech-context-vin mono">{active.vin}</p>
                  <p className="tech-context-vehicle">{active.vehicle}</p>
                  <div className="tech-code-chips" aria-label="Codes on RO">
                    {active.dtcs.length === 0 ? (
                      <span className="tech-code-chip muted">No codes</span>
                    ) : (
                      active.dtcs.map((d) => (
                        <span key={d.code + d.description} className="tech-code-chip mono">
                          {d.code}
                          {d.pending ? <span className="tech-code-dot" title="Pending" /> : null}
                        </span>
                      ))
                    )}
                  </div>
                  <label className="tech-name-inline">
                    <span className="tech-name-label">Technician</span>
                    <input
                      type="text"
                      className="tech-name-input"
                      value={stored.technicianName}
                      onChange={(e) => setTechnicianName(e.target.value)}
                      placeholder="Name on closure PDF"
                      autoComplete="name"
                    />
                  </label>
                </div>
                <div className="tech-progress-mini" aria-label="Progress">
                  <span className="tech-progress-bit">Overall {progressPercent}%</span>
                  <span className="tech-progress-bit">
                    Diag {diagDoneCount}/{unknownCodeWalkthrough.length}
                  </span>
                  <span className="tech-progress-bit">
                    Repair {repairDoneCount}/{Math.max(repairSteps.length, 1)}
                  </span>
                  <span className="tech-progress-bit">
                    Verify {verifyDoneCount}/{VERIFY_ITEMS_DEFAULT.length}
                  </span>
                </div>
                <div className="hank-ops-strip" aria-label="Job status at a glance">
                  <article className="hank-ops-card">
                    <span className="hank-ops-label">Active concern</span>
                    <p className="hank-ops-value">{active.concern}</p>
                  </article>
                  <article className="hank-ops-card">
                    <span className="hank-ops-label">Codes logged</span>
                    <p className="hank-ops-value mono">{active.dtcs.length || 0}</p>
                  </article>
                  <article className="hank-ops-card">
                    <span className="hank-ops-label">Checklist complete</span>
                    <p className="hank-ops-value mono">{completedChecklistItems}</p>
                  </article>
                  <article className="hank-ops-card">
                    <span className="hank-ops-label">Repair timer</span>
                    <p className="hank-ops-value mono">{formatElapsed(elapsedMs)}</p>
                  </article>
                </div>
              </header>

              <div className="tech-sticky-nav-wrap">
                <nav className="tech-jump-nav" aria-label="Jump to section">
                  {(
                    [
                      ["job-overview", "Overview"],
                      ["job-diag", "Diagnose"],
                      ["job-repair", "Repair"],
                      ["job-verify", "Verify"],
                      ["job-closure", "Closure"],
                    ] as const
                  ).map(([id, label]) => (
                    <button
                      key={id}
                      type="button"
                      className={`tech-jump-link ${activeSection === id ? "active" : ""}`}
                      onClick={() => jumpTo(id)}
                    >
                      {label}
                    </button>
                  ))}
                </nav>
              </div>

              <main ref={workScrollRef} className="tech-work-scroll">
                <section id="job-overview" className="tech-section panel">
                  <div className="tech-section-head">
                    <h2 className="panel-title">Overview</h2>
                  </div>
                  <p className="concern-lead">{active.concern}</p>
                  <h3 className="subheading">Writer</h3>
                  <p className="notes-from-writer">{active.writerNotes}</p>
                  <label className="field-label" htmlFor="tech-notes">
                    Bay notes{" "}
                    <span className="field-label-hint">saved on this device</span>
                  </label>
                  <textarea
                    id="tech-notes"
                    className="tech-textarea"
                    rows={4}
                    value={techNotes}
                    onChange={(e) => setTechNotes(e.target.value)}
                    placeholder="Repro plan, measurements, photos, what you ruled out…"
                  />
                </section>

                <section id="job-diag" className="tech-section panel">
                  <div className="panel-head">
                    <h2 className="panel-title">Diagnose</h2>
                    <span className="badge soft">
                      {diagDoneCount}/{unknownCodeWalkthrough.length} steps
                    </span>
                  </div>

                  <div className="job-diag-split">
                    <div className="job-diag-col">
                      <h3 className="subheading-inline">Codes</h3>
                      <div className="dtc-table">
                        <div className="dtc-row head">
                          <span>Code</span>
                          <span>Description</span>
                          <span>Pending</span>
                        </div>
                        {active.dtcs.map((d) => (
                          <div key={d.code + d.description} className="dtc-row">
                            <span className="mono">{d.code}</span>
                            <span>{d.description}</span>
                            <span>{d.pending ? "Yes" : "Stored"}</span>
                          </div>
                        ))}
                      </div>
                      <div className="add-dtc">
                        <input
                          className="tech-input mono"
                          placeholder="P-code"
                          value={newCode}
                          onChange={(e) => setNewCode(e.target.value)}
                          aria-label="Add DTC code"
                        />
                        <input
                          className="tech-input"
                          placeholder="Description"
                          value={newDesc}
                          onChange={(e) => setNewDesc(e.target.value)}
                          aria-label="DTC description"
                        />
                        <button type="button" className="btn btn-ghost btn-sm" onClick={addDtc}>
                          Add
                        </button>
                      </div>
                    </div>

                    <div className="job-diag-col">
                      <div className="panel-head nested">
                        <h3 className="subheading-inline">Unfamiliar code workflow</h3>
                        <div className="step-nav">
                          <button
                            type="button"
                            className="btn btn-ghost btn-sm"
                            onClick={() => setDiagIdx((i) => Math.max(0, i - 1))}
                            disabled={diagIdx === 0}
                          >
                            Prev
                          </button>
                          <button
                            type="button"
                            className="btn btn-sm"
                            onClick={() =>
                              setDiagIdx((i) =>
                                Math.min(unknownCodeWalkthrough.length - 1, i + 1)
                              )
                            }
                            disabled={diagIdx === unknownCodeWalkthrough.length - 1}
                          >
                            Next
                          </button>
                        </div>
                      </div>
                      <article className="diag-focus">
                        <div className="diag-focus-head">
                          <h3>{unknownCodeWalkthrough[diagIdx].title}</h3>
                          <label className="check-inline">
                            <input
                              type="checkbox"
                              checked={!!stored.diag[diagKey(diagIdx)]}
                              onChange={() => toggleDiag(diagIdx)}
                            />
                            Done
                          </label>
                        </div>
                        <p>{unknownCodeWalkthrough[diagIdx].body}</p>
                        <p className="pitfall-mini">
                          <strong>Avoid:</strong> {unknownCodeWalkthrough[diagIdx].pitfall}
                        </p>
                      </article>
                      <ul className="diag-all">
                        {unknownCodeWalkthrough.map((step, i) => (
                          <li key={step.title}>
                            <label className="check-row">
                              <input
                                type="checkbox"
                                checked={!!stored.diag[diagKey(i)]}
                                onChange={() => toggleDiag(i)}
                              />
                              <span>{step.title}</span>
                            </label>
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>
                </section>

                <section id="job-repair" className="tech-section panel">
                  <div className="panel-head">
                    <h2 className="panel-title">Repair</h2>
                    <span className="badge soft">
                      {repairDoneCount}/{repairSteps.length}
                    </span>
                  </div>
                  <p className="lead tight">
                    Demo checklist — follow OEM SI and your shop SOP on the floor.
                  </p>
                  <ul className="repair-list">
                    {repairSteps.map((s) => (
                      <li key={s.id} className="repair-item">
                        <label className="check-row block">
                          <input
                            type="checkbox"
                            checked={!!stored.repair[repairKey(s.id)]}
                            onChange={() => toggleRepair(s.id)}
                          />
                          <div>
                            <strong>{s.title}</strong>
                            <p>{s.detail}</p>
                          </div>
                        </label>
                      </li>
                    ))}
                  </ul>
                </section>

                <section id="job-verify" className="tech-section panel">
                  <div className="panel-head">
                    <h2 className="panel-title">Verify</h2>
                    <span className="badge soft">
                      {verifyDoneCount}/{VERIFY_ITEMS_DEFAULT.length}
                    </span>
                  </div>
                  <ul className="verify-list">
                    {VERIFY_ITEMS_DEFAULT.map((v) => (
                      <li key={v.id}>
                        <label className="check-row block">
                          <input
                            type="checkbox"
                            checked={!!stored.verify[verifyKey(v.id)]}
                            onChange={() => toggleVerify(v.id)}
                          />
                          <span>{v.label}</span>
                        </label>
                      </li>
                    ))}
                  </ul>
                </section>

                <section id="job-closure" className="tech-section panel closure-panel">
                  <div className="panel-head">
                    <h2 className="panel-title">Closure report</h2>
                  </div>
                  <p className="lead tight closure-lead">
                    When every Verify item is checked, a closure PDF downloads automatically with work summary
                    built from your Diagnose / Repair / Verify checklists and bay notes. Open below to add photos or
                    download again.
                  </p>
                  <div className="closure-summary">
                    <div className="closure-summary-item">
                      <span className="closure-summary-label">Repair clock</span>
                      <span className="closure-summary-value mono">
                        {repairStartedAt
                          ? repairStartedAt.toLocaleString(undefined, {
                              dateStyle: "medium",
                              timeStyle: "short",
                            })
                          : "—"}
                      </span>
                    </div>
                    <div className="closure-summary-item">
                      <span className="closure-summary-label">Elapsed (this RO)</span>
                      <span className="closure-summary-value mono">{formatElapsed(elapsedMs)}</span>
                    </div>
                    <div className="closure-summary-item">
                      <span className="closure-summary-label">Technician</span>
                      <span className="closure-summary-value">
                        {stored.technicianName.trim() || "— (set in header)"}
                      </span>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="btn closure-open-btn"
                    onClick={() => setClosureOpen(true)}
                  >
                    Finalize & download PDF
                  </button>
                </section>

                <div className="tech-work-footer-space" aria-hidden />
              </main>

              <div className="tech-fab-stack" aria-label="Quick actions">
                <button
                  type="button"
                  className="tech-fab tech-fab-secondary"
                  onClick={() => setHudOpen(true)}
                  title="Wear HUD"
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
                    <path
                      d="M4 10a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v3a3 3 0 0 1-3 3h-1l-1 3H9l-1-3H7a3 3 0 0 1-3-3v-3Z"
                      stroke="currentColor"
                      strokeWidth="1.6"
                      strokeLinejoin="round"
                    />
                    <path d="M9 14h6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
                  </svg>
                  HUD
                </button>
              </div>

              <button
                type="button"
                className="tech-reset-link"
                onClick={resetJobProgress}
              >
                Reset job checklists
              </button>
            </>
          )}
          </div>
        </div>
      </div>
    </div>
  );
}
