import SwiftUI

@main
struct PhoneGolfApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            Group {
                if !model.isSignedIn {
                    WelcomeView()
                } else {
                    NavigationStack {
                        RoundView()
                    }
                    // A change of mode or player starts again at the round screen.
                    .id(model.mode + "|" + (model.token ?? ""))
                }
            }
            .environmentObject(model)
            .tint(.green)
        }
    }
}
