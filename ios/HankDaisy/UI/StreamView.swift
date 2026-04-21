import SwiftUI

/// Main streaming view: displays glasses video feed + chat panel + voice controls.
struct StreamView: View {
    @StateObject var viewModel: StreamViewModel
    @State private var showSettings = false
    @State private var isListening = false

    var body: some View {
        ZStack(alignment: .bottom) {
            // Main content
            VStack(spacing: 0) {
                // Video preview (glasses feed)
                if let frame = viewModel.streamSessionManager.currentFrame {
                    Image(uiImage: frame)
                        .resizable()
                        .scaledToFit()
                        .frame(height: 400)
                        .background(Color.black)
                } else {
                    VStack {
                        Text("No video feed")
                            .foregroundColor(.gray)
                    }
                    .frame(height: 400)
                    .frame(maxWidth: .infinity)
                    .background(Color.black.opacity(0.3))
                }

                // Chat panel
                ChatPanel(messages: viewModel.chatMessages)
                    .frame(maxHeight: .infinity)
            }

            // Bottom controls
            VStack(spacing: 12) {
                // Status indicator
                HStack {
                    if viewModel.isAnalyzing {
                        ProgressView()
                            .tint(.orange)
                        Text("Analyzing...")
                            .foregroundColor(.orange)
                    } else if viewModel.ttsManager.isSpeaking {
                        ProgressView()
                            .tint(.green)
                        Text("Speaking...")
                            .foregroundColor(.green)
                    } else if isListening {
                        ProgressView()
                            .tint(.blue)
                        Text("Listening...")
                            .foregroundColor(.blue)
                    }

                    Spacer()

                    Button(action: { showSettings.toggle() }) {
                        Image(systemName: "gear")
                            .foregroundColor(.white)
                            .padding(8)
                            .background(Color.gray.opacity(0.5))
                            .clipShape(Circle())
                    }
                }
                .padding(.horizontal)
                .padding(.top, 8)

                // Voice control button
                Button(action: toggleListening) {
                    VStack {
                        Image(systemName: isListening ? "waveform.circle.fill" : "microphoneCircle.fill")
                            .font(.system(size: 32))
                        Text(isListening ? "Listening..." : "Ask Hank")
                            .font(.caption)
                    }
                    .frame(height: 80)
                    .frame(maxWidth: .infinity)
                    .background(isListening ? Color.blue : Color.orange)
                    .foregroundColor(.white)
                    .cornerRadius(12)
                }
                .padding(.horizontal)
                .padding(.bottom)
            }
            .background(Color.black.opacity(0.7))
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text("Hank Daisy")
                    .font(.headline)
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsView(viewModel: viewModel)
        }
        .onAppear {
            Task {
                await viewModel.startGlassesStream()
                viewModel.startWakeWordListening()
            }
        }
        .onDisappear {
            Task {
                await viewModel.stopGlassesStream()
            }
            viewModel.stopListening()
            viewModel.ttsManager.stop()
        }
    }

    private func toggleListening() {
        if isListening {
            viewModel.stopListening()
            isListening = false
        } else {
            viewModel.voiceManager.startActiveListening()
            isListening = true
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

    NavigationStack {
        StreamView(viewModel: svm)
    }
}
