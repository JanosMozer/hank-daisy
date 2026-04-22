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
        // In the simulator, set up MockDeviceKit for testing without hardware
        #if targetEnvironment(simulator)
        MockDeviceKit.shared.enable()
        #endif
    }
}
