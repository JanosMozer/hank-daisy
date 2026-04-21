import Foundation
import Combine

/// Orchestrates voice recognition, agent queries, and text-to-speech streaming.
@MainActor
class StreamViewModel: ObservableObject {
    @Published var chatMessages: [ChatMessage] = []
    @Published var isAnalyzing = false
    @Published var agentURL: URL?

    let deviceSessionManager: DeviceSessionManager
    let streamSessionManager: StreamSessionManager
    let voiceManager: VoiceManager
    let ttsManager: TTSManager
    var agentClient: AgentClient?

    private var cancellables = Set<AnyCancellable>()

    /// Initialize with all dependent managers.
    init(deviceSessionManager: DeviceSessionManager, streamSessionManager: StreamSessionManager, voiceManager: VoiceManager, ttsManager: TTSManager) {
        self.deviceSessionManager = deviceSessionManager
        self.streamSessionManager = streamSessionManager
        self.voiceManager = voiceManager
        self.ttsManager = ttsManager
        setupBindings()
    }

    /// Create and connect agent client with server URL.
    func initializeAgent(serverURL: URL) {
        agentClient = AgentClient(serverURL: serverURL)
        agentURL = serverURL
        Task {
            do {
                try await agentClient?.connect()
            } catch {
                appendMessage(.assistant, "Failed to connect to agent: \(error.localizedDescription)")
            }
        }
    }

    /// Bind voice state changes to automatic query dispatch.
    private func setupBindings() {
        voiceManager.$state
            .sink { [weak self] state in
                switch state {
                case .processing:
                    let question = self?.voiceManager.recognizedText ?? ""
                    if !question.isEmpty && !question.lowercased().contains("hey hank") {
                        Task { await self?.analyzeWithQuestion(question) }
                    }
                default:
                    break
                }
            }
            .store(in: &cancellables)
    }

    /// Start passive listening for "Hey Hank" wake word.
    func startWakeWordListening() {
        voiceManager.startPassiveListening()
    }

    /// Stop listening immediately.
    func stopListening() {
        voiceManager.stopListening()
    }

    /// Send query to agent with optional frame and stream response for TTS.
    func sendQuery(_ question: String) async {
        guard !question.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        await analyzeWithQuestion(question)
    }

    private func analyzeWithQuestion(_ question: String) async {
        guard let agentClient = agentClient else {
            appendMessage(.assistant, "Agent not connected")
            return
        }

        appendMessage(.user, question)
        isAnalyzing = true

        let frameBase64 = streamSessionManager.captureFrameAsBase64()

        var fullResponse = ""
        for await event in await agentClient.query(text: question, frameData: frameBase64?.data(using: .utf8)) {
            switch event {
            case .chunk(let text):
                fullResponse += text
                ttsManager.addText(text)
            case .done:
                ttsManager.finishSpeaking()
                isAnalyzing = false
            case .error(let msg):
                appendMessage(.assistant, "Error: \(msg)")
                isAnalyzing = false
            }
        }

        if !fullResponse.isEmpty {
            appendMessage(.assistant, fullResponse)
        }
    }

    /// Initialize and start glasses camera stream.
    func startGlassesStream() async {
        guard let deviceSession = deviceSessionManager.getActiveSession() else {
            appendMessage(.assistant, "No device session active")
            return
        }
        await streamSessionManager.initializeStream(from: deviceSession)
    }

    /// Stop glasses stream.
    func stopGlassesStream() async {
        await streamSessionManager.stopStream()
    }

    private func appendMessage(_ role: ChatMessage.Role, _ text: String) {
        let message = ChatMessage(role: role, text: text, timestamp: Date().timeIntervalSince1970)
        chatMessages.append(message)
        if chatMessages.count > 100 { chatMessages.removeFirst() }
    }

    func clearChat() {
        chatMessages.removeAll()
    }
}

// MARK: - Chat Message Model

struct ChatMessage: Identifiable {
    enum Role { case user, assistant }
    let id = UUID()
    let role: Role
    let text: String
    let timestamp: TimeInterval
}
