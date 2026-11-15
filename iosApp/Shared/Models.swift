import Foundation

struct Player: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let platform: String
}

struct Member: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let platform: String
    let handicap: Int
}

struct Course: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let startDate: String
    let endDate: String
    let status: String // upcoming | active | finished
    let currentHole: Int?
}

struct GolfGroup: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let inviteCode: String
    let ownerId: String
    let members: [Member]
    let courses: [Course]
}

struct Suggestion: Codable, Hashable {
    let value: Int
    let explanation: String
}

struct HandicapInfo: Codable {
    let current: Int
    let suggestion: Suggestion?
    let suggestionNeedsDays: Int
}

struct HoleResult: Codable, Hashable {
    let hole: Int
    let date: String
    let par: Int
    let handicap: Int
    let gross: Int?
    let toPar: Int?
    /// True when the count is from Screen Time and the player did not sign for it.
    /// The server does not send this value.
    var pencil: Bool? = nil
}

struct PlayerCard: Codable, Identifiable, Hashable {
    let playerId: String
    let name: String
    let handicap: Int
    let thru: Int
    let totalToPar: Int
    let holes: [HoleResult]
    var id: String { playerId }
}

struct LeaderboardCourse: Codable {
    let id: String
    let name: String
    let startDate: String
    let endDate: String
    let status: String
    let currentHole: Int?
    let groupId: String
}

struct Leaderboard: Codable {
    let course: LeaderboardCourse
    let leaderboard: [PlayerCard]
}

struct DayScore: Codable, Identifiable, Hashable {
    let date: String
    let count: Int
    let metric: String
    let source: String
    var id: String { date }
}

// MARK: - Formatting

enum Fmt {
    /// "E" for even, "-3" under par, "+2" over par.
    static func toPar(_ n: Int?) -> String {
        guard let n else { return "–" }
        if n == 0 { return "E" }
        return n > 0 ? "+\(n)" : "\(n)"
    }

    static let isoDay: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    static func day(_ date: Date) -> String { isoDay.string(from: date) }

    static func pretty(_ iso: String) -> String {
        guard let d = isoDay.date(from: iso) else { return iso }
        return d.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day())
    }
}
