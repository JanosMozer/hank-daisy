import Foundation
import MWDATCore
import MWDATCamera
import UIKit
import Combine

/// Manages glasses camera StreamSession with frame capture, compression, and streaming.
@MainActor
class StreamSessionManager: NSObject, ObservableObject {
    @Published var currentFrame: UIImage?
    @Published var streamState: StreamSessionState = .stopped
    @Published var errorMessage: String?

    private var streamSession: StreamSession?
    private var cancellables = Set<AnyCancellable>()

    /// Initialize stream with device session and start capturing frames.
    func initializeStream(from deviceSession: DeviceSession) async {
        do {
            let config = StreamSessionConfig(videoCodec: .raw, resolution: .medium, frameRate: 24)
            streamSession = try deviceSession.addStream(config: config)

            observeStreamState()
            observeVideoFrames()

            try await streamSession?.start()
        } catch {
            errorMessage = "Failed to initialize stream: \(error.localizedDescription)"
        }
    }

    /// Stop current stream session.
    func stopStream() async {
        do {
            try await streamSession?.stop()
            streamSession = nil
        } catch {
            errorMessage = "Failed to stop stream: \(error.localizedDescription)"
        }
    }

    /// Capture current frame as base64-encoded JPEG for sending to agent.
    func captureFrameAsBase64() -> String? {
        guard let frame = currentFrame else { return nil }
        guard let jpegData = frame.jpegData(compressionQuality: 0.5) else { return nil }
        return jpegData.base64EncodedString()
    }

    private func observeStreamState() {
        guard let streamSession = streamSession else { return }
        Task {
            for await state in streamSession.state() {
                self.streamState = state
            }
        }
    }

    private func observeVideoFrames() {
        guard let streamSession = streamSession else { return }
        Task {
            for await frame in streamSession.videoFrameStream() {
                if let image = frame.makeUIImage() {
                    self.currentFrame = image
                }
            }
        }
    }
}
