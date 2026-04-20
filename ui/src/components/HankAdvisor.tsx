import { useCallback, useEffect, useRef, useState } from "react";
import { askHank } from "../lib/hankClient";
import type { HankMessage, HankVehicleContext } from "../lib/hankTypes";
import {
  MAX_FILE_BYTES,
  MAX_IMAGES_PER_MESSAGE,
  fileToVisionDataUrl,
} from "../lib/hankImage";

export type HankAdvisorVariant = "modal" | "embedded";

type Props = {
  context: HankVehicleContext | null;
  /** `embedded` = always-visible column (no overlay). `modal` = slide-over dialog. */
  variant?: HankAdvisorVariant;
  onClose?: () => void;
};

export function HankAdvisor({ context, variant = "modal", onClose }: Props) {
  const [messages, setMessages] = useState<HankMessage[]>(() => [
    {
      role: "assistant",
      content:
        "I'm Hank — ask anything about diagnosis, tests, OEM workflow, or how to lay out a repair. " +
        "Attach photos (connector, leak, noise area, dash warning) and I'll walk through what to verify next. " +
        "You still confirm torque, calibrations, and TSB applicability in OEM SI for this VIN.",
    },
  ]);
  const [input, setInput] = useState("");
  const [pendingImages, setPendingImages] = useState<string[]>([]);
  const [attachError, setAttachError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [source, setSource] = useState<"live" | "fallback" | "fallback_error" | null>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const addImageDataUrls = useCallback(async (files: FileList | File[]) => {
    setAttachError(null);
    const toAdd: string[] = [];
    for (const file of Array.from(files)) {
      if (file.size > MAX_FILE_BYTES) {
        setAttachError("One file is too large (max ~12 MB before resize).");
        continue;
      }
      try {
        toAdd.push(await fileToVisionDataUrl(file));
      } catch {
        setAttachError("Could not read an image file.");
      }
    }
    if (toAdd.length === 0) return;

    setPendingImages((prev) => {
      const room = MAX_IMAGES_PER_MESSAGE - prev.length;
      if (room <= 0) {
        setAttachError(`Max ${MAX_IMAGES_PER_MESSAGE} images per message.`);
        return prev;
      }
      const sliced = toAdd.slice(0, room);
      if (toAdd.length > room) {
        setAttachError(`Only ${MAX_IMAGES_PER_MESSAGE} images per send — extra files ignored.`);
      }
      return [...prev, ...sliced];
    });
  }, []);

  const removePendingAt = (index: number) => {
    setPendingImages((p) => p.filter((_, i) => i !== index));
    setAttachError(null);
  };

  const send = async () => {
    const trimmed = input.trim();
    const defaultPrompt =
      "Analyze these photos for diagnostic clues (damage, leaks, routing, connector condition). What should I verify next in the bay?";
    const text =
      trimmed ||
      (pendingImages.length > 0 ? defaultPrompt : "");

    if (loading || !text) return;

    const nextUser: HankMessage = {
      role: "user",
      content: text,
      ...(pendingImages.length > 0 ? { images: [...pendingImages] } : {}),
    };

    setInput("");
    setPendingImages([]);
    setAttachError(null);
    setMessages((m) => [...m, nextUser]);
    setLoading(true);
    try {
      const history = [...messages, nextUser];
      const { reply, source: src } = await askHank(history, context);
      setSource(src);
      setMessages((m) => [...m, { role: "assistant", content: reply }]);
    } finally {
      setLoading(false);
    }
  };

  const canSend =
    !loading && (input.trim().length > 0 || pendingImages.length > 0);

  const embedded = variant === "embedded";

  const panel = (
    <div
      className={`hank-panel ${embedded ? "hank-panel--embedded" : ""}`}
      onClick={embedded ? undefined : (e) => e.stopPropagation()}
    >
      <header className={`hank-header ${embedded ? "hank-header--embedded" : ""}`}>
        <div className="hank-header-titles">
          <span className="hank-product-pill" aria-hidden>
            Copilot
          </span>
          <h2 id={embedded ? "hank-title-embed" : "hank-title"} className="hank-title">
            Hank
          </h2>
          <p className="hank-sub">
            {embedded
              ? "Your bay advisor — text + photos"
              : "Bay advisor · text + photo vision"}
          </p>
        </div>
        {!embedded && onClose ? (
          <button type="button" className="btn btn-ghost btn-sm" onClick={onClose}>
            Close
          </button>
        ) : null}
      </header>

      {context ? (
        <div className="hank-context mono">
          RO {context.ro} · Bay {context.bay} · {context.vehicle}
        </div>
      ) : (
        <div className="hank-context warn">
          Pick a repair order on the left — answers stay generic until an RO is loaded.
        </div>
      )}

      <div className="hank-messages">
        {messages.map((msg, i) => (
          <div key={i} className={`hank-bubble ${msg.role}`}>
            <span className="hank-role">{msg.role === "user" ? "You" : "Hank"}</span>
            {msg.images && msg.images.length > 0 && (
              <div className="hank-image-grid">
                {msg.images.map((src, ii) => (
                  <img key={ii} className="hank-thumb" src={src} alt="" />
                ))}
              </div>
            )}
            <pre className="hank-text">{msg.content}</pre>
          </div>
        ))}
        {loading && (
          <div className="hank-bubble assistant hank-bubble-pending">
            <span className="hank-role">Hank</span>
            <p className="hank-thinking" aria-live="polite">
              <span className="hank-thinking-label">Thinking</span>
              <span className="hank-typing-dots" aria-hidden>
                <span />
                <span />
                <span />
              </span>
            </p>
          </div>
        )}
        <div ref={endRef} />
      </div>

      <div className="hank-compose">
        {pendingImages.length > 0 && (
          <div className="hank-pending-images">
            {pendingImages.map((src, i) => (
              <div key={i} className="hank-pending-slot">
                <img src={src} alt="" className="hank-thumb" />
                <button
                  type="button"
                  className="hank-remove-img"
                  onClick={() => removePendingAt(i)}
                  aria-label="Remove image"
                >
                  ×
                </button>
              </div>
            ))}
          </div>
        )}
        {attachError && <p className="hank-attach-error">{attachError}</p>}

        <div className="hank-compose-toolbar">
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            multiple
            className="hank-file-input"
            onChange={(e) => {
              const f = e.target.files;
              if (f?.length) void addImageDataUrls(f);
              e.target.value = "";
            }}
          />
          <button
            type="button"
            className="hank-attach-btn"
            onClick={() => fileRef.current?.click()}
            disabled={loading || pendingImages.length >= MAX_IMAGES_PER_MESSAGE}
          >
            Photo
          </button>
        </div>

        <textarea
          className="hank-input"
          rows={embedded ? 2 : 3}
          placeholder="Ask Hank, or attach photos… (paste image from clipboard)"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onPaste={(e) => {
            const items = e.clipboardData?.items;
            if (!items) return;
            for (let i = 0; i < items.length; i++) {
              if (items[i].type.startsWith("image/")) {
                e.preventDefault();
                const f = items[i].getAsFile();
                if (f) void addImageDataUrls([f]);
              }
            }
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              void send();
            }
          }}
          disabled={loading}
        />
        <button type="button" className="btn" onClick={() => void send()} disabled={!canSend}>
          Send
        </button>
      </div>

      <footer className={`hank-footer ${embedded ? "hank-footer--embedded" : ""}`}>
        <p>
          <strong>Not OEM service information.</strong> Photo answers use the vision model when the proxy is
          running with your API key. Always confirm in OEM SI.
        </p>
        {source && (
          <p className="hank-source">
            Mode:{" "}
            {source === "live"
              ? "Live model (vision + text)"
              : source === "fallback"
                ? "Proxy message / offline fallback"
                : "Local fallback (start proxy + OPENAI_API_KEY for vision)"}
          </p>
        )}
      </footer>
    </div>
  );

  if (embedded) {
    return (
      <section className="hank-embed" aria-labelledby="hank-title-embed">
        {panel}
      </section>
    );
  }

  return (
    <div
      className="hank-backdrop"
      role="dialog"
      aria-labelledby="hank-title"
      aria-modal="true"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose?.();
      }}
    >
      {panel}
    </div>
  );
}
