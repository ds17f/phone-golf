import SwiftUI

/// The first screen. One tap starts a solo round. Group play is optional.
struct WelcomeView: View {
    @EnvironmentObject var model: AppModel
    @State private var handicap = "80"
    @State private var busy = false
    @State private var error: String?
    @State private var showGroup = false
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 20) {
            Spacer(minLength: 0)
            VStack(spacing: 10) {
                Image(systemName: "flag.fill").font(.system(size: 44)).foregroundStyle(Golf.gold)
                Text("Phone Golf")
                    .font(.system(size: 40, weight: .bold, design: .serif))
                    .foregroundStyle(.white)
                Text("Each day is a hole. Your score is how many times you pick up your phone. Low score wins.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Golf.boardText)
            }
            Spacer(minLength: 0)

            VStack(spacing: 4) {
                Text("HANDICAP")
                    .font(.system(size: 11, weight: .semibold)).tracking(1.5)
                    .foregroundStyle(Golf.boardText)
                TextField("80", text: $handicap)
                    .keyboardType(.numberPad)
                    .focused($focused)
                    .multilineTextAlignment(.center)
                    .font(.system(size: 40, weight: .bold, design: .monospaced))
                    .foregroundStyle(Golf.plaqueInk)
                    .frame(width: 150)
                    .padding(.vertical, 6)
                    .background(Golf.plaque, in: RoundedRectangle(cornerRadius: 6))
                    .onChange(of: handicap) { v in handicap = String(v.filter(\.isNumber).prefix(4)) }
                Text("Your normal day, in pickups. Not sure? Keep 80. You can change it later.")
                    .font(.footnote)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Golf.boardText)
                    .padding(.top, 4)
            }

            if let error { Text(error).font(.footnote).foregroundStyle(Golf.gold) }

            Button {
                Task { await teeOff() }
            } label: {
                if busy { ProgressView().tint(Golf.plaqueInk) } else { Text("Tee off") }
            }
            .buttonStyle(GolfButtonStyle(gold: true))
            .disabled(busy || Int(handicap) == nil)

            Button("Play with a group") { showGroup = true }
                .font(.subheadline)
                .foregroundStyle(.white)
                .padding(.bottom, 8)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Golf.board.ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture { focused = false }
        .sheet(isPresented: $showGroup) { GroupPlaySheet().environmentObject(model) }
    }

    private func teeOff() async {
        guard let h = Int(handicap) else { return }
        focused = false
        busy = true
        defer { busy = false }
        // Ask one time, at the start. The player can say no and type the number from Settings.
        if model.screenTimeStatus != .approved { await model.requestScreenTime() }
        do {
            try await model.startSolo(handicap: h)
        } catch {
            self.error = error.localizedDescription
        }
    }
}

/// The main button: club green on paper, gold on the green board.
struct GolfButtonStyle: ButtonStyle {
    var gold = false
    @Environment(\.isEnabled) private var enabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 18, weight: .bold, design: .serif))
            .foregroundStyle(gold ? Golf.plaqueInk : .white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(gold ? Golf.gold : Golf.green, in: RoundedRectangle(cornerRadius: 10))
            .opacity(enabled ? (configuration.isPressed ? 0.8 : 1) : 0.45)
    }
}
