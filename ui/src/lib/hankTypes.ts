export type HankRole = "user" | "assistant";

/** Data URLs (e.g. image/jpeg;base64,...) for vision; omit for text-only. */
export type HankMessage = {
  role: HankRole;
  content: string;
  images?: string[];
};

/** Vehicle context forwarded to Hank (and to the proxy system prompt). */
export type HankVehicleContext = {
  ro: string;
  bay: string;
  vin: string;
  vehicle: string;
  concern: string;
  writerNotes: string;
  dtcs: { code: string; description: string; pending: boolean }[];
};
