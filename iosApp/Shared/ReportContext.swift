import DeviceActivity
import Foundation
import SwiftUI

// Shared by the app and the report extension. The app asks iOS to draw the
// report with this name; the extension supplies the view for it.
extension DeviceActivityReport.Context {
    static let pickups = Self("Pickups")
    /// The scorecard and the leaderboard of the round, with the pickups of each day.
    static let scorecard = Self("Scorecard")
    /// The scoreboard strip of the round: the hole, today, and the round to par.
    static let strip = Self("Strip")
    /// One plaque for each day that the player signs for, in a column.
    static let signList = Self("SignList")
}

enum PickupsFilter {
    /// One local calendar day, iPhone only (ignores iPad/Mac data shared through iCloud).
    static func day(_ date: Date, calendar: Calendar = .current) -> DeviceActivityFilter {
        let start = calendar.startOfDay(for: date)
        let end = calendar.date(byAdding: .day, value: 1, to: start)!
        return DeviceActivityFilter(
            segment: .daily(during: DateInterval(start: start, end: end)),
            users: .all,
            devices: .init([.iPhone])
        )
    }

    /// Each day from the first day to the last day, iPhone only. The days are `YYYY-MM-DD`.
    static func days(from first: String, to last: String, calendar: Calendar = .current) -> DeviceActivityFilter {
        let start = calendar.startOfDay(for: Fmt.isoDay.date(from: first) ?? Date())
        let lastDay = calendar.startOfDay(for: Fmt.isoDay.date(from: last) ?? Date())
        let end = calendar.date(byAdding: .day, value: 1, to: max(start, lastDay))!
        return DeviceActivityFilter(
            segment: .daily(during: DateInterval(start: start, end: end)),
            users: .all,
            devices: .init([.iPhone])
        )
    }
}
