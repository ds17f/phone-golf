import SwiftUI

/// The change from solo play to group play: a player on the server, and a group.
struct GroupPlaySheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var joining = true
    @State private var text = ""
    @State private var handicap = ""
    @State private var server = ""
    @State private var key = ""
    /// The key of the player that this sheet made. A second try uses the same player.
    @State private var newToken: String?
    @State private var busy = false
    @State private var error: String?

    private var trimmedKey: String { key.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var ready: Bool {
        if !trimmedKey.isEmpty { return true }
        return !name.trimmingCharacters(in: .whitespaces).isEmpty
            && !text.trimmingCharacters(in: .whitespaces).isEmpty
            && Int(handicap) != nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Your name", text: $name).textContentType(.nickname)
                    Picker("Group", selection: $joining) {
                        Text("Join with a code").tag(true)
                        Text("Make a group").tag(false)
                    }
                    .pickerStyle(.segmented)
                    TextField(joining ? "Invite code" : "Group name", text: $text)
                        .textInputAutocapitalization(joining ? .characters : .words)
                        .autocorrectionDisabled()
                    TextField("Your handicap", text: $handicap).keyboardType(.numberPad)
                } footer: {
                    Text("Your handicap is the number of pickups on a normal day. Your solo card stays on this phone.")
                }

                if let error { Section { Text(error).foregroundStyle(.red) } }

                Section {
                    TextField("Player key", text: $key)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: {
                    Text("Do you play on a different device?")
                } footer: {
                    Text("On that device, open Settings and copy your player key. Paste it here. Then you do not need a name or a code.")
                }

                Section("Server") {
                    TextField("https://…", text: $server)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                }
            }
            .navigationTitle("Play with a group")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Play") { Task { await submit() } }.disabled(busy || !ready)
                }
            }
            .task { await prefill() }
        }
    }

    private func prefill() async {
        server = model.serverURL
        guard model.hasSolo else { return }
        if let me = try? await LocalBackend.shared.me(), me.name != "Me" { name = me.name }
        if let h = try? await LocalBackend.shared.handicap(groupId: LocalBackend.groupId) { handicap = "\(h.current)" }
    }

    private func submit() async {
        let address = server.trimmingCharacters(in: .whitespaces)
        guard let base = URL(string: address), base.scheme != nil else {
            error = "The server address is not correct."
            return
        }
        busy = true
        defer { busy = false }
        do {
            if !trimmedKey.isEmpty {
                do {
                    _ = try await Api(baseURL: base, token: trimmedKey).me()
                } catch let e as ApiError where e.status == 401 {
                    error = "That key does not match a player."
                    return
                }
                model.playWithGroup(token: trimmedKey, serverURL: address)
                dismiss()
                return
            }
            guard let h = Int(handicap) else { return }
            var token = newToken
            if token == nil {
                let (_, t) = try await Api(baseURL: base, token: nil).createPlayer(name: name.trimmingCharacters(in: .whitespaces))
                token = t
                newToken = t
            }
            guard let token else { return }
            let api = Api(baseURL: base, token: token)
            let t = text.trimmingCharacters(in: .whitespaces)
            if joining {
                _ = try await api.joinGroup(code: t, handicap: h)
            } else {
                _ = try await api.createGroup(name: t, handicap: h)
            }
            model.playWithGroup(token: token, serverURL: address)
            dismiss()
        } catch let e as ApiError {
            error = e.message
        } catch {
            self.error = "Could not reach the server: \(error.localizedDescription)"
        }
    }
}

/// Join or make one more group. The player is in group mode already.
struct GroupSheet: View {
    enum Mode: String, Identifiable { case join, create; var id: String { rawValue } }

    let mode: Mode
    var onDone: () -> Void
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var handicap = ""
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                TextField(mode == .join ? "Invite code" : "Group name", text: $text)
                    .textInputAutocapitalization(mode == .join ? .characters : .words)
                    .autocorrectionDisabled()
                TextField("Your handicap", text: $handicap).keyboardType(.numberPad)
                Text("Your handicap is the number of pickups on a normal day. The app can suggest a better number later.")
                    .font(.footnote).foregroundStyle(.secondary)
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle(mode == .join ? "Join a group" : "Make a group")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(mode == .join ? "Join" : "Make") { Task { await submit() } }
                        .disabled(text.isEmpty || Int(handicap) == nil)
                }
            }
            .task {
                // The solo handicap is a good first value.
                if handicap.isEmpty, model.hasSolo,
                   let h = try? await LocalBackend.shared.handicap(groupId: LocalBackend.groupId) {
                    handicap = "\(h.current)"
                }
            }
        }
    }

    private func submit() async {
        guard let h = Int(handicap) else { return }
        do {
            let t = text.trimmingCharacters(in: .whitespaces)
            if mode == .join {
                _ = try await model.backend.joinGroup(code: t, handicap: h)
            } else {
                _ = try await model.backend.createGroup(name: t, handicap: h)
            }
            onDone()
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
