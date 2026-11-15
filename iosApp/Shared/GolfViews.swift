import SwiftUI

// The golf look. The app and the report extension use the same views.

/// Fixed colors for the leaderboard and the paper scorecard (same in light and dark).
enum Golf {
    static let board = Color(red: 0x17 / 255, green: 0x43 / 255, blue: 0x2A / 255)
    static let boardLine = Color(red: 0x2B / 255, green: 0x5F / 255, blue: 0x40 / 255)
    static let boardText = Color(red: 0xCF / 255, green: 0xE3 / 255, blue: 0xD6 / 255)
    static let plaque = Color(red: 0xFB / 255, green: 0xFA / 255, blue: 0xF4 / 255)
    static let plaqueInk = Color(red: 0x14 / 255, green: 0x20 / 255, blue: 0x19 / 255)
    static let paper = Color(red: 0xFB / 255, green: 0xF6 / 255, blue: 0xE6 / 255)
    static let paperLine = Color(red: 0xC9 / 255, green: 0xBF / 255, blue: 0x9F / 255)
    static let paperInk = Color(red: 0x2A / 255, green: 0x2A / 255, blue: 0x22 / 255)
    static let paperMuted = Color(red: 0x6B / 255, green: 0x65 / 255, blue: 0x50 / 255)
    static let green = Color(red: 0x1F / 255, green: 0x5A / 255, blue: 0x36 / 255)
    static let greenDark = Color(red: 0x17 / 255, green: 0x3F / 255, blue: 0x27 / 255)
    static let gold = Color(red: 0xE8 / 255, green: 0xC5 / 255, blue: 0x47 / 255)
    static let red = Color(red: 0xC0 / 255, green: 0x28 / 255, blue: 0x2D / 255)
    /// A count that the player did not sign for: grey, as a pencil mark.
    static let pencil = Color(red: 0x9A / 255, green: 0x93 / 255, blue: 0x7C / 255)

    /// Golf convention: red is under par.
    static func toParColor(_ n: Int?, base: Color) -> Color {
        guard let n else { return .gray }
        return n < 0 ? red : n == 0 ? green : base
    }

    /// Tournament positions with ties: 1, T2, T2, 4. Players with no holes get none.
    static func positions(_ cards: [PlayerCard]) -> [String] {
        cards.map { p in
            guard p.thru > 0 else { return "" }
            let first = cards.firstIndex { $0.thru > 0 && $0.totalToPar == p.totalToPar } ?? 0
            let tied = cards.filter { $0.thru > 0 && $0.totalToPar == p.totalToPar }.count > 1
            return (tied ? "T" : "") + "\(first + 1)"
        }
    }
}

// MARK: - Tournament leaderboard

struct TournamentBoard: View {
    let board: Leaderboard
    let meId: String?
    let today: String

    private var subtitle: String {
        switch board.course.status {
        case "active": return "\(board.course.name) · Hole \(board.course.currentHole ?? 0) of 18"
        case "upcoming": return "\(board.course.name) · Starts \(Fmt.pretty(board.course.startDate))"
        default: return "\(board.course.name) · Final"
        }
    }

