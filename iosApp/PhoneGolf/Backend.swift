import Foundation

/// Where the game data lives. `Api` talks to the Phone Golf server.
/// `LocalBackend` keeps everything on this phone (solo mode, no server).
protocol Backend {
    var isLocal: Bool { get }
    func me() async throws -> Player
    func rename(_ name: String) async throws -> Player
    func putScore(date: String, count: Int) async throws
    func scores() async throws -> [DayScore]
    func groups() async throws -> [GolfGroup]
    func group(_ id: String) async throws -> GolfGroup
    func createGroup(name: String, handicap: Int) async throws -> GolfGroup
    func joinGroup(code: String, handicap: Int) async throws -> GolfGroup
    func leaveGroup(_ id: String) async throws
    func handicap(groupId: String) async throws -> HandicapInfo
    func setHandicap(groupId: String, value: Int, source: String) async throws
    func createCourse(groupId: String, name: String, startDate: String, weekdayPar: Int, weekendPar: Int) async throws -> Course
    func leaderboard(courseId: String) async throws -> Leaderboard
}

extension Api: Backend {
    var isLocal: Bool { false }
}
