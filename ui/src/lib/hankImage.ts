/** Resize and compress images before sending to Hank (smaller payloads, faster API). */
const MAX_EDGE = 1536;
const JPEG_QUALITY = 0.82;

export async function fileToVisionDataUrl(file: File): Promise<string> {
  if (!file.type.startsWith("image/")) {
    throw new Error("Choose an image file");
  }

  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(objectUrl);
      try {
        let w = img.naturalWidth;
        let h = img.naturalHeight;
        if (w < 1 || h < 1) {
          reject(new Error("Invalid image"));
          return;
        }
        if (w > MAX_EDGE || h > MAX_EDGE) {
          const scale = Math.min(MAX_EDGE / w, MAX_EDGE / h);
          w = Math.round(w * scale);
          h = Math.round(h * scale);
        }
        const canvas = document.createElement("canvas");
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext("2d");
        if (!ctx) {
          reject(new Error("Canvas unsupported"));
          return;
        }
        ctx.drawImage(img, 0, 0, w, h);
        resolve(canvas.toDataURL("image/jpeg", JPEG_QUALITY));
      } catch (e) {
        reject(e);
      }
    };
    img.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error("Could not read image"));
    };
    img.src = objectUrl;
  });
}

export const MAX_IMAGES_PER_MESSAGE = 4;
export const MAX_FILE_BYTES = 12 * 1024 * 1024;
