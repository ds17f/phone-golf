import { beforeEach, describe, expect, it } from "vitest";
import type { FastifyInstance } from "fastify";
import { buildApp } from "../src/app.js";

let app: FastifyInstance;
const TODAY = "2026-11-14";

beforeEach(() => {
  app = buildApp({ dbPath: ":memory:" }).app;
});

async function call(method: string, url: string, token?: string, body?: unknown, today = TODAY) {
  const res = await app.inject({
    method: method as "GET",
    url,
    headers: {
      ...(token ? { authorization: `Bearer ${token}` } : {}),
      "x-local-date": today,
    },
    ...(body !== undefined ? { payload: body as object } : {}),
  });
  return { status: res.statusCode, body: res.body ? res.json() : null };
}

async function newPlayer(name: string, platform = "android") {
  const r = await call("POST", "/api/players", undefined, { name, platform });
  expect(r.status).toBe(201);
  return r.body.token as string;
}

describe("api", () => {
  it("rejects requests without a key", async () => {
    expect((await call("GET", "/api/me")).status).toBe(401);
    expect((await call("GET", "/api/me", "nope")).status).toBe(401);
  });

  it("plays a full round", async () => {
    const alice = await newPlayer("Alice", "android");
    const bob = await newPlayer("Bob", "ios");

    const g = await call("POST", "/api/groups", alice, { name: "Family", handicap: 40 });
    expect(g.status).toBe(201);
    const { id: groupId, inviteCode } = g.body.group;
    expect(inviteCode).toMatch(/^[A-Z2-9]{6}$/);

    const j = await call("POST", "/api/groups/join", bob, { code: inviteCode.toLowerCase(), handicap: 20 });
    expect(j.status).toBe(200);
    expect(j.body.group.members.map((m: { name: string }) => m.name)).toEqual(["Alice", "Bob"]);

    const c = await call("POST", `/api/groups/${groupId}/courses`, alice, {
      name: "November Open",
      startDate: "2026-11-13", // Friday
      weekdayPar: 10,
      weekendPar: 5,
    });
    expect(c.status).toBe(201);
    expect(c.body.course).toMatchObject({ status: "active", currentHole: 2, endDate: "2026-11-30" });
    const courseId = c.body.course.id;

    await call("PUT", "/api/scores/2026-11-13", alice, { count: 52, metric: "unlocks", source: "auto" });
    await call("PUT", "/api/scores/2026-11-14", alice, { count: 41, metric: "unlocks", source: "auto" });
    await call("PUT", "/api/scores/2026-11-13", bob, { count: 25, metric: "pickups", source: "manual" });

    const lb = await call("GET", `/api/courses/${courseId}/leaderboard`, bob);
    expect(lb.status).toBe(200);
    const [first, second] = lb.body.leaderboard;
    // Alice: (52-40-10) + (41-40-5) = 2 + -4 = -2 ; Bob: 25-20-10 = -5
    expect(first).toMatchObject({ name: "Bob", thru: 1, totalToPar: -5 });
    expect(second).toMatchObject({ name: "Alice", thru: 2, totalToPar: -2 });
    expect(lb.body.holes[1]).toMatchObject({ hole: 2, date: "2026-11-14", par: 5 });
  });

  it("keeps a manual score over a later auto sync", async () => {
    const t = await newPlayer("A");
    await call("PUT", "/api/scores/2026-11-13", t, { count: 30, metric: "unlocks", source: "manual" });
    const r = await call("PUT", "/api/scores/2026-11-13", t, { count: 45, metric: "unlocks", source: "auto" });
    expect(r.body.saved).toBe(false);
    const s = await call("GET", "/api/scores", t);
    expect(s.body.scores[0]).toMatchObject({ date: "2026-11-13", count: 30 });
  });

  it("rejects future dates", async () => {
    const t = await newPlayer("A");
    const r = await call("PUT", "/api/scores/2026-11-20", t, { count: 1, metric: "unlocks", source: "manual" });
    expect(r.status).toBe(400);
  });

  it("suggests a handicap and applies changes from tomorrow", async () => {
    const t = await newPlayer("A");
    const g = await call("POST", "/api/groups", t, { name: "G", handicap: 50 });
    const gid = g.body.group.id;

    let h = await call("GET", `/api/groups/${gid}/handicap`, t);
    expect(h.body.suggestion).toBeNull();

    for (const [d, n] of [["2026-11-10", 30], ["2026-11-11", 40], ["2026-11-12", 20], ["2026-11-13", 60]] as const) {
      await call("PUT", `/api/scores/${d}`, t, { count: n, metric: "unlocks", source: "auto" });
    }
    h = await call("GET", `/api/groups/${gid}/handicap`, t);
    expect(h.body.suggestion).toMatchObject({ value: 25, daysUsed: 2, daysAvailable: 4 });

    const p = await call("PUT", `/api/groups/${gid}/handicap`, t, { handicap: 25, source: "suggestion" });
    expect(p.body.effectiveDate).toBe("2026-11-15");

    const c = await call("POST", `/api/groups/${gid}/courses`, t, {
      name: "C", startDate: "2026-11-14", weekdayPar: 0, weekendPar: 0,
    });
    await call("PUT", "/api/scores/2026-11-14", t, { count: 50, metric: "unlocks", source: "auto" });
    await call("PUT", "/api/scores/2026-11-15", t, { count: 25, metric: "unlocks", source: "auto" }, "2026-11-15");
    const lb = await call("GET", `/api/courses/${c.body.course.id}/leaderboard`, t);
    const holes = lb.body.leaderboard[0].holes;
    expect(holes[0]).toMatchObject({ handicap: 50, toPar: 0 });
    expect(holes[1]).toMatchObject({ handicap: 25, toPar: 0 });
  });

  it("hides groups from non-members and limits par edits to the creator", async () => {
    const a = await newPlayer("A");
    const b = await newPlayer("B");
    const g = await call("POST", "/api/groups", a, { name: "G", handicap: 10 });
    expect((await call("GET", `/api/groups/${g.body.group.id}`, b)).status).toBe(404);

    await call("POST", "/api/groups/join", b, { code: g.body.group.inviteCode, handicap: 10 });
    const c = await call("POST", `/api/groups/${g.body.group.id}/courses`, a, {
      name: "C", startDate: "2026-11-14", weekdayPar: 0, weekendPar: 0,
    });
    const cid = c.body.course.id;
    expect((await call("PATCH", `/api/courses/${cid}/holes/3`, b, { par: 7 })).status).toBe(403);
    expect((await call("PATCH", `/api/courses/${cid}/holes/3`, a, { par: 7 })).status).toBe(200);
    expect((await call("PATCH", `/api/courses/${cid}/holes/19`, a, { par: 7 })).status).toBe(404);
  });
});
