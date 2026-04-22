import SwiftUI

/// Settings view for configuring agent URL and other preferences.
struct SettingsView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var viewModel: StreamViewModel
    @State private var apiKeyString = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("OpenRouter API Key") {
                    SecureField("sk-or-v1-...", text: $apiKeyString)
                        .textInputAutocapitalization(.none)
                        .autocorrectionDisabled()

                    Button(action: saveApiKey) {
                        Text("Save & Connect")
                            .frame(maxWidth: .infinity, alignment: .center)
                    }

                    if viewModel.isAgentInitialized {
                        Text("API Key Loaded ✓")
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
                // Load saved API Key if available
                apiKeyString = UserDefaults.standard.string(forKey: "openRouterApiKey") ?? ""
            }
        }
    }

    private func saveApiKey() {
        let key = apiKeyString.trimmingCharacters(in: .whitespacesAndNewlines)
        UserDefaults.standard.set(key, forKey: "openRouterApiKey")
        viewModel.initializeAgent(apiKey: key)
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
