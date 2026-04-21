import SwiftUI
import MWDATCore
import MWDATMockDevice

@main
struct HankDaisyApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    setupWearables()
                }
        }
    }

    private func setupWearables() {
        // In DEBUG mode, set up MockDeviceKit for simulator testing
        #if DEBUG
        do {
            let mockDeviceKit = try MockDeviceKit()
            try mockDeviceKit.pairDisplaylessGlasses()
        } catch {
            print("Failed to setup MockDeviceKit: \(error)")
        }
        #endif
    }
}
