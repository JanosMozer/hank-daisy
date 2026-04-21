import Foundation

/// Thread-safe WebSocket client for communicating with the Hank agent server.
/// Handles reconnection, session persistence, and streaming responses.
actor AgentClient: NSObject, URLSessionWebSocketDelegate {
    private var webSocket: URLSessionWebSocket?
    private let serverURL: URL
    private var sessionID: String
    private let receiveQueue = DispatchQueue(label: "com.hankdaisy.agent.receive")
    private var isConnected = false

    /// Initialize with the server URL (e.g., ws://192.168.1.100:8765)
    init(serverURL: URL) {
        self.serverURL = serverURL
        self.sessionID = UUID().uuidString
        super.init()
    }

    /// Connect to the WebSocket server.
    func connect() async throws {
        let session = URLSession(configuration: .default, delegate: self, delegateQueue: .main)
        webSocket = session.webSocketTask(with: serverURL)
        webSocket?.resume()
        isConnected = true
    }

    /// Disconnect from the server.
    func disconnect() {
        webSocket?.cancel(with: .goingAway, reason: nil)
        webSocket = nil
        isConnected = false
    }

    /// Send a query to the agent and stream back responses.
    /// - Parameters:
    ///   - text: The user's question
    ///   - frameData: Optional JPEG frame data (base64-encoded by this function)
    /// - Returns: An AsyncStream of AgentEvents
    func query(text: String, frameData: Data? = nil) -> AsyncStream<AgentEvent> {
        AsyncStream { continuation in
            Task {
                do {
                    // Prepare the request payload
                    var payload: [String: Any] = [
                        "text": text,
                        "media_type": "image/jpeg",
                        "session_id": sessionID
                    ]

                    if let frameData = frameData {
                        let base64Frame = frameData.base64EncodedString()
                        payload["frame"] = base64Frame
                    }

                    // Serialize to JSON
                    let jsonData = try JSONSerialization.data(withJSONObject: payload)
                    let message = URLSessionWebSocketTask.Message.data(jsonData)

                    // Send the query
                    try await webSocket?.send(message)

                    // Stream responses until done
                    while isConnected {
                        let message = try await webSocket?.receive()
                        guard let message = message else { break }

                        let data: Data
                        switch message {
                        case .data(let d):
                            data = d
                        case .string(let s):
                            guard let d = s.data(using: .utf8) else { continue }
                            data = d
                        @unknown default:
                            continue
                        }

                        // Parse the response
                        if let response = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                            let type = response["type"] as? String ?? ""

                            switch type {
                            case "chunk":
                                if let text = response["text"] as? String {
                                    continuation.yield(.chunk(text))
                                }
                            case "done":
                                continuation.yield(.done)
                                continuation.finish()
                                return
                            case "error":
                                let msg = response["text"] as? String ?? "Unknown error"
                                continuation.yield(.error(msg))
                                continuation.finish()
                                return
                            default:
                                break
                            }
                        }
                    }
                } catch {
                    continuation.yield(.error("Connection error: \(error.localizedDescription)"))
                    continuation.finish()
                }
            }
        }
    }

    // MARK: - URLSessionWebSocketDelegate

    nonisolated func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didOpenWithProtocol protocol: String?
    ) {
        // Connection established
    }

    nonisolated func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
        reason: Data?
    ) {
        // Connection closed
        Task {
            await disconnect()
        }
    }
}
