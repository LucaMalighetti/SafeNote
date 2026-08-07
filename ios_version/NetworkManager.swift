import Foundation

class NetworkManager: ObservableObject {
    static let shared = NetworkManager()
    let baseURL = "https://safenote-szau.onrender.com"

    @Published var currentUser: String? = UserDefaults.standard.string(forKey: "user")
    @Published var currentClass: String? = UserDefaults.standard.string(forKey: "class")

    func login(email: String, username: String, pass: String, className: String, completion: @escaping (Bool) -> Void) {
        // Logica di login/registrazione speculare al server
        // Invia richiesta a /auth/request-code...
        completion(true) // Mock per struttura
    }

    func saveSession(user: String, className: String) {
        UserDefaults.standard.set(user, forKey: "user")
        UserDefaults.standard.set(className, forKey: "class")
        self.currentUser = user
        self.currentClass = className
    }
}
