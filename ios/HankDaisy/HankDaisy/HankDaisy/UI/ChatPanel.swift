import SwiftUI

/// Scrollable chat message history panel.
struct ChatPanel: View {
    let messages: [ChatMessage]

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(messages) { msg in
                        HStack(alignment: .top, spacing: 8) {
                            if msg.role == .user {
                                Spacer()
                            }

                            VStack(alignment: msg.role == .user ? .trailing : .leading, spacing: 4) {
                                Text(msg.text)
                                    .font(.body)
                                    .lineLimit(nil)
                                    .padding(12)
                                    .background(msg.role == .user ? Color.blue : Color.gray.opacity(0.3))
                                    .foregroundColor(msg.role == .user ? .white : .black)
                                    .cornerRadius(12)

                                Text(formatTime(msg.timestamp))
                                    .font(.caption2)
                                    .foregroundColor(.gray)
                                    .padding(.horizontal, 12)
                            }

                            if msg.role == .assistant {
                                Spacer()
                            }
                        }
                        .id(msg.id)
                    }

                    if messages.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "message.fill")
                                .font(.system(size: 48))
                                .foregroundColor(.gray)
                            Text("No messages yet")
                                .font(.body)
                                .foregroundColor(.gray)
                        }
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding()
                    }
                }
                .padding()
                .onChange(of: messages.count) { _ in
                    if let lastMsg = messages.last {
                        proxy.scrollTo(lastMsg.id, anchor: .bottom)
                    }
                }
            }
        }
        .background(Color(.systemBackground))
    }

    private func formatTime(_ timestamp: TimeInterval) -> String {
        let date = Date(timeIntervalSince1970: timestamp)
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

#Preview {
    ChatPanel(messages: [
        ChatMessage(role: .user, text: "What's wrong with my car?", timestamp: Date().timeIntervalSince1970),
        ChatMessage(role: .assistant, text: "Based on the image, I can see your alternator looks like it needs replacement. The brushes are worn.", timestamp: Date().timeIntervalSince1970)
    ])
}
