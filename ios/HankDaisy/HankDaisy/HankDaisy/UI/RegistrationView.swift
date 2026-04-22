import SwiftUI

/// Registration flow: shows Wearables registration state and handles callback from Meta AI app.
struct RegistrationView: View {
    @ObservedObject var deviceManager: DeviceSessionManager
    @ObservedObject var streamViewModel: StreamViewModel
    @State private var showingSettings = false

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "eyeglasses")
                .font(.system(size: 64))
                .foregroundColor(.orange)

            Text("Pair with Oakley Meta Vanguard")
                .font(.headline)

            Text("You'll be redirected to the Meta AI app to complete pairing with your glasses.")
                .font(.body)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)

            switch deviceManager.registrationState {
            case .unavailable:
                Text("Connect to the internet and open Meta AI app to enable registration.")
                    .font(.caption)
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)

            case .available:
                Button(action: startRegistration) {
                    Text("Start Registration")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)

            case .registering:
                VStack(spacing: 12) {
                    ProgressView()
                    Text("Registering...")
                        .foregroundColor(.gray)
                }
                .frame(maxWidth: .infinity)

            case .registered:
                VStack(spacing: 12) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 48))
                        .foregroundColor(.green)

                    Text("Registered!")
                        .font(.headline)

                    if !deviceManager.availableDevices.isEmpty {
                        Text("Found \(deviceManager.availableDevices.count) device(s)")
                            .foregroundColor(.gray)
                    }

                    Button(action: createSession) {
                        Text("Connect to Glasses")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }

            @unknown default:
                EmptyView()
            }

            if let error = deviceManager.errorMessage {
                VStack(spacing: 8) {
                    HStack {
                        Image(systemName: "exclamationmark.circle.fill")
                            .foregroundColor(.red)
                        Text(error)
                            .font(.caption)
                            .foregroundColor(.red)
                    }
                    .padding()
                    .background(Color.red.opacity(0.1))
                    .cornerRadius(8)

                    Button("Dismiss") {
                        deviceManager.errorMessage = nil
                    }
                    .frame(maxWidth: .infinity)
                    .tint(.red)
                }
            }

            Spacer()
        }
        .padding()
        .navigationTitle("Hank Daisy")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { showingSettings = true }) {
                    Image(systemName: "gear")
                }
            }
        }
        .sheet(isPresented: $showingSettings) {
            SettingsView(viewModel: streamViewModel)
        }
        .onAppear {
            Task {
                await deviceManager.initialize()
            }
        }
        .onOpenURL { url in
            Task {
                await deviceManager.handleRegistrationCallback(url: url)
            }
        }
    }

    private func startRegistration() {
        Task {
            await deviceManager.startRegistration()
        }
    }

    private func createSession() {
        Task {
            await deviceManager.createAndStartSession()
        }
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
    
    return RegistrationView(deviceManager: dvm, streamViewModel: svm)
}
