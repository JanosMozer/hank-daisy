import Foundation

/// Events streamed from the Python Hank agent WebSocket server.
enum AgentEvent: Equatable {
    /// A chunk of the response text (streaming token).
    case chunk(String)

    /// The response is complete.
    case done

    /// An error occurred during the query.
    case error(String)
}
