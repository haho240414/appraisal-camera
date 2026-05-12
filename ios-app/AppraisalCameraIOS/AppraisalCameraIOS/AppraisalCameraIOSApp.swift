import SwiftUI

@main
struct AppraisalCameraIOSApp: App {
    @State private var store = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView(store: store)
        }
    }
}
