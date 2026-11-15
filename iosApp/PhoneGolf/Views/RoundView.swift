import DeviceActivity
import FamilyControls
import SwiftUI

enum Route: Hashable {
    case group(String)
    case course(String)
    case settings
}

/// The round that the app shows. A player plays one round at a time.
enum Featured {
    /// The active course. If there is none, the next course. If there is none, the last course.
    static func pick(_ groups: [GolfGroup]) -> (group: GolfGroup, course: Course)? {
        let all = groups.flatMap { g in g.courses.map { (group: g, course: $0) } }
        if let c = all.filter({ $0.course.status == "active" }).max(by: { $0.course.startDate < $1.course.startDate }) { return c }
        if let c = all.filter({ $0.course.status == "upcoming" }).min(by: { $0.course.startDate < $1.course.startDate }) { return c }
        return all.filter { $0.course.status == "finished" }.max { $0.course.startDate < $1.course.startDate }
    }

    /// Finished holes that the player did not sign for. The latest hole is first.
    static func unsigned(_ holes: [HoleResult], today: String, except: Set<String> = []) -> [HoleResult] {
        holes
            .filter { $0.date < today && $0.gross == nil && !except.contains($0.date) }
            .sorted { $0.date > $1.date }
    }
}

/// The home screen: the scoreboard strip and the scorecard of the current round.
///
/// With Screen Time access, iOS draws the round (the report extension): the
/// card shows the pickups of each day, and the player types nothing to see
/// them. The app cannot read those numbers. For a group, the player signs the
/// card: the player types each count, and then the server has it.
struct RoundView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var loaded = false
    @State private var group: GolfGroup?
    @State private var board: Leaderboard?
    @State private var meId: String?
    @State private var error: String?
    @State private var sign: SignTarget?
    @State private var groupSheet: GroupSheet.Mode?
    @State private var busy = false
    /// Changes when the round data changes. iOS then draws the report again.
    @State private var specVersion = 0

    private var today: String { Fmt.day(Date()) }
    private var myCard: PlayerCard? { board?.leaderboard.first { $0.playerId == meId } }
    private var unsigned: [HoleResult] { Featured.unsigned(myCard?.holes ?? [], today: today) }

    /// The round that iOS draws, or nil when the app draws the round.
    private var liveBoard: Leaderboard? {
        guard model.screenTimeStatus == .approved, let b = board, b.course.status != "upcoming" else { return nil }
        return b
    }

    var body: some View {
        Group {
            if let b = liveBoard {
                let days = PickupsFilter.days(from: b.course.startDate, to: min(today, b.course.endDate))
                VStack(spacing: 0) {
                    DeviceActivityReport(.strip, filter: days)
                        .frame(height: 116)
                        .id(specVersion)
                    signArea(b)
                        .padding(.horizontal)
                        .padding(.bottom, 12)
                    DeviceActivityReport(.scorecard, filter: days)
                        .id(specVersion)
                    // The bottom of the screen is from the app, not from the report,
                    // so that the swipe up that closes the app works.
                    bottomBar(b)
                }
            } else {
                scroll
            }
        }
        .background(Color(.systemGroupedBackground))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text("Phone Golf").font(.system(size: 19, weight: .bold, design: .serif))
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                NavigationLink(value: Route.settings) { Image(systemName: "gearshape") }
                    .accessibilityLabel("Settings")
            }
        }
        .navigationDestination(for: Route.self) { route in
            switch route {
            case .group(let id): GroupView(groupId: id)
            case .course(let id): CourseView(courseId: id)
            case .settings: SettingsView()
            }
        }
        .sheet(item: $sign) { target in
            SignCardSheet(holes: (myCard?.holes ?? []).filter { target.dates.contains($0.date) }) { await load() }
                .environmentObject(model)
        }
        .sheet(item: $groupSheet) { mode in
            GroupSheet(mode: mode) { Task { await load() } }
        }
        .task { await load() }
        .onChange(of: scenePhase) { phase in
            if phase == .active { Task { await load() } }
        }
    }

    // MARK: Parts

    /// The holes that the sign table shows: each finished hole with no
    /// signature, and the hole of today. They are in hole order.
    private var toSign: [HoleResult] {
        let open = Set(unsigned.map(\.date))
        return (myCard?.holes ?? []).filter { open.contains($0.date) || $0.date == today }
    }

    /// Between the strip and the card: the button to sign the card.
    @ViewBuilder private func signArea(_ b: Leaderboard) -> some View {
        let todayHole = myCard?.holes.first { $0.date == today }
        if !unsigned.isEmpty {
            signButton
        } else if let t = todayHole, t.gross == nil {
            Button("Sign for today") { sign = SignTarget(dates: [t.date]) }
                .buttonStyle(GolfButtonStyle())
        } else if let t = todayHole {
            // Today has a signature, but the count can be higher now.
            Button("Sign for today again") { sign = SignTarget(dates: [t.date]) }
                .font(.subheadline)
        }
    }

    /// Below the report: the next round, and one line that explains the grey scores.
    @ViewBuilder private func bottomBar(_ b: Leaderboard) -> some View {
        VStack(spacing: 8) {
            if let error { Text(error).font(.footnote).foregroundStyle(.red) }
            if b.course.status == "finished" {
                newRoundButton
            } else {
                Text("Grey scores are from Screen Time. Sign for them to make them official.")
                    .font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(Color(.systemGroupedBackground))
    }

    /// The player makes the scores official: the player types each count.
    private var signButton: some View {
        Button(unsigned.count == 1 ? "Sign your card · 1 hole" : "Sign your card · \(unsigned.count) holes") {
            sign = SignTarget(dates: toSign.map(\.date))
        }
        .buttonStyle(GolfButtonStyle())
    }

    private var newRoundButton: some View {
        Button(model.isLocal ? "Play another round" : "Start a new round") { Task { await newRound() } }
            .buttonStyle(GolfButtonStyle())
            .disabled(busy)
    }

    /// The round as the app draws it: only the scores that the player typed.
    private var scroll: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let error { Text(error).font(.footnote).foregroundStyle(.red) }
                if let b = board {
                    ScoreStrip(course: b.course, card: myCard, today: today)
                    action(b)
                    PaperScorecard(board: b, meId: meId, today: today) { sign = SignTarget(dates: [$0.date]) }
                    if b.leaderboard.count > 1 {
                        TournamentBoard(board: b, meId: meId, today: today)
                    }
                } else if loaded {
                    noRound
                } else if error == nil {
                    ProgressView().padding(40)
                }
            }
            .padding()
        }
        .refreshable { await load() }
    }

    /// The one button below the strip. It depends on the state of the round.
    @ViewBuilder private func action(_ b: Leaderboard) -> some View {
        switch b.course.status {
        case "active":
            if !unsigned.isEmpty {
                signButton
            } else if myCard != nil {
                Button("See today so far") { sign = SignTarget(dates: [today]) }
                    .font(.subheadline)
            }
        case "finished":
            if !unsigned.isEmpty { signButton }
            newRoundButton
        default:
            // A solo round can start today. The player does not wait for the first day.
            if model.isLocal {
                Button("Tee off today") {
                    Task {
                        try? await LocalBackend.shared.moveCourse(b.course.id, startDate: today)
                        await load()
                    }
                }
                .buttonStyle(GolfButtonStyle())
            }
        }
    }

    @ViewBuilder private var noRound: some View {
        VStack(spacing: 12) {
            Image(systemName: "flag.fill").font(.system(size: 40)).foregroundStyle(Golf.green)
            if model.isLocal || group != nil {
                Text("No round is on.").foregroundStyle(.secondary)
                Button(model.isLocal ? "Tee off" : "Start a round") { Task { await newRound() } }
                    .buttonStyle(GolfButtonStyle())
                    .disabled(busy)
            } else {
                Text("You are not in a group yet.").foregroundStyle(.secondary)
                Button("Join with a code") { groupSheet = .join }.buttonStyle(GolfButtonStyle())
                Button("Make a group") { groupSheet = .create }
            }
        }
        .padding(.top, 60)
    }

    // MARK: Data

    private func load() async {
        do {
            let backend = model.backend
            let groups = try await backend.groups()
            meId = try await backend.me().id
            let pick = Featured.pick(groups)
            group = pick?.group ?? groups.first
            board = try await pick.asyncMap { try await backend.leaderboard(courseId: $0.course.id) }
            error = nil
            loaded = true
            shareRound()
        } catch {
            self.error = model.isLocal ? error.localizedDescription : "Could not reach the server: \(error.localizedDescription)"
        }
    }

    /// Gives the round to the report extension. The extension adds the pickups.
    private func shareRound() {
        guard let board else { return }
        let spec = CardSpec(board: board, meId: meId, today: today, solo: model.isLocal)
        spec.save()
        // Draw the report again only when the round is different.
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        let v = (try? encoder.encode(spec))?.hashValue ?? 0
        if v != specVersion { specVersion = v }
    }

    private func newRound() async {
        busy = true
        defer { busy = false }
        do {
            if model.isLocal {
                try await model.startSoloRound()
            } else if let g = group {
                _ = try await model.backend.createCourse(
                    groupId: g.id, name: "The \(g.name) Open", startDate: today, weekdayPar: 0, weekendPar: 0)
            }
            await load()
        } catch {
            self.error = error.localizedDescription
        }
    }
}

private extension Optional {
    func asyncMap<T>(_ f: (Wrapped) async throws -> T) async rethrows -> T? {
        guard let v = self else { return nil }
        return try await f(v)
    }
}