    var body: some View {
        let pos = Golf.positions(board.leaderboard)
        VStack(spacing: 6) {
            Text("LEADERBOARD")
                .font(.system(size: 24, weight: .bold, design: .serif))
                .tracking(5)
                .foregroundStyle(.white)
            Text(subtitle).font(.caption).foregroundStyle(Golf.boardText).padding(.bottom, 6)
            HStack(spacing: 0) {
                header("POS").frame(width: 40)
                header("PLAYER").frame(maxWidth: .infinity, alignment: .leading)
                header("TO PAR").frame(width: 60)
                header("TODAY").frame(width: 52)
                header("THRU").frame(width: 42)
            }
            ForEach(Array(board.leaderboard.enumerated()), id: \.element.id) { i, p in
                row(p, pos: pos[i])
            }
        }
        .padding(12)
        .background(Golf.board, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Golf.boardLine, lineWidth: 3))
    }

    private func header(_ t: String) -> some View {
        Text(t).font(.system(size: 10, weight: .semibold)).tracking(1.5).foregroundStyle(Golf.boardText)
    }

    private func thru(_ p: PlayerCard) -> String {
        if p.thru == 0 { return "–" }
        if board.course.status == "finished" || p.holes.last?.gross != nil { return "F" }
        return "\(p.thru)"
    }

    private func row(_ p: PlayerCard, pos: String) -> some View {
        let todayToPar = p.holes.first { $0.date == today }?.toPar
        let total: Int? = p.thru > 0 ? p.totalToPar : nil
        return HStack(spacing: 0) {
            plaque(pos, size: 15).frame(width: 40)
            Rectangle().fill(Golf.board).frame(width: 2)
            Text(p.name.uppercased())
                .font(.system(size: 15, weight: .bold, design: .serif))
                .tracking(0.5)
                .lineLimit(1)
                .foregroundStyle(Golf.plaqueInk)
                .padding(.horizontal, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
            Rectangle().fill(Golf.board).frame(width: 2)
            plaque(Fmt.toPar(total), color: Golf.toParColor(total, base: Golf.plaqueInk), size: 19).frame(width: 58)
            plaque(Fmt.toPar(todayToPar), color: Golf.toParColor(todayToPar, base: Golf.plaqueInk)).frame(width: 52)
            plaque(thru(p)).frame(width: 42)
        }
        .frame(height: 40)
        .background(Golf.plaque, in: RoundedRectangle(cornerRadius: 4))
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(p.playerId == meId ? Golf.gold : .clear, lineWidth: 2))
    }

    private func plaque(_ t: String, color: Color = Golf.plaqueInk, size: CGFloat = 16) -> some View {
        Text(t).font(.system(size: size, weight: .bold, design: .monospaced)).foregroundStyle(color)
    }
}

// MARK: - Paper scorecard

/// The paper card. It fills the width of the phone: two nines (Out, In) and a totals table.
struct PaperScorecard: View {
    let board: Leaderboard
    let meId: String?
    let today: String
    /// Called when the player taps one of their own holes (today or earlier).
    var onTapHole: ((HoleResult) -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(board.course.name).font(.system(size: 19, weight: .bold, design: .serif)).foregroundStyle(.white)
                    Text("\(Fmt.pretty(board.course.startDate)) – \(Fmt.pretty(board.course.endDate))")
                        .font(.caption).foregroundStyle(Golf.boardText)
                }
                Spacer()
                Label("SCORECARD", systemImage: "flag.fill")
                    .font(.system(size: 11, weight: .semibold, design: .serif)).tracking(2).foregroundStyle(Golf.gold)
            }
            .padding(12)
            .background(Golf.green)

            ScorecardHalf(label: "OUT", range: 1...9, cards: board.leaderboard, meId: meId, today: today, onTap: onTapHole)
            ScorecardHalf(label: "IN", range: 10...18, cards: board.leaderboard, meId: meId, today: today, onTap: onTapHole)
            ScorecardTotals(cards: board.leaderboard, meId: meId)

            HStack(spacing: 8) {
                ScoreMarkView(text: "12", color: Golf.red, circle: true, double: false); Text("under").font(.caption)
                ScoreMarkView(text: "40", color: Golf.paperInk, circle: false, double: false); Text("over").font(.caption)
                ScoreMarkView(text: "8", color: Golf.red, circle: true, double: true); Text("10+").font(.caption)
                if board.leaderboard.contains(where: { $0.holes.contains { $0.pencil == true } }) {
                    ScoreMarkView(text: "55", color: Golf.pencil, circle: nil, double: false); Text("not signed").font(.caption)
                }
            }
            .foregroundStyle(Golf.paperInk)
            .padding(.horizontal, 12)
            Text("Numbers are literal unlocks or pickups. Target = handicap + par.")
                .font(.caption2).foregroundStyle(Golf.paperMuted)
                .padding(.horizontal, 12).padding(.bottom, 12)
        }
        .background(Golf.paper)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Golf.paperLine, lineWidth: 1))
        .shadow(color: .black.opacity(0.1), radius: 8, y: 4)
    }
}

