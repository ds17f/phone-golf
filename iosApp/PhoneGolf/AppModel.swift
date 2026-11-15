import Combine
import FamilyControls
import Foundation
import UserNotifications

@MainActor
final class AppModel: ObservableObject {
    @Published var token: String? {
        didSet { UserDefaults.standard.set(token, forKey: "token") }
    }
    /// "server" (play with a group) or "local" (solo, no server).
    @Published var mode: String {
        didSet { UserDefaults.standard.set(mode, forKey: "mode") }
    }
    @Published var serverURL: String {
        didSet { UserDefaults.standard.set(serverURL, forKey: "serverURL") }
    }
    @Published var screenTimeStatus: AuthorizationStatus = AuthorizationCenter.shared.authorizationStatus
    @Published var reminderOn: Bool {
        didSet { UserDefaults.standard.set(reminderOn, forKey: "reminderOn"); Task { await updateReminder() } }
    }
    @Published var reminderHour: Int {
        didSet { UserDefaults.standard.set(reminderHour, forKey: "reminderHour"); Task { await updateReminder() } }
    }

    init() {
        let d = UserDefaults.standard
        token = d.string(forKey: "token")
        // Solo is the default. A player from an older version who has a key stays in group mode.
        mode = d.string(forKey: "mode") ?? (d.string(forKey: "token") != nil ? "server" : "local")
        serverURL = d.string(forKey: "serverURL")
            ?? (Bundle.main.object(forInfoDictionaryKey: "PGApiBaseURL") as? String)
            ?? "https://golf.example.com"
        reminderOn = d.object(forKey: "reminderOn") as? Bool ?? true
        reminderHour = d.object(forKey: "reminderHour") as? Int ?? 9

        // iOS does not always know the status when the app starts. It reports the
        // status a moment later, so the model follows each change.
        statusWatch = AuthorizationCenter.shared.$authorizationStatus
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                self?.screenTimeStatus = status
                if status == .approved { UserDefaults.standard.set(true, forKey: "screenTimeAllowed") }
            }
        // The player gave access before. Ask iOS again: it answers with no prompt.
        if d.bool(forKey: "screenTimeAllowed"), screenTimeStatus != .approved {
            Task { await requestScreenTime() }
        }
    }

    private var statusWatch: AnyCancellable?

    var api: Api {
        Api(baseURL: URL(string: serverURL) ?? URL(string: "https://golf.example.com")!, token: token)
    }

    var isLocal: Bool { mode == "local" }

    var isSignedIn: Bool { isLocal ? LocalBackend.exists : token != nil }

    /// The server in group mode, or this phone in solo mode.
    var backend: any Backend { isLocal ? LocalBackend.shared : api }

    /// True when this phone has a solo card.
    var hasSolo: Bool { LocalBackend.exists }

    /// Quick start: a solo player and a round that starts today. No server.
    func startSolo(name: String = "Me", handicap: Int) async throws {
        try LocalBackend.create(name: name, handicap: handicap)
        try await startSoloRound()
        mode = "local"
    }

    /// Starts the next solo round today: "Round 1", "Round 2", ...
    func startSoloRound() async throws {
        let n = (try await LocalBackend.shared.groups().first?.courses.count ?? 0) + 1
        _ = try await LocalBackend.shared.createCourse(
            groupId: LocalBackend.groupId, name: "Round \(n)", startDate: Fmt.day(Date()), weekdayPar: 0, weekendPar: 0)
    }

    /// Changes to group mode with this player key. The solo card stays on the phone.
    func playWithGroup(token: String, serverURL: String? = nil) {
        if let serverURL { self.serverURL = serverURL }
        self.token = token
        mode = "server"
    }

    /// Goes back to the solo card. Does not sign out of the server.
    func playSolo() {
        mode = "local"
    }

    /// Group mode: forgets the player key. Goes back to the solo card if there is one.
    func signOut() {
        token = nil
        mode = "local"
    }

    /// Deletes the solo card and forgets the player key. The app shows the first screen again.
    func startOver() {
        LocalBackend.delete()
        token = nil
        mode = "local"
    }

    // MARK: Screen Time

    /// Asks for Screen Time access for this person (not a child). Needed to show the pickups report.
    func requestScreenTime() async {
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
        } catch {
            // The user said no, or the device does not support it. Manual entry still works.
        }
        screenTimeStatus = AuthorizationCenter.shared.authorizationStatus
        if screenTimeStatus == .approved { UserDefaults.standard.set(true, forKey: "screenTimeAllowed") }
    }

    // MARK: Daily reminder

    static let reminderId = "daily-score-reminder"

    func updateReminder() async {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [Self.reminderId])
        guard reminderOn, isSignedIn else { return }
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
        guard granted else { return }
        let content = UNMutableNotificationContent()
        content.title = "Phone Golf"
        content.body = "Time to post yesterday's score. Open the app and type your pickups."
        var when = DateComponents()
        when.hour = reminderHour
        when.minute = 0
        let request = UNNotificationRequest(
            identifier: Self.reminderId,
            content: content,
            trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true)
        )
        try? await center.add(request)
    }
}
