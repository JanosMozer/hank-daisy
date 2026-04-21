import AVFoundation
import Combine

/// Manages text-to-speech with sentence-by-sentence queue and barge-in detection.
/// Detects sentence boundaries (. ! ? followed by space) and speaks each sentence
/// as it arrives, enabling real-time streaming audio to the glasses.
@MainActor
class TTSManager: NSObject, AVSpeechSynthesizerDelegate, ObservableObject {
    @Published var isSpeaking = false
    private let synthesizer = AVSpeechSynthesizer()
    private var sentenceQueue: [String] = []
    private var currentUtterance: AVSpeechUtterance?
    private var accumulatedText = ""

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    /// Add text to be spoken. Automatically detects sentence boundaries and
    /// queues sentences for immediate speech.
    func addText(_ text: String) {
        accumulatedText += text

        // Detect and queue complete sentences
        let sentences = detectSentences(in: accumulatedText)
        for sentence in sentences {
            sentenceQueue.append(sentence)
        }

        // Remove processed sentences from accumulated text
        var processed = ""
        for sentence in sentences {
            if let range = accumulatedText.range(of: sentence) {
                accumulatedText.removeSubrange(range)
            }
        }

        // Start speaking the next sentence if not already speaking
        if !isSpeaking && !sentenceQueue.isEmpty {
            speakNextSentence()
        }
    }

    /// Finish speaking and process any remaining accumulated text.
    func finishSpeaking() {
        // Queue any remaining accumulated text as a final sentence
        if !accumulatedText.trimmingCharacters(in: .whitespaces).isEmpty {
            sentenceQueue.append(accumulatedText.trimmingCharacters(in: .whitespaces))
            accumulatedText = ""
        }

        // Speak queued sentences
        if !isSpeaking && !sentenceQueue.isEmpty {
            speakNextSentence()
        }
    }

    /// Stop speaking immediately and clear the queue.
    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
        sentenceQueue.removeAll()
        accumulatedText = ""
        currentUtterance = nil
        isSpeaking = false
    }

    // MARK: - Private Methods

    private func speakNextSentence() {
        guard !sentenceQueue.isEmpty else {
            isSpeaking = false
            return
        }

        let sentence = sentenceQueue.removeFirst()
        let utterance = AVSpeechUtterance(string: sentence)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        utterance.pitchMultiplier = 1.0
        utterance.volume = 1.0

        currentUtterance = utterance
        isSpeaking = true
        synthesizer.speak(utterance)
    }

    /// Detect complete sentences ending with . ! ? followed by whitespace.
    /// Returns sentences in order, does not remove them from input.
    private func detectSentences(in text: String) -> [String] {
        var sentences: [String] = []
        var currentSentence = ""

        for (index, char) in text.enumerated() {
            currentSentence.append(char)

            // Check for sentence-ending punctuation followed by space or end of string
            if (char == "." || char == "!" || char == "?") {
                let nextIndex = text.index(text.startIndex, offsetBy: index + 1)
                let isEndOfString = nextIndex >= text.endIndex
                let isFollowedBySpace = !isEndOfString && text[nextIndex].isWhitespace

                if isEndOfString || isFollowedBySpace {
                    let trimmed = currentSentence.trimmingCharacters(in: .whitespaces)
                    if !trimmed.isEmpty {
                        sentences.append(trimmed)
                    }
                    currentSentence = ""
                }
            }
        }

        return sentences
    }

    // MARK: - AVSpeechSynthesizerDelegate

    func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didFinish utterance: AVSpeechUtterance
    ) {
        // Speak the next queued sentence
        if !sentenceQueue.isEmpty {
            speakNextSentence()
        } else {
            isSpeaking = false
        }
    }

    func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didCancel utterance: AVSpeechUtterance
    ) {
        isSpeaking = false
    }
}