/// Cells and text styles of the card. The label and sum columns have a fixed
/// width. The nine hole cells share the remaining width, so the card needs no scroll.
private enum Card {
    static let labelW: CGFloat = 52, sumW: CGFloat = 38, rowH: CGFloat = 32, playerH: CGFloat = 40
    static let shade = Golf.paperInk.opacity(0.05)

    static func cell<C: View>(_ w: CGFloat?, h: CGFloat = rowH, bg: Color = .clear, leading: Bool = false,
                              @ViewBuilder _ content: () -> C) -> some View {
        // The color fills the cell. An empty cell keeps its size.
        bg.frame(minWidth: w ?? 0, maxWidth: w ?? .infinity, minHeight: h, maxHeight: h)
            .overlay(alignment: leading ? .leading : .center) { content() }
            .overlay(Rectangle().stroke(Golf.paperLine, lineWidth: 0.5))
    }

    static func label(_ t: String, _ c: Color, _ size: CGFloat = 11) -> some View {
        Text(t).font(.system(size: size, weight: .bold, design: .serif)).tracking(0.5).lineLimit(1)
            .foregroundStyle(c).padding(.horizontal, 5)
    }

    static func num(_ t: String, bold: Bool = false, color: Color = Golf.paperInk) -> some View {
        Text(t).font(.system(size: 12, weight: bold ? .heavy : .semibold, design: .monospaced))
            .lineLimit(1).minimumScaleFactor(0.7).foregroundStyle(color)
    }

    static func name(_ p: PlayerCard, me: Bool) -> some View {
        cell(labelW, h: playerH, bg: me ? Golf.gold.opacity(0.28) : .clear, leading: true) {
            VStack(alignment: .leading, spacing: 1) {
                Text(p.name).font(.system(size: 11, weight: .bold, design: .serif)).lineLimit(1)
                    .foregroundStyle(Golf.paperInk)
                Text("hcp \(p.handicap)").font(.system(size: 8)).foregroundStyle(Golf.paperMuted)
            }
            .padding(.horizontal, 5)
        }
    }

    static func sum(_ hs: [HoleResult]) -> String {
        let g = hs.compactMap(\.gross)
        return g.isEmpty ? "" : "\(g.reduce(0, +))"
    }
}

private struct ScorecardHalf: View {
    let label: String
    let range: ClosedRange<Int>
    let cards: [PlayerCard]
    let meId: String?
    let today: String
    var onTap: ((HoleResult) -> Void)?

    private var holes: [HoleResult] { (cards.first?.holes ?? []).filter { range.contains($0.hole) } }

    private func dow(_ d: String) -> String {
        guard let date = Fmt.isoDay.date(from: d) else { return "" }
        return String(date.formatted(.dateTime.weekday(.narrow)))
    }

