import SwiftUI

/// Main navigation: routes between registration and streaming based on registration state.
struct ContentView: View {
    @StateObject private var deviceManager = DeviceSessionManager()
    @StateObject private var streamManager = StreamSessionManager()
    @StateObject private var voiceManager = VoiceManager()
    @StateObject private var ttsManager = TTSManager()
    @StateObject private var streamViewModel: StreamViewModel

    @State private var agentURL: URL?

    init() {
        let dvm = DeviceSessionManager()
        let sm = StreamSessionManager()
        let vm = VoiceManager()
        let tm = TTSManager()
        let svm = StreamViewModel(
            deviceSessionManager: dvm,
            streamSessionManager: sm,
            voiceManager: vm,
            ttsManager: tm
        )

        _deviceManager = StateObject(wrappedValue: dvm)
        _streamManager = StateObject(wrappedValue: sm)
        _voiceManager = StateObject(wrappedValue: vm)
        _ttsManager = StateObject(wrappedValue: tm)
        _streamViewModel = StateObject(wrappedValue: svm)

        // Load saved agent URL
        let savedURL = UserDefaults.standard.string(forKey: "agentURL")
            .flatMap { URL(string: $0) }
            ?? URL(string: "ws://127.0.0.1:8765")!
        svm.initializeAgent(serverURL: savedURL)
    }

    var body: some View {
        NavigationStack {
            Group {
                if deviceManager.registrationState == .registered &&
                   deviceManager.deviceSessionState == .started {
                    StreamView(viewModel: streamViewModel)
                } else {
                    RegistrationView(deviceManager: deviceManager)
                }
            }
            .navigationBarBackButtonHidden()
        }
    }
}

#Preview {
    ContentView()
}
