import { serve, file } from "bun";
import { readdir, stat, readFile } from "node:fs/promises";
import { join } from "node:path";
import { existsSync } from "node:fs";

const PORT = 3000;
const RECORDS_DIR = join(process.cwd(), "records");

async function getRecordsData() {
  if (!existsSync(RECORDS_DIR)) return [];
  
  const records = [];
  const sessions = await readdir(RECORDS_DIR);
  
  for (const session of sessions) {
    if (session.startsWith(".")) continue;
    
    const sessionPath = join(RECORDS_DIR, session);
    const sessionStat = await stat(sessionPath);
    if (!sessionStat.isDirectory()) continue;
    
    const sessionData = {
      id: session,
      stream: null,
      frames: []
    };
    
    const sessionFiles = await readdir(sessionPath);
    for (const item of sessionFiles) {
      if (item === "stream.mp4") {
        sessionData.stream = `/file/records/${session}/stream.mp4`;
      } else {
        const itemPath = join(sessionPath, item);
        const itemStat = await stat(itemPath);
        if (itemStat.isDirectory()) {
          const frameFiles = await readdir(itemPath);
          const frameData = {
            id: item,
            image: null,
            analysis: null
          };
          
          if (frameFiles.includes("frame.jpg")) {
            frameData.image = `/file/records/${session}/${item}/frame.jpg`;
          }
          if (frameFiles.includes("analysis.txt")) {
            const analysisPath = join(itemPath, "analysis.txt");
            try {
              frameData.analysis = await readFile(analysisPath, "utf-8");
            } catch (e) {
              frameData.analysis = "Error reading analysis";
            }
          }
          
          if (frameData.image || frameData.analysis) {
            sessionData.frames.push(frameData);
          }
        }
      }
    }
    
    sessionData.frames.sort((a, b) => a.id.localeCompare(b.id));
    records.push(sessionData);
  }
  
  records.sort((a, b) => b.id.localeCompare(a.id));
  return records;
}

serve({
  port: PORT,
  async fetch(req) {
    const url = new URL(req.url);
    
    if (url.pathname === "/") {
      return new Response(file(join(import.meta.dir, "index.html")));
    }
    
    if (url.pathname === "/api/records") {
      try {
        const data = await getRecordsData();
        return new Response(JSON.stringify(data), {
          headers: { "Content-Type": "application/json" }
        });
      } catch (err) {
        return new Response(JSON.stringify({ error: err?.message }), { status: 500 });
      }
    }
    
    if (url.pathname.startsWith("/file/records/")) {
      const filePath = url.pathname.replace("/file/records/", "");
      const fullPath = join(RECORDS_DIR, filePath);
      
      if (!fullPath.startsWith(RECORDS_DIR)) {
        return new Response("Forbidden", { status: 403 });
      }
      
      if (existsSync(fullPath)) {
        return new Response(file(fullPath));
      }
      return new Response("Not found", { status: 404 });
    }
    
    return new Response("Not Found", { status: 404 });
  }
});

console.log(`Dashboard listening on http://localhost:${PORT}`);