    var body: some View {
        VStack(spacing: 0) {
            // HOLE
            HStack(spacing: 0) {
                Card.cell(Card.labelW, bg: Golf.green, leading: true) { Card.label("HOLE", .white) }
                ForEach(holes, id: \.hole) { h in
                    let now = h.date == today
                    Card.cell(nil, bg: now ? Golf.gold : Golf.green) {
                        Text("\(h.hole)").font(.system(size: 12, weight: .bold, design: .serif))
                            .foregroundStyle(now ? Golf.paperInk : .white)
                    }
                }
                Card.cell(Card.sumW, bg: Golf.greenDark) { Card.label(label, .white) }
            }
            // DAY
            HStack(spacing: 0) {
                Card.cell(Card.labelW, h: 20, leading: true) { Card.label("DAY", Golf.paperMuted, 9) }
                ForEach(holes, id: \.hole) { h in
                    Card.cell(nil, h: 20) { Text(dow(h.date)).font(.system(size: 9)).foregroundStyle(Golf.paperMuted) }
                }
                Card.cell(Card.sumW, h: 20, bg: Card.shade) { EmptyView() }
            }
            // PAR
            let parBg = Golf.green.opacity(0.10)
            HStack(spacing: 0) {
                Card.cell(Card.labelW, bg: parBg, leading: true) { Card.label("PAR", Golf.paperInk) }
                ForEach(holes, id: \.hole) { h in Card.cell(nil, bg: parBg) { Card.num("\(h.par)") } }
                Card.cell(Card.sumW, bg: parBg) { Card.num("\(holes.reduce(0) { $0 + $1.par })", bold: true) }
            }
            // Players
            ForEach(cards) { p in
                let ph = p.holes.filter { range.contains($0.hole) }
                let me = p.playerId == meId
                HStack(spacing: 0) {
                    Card.name(p, me: me)
                    ForEach(ph, id: \.hole) { h in
                        let canTap = me && onTap != nil && h.date <= today
                        Card.cell(nil, h: Card.playerH, bg: h.date == today ? Golf.gold.opacity(0.22) : .clear) {
                            if !me && h.gross == nil && h.date < today {
                                // A different player has no score for a finished hole.
                                Text("–").font(.system(size: 12, weight: .semibold)).foregroundStyle(Golf.pencil)
                            } else {
                                ScoreMark(hole: h)
                            }
                        }
                            .contentShape(Rectangle())
                            .onTapGesture { if canTap { onTap?(h) } }
                            .accessibilityLabel("Hole \(h.hole): \(h.gross.map(String.init) ?? "no score")")
                    }
                    Card.cell(Card.sumW, h: Card.playerH, bg: Card.shade) { Card.num(Card.sum(ph), bold: true) }
                }
            }
        }
        .overlay(Rectangle().stroke(Golf.paperInk, lineWidth: 2))
        .padding(.horizontal, 10)
    }
}

/// Out, In, Tot and the total to par for each player.
private struct ScorecardTotals: View {
    let cards: [PlayerCard]
    let meId: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Card.cell(Card.labelW, bg: Golf.green, leading: true) { EmptyView() }
                ForEach(["OUT", "IN", "TOT", "±"], id: \.self) { t in
                    Card.cell(nil, bg: Golf.greenDark) { Card.label(t, .white) }
                }
            }
            ForEach(cards) { p in
                HStack(spacing: 0) {
                    Card.name(p, me: p.playerId == meId)
                    Card.cell(nil, h: Card.playerH) { Card.num(Card.sum(p.holes.filter { $0.hole <= 9 }), bold: true) }
                    Card.cell(nil, h: Card.playerH) { Card.num(Card.sum(p.holes.filter { $0.hole > 9 }), bold: true) }
                    Card.cell(nil, h: Card.playerH, bg: Card.shade) { Card.num(Card.sum(p.holes), bold: true) }
                    Card.cell(nil, h: Card.playerH, bg: Card.shade) {
                        if p.thru > 0 {
                            Card.num(Fmt.toPar(p.totalToPar), bold: true, color: Golf.toParColor(p.totalToPar, base: Golf.paperInk))
                        }
                    }
                }
            }
        }
        .overlay(Rectangle().stroke(Golf.paperInk, lineWidth: 2))
        .padding(.horizontal, 10)
    }
}

