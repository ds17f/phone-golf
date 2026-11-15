import DeviceActivity
import FamilyControls
import SwiftUI

/// The holes that the sheet opens with.
struct SignTarget: Identifiable {
    let dates: [String]
    var id: String { dates.joined(separator: ",") }
}

/// The player signs the card: a table with one row for each hole. Each row has
/// the Screen Time pickups of the day (iOS draws the number, the app cannot
/// read it) and a field. The player types each number. Then the app and the
/// group have the scores.
struct SignCardSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss

    /// The holes to sign for, in hole order.
    let holes: [HoleResult]
    var onSaved: () async -> Void

    @State private var text: [String: String] = [:]
    @State private var saving = false
    @State private var error: String?
    @State private var showWhy = false
    @FocusState private var focus: String?

    private let fieldW: CGFloat = 92
    private var today: String { Fmt.day(Date()) }
    private var approved: Bool { model.screenTimeStatus == .approved }
    /// The rows that have a number that is different from the signed score.
    private var ready: [(hole: HoleResult, count: Int)] {
        holes.compactMap { h in
            guard let n = Int(text[h.date] ?? ""), n != h.gross else { return nil }
            return (h, n)
        }
    }

    init(holes: [HoleResult], onSaved: @escaping () async -> Void) {
        self.holes = holes
        self.onSaved = onSaved
        // The extension draws the plaques in this order.
        SignList.save(holes.map(\.date))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    if !approved { allow }
                    table
                    if let error { Text(error).font(.footnote).foregroundStyle(.red) }
                    Button {
                        Task { await save() }
                    } label: {
                        if saving { ProgressView().tint(.white) }
                        else { Text(ready.count > 1 ? "Sign for \(ready.count) holes" : "Sign") }
                    }
                    .buttonStyle(GolfButtonStyle())
                    .disabled(saving || ready.isEmpty)
                    Button("Why do I type it?") { showWhy = true }.font(.footnote)
                }
                .padding()
            }
            .navigationTitle("Sign your card")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Next") { next() }
                }
            }
            .sheet(isPresented: $showWhy) { WhyTypeSheet() }
        }
        .onAppear {
            for h in holes { if let g = h.gross { text[h.date] = "\(g)" } }
            focus = holes.first { $0.gross == nil }?.date ?? holes.first?.date
        }
    }

    // MARK: Parts

    private var table: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                head("HOLE").frame(maxWidth: .infinity, alignment: .leading)
                if approved { head("SCREEN TIME").frame(width: fieldW) }
                head("YOUR CARD").frame(width: fieldW)
            }
            .padding(.vertical, 8).padding(.horizontal, 10)
            .background(Golf.green)

            HStack(alignment: .top, spacing: 8) {
                VStack(spacing: 0) {
                    ForEach(holes, id: \.date) { h in
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Hole \(h.hole)").font(.system(size: 15, weight: .bold, design: .serif))
                                .foregroundStyle(Golf.paperInk)
                            Text(h.date == today ? "today so far" : Fmt.pretty(h.date))
                                .font(.caption).foregroundStyle(Golf.paperMuted)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .frame(height: SignList.rowHeight)
                    }
                }
                if approved, let first = holes.first?.date, let last = holes.last?.date {
                    DeviceActivityReport(.signList, filter: PickupsFilter.days(from: first, to: last))
                        .frame(width: fieldW, height: SignList.rowHeight * CGFloat(holes.count))
                        .allowsHitTesting(false)
                }
                VStack(spacing: 0) {
                    ForEach(holes, id: \.date) { h in
                        TextField("–", text: field(h.date))
                            .keyboardType(.numberPad)
                            .focused($focus, equals: h.date)
                            .multilineTextAlignment(.center)
                            .font(.system(size: 24, weight: .bold, design: .monospaced))
                            .foregroundStyle(Golf.paperInk)
                            .tint(Golf.green)
                            .frame(width: fieldW, height: SignList.rowHeight - 14)
                            .overlay(Rectangle().stroke(Golf.paperInk, lineWidth: focus == h.date ? 2.5 : 1.5))
                            .frame(height: SignList.rowHeight)
                            .accessibilityLabel("Pickups for hole \(h.hole)")
                    }
                }
            }
            .padding(.horizontal, 10).padding(.vertical, 4)
        }
        .background(Golf.paper)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Golf.paperLine, lineWidth: 1))
    }

    private func head(_ t: String) -> some View {
        Text(t).font(.system(size: 10, weight: .bold, design: .serif)).tracking(1).foregroundStyle(.white)
    }

    private var allow: some View {
        VStack(spacing: 8) {
            Text("Allow Screen Time, and your pickups show in the table.")
                .font(.subheadline).foregroundStyle(.white)
            Button("Allow Screen Time") { Task { await model.requestScreenTime() } }
                .buttonStyle(GolfButtonStyle(gold: true))
            Text("Or find the numbers in Settings › Screen Time › See All App & Website Activity › Pickups.")
                .font(.caption2).foregroundStyle(Golf.boardText)
        }
        .multilineTextAlignment(.center)
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(Golf.board, in: RoundedRectangle(cornerRadius: 10))
    }

    // MARK: Actions

    private func field(_ date: String) -> Binding<String> {
        Binding(
            get: { text[date] ?? "" },
            set: { text[date] = String($0.filter(\.isNumber).prefix(5)) }
        )
    }

    /// Goes to the next row. After the last row, the keyboard closes.
    private func next() {
        guard let f = focus, let i = holes.firstIndex(where: { $0.date == f }) else { focus = nil; return }
        focus = i + 1 < holes.count ? holes[i + 1].date : nil
    }

    private func save() async {
        saving = true
        defer { saving = false }
        do {
            for (h, count) in ready {
                try await model.backend.putScore(date: h.date, count: count)
            }
        } catch {
            self.error = error.localizedDescription
            await onSaved()
            return
        }
        await onSaved()
        // The first signature is a good time to ask for the morning reminder.
        Task { await model.updateReminder() }
        dismiss()
    }
}

struct WhyTypeSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Apple keeps Screen Time data private. Apps can ask iOS to *show* your numbers, but iOS draws them in a sealed box. Phone Golf itself cannot read what is inside the box, and it cannot send it anywhere.")
                    Text("So your card shows the numbers, and you sign for them: you type each number. It takes a few seconds.")
                    Text("**Pickups, not unlocks.** iPhone does not count unlocks. The closest number is pickups: how many times you picked up your phone and started to use it. Your handicap is in pickups too, so the game stays fair inside your group.")
                    Text("**Android players** get their unlock count sent automatically, because Android lets apps read it.")
                    Text("**Honor system.** Like golf, you keep your own scorecard.")
                }
                .padding()
            }
            .navigationTitle("Why do I type it?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("OK") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}
