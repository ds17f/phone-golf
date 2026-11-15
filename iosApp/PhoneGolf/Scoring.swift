import Foundation

/// The scoring rules, for solo mode. These must stay the same as api/src/scoring.ts.
///
///     net   = gross - handicap
///     toPar = net - par
enum Scoring {
    static let holes = 18
    static let suggestionWindowDays = 14
    static let suggestionMinDays = 3

    struct HandicapEntry: Codable, Hashable {
        var effectiveDate: String
        var value: Int
        var source: String
    }

    struct HoleDef {
        let hole: Int
        let date: String
        let par: Int
    }

    private static var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = .current
        return c
    }

    static func addDays(_ day: String, _ n: Int) -> String {
        guard let d = Fmt.isoDay.date(from: day), let r = calendar.date(byAdding: .day, value: n, to: d) else { return day }
        return Fmt.day(r)
    }

    static func daysBetween(_ a: String, _ b: String) -> Int {
        guard let da = Fmt.isoDay.date(from: a), let db = Fmt.isoDay.date(from: b) else { return 0 }
        return calendar.dateComponents([.day], from: da, to: db).day ?? 0
    }

    static func isWeekend(_ day: String) -> Bool {
        guard let d = Fmt.isoDay.date(from: day) else { return false }
        let wd = calendar.component(.weekday, from: d) // 1 = Sunday, 7 = Saturday
        return wd == 1 || wd == 7
    }

    /// History must be sorted by effectiveDate.
    static func handicapOn(_ history: [HandicapEntry], _ date: String) -> Int {
        var value = 0
        for h in history {
            if h.effectiveDate <= date { value = h.value } else { break }
        }
        return value
    }

    /// Average of the best (lowest) half of the counts. Nil with fewer than 3 days.
    static func suggest(_ counts: [Int]) -> (value: Int, used: Int)? {
        guard counts.count >= suggestionMinDays else { return nil }
        let used = (counts.count + 1) / 2
        let best = counts.sorted().prefix(used)
        return (Int((Double(best.reduce(0, +)) / Double(used)).rounded()), used)
    }

    static func pars(start: String, weekdayPar: Int, weekendPar: Int) -> [Int] {
        (0..<holes).map { isWeekend(addDays(start, $0)) ? weekendPar : weekdayPar }
    }

    static func courseInfo(id: String, name: String, start: String, today: String) -> Course {
        let end = addDays(start, holes - 1)
        let status = today < start ? "upcoming" : today > end ? "finished" : "active"
        let hole = status == "active" ? daysBetween(start, today) + 1 : nil
        return Course(id: id, name: name, startDate: start, endDate: end, status: status, currentHole: hole)
    }

    static func buildCard(playerId: String, name: String, holes: [HoleDef], history: [HandicapEntry], scores: [String: Int]) -> PlayerCard {
        let results = holes.map { h -> HoleResult in
            let handicap = handicapOn(history, h.date)
            let gross = scores[h.date]
            return HoleResult(hole: h.hole, date: h.date, par: h.par, handicap: handicap,
                              gross: gross, toPar: gross.map { $0 - handicap - h.par })
        }
        let played = results.filter { $0.gross != nil }
        return PlayerCard(playerId: playerId, name: name, handicap: history.last?.value ?? 0, thru: played.count,
                          totalToPar: played.reduce(0) { $0 + ($1.toPar ?? 0) }, holes: results)
    }
}
