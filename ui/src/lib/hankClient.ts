import type { HankMessage, HankVehicleContext } from "./hankTypes";
import { mockHankReply } from "./hankMock";

function chatEndpoint(): string {
  const base = import.meta.env.VITE_HANK_API_URL as string | undefined;
  if (base && base.trim()) {
    const b = base.replace(/\/$/, "");
    return `${b}/chat`;
  }
  return "/api/hank/chat";
}

export async function askHank(
  messages: HankMessage[],
  context: HankVehicleContext | null
): Promise<{ reply: string; source: "live" | "fallback" | "fallback_error" }> {
  try {
    const res = await fetch(chatEndpoint(), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ messages, context }),
    });

    let data: { reply?: string; error?: string } = {};
    try {
      data = (await res.json()) as { reply?: string; error?: string };
    } catch {
      /* empty */
    }

    if (typeof data.reply === "string" && data.reply.length > 0) {
      if (res.ok) {
        return { reply: data.reply, source: "live" };
      }
      return { reply: data.reply, source: "fallback" };
    }

    return {
      reply: mockHankReply(messages, context),
      source: "fallback_error",
    };
  } catch {
    return {
      reply: mockHankReply(messages, context),
      source: "fallback_error",
    };
  }
}
