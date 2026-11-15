import FamilyControls
import SwiftUI

/// All that is not the scorecard: handicap, name, reminder, group play, past rounds.
struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @State private var name = ""
    @State private var server = ""
    @State private var message: String?
    @State private var groups: [GolfGroup] = []
    @State private var groupId: String?
    @State private var hcp: HandicapInfo?
    @State private var showHandicap = false
    @State private var handicapText = ""
    @State private var showGroupPlay = false
    @State private var groupSheet: GroupSheet.Mode?
    @State private var showWhy = false
    @State private var confirmOut = false

    private var finished: [Course] {
        groups.flatMap(\.courses).filter { $0.status == "finished" }.sorted { $0.startDate > $1.startDate }
    }

    var body: some View {
        Form {
            if let h = hcp {
                Section {
                    if let s = h.suggestion {
                        Text("Suggested: **\(s.value)**. \(s.explanation)")
                        if s.value != h.current {
                            Button("Use \(s.value)") { Task { await setHandicap(s.value, source: "suggestion") } }
                        }
                    } else {
                        Text("Score \(h.suggestionNeedsDays) more day(s) to get a suggested handicap.")
                            .foregroundStyle(.secondary)
                    }
                    Button("Set a different number") {
                        handicapText = "\(h.current)"
                        showHandicap = true
                    }
                } header: {
                    Text("Your handicap: \(h.current)")
                } footer: {
                    Text("Your handicap is the number of pickups on a normal day. A change starts tomorrow.")
                }
            }

            Section("Name") {
                TextField("Name", text: $name)
                Button("Save name") {
                    Task {
                        do {
                            _ = try await model.backend.rename(name.trimmingCharacters(in: .whitespaces))
                            message = "Saved."
                        } catch { message = error.localizedDescription }
                    }
                }
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
            }

            Section {
                Toggle("Daily reminder", isOn: $model.reminderOn)
                if model.reminderOn {
                    Picker("Time", selection: $model.reminderHour) {
                        ForEach(5..<13) { h in Text("\(h):00").tag(h) }
                    }
                }
            } header: {
                Text("Reminder")
            } footer: {
                Text("A notification each morning to mark your card for yesterday.")
            }

            Section("Screen Time") {
                LabeledContent("Access", value: model.screenTimeStatus == .approved ? "Allowed" : "Not allowed")
                if model.screenTimeStatus != .approved {
                    Button("Allow Screen Time") { Task { await model.requestScreenTime() } }
                }
                Button("Why do I type my score?") { showWhy = true }
            }

            groupSection

            if !finished.isEmpty {
                Section("Past rounds") {
                    ForEach(finished) { c in
                        NavigationLink(value: Route.course(c.id)) {
                            VStack(alignment: .leading) {
                                Text(c.name)
                                Text("\(Fmt.pretty(c.startDate)) – \(Fmt.pretty(c.endDate))")
                                    .font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }

            if let message { Section { Text(message) } }

            Section {
                Button(model.isLocal ? "Start over" : "Sign out", role: .destructive) { confirmOut = true }
            }
        }
        .navigationTitle("Settings")
        .task { await load() }
        .sheet(isPresented: $showGroupPlay) { GroupPlaySheet().environmentObject(model) }
        .sheet(item: $groupSheet) { mode in GroupSheet(mode: mode) { Task { await load() } } }
        .sheet(isPresented: $showWhy) { WhyTypeSheet() }
        .alert("Set handicap", isPresented: $showHandicap) {
            TextField("Handicap", text: $handicapText).keyboardType(.numberPad)
            Button("Save") { if let v = Int(handicapText) { Task { await setHandicap(v, source: "manual") } } }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog(model.isLocal ? "Start over?" : "Sign out?", isPresented: $confirmOut, titleVisibility: .visible) {
            if model.isLocal {
                Button("Delete and start over", role: .destructive) { model.startOver() }
            } else {
                Button("Sign out", role: .destructive) { model.signOut() }
            }
        } message: {
            Text(model.isLocal
                 ? "This deletes your scores, handicap and rounds from this phone."
                 : "Copy your player key first. Without it, you cannot get this player back.")
        }
    }

    @ViewBuilder private var groupSection: some View {
        if model.isLocal {
            Section {
                if let token = model.token {
                    Button("Back to group play") { model.playWithGroup(token: token) }
                } else {
                    Button("Play with a group") { showGroupPlay = true }
                }
            } header: {
                Text("Group play")
            } footer: {
                Text("You play solo. All your scores stay on this phone. A group needs a Phone Golf server. Your solo card stays on this phone.")
            }
        } else {
            Section {
                ForEach(groups) { g in
                    NavigationLink(value: Route.group(g.id)) {
                        LabeledContent("Group", value: g.name)
                    }
                }
                Button("Join a group") { groupSheet = .join }
                Button("Make a group") { groupSheet = .create }
                Button("Play solo again") { Task { await playSolo() } }
            } header: {
                Text("Group play")
            }
            Section {
                Button("Copy player key") {
                    UIPasteboard.general.string = model.token
                    message = "Player key copied."
                }
            } footer: {
                Text("This key is your login. Paste it on another device or on the website to play as the same person. Keep it private.")
            }
            Section("Server") {
                TextField("https://…", text: $server)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                Button("Save server") { model.serverURL = server.trimmingCharacters(in: .whitespaces); message = "Server saved." }
            }
        }
    }

    private func load() async {
        server = model.serverURL
        let backend = model.backend
        if let me = try? await backend.me() { name = me.name }
        groups = (try? await backend.groups()) ?? []
        // The handicap of the round on the home screen.
        groupId = (Featured.pick(groups)?.group ?? groups.first)?.id
        if let groupId { hcp = try? await backend.handicap(groupId: groupId) } else { hcp = nil }
    }

    private func setHandicap(_ value: Int, source: String) async {
        guard let groupId else { return }
        do {
            try await model.backend.setHandicap(groupId: groupId, value: value, source: source)
            await load()
        } catch {
            message = error.localizedDescription
        }
    }

    private func playSolo() async {
        if model.hasSolo {
            model.playSolo()
            return
        }
        do {
            let n = name.trimmingCharacters(in: .whitespaces)
            try await model.startSolo(name: n.isEmpty ? "Me" : n, handicap: hcp?.current ?? 80)
        } catch {
            message = error.localizedDescription
        }
    }
}
