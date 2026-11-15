import DeviceActivity
import SwiftUI

// Runs inside Apple's sandbox. It can read Screen Time data and draw it, but it
// cannot send data to the app or to the network. That is why the player types
// the number in the main app.
@main
struct PickupsReportExtension: DeviceActivityReportExtension {
    var body: some DeviceActivityReportScene {
        TotalPickupsReport(context: .pickups) { config in
            PickupsView(config: config)
        }
        ScorecardReport { pickups in
            ScorecardReportView(pickups: pickups)
        }
        StripReport { pickups in
            StripReportView(pickups: pickups)
        }
        SignListReport { pickups in
            SignListView(pickups: pickups)
        }
    }
}

struct PickupsConfiguration {
    let pickups: Int
    let hasData: Bool
}

struct TotalPickupsReport: DeviceActivityReportScene {
    let context: DeviceActivityReport.Context
    let content: (PickupsConfiguration) -> PickupsView

    func makeConfiguration(representing data: DeviceActivityResults<DeviceActivityData>) async -> PickupsConfiguration {
        // Screen Time "Pickups" = pickups that led to an app
        // plus pickups where no app was opened.
        var pickups = 0
        var hasData = false
        for await device in data {
            for await segment in device.activitySegments {
                hasData = true
                pickups += segment.totalPickupsWithoutApplicationActivity
                for await category in segment.categories {
                    for await app in category.applications {
                        pickups += app.numberOfPickups
                    }
                }
            }
        }
        return PickupsConfiguration(pickups: pickups, hasData: hasData)
    }
}

/// The number on the scoreboard: a cream plaque on the green board. The colors
/// are the same as `Golf.board`, `Golf.plaque` and `Golf.plaqueInk` in the app.
struct PickupsView: View {
    let config: PickupsConfiguration

    private let board = Color(red: 0x17 / 255, green: 0x43 / 255, blue: 0x2A / 255)
    private let boardText = Color(red: 0xCF / 255, green: 0xE3 / 255, blue: 0xD6 / 255)
    private let plaque = Color(red: 0xFB / 255, green: 0xFA / 255, blue: 0xF4 / 255)
    private let ink = Color(red: 0x14 / 255, green: 0x20 / 255, blue: 0x19 / 255)

    var body: some View {
        VStack(spacing: 4) {
            if config.hasData {
                Text("\(config.pickups)")
                    .font(.system(size: 48, weight: .bold, design: .monospaced))
                    .foregroundStyle(ink)
                    .padding(.horizontal, 22)
                    .padding(.vertical, 2)
                    .background(plaque, in: RoundedRectangle(cornerRadius: 6))
                Text("PICKUPS")
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(1.5)
                    .foregroundStyle(boardText)
            } else {
                Text("No Screen Time data for this day yet.")
                    .font(.subheadline)
                    .foregroundStyle(boardText)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(board)
    }
}

/// The pickups of each day in the report: `YYYY-MM-DD` to the count.
func pickupsByDay(_ data: DeviceActivityResults<DeviceActivityData>) async -> [String: Int] {
    var days: [String: Int] = [:]
    for await device in data {
        for await segment in device.activitySegments {
            var pickups = segment.totalPickupsWithoutApplicationActivity
            for await category in segment.categories {
                for await app in category.applications {
                    pickups += app.numberOfPickups
                }
            }
            days[Fmt.day(segment.dateInterval.start), default: 0] += pickups
        }
    }
    return days
}

struct ScorecardReport: DeviceActivityReportScene {
    let context: DeviceActivityReport.Context = .scorecard
    let content: ([String: Int]) -> ScorecardReportView
    func makeConfiguration(representing data: DeviceActivityResults<DeviceActivityData>) async -> [String: Int] {
        await pickupsByDay(data)
    }
}

struct StripReport: DeviceActivityReportScene {
    let context: DeviceActivityReport.Context = .strip
    let content: ([String: Int]) -> StripReportView
    func makeConfiguration(representing data: DeviceActivityResults<DeviceActivityData>) async -> [String: Int] {
        await pickupsByDay(data)
    }
}

struct SignListReport: DeviceActivityReportScene {
    let context: DeviceActivityReport.Context = .signList
    let content: ([String: Int]) -> SignListView
    func makeConfiguration(representing data: DeviceActivityResults<DeviceActivityData>) async -> [String: Int] {
        await pickupsByDay(data)
    }
}

/// The scoreboard strip, with the pickups of today and the round to par.
struct StripReportView: View {
    let pickups: [String: Int]

    var body: some View {
        Group {
            if let spec = CardSpec.load() {
                let live = spec.merged(with: pickups)
                ScoreStrip(course: live.course, card: live.leaderboard.first { $0.playerId == spec.meId }, today: spec.today)
                    .padding(.horizontal)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemGroupedBackground))
    }
}

/// The sign table: one plaque for each day, in the order that the app gives.
struct SignListView: View {
    let pickups: [String: Int]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(SignList.load(), id: \.self) { day in
                Text(pickups[day].map { "\($0)" } ?? "–")
                    .font(.system(size: 24, weight: .bold, design: .monospaced))
                    .foregroundStyle(Golf.plaqueInk)
                    .lineLimit(1).minimumScaleFactor(0.6)
                    .frame(maxWidth: .infinity)
                    .frame(height: SignList.rowHeight - 14)
                    .background(Golf.plaque, in: RoundedRectangle(cornerRadius: 4))
                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(Golf.paperLine, lineWidth: 1))
                    .frame(height: SignList.rowHeight)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Golf.paper)
    }
}

/// The scorecard and the leaderboard, drawn by iOS. The card of the player has
/// the Screen Time pickups. The app cannot read them.
struct ScorecardReportView: View {
    let pickups: [String: Int]

    var body: some View {
        Group {
            if let spec = CardSpec.load() {
                // No scroll when the card fits the screen.
                ViewThatFits(in: .vertical) {
                    round(spec)
                    ScrollView { round(spec) }
                }
            } else {
                Text("Open Phone Golf again to see your card.")
                    .font(.subheadline).foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color(.systemGroupedBackground))
    }

    private func round(_ spec: CardSpec) -> some View {
        let live = spec.merged(with: pickups)
        return VStack(spacing: 16) {
            PaperScorecard(board: live, meId: spec.meId, today: spec.today)
            if spec.board.leaderboard.count > 1 {
                // The leaderboard is official: signed scores only.
                TournamentBoard(board: spec.board, meId: spec.meId, today: spec.today)
            }
        }
        .padding(.horizontal)
        .padding(.bottom)
    }
}
