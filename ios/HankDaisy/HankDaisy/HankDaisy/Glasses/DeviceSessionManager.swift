import Foundation
import MWDATCore
import Combine

/// Manages Oakley Meta Vanguard glasses registration, connection, and session lifecycle.
@MainActor
class DeviceSessionManager: NSObject, ObservableObject {
    @Published var registrationState: RegistrationState = .unavailable
    @Published var availableDevices: [DeviceIdentifier] = []
    @Published var activeDevice: DeviceIdentifier?
    @Published var deviceSessionState: DeviceSessionState = .idle
    @Published var errorMessage: String?

    private var wearables: WearablesInterface?
    private var deviceSession: DeviceSession?
    private var cancellables = Set<AnyCancellable>()

    /// Initialize and configure Wearables SDK.
    func initialize() async {
        do {
            try Wearables.configure()
            wearables = Wearables.shared
            
            Task { await observeRegistrationState() }
            Task { await observeAvailableDevices() }
        } catch {
            errorMessage = "Failed to configure Wearables: \(error.localizedDescription)"
        }
    }

    /// Start registration process which opens Meta AI app.
    func startRegistration() async {
        do {
            try await wearables?.startRegistration()
        } catch {
            errorMessage = "Registration failed: \(error.localizedDescription)"
        }
    }

    /// Handle Meta AI app callback URL after registration.
    func handleRegistrationCallback(url: URL) async {
        _ = try? await Wearables.shared.handleUrl(url)
    }

    /// Create and start DeviceSession with best available device.
    func createAndStartSession() async {
        do {
            guard let wearables = wearables else {
                errorMessage = "Wearables not initialized"
                return
            }

            let selector = AutoDeviceSelector(wearables: wearables)
            deviceSession = try wearables.createSession(deviceSelector: selector)

            Task { await observeDeviceSessionState() }
            try deviceSession?.start()
        } catch {
            errorMessage = "Failed to create/start device session: \(error.localizedDescription)"
        }
    }

    /// Stop current DeviceSession.
    func stopSession() async {
        deviceSession?.stop()
        deviceSession = nil
    }

    /// Unregister the device.
    func unregister() async {
        do {
            try await wearables?.startUnregistration()
        } catch {
            errorMessage = "Unregistration failed: \(error.localizedDescription)"
        }
    }

    /// Get current active DeviceSession.
    func getActiveSession() -> DeviceSession? {
        return deviceSession
    }

    private func observeRegistrationState() async {
        guard let wearables = wearables else { return }
        for await state in wearables.registrationStateStream() {
            registrationState = state
        }
    }

    private func observeAvailableDevices() async {
        guard let wearables = wearables else { return }
        for await devices in wearables.devicesStream() {
            availableDevices = devices
            if devices.isEmpty { activeDevice = nil }
        }
    }

    private func observeDeviceSessionState() async {
        guard let deviceSession = deviceSession else { return }
        for await state in deviceSession.stateStream() {
            deviceSessionState = state
        }
    }
}
