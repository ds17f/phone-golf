import Foundation

/// Solo mode: one player, one built-in group ("Solo"), and all data in one JSON
/// file on this phone. No network. Rules are the same as on the server.
actor LocalBackend: Backend {
    static let groupId = "solo"
    static let shared = LocalBackend(url: defaultURL)

    static var defaultURL: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent("solo.json")
    }

    static var exists: Bool { FileManager.default.fileExists(atPath: defaultURL.path) }

    struct StoredScore: Codable { var count: Int; var metric: String; var source: String }
    struct StoredCourse: Codable { var id: String; var name: String; var startDate: String; var pars: [Int] }
    struct SoloData: Codable {
        var player: Player
        var scores: [String: StoredScore]
        var handicaps: [Scoring.HandicapEntry]
        var courses: [StoredCourse]
    }

    private let url: URL
    private let today: () -> String

    init(url: URL, today: @escaping () -> String = { Fmt.day(Date()) }) {
        self.url = url
        self.today = today
    }

    nonisolated var isLocal: Bool { true }

    /// Makes a new solo player. Replaces any old solo data.
    static func create(name: String, handicap: Int, url: URL = defaultURL) throws {
        let data = SoloData(
            player: Player(id: UUID().uuidString, name: name, platform: "ios"),
            scores: [:],
            handicaps: [.init(effectiveDate: "0000-01-01", value: handicap, source: "start")],
            courses: []
        )
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(data).write(to: url, options: .atomic)
    }

    static func delete(url: URL = defaultURL) {
        try? FileManager.default.removeItem(at: url)
    }

    private func load() throws -> SoloData {
        try JSONDecoder().decode(SoloData.self, from: Data(contentsOf: url))
    }

    private func save(_ d: SoloData) throws {
        try JSONEncoder().encode(d).write(to: url, options: .atomic)
    }

    private func history(_ d: SoloData) -> [Scoring.HandicapEntry] {
        d.handicaps.sorted { $0.effectiveDate < $1.effectiveDate }
    }

    private func current(_ d: SoloData) -> Int { history(d).last?.value ?? 0 }

    private func info(_ c: StoredCourse) -> Course {
        Scoring.courseInfo(id: c.id, name: c.name, start: c.startDate, today: today())
    }

    private func solo(_ d: SoloData) -> GolfGroup {
        GolfGroup(
            id: Self.groupId, name: "Solo", inviteCode: "", ownerId: d.player.id,
            members: [Member(id: d.player.id, name: d.player.name, platform: "ios", handicap: current(d))],
            courses: d.courses.map(info).sorted { $0.startDate > $1.startDate }
        )
    }

    private func requireSolo(_ id: String) throws {
        guard id == Self.groupId else { throw ApiError(status: 404, message: "Group not found") }
    }

    private static let needServer = ApiError(status: 400, message: "Groups need a server. You play solo on this phone.")

    // MARK: Backend

    func me() async throws -> Player { try load().player }

    func rename(_ name: String) async throws -> Player {
        var d = try load()
        d.player = Player(id: d.player.id, name: name, platform: "ios")
        try save(d)
        return d.player
    }

    func putScore(date: String, count: Int) async throws {
        var d = try load()
        d.scores[date] = StoredScore(count: count, metric: "pickups", source: "manual")
        try save(d)
    }

    func scores() async throws -> [DayScore] {
        let d = try load()
        let from = Scoring.addDays(today(), -30)
        return d.scores.filter { $0.key >= from }
            .map { DayScore(date: $0.key, count: $0.value.count, metric: $0.value.metric, source: $0.value.source) }
            .sorted { $0.date > $1.date }
    }

    func groups() async throws -> [GolfGroup] { [solo(try load())] }

    func group(_ id: String) async throws -> GolfGroup {
        try requireSolo(id)
        return solo(try load())
    }

    func createGroup(name: String, handicap: Int) async throws -> GolfGroup { throw Self.needServer }
    func joinGroup(code: String, handicap: Int) async throws -> GolfGroup { throw Self.needServer }
    func leaveGroup(_ id: String) async throws { throw Self.needServer }

    func handicap(groupId: String) async throws -> HandicapInfo {
        try requireSolo(groupId)
        let d = try load()
        let t = today()
        let from = Scoring.addDays(t, -Scoring.suggestionWindowDays)
        let to = Scoring.addDays(t, -1)
        let counts = d.scores.filter { $0.key >= from && $0.key <= to }.map { $0.value.count }
        let s = Scoring.suggest(counts)
        return HandicapInfo(
            current: current(d),
            suggestion: s.map { Suggestion(value: $0.value, explanation: "Average of your best \($0.used) of the last \(counts.count) days you scored.") },
            suggestionNeedsDays: s == nil ? Scoring.suggestionMinDays - counts.count : 0
        )
    }

    func setHandicap(groupId: String, value: Int, source: String) async throws {
        try requireSolo(groupId)
        var d = try load()
        // Same rule as the server: a change starts tomorrow.
        let effective = Scoring.addDays(today(), 1)
        d.handicaps.removeAll { $0.effectiveDate == effective }
        d.handicaps.append(.init(effectiveDate: effective, value: value, source: source))
        try save(d)
    }

    func createCourse(groupId: String, name: String, startDate: String, weekdayPar: Int, weekendPar: Int) async throws -> Course {
        try requireSolo(groupId)
        var d = try load()
        let c = StoredCourse(id: UUID().uuidString, name: name, startDate: startDate,
                             pars: Scoring.pars(start: startDate, weekdayPar: weekdayPar, weekendPar: weekendPar))
        d.courses.append(c)
        try save(d)
        return info(c)
    }

    /// Solo only: changes the first day of a course. Each par stays with its hole number.
    func moveCourse(_ courseId: String, startDate: String) throws {
        var d = try load()
        guard let i = d.courses.firstIndex(where: { $0.id == courseId }) else {
            throw ApiError(status: 404, message: "Course not found")
        }
        d.courses[i].startDate = startDate
        try save(d)
    }

    func leaderboard(courseId: String) async throws -> Leaderboard {
        let d = try load()
        guard let c = d.courses.first(where: { $0.id == courseId }) else {
            throw ApiError(status: 404, message: "Course not found")
        }
        let holes = c.pars.enumerated().map { i, par in
            Scoring.HoleDef(hole: i + 1, date: Scoring.addDays(c.startDate, i), par: par)
        }
        let scores = Dictionary(uniqueKeysWithValues: holes.compactMap { h in d.scores[h.date].map { (h.date, $0.count) } })
        let i = info(c)
        return Leaderboard(
            course: LeaderboardCourse(id: i.id, name: i.name, startDate: i.startDate, endDate: i.endDate,
                                      status: i.status, currentHole: i.currentHole, groupId: Self.groupId),
            leaderboard: [Scoring.buildCard(playerId: d.player.id, name: d.player.name, holes: holes,
                                            history: history(d), scores: scores)]
        )
    }
}
