import Speech
import AVFoundation
import Combine

/// Manages continuous speech recognition with "Hey Hank" wake word detection.
/// Runs a recognizer in passive mode listening for the wake word, then switches
/// to active listening for the actual question.
@MainActor
class VoiceManager: NSObject, SFSpeechRecognizerDelegate, ObservableObject {
    @Published var state: VoiceState = .off
    @Published var partialText: String = ""
    @Published var recognizedText: String = ""

    private var recognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let audioEngine = AVAudioEngine()

    enum VoiceState {
        case off
        case passive                         // Listening for "Hey Hank"
        case listening                       // Recording the question
        case processing                      // Processing the audio
        case error(String)
    }

    override init() {
        super.init()
        recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
        recognizer?.delegate = self
        requestMicrophonePermission()
    }

    /// Request microphone permission from the user.
    private func requestMicrophonePermission() {
        AVAudioApplication.requestRecordPermission { granted in
            DispatchQueue.main.async {
                if !granted {
                    self.state = .error("Microphone permission denied")
                }
            }
        }
    }

    /// Start continuous listening for the "Hey Hank" wake word.
    func startPassiveListening() {
        guard recognizer?.isAvailable == true else {
            state = .error("Speech recognizer unavailable")
            return
        }

        state = .passive
        startRecognition(isActive: false)
    }

    /// Start active listening for a question (after wake word detected).
    func startActiveListening() {
        state = .listening
        partialText = ""
        recognizedText = ""
        startRecognition(isActive: true)
    }

    /// Stop listening and end the current recognition task.
    func stopListening() {
        audioEngine.stop()
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        recognitionTask = nil
        recognitionRequest = nil
        state = .off
    }

    // MARK: - Private Methods

    private func startRecognition(isActive: Bool) {
        do {
            // Stop any existing recognition
            recognitionTask?.cancel()
            recognitionTask = nil

            // Configure audio session
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.record, mode: .measurement, options: .defaultToSpeaker)
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)

            // Create recognition request
            recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
            guard let recognitionRequest = recognitionRequest else {
                state = .error("Failed to create recognition request")
                return
            }
            recognitionRequest.shouldReportPartialResults = true

            // Attach audio input
            let inputNode = audioEngine.inputNode
            let recordingFormat = inputNode.outputFormat(forBus: 0)!

            inputNode.installTap(
                onBus: 0,
                bufferSize: 4096,
                format: recordingFormat
            ) { buffer, _ in
                recognitionRequest.append(buffer)
            }

            // Start audio engine
            audioEngine.prepare()
            try audioEngine.start()

            // Start recognition task
            recognitionTask = recognizer?.recognitionTask(with: recognitionRequest) { result, error in
                DispatchQueue.main.async {
                    if let error = error {
                        self.state = .error(error.localizedDescription)
                        return
                    }

                    if let result = result {
                        let transcript = result.bestTranscription.formattedString
                        self.partialText = transcript

                        // Wake word detection: if we're in passive mode and "Hey Hank" is detected
                        if !isActive && transcript.lowercased().contains("hey hank") {
                            self.stopListening()
                            self.startActiveListening()
                        }

                        if result.isFinal {
                            self.recognizedText = transcript
                            self.stopListening()

                            // Emit recognized question (ignore if it's just the wake word)
                            if isActive && !transcript.lowercased().contains("hey hank") {
                                self.state = .processing
                                // The caller will detect this state change and use recognizedText
                            }
                        }
                    }
                }
            }
        } catch {
            state = .error("Audio setup error: \(error.localizedDescription)")
        }
    }
}
