import Foundation
import MWDATCore
import Combine

/// Manages the lifecycle of a DeviceSession with the Oakley Meta Vanguard glasses.
/// Handles registration, connection, and session state transitions.
@MainActor
class DeviceSessionManager: NSObject, ObservableObject {
    @Published var registrationState: WearablesRegistrationState = .unregistered
    @Published var availableDevices: [WearablesDevice] = []
    @Published var activeDevice: WearablesDevice?
    @Published var deviceSessionState: DeviceSessionState = .idle
    @Published var errorMessage: String?

    private var wearables: Wearables?
    private var deviceSession: DeviceSession?
    private var cancellables = Set<AnyCancellable>()

    /// Initialize and configure Wearables.
    func initialize() async {
        do {
            try Wearables.configure()
            wearables = Wearables.shared
            await observeRegistrationState()
            await observeAvailableDevices()
            await observeDeviceSessionState()
        } catch {
            errorMessage = "Failed to configure Wearables: \(error.localizedDescription)"
        }
    }

    /// Start the registration process (opens Meta AI app).
    func startRegistration() async {
        do {
            try await wearables?.startRegistration()
        } catch {
            errorMessage = "Registration failed: \(error.localizedDescription)"
        }
    }

    /// Handle the callback URL from Meta AI app after registration.
    func handleRegistrationCallback(url: URL) async {
        do {
            try await Wearables.shared.handleUrl(url)
        } catch {
            errorMessage = "Failed to process registration callback: \(error.localizedDescription)"
        }
    }

    /// Create and start a DeviceSession for the best available device.
    func createAndStartSession() async {
        do {
            guard let wearables = wearables else {
                errorMessage = "Wearables not initialized"
                return
            }

            // Use AutoDeviceSelector to pick the best available device
            let selector = AutoDeviceSelector(wearables: wearables)
            deviceSession = try wearables.createSession(deviceSelector: selector)

            try deviceSession?.start()
        } catch {
            errorMessage = "Failed to create/start device session: \(error.localizedDescription)"
        }
    }

    /// Stop the current DeviceSession.
    func stopSession() async {
        do {
            try deviceSession?.stop()
            deviceSession = nil
        } catch {
            errorMessage = "Failed to stop session: \(error.localizedDescription)"
        }
    }

    /// Unregister the device.
    func unregister() async {
        do {
            try await wearables?.startUnregistration()
        } catch {
            errorMessage = "Unregistration failed: \(error.localizedDescription)"
        }
    }

    /// Get the current active DeviceSession.
    func getActiveSession() -> DeviceSession? {
        return deviceSession
    }

    // MARK: - Private Observation Methods

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
            if devices.isEmpty {
                activeDevice = nil
            }
        }
    }

    private func observeDeviceSessionState() async {
        guard let deviceSession = deviceSession else { return }
        for await state in deviceSession.state() {
            deviceSessionState = state
        }
    }
}
