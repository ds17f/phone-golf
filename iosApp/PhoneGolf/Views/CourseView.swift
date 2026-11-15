import SwiftUI

struct CourseView: View {
    let courseId: String
    @EnvironmentObject var model: AppModel
    @State private var board: Leaderboard?
    @State private var meId: String?
    @State private var error: String?

    private let today = Fmt.day(Date())

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let error { Text(error).foregroundStyle(.red) }
                if let b = board {
                    ScoreStrip(course: b.course, card: b.leaderboard.first { $0.playerId == meId }, today: today)
                    PaperScorecard(board: b, meId: meId, today: today)
                    if b.leaderboard.count > 1 {
                        TournamentBoard(board: b, meId: meId, today: today)
                    }
                } else if error == nil {
                    ProgressView().padding(40)
                }
            }
            .padding()
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle(board?.course.name ?? "Course")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        do {
            board = try await model.backend.leaderboard(courseId: courseId)
            meId = try? await model.backend.me().id
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}
