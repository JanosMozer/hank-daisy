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
    private var tokens: [any AnyListenerToken] = []

    /// Initialize stream with device session and start capturing frames.
    func initializeStream(from deviceSession: DeviceSession) async {
        let config = StreamSessionConfig(videoCodec: .raw, resolution: .medium, frameRate: 24)
        
        do {
            streamSession = try deviceSession.addStream(config: config)
            observeStreamState()
            observeVideoFrames()
            await streamSession?.start()
        } catch {
            errorMessage = "Failed to add stream: \(error.localizedDescription)"
        }
    }

    /// Stop current stream session.
    func stopStream() async {
        await streamSession?.stop()
        streamSession = nil
        tokens.removeAll()
    }

    /// Capture current frame as base64-encoded JPEG for sending to agent.
    func captureFrameAsBase64() -> String? {
        guard let frame = currentFrame else { return nil }
        guard let jpegData = frame.jpegData(compressionQuality: 0.5) else { return nil }
        return jpegData.base64EncodedString()
    }

    private func observeStreamState() {
        guard let streamSession = streamSession else { return }
        let token = streamSession.statePublisher.listen { [weak self] state in
            Task { @MainActor in
                self?.streamState = state
            }
        }
        tokens.append(token)
    }

    private func observeVideoFrames() {
        guard let streamSession = streamSession else { return }
        let token = streamSession.videoFramePublisher.listen { [weak self] frame in
            if let image = frame.makeUIImage() {
                Task { @MainActor in
                    self?.currentFrame = image
                }
            }
        }
        tokens.append(token)
    }
}
