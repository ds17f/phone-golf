import Foundation

struct ApiError: LocalizedError {
    let status: Int
    let message: String
    var errorDescription: String? { message }
}

/// Minimal client for the Phone Golf API.
struct Api {
    let baseURL: URL
    let token: String?

    private struct ErrorBody: Decodable { let error: String }
    private struct Empty: Decodable {}

    private func send<T: Decodable>(_ method: String, _ path: String, body: [String: Any]? = nil) async throws -> T {
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.httpMethod = method
        req.timeoutInterval = 15
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue(Fmt.day(Date()), forHTTPHeaderField: "X-Local-Date")
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let (data, response) = try await URLSession.shared.data(for: req)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let msg = (try? JSONDecoder().decode(ErrorBody.self, from: data))?.error ?? "Request failed (\(status))"
            throw ApiError(status: status, message: msg)
        }
        if T.self == Empty.self || data.isEmpty { return try JSONDecoder().decode(T.self, from: Data("{}".utf8)) }
        return try JSONDecoder().decode(T.self, from: data)
    }

    // MARK: players

    private struct PlayerResponse: Decodable { let player: Player; let token: String? }

    func createPlayer(name: String) async throws -> (Player, String) {
        let r: PlayerResponse = try await send("POST", "api/players", body: ["name": name, "platform": "ios"])
        return (r.player, r.token ?? "")
    }

    func me() async throws -> Player {
        let r: PlayerResponse = try await send("GET", "api/me")
        return r.player
    }

    func rename(_ name: String) async throws -> Player {
        let r: PlayerResponse = try await send("PUT", "api/me", body: ["name": name])
        return r.player
    }

    // MARK: scores

    func putScore(date: String, count: Int) async throws {
        let _: Empty = try await send("PUT", "api/scores/\(date)", body: ["count": count, "metric": "pickups", "source": "manual"])
    }

    private struct ScoresResponse: Decodable { let scores: [DayScore] }
    func scores() async throws -> [DayScore] {
        let r: ScoresResponse = try await send("GET", "api/scores")
        return r.scores
    }

    // MARK: groups

    private struct GroupsResponse: Decodable { let groups: [GolfGroup] }
    private struct GroupResponse: Decodable { let group: GolfGroup }

    func groups() async throws -> [GolfGroup] {
        let r: GroupsResponse = try await send("GET", "api/groups")
        return r.groups
    }

    func group(_ id: String) async throws -> GolfGroup {
        let r: GroupResponse = try await send("GET", "api/groups/\(id)")
        return r.group
    }

    func createGroup(name: String, handicap: Int) async throws -> GolfGroup {
        let r: GroupResponse = try await send("POST", "api/groups", body: ["name": name, "handicap": handicap])
        return r.group
    }

    func joinGroup(code: String, handicap: Int) async throws -> GolfGroup {
        let r: GroupResponse = try await send("POST", "api/groups/join", body: ["code": code, "handicap": handicap])
        return r.group
    }

    func leaveGroup(_ id: String) async throws {
        let _: Empty = try await send("DELETE", "api/groups/\(id)/membership")
    }

    func handicap(groupId: String) async throws -> HandicapInfo {
        try await send("GET", "api/groups/\(groupId)/handicap")
    }

    func setHandicap(groupId: String, value: Int, source: String) async throws {
        let _: Empty = try await send("PUT", "api/groups/\(groupId)/handicap", body: ["handicap": value, "source": source])
    }

    // MARK: courses

    private struct CourseResponse: Decodable { let course: Course }

    func createCourse(groupId: String, name: String, startDate: String, weekdayPar: Int, weekendPar: Int) async throws -> Course {
        let r: CourseResponse = try await send(
            "POST", "api/groups/\(groupId)/courses",
            body: ["name": name, "startDate": startDate, "weekdayPar": weekdayPar, "weekendPar": weekendPar]
        )
        return r.course
    }

    func leaderboard(courseId: String) async throws -> Leaderboard {
        try await send("GET", "api/courses/\(courseId)/leaderboard")
    }
}
