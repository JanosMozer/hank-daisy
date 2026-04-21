import Foundation

import Foundation
import UIKit

/// Standalone REST client for OpenRouter, matching GeminiService.kt on Android.
actor AgentClient {
    private let apiKey: String
    private var history: [[String: String]] = []
    
    // Identical prompt to android
    private let systemPrompt = """
You are Hank — a friendly, sharp mechanic talking to someone wearing smart glasses. You see what they see, in real time. You're having a CONVERSATION, not delivering a manual.

FIRST, BEFORE ANYTHING ELSE, decide whether the camera view is actually relevant to what the user just asked:
- If the view shows something automotive (engine, dash, leak, wiring, tire, undercarriage, lift bay, tool, etc.) AND the question is about it → use what you see.
- If the view is NOT relevant (a wall, a person, a room, a hand, the floor, ambient background, or the question isn't about what's visible) → ignore the image entirely and just answer the question normally, as a plain conversation. Do not force a visual interpretation. Do not describe the scene. Do not say "I can see" unless you genuinely need to reference it.
- If the user asks a general question ("what does a turbocharger do", "how do I set torque", "tell me a joke") → answer it directly. Don't mention the camera.

Voice rules (this is critical — your replies are spoken aloud through their glasses):
- Keep replies digestible. Usually 2–5 sentences. Hard ceiling: 7 sentences.
- ONE STEP AT A TIME. Give exactly one action, then STOP. Never batch multiple steps. Never say "first do X, then Y" — just say "first do X" and wait.
- After giving a step, end with something like "let me know when you're there" or "say go when you're ready".
- When the view IS relevant and you're mid-procedure, use it to verify the previous step BEFORE giving the next one. If not visibly done, stay quiet — do not advance.
- If you need to see better, say so plainly and ask for a closer / different angle / light. Don't speculate.
- Talk like a buddy in the shop — warm, direct, a little casual. No bullet points, no markdown, no numbered lists. Just talk.
- If something is dangerous, lead with the warning in one short sentence.
- If you genuinely don't understand the question, ask one clarifying question. Don't guess.

You're being interrupted often — that's normal. Pick up the thread.
"""

    init(apiKey: String) {
        self.apiKey = apiKey
    }

    /// In standalone mode, we don't hold a websocket open.
    func connect() async throws {}
    func disconnect() {}

    /// Send question to API and yield response.
    func query(text: String, frameData: Data? = nil) -> AsyncStream<AgentEvent> {
        AsyncStream { continuation in
            Task {
                do {
                    if apiKey.isEmpty {
                        continuation.yield(.chunk("OpenRouter API key not configured. Add it in settings."))
                        continuation.yield(.done)
                        continuation.finish()
                        return
                    }

                    var messages: [[String: Any]] = [
                        ["role": "system", "content": systemPrompt]
                    ]
                    
                    for turn in history {
                        messages.append(["role": turn["role"]!, "content": turn["text"]!])
                    }
                    
                    var userContent: [[String: Any]] = [
                        ["type": "text", "text": text]
                    ]
                    
                    if let frameData = frameData,
                       let image = UIImage(data: frameData),
                       let jpegData = image.jpegData(compressionQuality: 0.85) {
                        
                        let b64 = jpegData.base64EncodedString()
                        let dataUrl = "data:image/jpeg;base64,\(b64)"
                        userContent.append([
                            "type": "image_url",
                            "image_url": ["url": dataUrl]
                        ])
                    }
                    
                    messages.append([
                        "role": "user",
                        "content": userContent
                    ])
                    
                    let payload: [String: Any] = [
                        "model": "google/gemini-3.1-flash-lite-preview",
                        "messages": messages,
                        "max_tokens": 700,
                        "temperature": 0.6
                    ]
                    
                    let jsonData = try JSONSerialization.data(withJSONObject: payload)
                    var request = URLRequest(url: URL(string: "https://openrouter.ai/api/v1/chat/completions")!)
                    request.httpMethod = "POST"
                    request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
                    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    request.setValue("https://github.com/JanosMozer/hank-daisy", forHTTPHeaderField: "HTTP-Referer")
                    request.setValue("Hank", forHTTPHeaderField: "X-Title")
                    request.httpBody = jsonData
                    request.timeoutInterval = 60
                    
                    let (data, response) = try await URLSession.shared.data(for: request)
                    
                    guard let httpResponse = response as? HTTPURLResponse else {
                        throw URLError(.badServerResponse)
                    }
                    
                    if !(200...299).contains(httpResponse.statusCode) {
                        let errorBody = String(data: data, encoding: .utf8) ?? "Unknown HTTP edge case"
                        let msg: String
                        switch httpResponse.statusCode {
                        case 401, 403: msg = "Invalid API Key"
                        case 429: msg = "Rate limited. Too many requests."
                        default: msg = "Server error \(httpResponse.statusCode): \(errorBody)"
                        }
                        continuation.yield(.chunk(msg))
                        continuation.yield(.done)
                        continuation.finish()
                        return
                    }
                    
                    if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                       let choices = json["choices"] as? [[String: Any]],
                       let firstChoice = choices.first,
                       let message = firstChoice["message"] as? [String: Any],
                       let assistantText = message["content"] as? String {
                        
                        history.append(["role": "user", "text": text])
                        history.append(["role": "assistant", "text": assistantText])
                        
                        continuation.yield(.chunk(assistantText))
                    } else {
                        continuation.yield(.chunk("No response from Hank."))
                    }
                    continuation.yield(.done)
                    continuation.finish()
                    
                } catch {
                    continuation.yield(.error("Connection error: \(error.localizedDescription)"))
                    continuation.finish()
                }
            }
        }
    }
}
