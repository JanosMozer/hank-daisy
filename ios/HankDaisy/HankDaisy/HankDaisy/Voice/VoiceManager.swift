import Speech
import AVFoundation
import Combine

/// Manages speech recognition with "Hey Hank" wake word detection and active listening.
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
        case passive
        case listening
        case processing
        case error(String)
    }

    /// Initialize speech recognizer and request microphone permission.
    override init() {
        super.init()
        recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
        recognizer?.delegate = self
        requestMicrophonePermission()
    }

    /// Request microphone permission from user.
    private func requestMicrophonePermission() {
        AVAudioApplication.requestRecordPermission { granted in
            DispatchQueue.main.async {
                if !granted {
                    self.state = .error("Microphone permission denied")
                }
            }
        }
    }

    /// Start continuous listening for "Hey Hank" wake word.
    func startPassiveListening() {
        guard recognizer?.isAvailable == true else {
            state = .error("Speech recognizer unavailable")
            return
        }
        state = .passive
        startRecognition(isActive: false)
    }

    /// Start active listening for user question after wake word detected.
    func startActiveListening() {
        state = .listening
        partialText = ""
        recognizedText = ""
        startRecognition(isActive: true)
    }

    /// Stop listening and cancel current recognition task.
    func stopListening() {
        audioEngine.stop()
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        recognitionTask = nil
        recognitionRequest = nil
        state = .off
    }

    private func startRecognition(isActive: Bool) {
        do {
            recognitionTask?.cancel()
            recognitionTask = nil

            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.record, mode: .measurement, options: .defaultToSpeaker)
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)

            recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
            guard let recognitionRequest = recognitionRequest else {
                state = .error("Failed to create recognition request")
                return
            }
            recognitionRequest.shouldReportPartialResults = true

            let inputNode = audioEngine.inputNode
            let recordingFormat = inputNode.outputFormat(forBus: 0)

            inputNode.installTap(onBus: 0, bufferSize: 4096, format: recordingFormat) { buffer, _ in
                recognitionRequest.append(buffer)
            }

            audioEngine.prepare()
            try audioEngine.start()

            recognitionTask = recognizer?.recognitionTask(with: recognitionRequest) { result, error in
                DispatchQueue.main.async {
                    if let error = error {
                        self.state = .error(error.localizedDescription)
                        return
                    }

                    if let result = result {
                        let transcript = result.bestTranscription.formattedString
                        self.partialText = transcript

                        if !isActive && transcript.lowercased().contains("hey hank") {
                            self.stopListening()
                            self.startActiveListening()
                        }

                        if result.isFinal {
                            self.recognizedText = transcript
                            self.stopListening()

                            if isActive && !transcript.lowercased().contains("hey hank") {
                                self.state = .processing
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
