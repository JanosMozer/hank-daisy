import Foundation
import MWDATCore
import MWDATCamera
import UIKit
import Combine

/// Manages the StreamSession lifecycle: video frame capture, compression, and publishing.
@MainActor
class StreamSessionManager: NSObject, ObservableObject {
    @Published var currentFrame: UIImage?
    @Published var streamState: StreamSessionState = .stopped
    @Published var errorMessage: String?

    private var streamSession: StreamSession?
    private var cancellables = Set<AnyCancellable>()

    /// Initialize the stream with a device session.
    /// - Parameter deviceSession: The active DeviceSession from DeviceSessionManager
    func initializeStream(from deviceSession: DeviceSession) async {
        do {
            // Create stream configuration
            let config = StreamSessionConfig(
                videoCodec: .raw,
                resolution: .medium,  // 504x896
                frameRate: 24
            )

            // Add stream to device session
            streamSession = try deviceSession.addStream(config: config)

            // Observe stream state
            observeStreamState()

            // Observe video frames
            observeVideoFrames()

            // Start the stream
            try await streamSession?.start()
        } catch {
            errorMessage = "Failed to initialize stream: \(error.localizedDescription)"
        }
    }

    /// Stop the current stream session.
    func stopStream() async {
        do {
            try await streamSession?.stop()
            streamSession = nil
        } catch {
            errorMessage = "Failed to stop stream: \(error.localizedDescription)"
        }
    }

    /// Capture the current frame and return it as base64-encoded JPEG data.
    /// Used to send visual context with queries to the agent.
    func captureFrameAsBase64() -> String? {
        guard let frame = currentFrame else { return nil }

        // Compress to JPEG at 50% quality to reduce bandwidth
        guard let jpegData = frame.jpegData(compressionQuality: 0.5) else {
            return nil
        }

        return jpegData.base64EncodedString()
    }

    // MARK: - Private Observation Methods

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
                // Convert VideoFrame to UIImage
                if let image = frame.makeUIImage() {
                    self.currentFrame = image
                }
            }
        }
    }
}
