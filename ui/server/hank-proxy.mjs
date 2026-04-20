/**
 * Hank advisor backend — keeps OPENAI_API_KEY off the browser.
 * Vision: send images as data URLs in messages[].images — mapped to OpenAI image_url parts.
 * Run: OPENAI_API_KEY=sk-... node server/hank-proxy.mjs
 */
import http from "http";

const PORT = Number(process.env.HANK_PORT || 8787);
const OPENAI_KEY = process.env.OPENAI_API_KEY;
const MODEL = process.env.OPENAI_MODEL || "gpt-4o-mini";

const SYSTEM_PROMPT = `You are Hank, an ASE Master Technician–level diagnostic and repair advisor embedded in dealership service bays. Your knowledge priority is:
1) OEM service information (procedures, torque sequence, calibration requirements, TSB applicability by VIN)
2) Evidence-based diagnostics: tests before parts; distinguish codes (symptoms) from root causes
3) Safe shop practice: jacks, jacks stands, HV/EV lockout awareness, ADAS precautions, torque + witness marks where critical
4) Documentation: what to log on the RO, photos, freeze frame IDs, verification drive profiles

When the user attaches photos, systematically observe: fluid stains and level clues, harness routing and chafe points, connector locks/corrosion, belt and pulley condition, obvious damage or missing fasteners, labels/casting marks if readable, and unusual wear patterns. State clearly what you cannot confirm from the image alone and what close-up or second angle would resolve. Never invent a part number or torque value visible only as a blur.

Tone: concise, procedural, respectful of working techs under flat-rate pressure. Prefer numbered steps and bullet reasoning.
When unsure, say what to verify on the scan tool or which OEM resource to open — do not invent torque values or single-line "replace this part" verdicts without a diagnostic rationale.

Always end with one line: "Verify against OEM SI for this VIN before turning wrenches."

You are not a lawyer or warranty adjudicator. Do not guarantee outcomes. No HTML — plain text only.`;

function toOpenAIMessage(m) {
  if (m.role === "assistant") {
    return { role: "assistant", content: m.content };
  }
  const imgs = Array.isArray(m.images) ? m.images : [];
  const valid = imgs.filter((u) => typeof u === "string" && u.startsWith("data:image"));
  if (valid.length > 0) {
    const text =
      (typeof m.content === "string" && m.content.trim()) ||
      "Analyze these photos for diagnostic clues (damage, leaks, routing, connector condition, labels). What should the technician verify next?";
    const parts = [{ type: "text", text }];
    for (const url of valid) {
      parts.push({
        type: "image_url",
        image_url: { url, detail: "high" },
      });
    }
    return { role: "user", content: parts };
  }
  return { role: "user", content: m.content ?? "" };
}

async function openaiChat(messages, context) {
  const ctxBlock =
    context && typeof context === "object"
      ? `\n\nCurrent bay context (JSON):\n${JSON.stringify(context, null, 2)}`
      : "";
  const body = JSON.stringify({
    model: MODEL,
    temperature: 0.35,
    max_tokens: 2048,
    messages: [{ role: "system", content: SYSTEM_PROMPT + ctxBlock }, ...messages.map(toOpenAIMessage)],
  });

  const res = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${OPENAI_KEY}`,
      "Content-Type": "application/json",
    },
    body,
  });

  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`OpenAI ${res.status}: ${errText.slice(0, 200)}`);
  }
  const json = await res.json();
  const text = json.choices?.[0]?.message?.content;
  if (!text) throw new Error("Empty model response");
  return text.trim();
}

const server = http.createServer(async (req, res) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.method !== "POST" || req.url !== "/api/hank/chat") {
    res.writeHead(404, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "Not found" }));
    return;
  }

  let raw = "";
  for await (const chunk of req) raw += chunk;

  let payload;
  try {
    payload = JSON.parse(raw);
  } catch {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "Invalid JSON" }));
    return;
  }

  const messages = payload.messages;
  const context = payload.context ?? null;

  if (!OPENAI_KEY) {
    res.writeHead(503, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        error: "no_key",
        reply:
          "Hank proxy has no OPENAI_API_KEY. The app will use local fallback answers. Set OPENAI_API_KEY and restart server/hank-proxy.mjs.",
      })
    );
    return;
  }

  if (!Array.isArray(messages) || messages.length === 0) {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "messages required" }));
    return;
  }

  try {
    const reply = await openaiChat(messages, context);
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ reply }));
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    res.writeHead(502, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: msg }));
  }
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`Hank proxy listening on http://127.0.0.1:${PORT}/api/hank/chat`);
  console.log(`Model: ${MODEL} (vision if messages include images)`);
  if (!OPENAI_KEY) console.warn("WARN: OPENAI_API_KEY not set — requests return 503 until configured.");
});
