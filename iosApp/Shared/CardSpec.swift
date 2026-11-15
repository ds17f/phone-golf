import Foundation

/// The round that the report extension draws. The app writes it to the App
/// Group. The extension reads it and adds the Screen Time pickups. Data goes
/// only from the app to the extension: the extension cannot send data out.
struct CardSpec: Codable {
    static let suite = "group.com.phonegolf.app"
    private static let key = "card"

    /// The round with the signed scores only.
    let board: Leaderboard
    let meId: String?
    let today: String
    /// Solo: there is no group. A signature is optional.
    let solo: Bool

    func save() {
        guard let data = try? JSONEncoder().encode(self) else { return }
        UserDefaults(suiteName: Self.suite)?.set(data, forKey: Self.key)
    }

    static func load() -> CardSpec? {
        guard let data = UserDefaults(suiteName: suite)?.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(CardSpec.self, from: data)
    }

    /// The round with the pickups of each day on the card of the player. A hole
    /// that the player signed keeps its signed score, but not the hole of today.
    func merged(with pickups: [String: Int]) -> Leaderboard {
        let cards = board.leaderboard.map { p -> PlayerCard in
            guard p.playerId == meId else { return p }
            let holes = p.holes.map { h -> HoleResult in
                guard h.date <= today, let n = pickups[h.date] else { return h }
                // A signed score stays. But today is not finished: if the count
                // is higher now than the signed count, show the count of now.
                if let signed = h.gross, !(h.date == today && signed != n) { return h }
                return HoleResult(hole: h.hole, date: h.date, par: h.par, handicap: h.handicap,
                                  gross: n, toPar: n - h.handicap - h.par, pencil: true)
            }
            let played = holes.filter { $0.gross != nil }
            return PlayerCard(playerId: p.playerId, name: p.name, handicap: p.handicap, thru: played.count,
                              totalToPar: played.reduce(0) { $0 + ($1.toPar ?? 0) }, holes: holes)
        }
        return Leaderboard(course: board.course, leaderboard: cards)
    }
}

/// The days in the sign table. The app draws the rows and the fields. The
/// extension draws one plaque for each day, with the same row height.
enum SignList {
    static let rowHeight: CGFloat = 56
    private static let key = "signDates"

    static func save(_ dates: [String]) {
        UserDefaults(suiteName: CardSpec.suite)?.set(dates, forKey: key)
    }

    static func load() -> [String] {
        UserDefaults(suiteName: CardSpec.suite)?.stringArray(forKey: key) ?? []
    }
}
