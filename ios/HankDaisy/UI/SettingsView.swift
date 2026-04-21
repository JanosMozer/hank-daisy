import SwiftUI

/// Settings view for configuring agent URL and other preferences.
struct SettingsView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var viewModel: StreamViewModel
    @State private var agentURLString = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Agent Connection") {
                    TextField("WebSocket URL", text: $agentURLString)
                        .textInputAutocapitalization(.none)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()

                    Button(action: connectAgent) {
                        Text("Connect")
                            .frame(maxWidth: .infinity, alignment: .center)
                    }

                    if let url = viewModel.agentURL {
                        Text("Connected: \(url.absoluteString)")
                            .font(.caption)
                            .foregroundColor(.green)
                    }
                }

                Section("Chat") {
                    Button(action: { viewModel.clearChat() }) {
                        Text("Clear Messages")
                            .foregroundColor(.red)
                    }
                }

                Section("Info") {
                    HStack {
                        Text("App Version")
                        Spacer()
                        Text("1.0.0")
                            .foregroundColor(.gray)
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
            .onAppear {
                // Load saved URL if available
                if let url = viewModel.agentURL {
                    agentURLString = url.absoluteString
                } else {
                    // Default for simulator
                    agentURLString = "ws://localhost:8765"
                }
            }
        }
    }

    private func connectAgent() {
        guard !agentURLString.isEmpty,
              let url = URL(string: agentURLString) else {
            return
        }

        viewModel.initializeAgent(serverURL: url)
    }
}

#Preview {
    let dvm = DeviceSessionManager()
    let ssm = StreamSessionManager()
    let vm = VoiceManager()
    let tm = TTSManager()
    let svm = StreamViewModel(
        deviceSessionManager: dvm,
        streamSessionManager: ssm,
        voiceManager: vm,
        ttsManager: tm
    )

    SettingsView(viewModel: svm)
}