/// The count, circled when under the target, boxed when over. Doubled at 10 or more.
private struct ScoreMark: View {
    let hole: HoleResult
    var body: some View {
        if let g = hole.gross {
            let t = hole.toPar ?? 0
            // Pencil: the count is from Screen Time and the player did not sign for it.
            let pencil = hole.pencil == true
            if t < 0 {
                ScoreMarkView(text: "\(g)", color: pencil ? Golf.pencil : Golf.red, circle: true, double: t <= -10)
            } else if t > 0 {
                ScoreMarkView(text: "\(g)", color: pencil ? Golf.pencil : Golf.paperInk, circle: false, double: t >= 10)
            } else {
                ScoreMarkView(text: "\(g)", color: pencil ? Golf.pencil : Golf.paperInk, circle: nil, double: false)
            }
        }
    }
}

struct ScoreMarkView: View {
    let text: String
    let color: Color
    /// true = circle, false = square, nil = no mark (even).
    let circle: Bool?
    let double: Bool

    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .semibold, design: .monospaced))
            .lineLimit(1)
            .fixedSize()
            .foregroundStyle(color)
            .frame(minWidth: 17, minHeight: 17)
            .padding(.horizontal, 1)
            .overlay(outline)
            .padding(double ? 2 : 0)
            .overlay(double ? AnyView(outline) : AnyView(EmptyView()))
    }

    @ViewBuilder private var outline: some View {
        switch circle {
        case .some(true): Capsule().stroke(color, lineWidth: 1)
        case .some(false): Rectangle().stroke(color, lineWidth: 1)
        case .none: EmptyView()
        }
    }
}

struct ToParText: View {
    let value: Int?
    var body: some View {
        Text(Fmt.toPar(value))
            .bold()
            .monospacedDigit()
            .foregroundStyle(Golf.toParColor(value, base: .primary))
    }
}

// MARK: - Scoreboard strip

/// Three plaques on the green board: the hole, the target for today, and the round to par.
struct ScoreStrip: View {
    let course: LeaderboardCourse
    let card: PlayerCard?
    let today: String

    var body: some View {
        let hole = card?.holes.first { $0.date == today }
        let total: Int? = (card?.thru ?? 0) > 0 ? card?.totalToPar : nil
        HStack(spacing: 8) {
            switch course.status {
            case "active": plaque("HOLE", "\(course.currentHole ?? 0)", "of 18")
            case "upcoming": plaque("HOLE", "–", "starts \(Fmt.pretty(course.startDate))")
            default: plaque("HOLE", "F", "final")
            }
            if let h = hole, let g = h.gross {
                plaque("TODAY", "\(g)", "target \(h.handicap + h.par)", color: Golf.toParColor(h.toPar, base: Golf.plaqueInk))
            } else if let h = hole {
                plaque("TARGET", "\(h.handicap + h.par)", "pickups today")
            } else {
                plaque("HANDICAP", card.map { "\($0.handicap)" } ?? "–", "pickups a day")
            }
            plaque("ROUND", Fmt.toPar(total), course.status == "finished" ? "final" : "thru \(card?.thru ?? 0)",
                   color: Golf.toParColor(total, base: Golf.plaqueInk))
        }
        .padding(10)
        .background(Golf.board, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Golf.boardLine, lineWidth: 3))
    }

    private func plaque(_ label: String, _ value: String, _ sub: String, color: Color = Golf.plaqueInk) -> some View {
        VStack(spacing: 5) {
            Text(label).font(.system(size: 10, weight: .semibold)).tracking(1.5).foregroundStyle(Golf.boardText)
            VStack(spacing: 0) {
                Text(value).font(.system(size: 30, weight: .bold, design: .monospaced)).foregroundStyle(color)
                    .lineLimit(1).minimumScaleFactor(0.6)
                Text(sub).font(.system(size: 10)).foregroundStyle(Golf.paperMuted).lineLimit(1).minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 66)
            .background(Golf.plaque, in: RoundedRectangle(cornerRadius: 4))
        }
        .accessibilityElement(children: .combine)
    }
}

