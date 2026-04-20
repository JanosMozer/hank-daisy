import { useEffect, useState } from "react";
import { glassesScenario } from "../data/content";

export type HudVehicleContext = {
  vin: string;
  vehicle: string;
  code: string;
  label: string;
  bay: string;
  pinnedNote?: string;
};

type Props = {
  context?: HudVehicleContext;
  onExit: () => void;
};

export function GlassesHUD({ context, onExit }: Props) {
  const [tick, setTick] = useState(0);

  useEffect(() => {
    const id = window.setInterval(() => setTick((t) => t + 1), 900);
    return () => window.clearInterval(id);
  }, []);

  const s = glassesScenario;
  const vin = context?.vin ?? s.vin;
  const vehicle = context?.vehicle ?? s.vehicle;
  const code = context?.code ?? s.code;
  const label = context?.label ?? s.label;
  const bay = context?.bay ?? "4";
  const pinned = context?.pinnedNote ?? "Uphill · warm · intermittent whistle";

  const rpmJitter = s.rpm + Math.round(Math.sin(tick * 0.7) * 35);
  const boostJitter = Math.max(2.8, s.boost + Math.sin(tick * 0.5) * 0.35);
  const boostPct = Math.min(100, (boostJitter / s.targetBoost) * 100);

  return (
    <div className="hud-root" role="application" aria-label="Technician AR glasses HUD">
      <button type="button" className="btn btn-ghost hud-exit" onClick={onExit}>
        Exit HUD
      </button>
      <div className="hud-scanlines" />
      <div className="hud-vignette" />
      <div className="hud-corner tl" />
      <div className="hud-corner tr" />
      <div className="hud-corner bl" />
      <div className="hud-corner br" />
      <div className="hud-crosshair" />

      <div
        className="hud-panel"
        style={{ top: 72, left: 24, width: "min(360px, calc(100vw - 48px))" }}
      >
        <h3>Vehicle</h3>
        <div className="row">
          <span className="label">VIN</span>
          <span className="value mono">{vin}</span>
        </div>
        <div className="row">
          <span className="label">Model</span>
          <span className="value">{vehicle}</span>
        </div>
        <div className="row">
          <span className="label">Session</span>
          <span className="value">LIVE · Bay {bay}</span>
        </div>
      </div>

      <div
        className="hud-panel warn"
        style={{ top: 72, right: 24, width: "min(340px, calc(100vw - 48px))" }}
      >
        <h3>Active fault</h3>
        <div className="row">
          <span className="label">DTC</span>
          <span className="value mono">{code}</span>
        </div>
        <div className="row">
          <span className="label">Description</span>
          <span className="value" style={{ textAlign: "right", maxWidth: 200 }}>
            {label}
          </span>
        </div>
        <div className="row">
          <span className="label">Severity</span>
          <span className="value" style={{ color: "#fecaca" }}>
            Underboost vs target
          </span>
        </div>
      </div>

      <div
        className="hud-panel"
        style={{ top: 220, left: 24, width: "min(360px, calc(100vw - 48px))" }}
      >
        <h3>Live data</h3>
        <div className="row">
          <span className="label">Engine RPM</span>
          <span className="value mono">{rpmJitter}</span>
        </div>
        <div className="row">
          <span className="label">Boost (meas / tgt)</span>
          <span className="value mono">
            {boostJitter.toFixed(1)} / {s.targetBoost.toFixed(1)} psi
          </span>
        </div>
        <div className="bar warn">
          <span style={{ width: `${boostPct}%` }} />
        </div>
        <div className="row" style={{ marginTop: 10 }}>
          <span className="label">Fuel trim ST / LT</span>
          <span className="value mono">
            {s.trims.short.toFixed(1)}% / {s.trims.long.toFixed(1)}%
          </span>
        </div>
      </div>

      <div
        className="hud-panel"
        style={{ top: 220, right: 24, width: "min(340px, calc(100vw - 48px))" }}
      >
        <h3>Hands-free</h3>
        <div className="row">
          <span className="label">Voice</span>
          <span className="value">“Log snapshot”</span>
        </div>
        <div className="row">
          <span className="label">Gesture</span>
          <span className="value">Pinch → next test</span>
        </div>
        <div className="row">
          <span className="label">Pinned RO note</span>
          <span className="value" style={{ textAlign: "right", maxWidth: 200, fontSize: "0.72rem" }}>
            {pinned}
          </span>
        </div>
      </div>

      <div className="hud-panel hud-tips">
        <h3>Guidance (TSB / pattern)</h3>
        <ul>
          {s.tips.map((t) => (
            <li key={t}>{t}</li>
          ))}
        </ul>
      </div>

      <div className="hud-footer">
        <div className="hud-mic">
          <span className="pulse-dot" aria-hidden />
          Listening · noise floor OK
        </div>
        <div className="mono" style={{ textAlign: "right" }}>
          AR lock: chassis reference stable
          <br />
          <span style={{ opacity: 0.75 }}>Demo overlay · not connected to OEM</span>
        </div>
      </div>
    </div>
  );
}
