import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // Password-recovery links (sharedrecipes://auth?code=…) arrive here. The URL
                // scheme is declared in Info.plist (CFBundleURLTypes); this forwards the link
                // to SupabaseClient.handleDeeplinks through the shared Kotlin code.
                .onOpenURL { url in
                    MainViewControllerKt.handleAuthDeepLink(url: url)
                }
        }
    }
}