import Foundation
import Combine

/// Central orchestration ViewModel: voice → agent → TTS.
/// Coordinates VoiceManager, AgentClient, TTSManager, and glasses integration.
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

    init(
        deviceSessionManager: DeviceSessionManager,
        streamSessionManager: StreamSessionManager,
        voiceManager: VoiceManager,
        ttsManager: TTSManager
    ) {
        self.deviceSessionManager = deviceSessionManager
        self.streamSessionManager = streamSessionManager
        self.voiceManager = voiceManager
        self.ttsManager = ttsManager

        setupBindings()
    }

    // MARK: - Setup

    /// Initialize the agent client with a server URL.
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

    /// Setup Combine bindings between voice, agent, and TTS.
    private func setupBindings() {
        // When voice detects "Hey Hank" → start active listening
        voiceManager.$state
            .sink { [weak self] state in
                switch state {
                case .processing:
                    // User has finished speaking their question
                    let question = self?.voiceManager.recognizedText ?? ""
                    if !question.isEmpty && !question.lowercased().contains("hey hank") {
                        Task {
                            await self?.analyzeWithQuestion(question)
                        }
                    }
                default:
                    break
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - Voice & Listening

    /// Start listening for the "Hey Hank" wake word.
    func startWakeWordListening() {
        voiceManager.startPassiveListening()
    }

    /// Stop listening immediately.
    func stopListening() {
        voiceManager.stopListening()
    }

    // MARK: - Agent Queries

    /// Send a text query to the agent, optionally with a camera frame.
    private func analyzeWithQuestion(_ question: String) async {
        guard let agentClient = agentClient else {
            appendMessage(.assistant, "Agent not connected")
            return
        }

        // Append user message to chat
        appendMessage(.user, question)

        isAnalyzing = true

        // Capture the current frame if available
        let frameBase64 = streamSessionManager.captureFrameAsBase64()

        // Stream response from agent
        var fullResponse = ""
        for await event in agentClient.query(text: question, frameData: frameBase64?.data(using: .utf8)) {
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

        // If we got a full response, add it to chat
        if !fullResponse.isEmpty {
            appendMessage(.assistant, fullResponse)
        }
    }

    // MARK: - Glasses Stream

    /// Initialize and start the glasses stream.
    func startGlassesStream() async {
        guard let deviceSession = deviceSessionManager.getActiveSession() else {
            appendMessage(.assistant, "No device session active")
            return
        }

        await streamSessionManager.initializeStream(from: deviceSession)
    }

    /// Stop the glasses stream.
    func stopGlassesStream() async {
        await streamSessionManager.stopStream()
    }

    // MARK: - Chat Management

    /// Append a message to the chat history.
    private func appendMessage(_ role: ChatMessage.Role, _ text: String) {
        let message = ChatMessage(role: role, text: text, timestamp: Date().timeIntervalSince1970)
        chatMessages.append(message)

        // Keep last 100 messages
        if chatMessages.count > 100 {
            chatMessages.removeFirst()
        }
    }

    /// Clear all messages.
    func clearChat() {
        chatMessages.removeAll()
    }
}

// MARK: - Chat Message Model

struct ChatMessage: Identifiable {
    enum Role {
        case user
        case assistant
    }

    let id = UUID()
    let role: Role
    let text: String
    let timestamp: TimeInterval
}
