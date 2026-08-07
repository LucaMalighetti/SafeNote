import SwiftUI

struct ContentView: View {
    @StateObject var security = SecurityManager()
    @StateObject var network = NetworkManager.shared

    var body: some View {
        ZStack {
            if network.currentUser == nil {
                LoginView()
            } else {
                MainGalleryView()
            }

            if security.isThreatDetected {
                Color.black.edgesIgnoringSafeArea(.all)
            }
        }
    }
}

struct LoginView: View {
    var body: some View {
        VStack {
            Text("SafeNote iOS").font(.largeTitle).bold()
            // Form di login speculare ad Android
        }
    }
}

struct MainGalleryView: View {
    var body: some View {
        NavigationView {
            ScrollView {
                Text("Benvenuto nella galleria protetta")
            }
            .navigationTitle("SafeNote")
        }
    }
}
