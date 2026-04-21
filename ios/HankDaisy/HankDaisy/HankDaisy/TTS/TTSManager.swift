import AVFoundation
import Combine

/// Manages text-to-speech with sentence-by-sentence streaming and real-time playback.
@MainActor
class TTSManager: NSObject, AVSpeechSynthesizerDelegate, ObservableObject {
    @Published var isSpeaking = false
    private let synthesizer = AVSpeechSynthesizer()
    private var sentenceQueue: [String] = []
    private var currentUtterance: AVSpeechUtterance?
    private var accumulatedText = ""

    /// Initialize speech synthesizer delegate.
    override init() {
        super.init()
        synthesizer.delegate = self
    }

    /// Add text chunk and queue sentences as they become complete.
    func addText(_ text: String) {
        accumulatedText += text

        let sentences = detectSentences(in: accumulatedText)
        for sentence in sentences {
            sentenceQueue.append(sentence)
        }

        for sentence in sentences {
            if let range = accumulatedText.range(of: sentence) {
                accumulatedText.removeSubrange(range)
            }
        }

        if !isSpeaking && !sentenceQueue.isEmpty {
            speakNextSentence()
        }
    }

    /// Finish speaking and process remaining accumulated text.
    func finishSpeaking() {
        if !accumulatedText.trimmingCharacters(in: .whitespaces).isEmpty {
            sentenceQueue.append(accumulatedText.trimmingCharacters(in: .whitespaces))
            accumulatedText = ""
        }

        if !isSpeaking && !sentenceQueue.isEmpty {
            speakNextSentence()
        }
    }

    /// Stop speaking immediately and clear queue.
    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
        sentenceQueue.removeAll()
        accumulatedText = ""
        currentUtterance = nil
        isSpeaking = false
    }

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

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            if !self.sentenceQueue.isEmpty {
                self.speakNextSentence()
            } else {
                self.isSpeaking = false
            }
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in
            self.isSpeaking = false
        }
    }
}
