import SwiftUI

struct GroupView: View {
    let groupId: String
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var group: GolfGroup?
    @State private var error: String?
    @State private var showCourse = false
    @State private var confirmLeave = false

    var body: some View {
        List {
            if let error { Text(error).foregroundStyle(.red) }
            if let g = group {
                if !model.isLocal { Section("Invite code") {
                    Text(g.inviteCode).font(.system(.title, design: .monospaced).bold())
                    ShareLink(item: "Play Phone Golf with me. Fewest phone pickups wins. Join \"\(g.name)\" with code \(g.inviteCode)") {
                        Label("Share invite", systemImage: "square.and.arrow.up")
                    }
                } }

                Section("Courses") {
                    if g.courses.isEmpty {
                        Text("No courses yet. A course is 18 days in a row.").foregroundStyle(.secondary)
                    }
                    ForEach(g.courses) { c in
                        NavigationLink(value: Route.course(c.id)) {
                            VStack(alignment: .leading) {
                                HStack {
                                    Text(c.name).bold()
                                    Spacer()
                                    Text(c.status == "active" ? "hole \(c.currentHole ?? 0)" : c.status)
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                                Text("\(Fmt.pretty(c.startDate)) – \(Fmt.pretty(c.endDate))")
                                    .font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                    }
                    Button("New course") { showCourse = true }
                }

                if !model.isLocal { Section("Players") {
                    ForEach(g.members) { m in
                        HStack {
                            Text(m.name + (m.id == g.ownerId ? " (owner)" : ""))
                            Spacer()
                            Text("handicap \(m.handicap) · \(m.platform)").font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                } }

                if !model.isLocal {
                    Section {
                        Button("Leave group", role: .destructive) { confirmLeave = true }
                    }
                }
            } else if error == nil {
                ProgressView()
            }
        }
        .navigationTitle(group?.name ?? "Group")
        .task { await load() }
        .refreshable { await load() }
        .sheet(isPresented: $showCourse) {
            NewCourseSheet(groupId: groupId, groupName: group?.name ?? "") { Task { await load() } }
        }
        .confirmationDialog("Leave \(group?.name ?? "group")?", isPresented: $confirmLeave, titleVisibility: .visible) {
            Button("Leave", role: .destructive) {
                Task {
                    try? await model.backend.leaveGroup(groupId)
                    dismiss()
                }
            }
        } message: {
            Text("Your scores stay, but you leave the leaderboard.")
        }
    }

    private func load() async {
        do {
            let g = try await model.backend.group(groupId)
            group = g
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}

struct NewCourseSheet: View {
    let groupId: String
    let groupName: String
    var onDone: () -> Void
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var start = Date()
    @State private var weekdayPar = 0
    @State private var weekendPar = 0
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $name)
                DatePicker("First day (hole 1)", selection: $start, displayedComponents: .date)
                Stepper("Par on weekdays: \(weekdayPar)", value: $weekdayPar, in: -100...500)
                Stepper("Par on weekends: \(weekendPar)", value: $weekendPar, in: -100...500)
                Text("A course is 18 days in a row. Par is how many pickups above your handicap count as even. Par 0 means: stay at your baseline.")
                    .font(.footnote).foregroundStyle(.secondary)
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle("New course")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Make") { Task { await submit() } }.disabled(name.isEmpty)
                }
            }
            .onAppear { if name.isEmpty { name = "The \(groupName) Open" } }
        }
    }

    private func submit() async {
        do {
            _ = try await model.backend.createCourse(
                groupId: groupId, name: name, startDate: Fmt.day(start),
                weekdayPar: weekdayPar, weekendPar: weekendPar)
            onDone()
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
