import Fastify, { type FastifyInstance, type FastifyRequest } from "fastify";
import fastifyStatic from "@fastify/static";
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { openDb, type DB } from "./db.js";
import { addDays, isDate, isWeekend, utcToday } from "./dates.js";
import {
  buildCard,
  rankCards,
  suggestHandicap,
  SUGGESTION_WINDOW_DAYS,
  type HandicapRow,
  type HoleDef,
  type ScoreRow,
} from "./scoring.js";

export interface AppOptions {
  dbPath: string;
  webDir?: string;
  logger?: boolean;
}

interface Player {
  id: string;
  name: string;
  platform: string;
  created_at: string;
}

declare module "fastify" {
  interface FastifyRequest {
    player: Player;
  }
}

const HOLES_PER_COURSE = 18;
const INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
const MAX_COUNT = 10_000;

const hashToken = (t: string) => createHash("sha256").update(t).digest("hex");
const now = () => new Date().toISOString();

function inviteCode(): string {
  const bytes = randomBytes(6);
  return Array.from(bytes, (b) => INVITE_ALPHABET[b % INVITE_ALPHABET.length]).join("");
}

class HttpError extends Error {
  constructor(public statusCode: number, message: string) {
    super(message);
  }
}

/** The client's local calendar day, sent in X-Local-Date. Falls back to UTC. */
function localToday(req: FastifyRequest): string {
  const h = req.headers["x-local-date"];
  return isDate(h) ? h : utcToday();
}

const nameSchema = { type: "string", minLength: 1, maxLength: 40 } as const;
const handicapSchema = { type: "integer", minimum: 0, maximum: MAX_COUNT } as const;
const parSchema = { type: "integer", minimum: -MAX_COUNT, maximum: MAX_COUNT } as const;
const dateSchema = { type: "string", pattern: "^\\d{4}-\\d{2}-\\d{2}$" } as const;

export function buildApp(opts: AppOptions): { app: FastifyInstance; db: DB } {
  const db = openDb(opts.dbPath);
  const app = Fastify({ logger: opts.logger ?? false });

  const tx = <T>(fn: () => T): T => {
    db.exec("BEGIN");
    try {
      const r = fn();
      db.exec("COMMIT");
      return r;
    } catch (e) {
      db.exec("ROLLBACK");
      throw e;
    }
  };

  app.setErrorHandler((err: Error & { statusCode?: number; validation?: unknown }, _req, reply) => {
    if (err instanceof HttpError) return reply.code(err.statusCode).send({ error: err.message });
    if (err.validation) return reply.code(400).send({ error: err.message });
    app.log.error(err);
    return reply.code(err.statusCode ?? 500).send({ error: "Internal error" });
  });

  // ---- auth -------------------------------------------------------------

  const authenticate = async (req: FastifyRequest) => {
    const auth = req.headers.authorization ?? "";
    const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
    if (!token) throw new HttpError(401, "Missing player key");
    const p = db
      .prepare("SELECT id, name, platform, created_at FROM players WHERE token_hash = ?")
      .get(hashToken(token)) as Player | undefined;
    if (!p) throw new HttpError(401, "Unknown player key");
    req.player = p;
  };

  const requireMember = (groupId: string, playerId: string) => {
    const m = db
      .prepare("SELECT 1 FROM memberships WHERE group_id = ? AND player_id = ?")
      .get(groupId, playerId);
    if (!m) throw new HttpError(404, "Group not found");
  };

  const courseRow = (courseId: string) => {
    const c = db.prepare("SELECT * FROM courses WHERE id = ?").get(courseId) as
      | { id: string; group_id: string; name: string; start_date: string; created_by: string }
      | undefined;
    if (!c) throw new HttpError(404, "Course not found");
    return c;
  };

  // ---- routes -----------------------------------------------------------

  app.get("/api/health", async () => ({ ok: true }));

  app.post<{ Body: { name: string; platform: string } }>(
    "/api/players",
    {
      schema: {
        body: {
          type: "object",
          required: ["name", "platform"],
          properties: { name: nameSchema, platform: { enum: ["ios", "android", "web"] } },
        },
      },
    },
    async (req, reply) => {
      const token = randomBytes(24).toString("base64url");
      const id = randomUUID();
      const name = req.body.name.trim();
      if (!name) throw new HttpError(400, "Name is required");
      db.prepare(
        "INSERT INTO players (id, name, token_hash, platform, created_at) VALUES (?, ?, ?, ?, ?)",
      ).run(id, name, hashToken(token), req.body.platform, now());
      return reply.code(201).send({ player: { id, name, platform: req.body.platform }, token });
    },
  );

  app.register(async (authed) => {
    authed.addHook("preHandler", authenticate);

    authed.get("/api/me", async (req) => ({
      player: { id: req.player.id, name: req.player.name, platform: req.player.platform },
    }));

    // PUT and PATCH both rename. Android's HttpURLConnection cannot send PATCH.
    for (const method of ["PATCH", "PUT"] as const) {
      authed.route<{ Body: { name: string } }>({
        method,
        url: "/api/me",
        schema: { body: { type: "object", required: ["name"], properties: { name: nameSchema } } },
        handler: async (req) => {
          const name = req.body.name.trim();
          if (!name) throw new HttpError(400, "Name is required");
          db.prepare("UPDATE players SET name = ? WHERE id = ?").run(name, req.player.id);
          return { player: { id: req.player.id, name, platform: req.player.platform } };
        },
      });
    }

    // -- scores --

    authed.put<{
      Params: { date: string };
      Body: { count: number; metric: string; source: string };
    }>(
      "/api/scores/:date",
      {
        schema: {
          params: { type: "object", properties: { date: dateSchema } },
          body: {
            type: "object",
            required: ["count", "metric", "source"],
            properties: {
              count: { type: "integer", minimum: 0, maximum: MAX_COUNT },
              metric: { enum: ["unlocks", "pickups"] },
              source: { enum: ["auto", "manual"] },
            },
          },
        },
      },
      async (req) => {
        const { date } = req.params;
        if (!isDate(date)) throw new HttpError(400, "Bad date");
        // Allow one day of slack for time zones ahead of the client's own clock.
        if (date > addDays(localToday(req), 1)) throw new HttpError(400, "Date is in the future");
        const { count, metric, source } = req.body;
        const existing = db
          .prepare("SELECT source FROM scores WHERE player_id = ? AND date = ?")
          .get(req.player.id, date) as { source: string } | undefined;
        // A number the player typed in wins over a later automatic sync.
        if (existing?.source === "manual" && source === "auto") {
          return { saved: false, reason: "manual score kept" };
        }
        db.prepare(
          `INSERT INTO scores (player_id, date, count, metric, source, updated_at)
           VALUES (?, ?, ?, ?, ?, ?)
           ON CONFLICT (player_id, date) DO UPDATE SET
             count = excluded.count, metric = excluded.metric,
             source = excluded.source, updated_at = excluded.updated_at`,
        ).run(req.player.id, date, count, metric, source, now());
        return { saved: true };
      },
    );

    authed.get<{ Querystring: { from?: string; to?: string } }>("/api/scores", async (req) => {
      const to = isDate(req.query.to) ? req.query.to : localToday(req);
      const from = isDate(req.query.from) ? req.query.from : addDays(to, -30);
      const rows = db
        .prepare(
          "SELECT date, count, metric, source FROM scores WHERE player_id = ? AND date BETWEEN ? AND ? ORDER BY date DESC",
        )
        .all(req.player.id, from, to);
      return { scores: rows };
    });

    // -- groups --

    const groupSummary = (groupId: string, today: string) => {
      const g = db
        .prepare("SELECT id, name, invite_code, owner_id, created_at FROM groups WHERE id = ?")
        .get(groupId) as { id: string; name: string; invite_code: string; owner_id: string };
      const members = (
        db
          .prepare(
            `SELECT p.id, p.name, p.platform FROM memberships m JOIN players p ON p.id = m.player_id
             WHERE m.group_id = ? ORDER BY p.name`,
          )
          .all(groupId) as { id: string; name: string; platform: string }[]
      ).map((m) => ({ ...m, handicap: currentHandicap(groupId, m.id, today) }));
      const courses = (
        db
          .prepare("SELECT id, name, start_date FROM courses WHERE group_id = ? ORDER BY start_date DESC")
          .all(groupId) as { id: string; name: string; start_date: string }[]
      ).map((c) => courseInfo(c, today));
      return {
        id: g.id,
        name: g.name,
        inviteCode: g.invite_code,
        ownerId: g.owner_id,
        members,
        courses,
      };
    };

    const currentHandicap = (groupId: string, playerId: string, today: string) => {
      // The latest value, including one that starts tomorrow, so the player sees their change.
      const r = db
        .prepare(
          `SELECT value, effective_date FROM handicaps WHERE group_id = ? AND player_id = ?
           ORDER BY effective_date DESC LIMIT 1`,
        )
        .get(groupId, playerId) as { value: number; effective_date: string } | undefined;
      return r ? r.value : 0;
    };

    const courseInfo = (c: { id: string; name: string; start_date: string }, today: string) => {
      const endDate = addDays(c.start_date, HOLES_PER_COURSE - 1);
      const status = today < c.start_date ? "upcoming" : today > endDate ? "finished" : "active";
      const currentHole =
        status === "active"
          ? Math.round((Date.parse(today) - Date.parse(c.start_date)) / 86_400_000) + 1
          : null;
      return { id: c.id, name: c.name, startDate: c.start_date, endDate, status, currentHole };
    };

    const addMember = (groupId: string, playerId: string, handicap: number) => {
      db.prepare("INSERT INTO memberships (group_id, player_id, joined_at) VALUES (?, ?, ?)").run(
        groupId,
        playerId,
        now(),
      );
      // The starting handicap applies to every day, including days before joining.
      db.prepare(
        `INSERT INTO handicaps (group_id, player_id, effective_date, value, source, created_at)
         VALUES (?, ?, '0000-01-01', ?, 'start', ?)`,
      ).run(groupId, playerId, handicap, now());
    };

    authed.get("/api/groups", async (req) => {
      const today = localToday(req);
      const ids = db
        .prepare(
          `SELECT g.id FROM groups g JOIN memberships m ON m.group_id = g.id
           WHERE m.player_id = ? ORDER BY g.name`,
        )
        .all(req.player.id) as { id: string }[];
      return { groups: ids.map((r) => groupSummary(r.id, today)) };
    });

    authed.post<{ Body: { name: string; handicap: number } }>(
      "/api/groups",
      {
        schema: {
          body: {
            type: "object",
            required: ["name", "handicap"],
            properties: { name: nameSchema, handicap: handicapSchema },
          },
        },
      },
      async (req, reply) => {
        const id = randomUUID();
        tx(() => {
          let code = inviteCode();
          while (db.prepare("SELECT 1 FROM groups WHERE invite_code = ?").get(code)) code = inviteCode();
          db.prepare(
            "INSERT INTO groups (id, name, invite_code, owner_id, created_at) VALUES (?, ?, ?, ?, ?)",
          ).run(id, req.body.name.trim(), code, req.player.id, now());
          addMember(id, req.player.id, req.body.handicap);
        });
        return reply.code(201).send({ group: groupSummary(id, localToday(req)) });
      },
    );

    authed.post<{ Body: { code: string; handicap: number } }>(
      "/api/groups/join",
      {
        schema: {
          body: {
            type: "object",
            required: ["code", "handicap"],
            properties: { code: { type: "string", minLength: 4, maxLength: 12 }, handicap: handicapSchema },
          },
        },
      },
      async (req) => {
        const code = req.body.code.trim().toUpperCase();
        const g = db.prepare("SELECT id FROM groups WHERE invite_code = ?").get(code) as
          | { id: string }
          | undefined;
        if (!g) throw new HttpError(404, "No group has that invite code");
        const already = db
          .prepare("SELECT 1 FROM memberships WHERE group_id = ? AND player_id = ?")
          .get(g.id, req.player.id);
        if (!already) tx(() => addMember(g.id, req.player.id, req.body.handicap));
        return { group: groupSummary(g.id, localToday(req)) };
      },
    );

    authed.get<{ Params: { id: string } }>("/api/groups/:id", async (req) => {
      requireMember(req.params.id, req.player.id);
      return { group: groupSummary(req.params.id, localToday(req)) };
    });

    authed.delete<{ Params: { id: string } }>("/api/groups/:id/membership", async (req, reply) => {
      requireMember(req.params.id, req.player.id);
      db.prepare("DELETE FROM memberships WHERE group_id = ? AND player_id = ?").run(
        req.params.id,
        req.player.id,
      );
      return reply.code(204).send();
    });

    // -- handicaps --

    authed.get<{ Params: { id: string } }>("/api/groups/:id/handicap", async (req) => {
      requireMember(req.params.id, req.player.id);
      const today = localToday(req);
      const history = db
        .prepare(
          `SELECT effective_date AS effectiveDate, value, source FROM handicaps
           WHERE group_id = ? AND player_id = ? ORDER BY effective_date`,
        )
        .all(req.params.id, req.player.id);
      // Suggestion window: the last N complete days (today is still in progress).
      const to = addDays(today, -1);
      const from = addDays(today, -SUGGESTION_WINDOW_DAYS);
      const counts = (
        db
          .prepare("SELECT count FROM scores WHERE player_id = ? AND date BETWEEN ? AND ?")
          .all(req.player.id, from, to) as { count: number }[]
      ).map((r) => r.count);
      const s = suggestHandicap(counts);
      return {
        current: currentHandicap(req.params.id, req.player.id, today),
        history,
        suggestion: s
          ? {
              value: s.suggested,
              daysUsed: s.used,
              daysAvailable: counts.length,
              explanation: `Average of your best ${s.used} of the last ${counts.length} days you scored.`,
            }
          : null,
        suggestionNeedsDays: s ? 0 : 3 - counts.length,
      };
    });

    authed.put<{ Params: { id: string }; Body: { handicap: number; source: string } }>(
      "/api/groups/:id/handicap",
      {
        schema: {
          body: {
            type: "object",
            required: ["handicap", "source"],
            properties: { handicap: handicapSchema, source: { enum: ["manual", "suggestion"] } },
          },
        },
      },
      async (req) => {
        requireMember(req.params.id, req.player.id);
        // Changes start tomorrow, so nobody can rescue a bad day after the fact.
        const effective = addDays(localToday(req), 1);
        db.prepare(
          `INSERT INTO handicaps (group_id, player_id, effective_date, value, source, created_at)
           VALUES (?, ?, ?, ?, ?, ?)
           ON CONFLICT (group_id, player_id, effective_date) DO UPDATE SET
             value = excluded.value, source = excluded.source, created_at = excluded.created_at`,
        ).run(req.params.id, req.player.id, effective, req.body.handicap, req.body.source, now());
        return { handicap: req.body.handicap, effectiveDate: effective };
      },
    );

    // -- courses --

    authed.post<{
      Params: { id: string };
      Body: { name: string; startDate: string; weekdayPar: number; weekendPar: number };
    }>(
      "/api/groups/:id/courses",
      {
        schema: {
          body: {
            type: "object",
            required: ["name", "startDate", "weekdayPar", "weekendPar"],
            properties: {
              name: nameSchema,
              startDate: dateSchema,
              weekdayPar: parSchema,
              weekendPar: parSchema,
            },
          },
        },
      },
      async (req, reply) => {
        requireMember(req.params.id, req.player.id);
        const { name, startDate, weekdayPar, weekendPar } = req.body;
        if (!isDate(startDate)) throw new HttpError(400, "Bad start date");
        const id = randomUUID();
        tx(() => {
          db.prepare(
            "INSERT INTO courses (id, group_id, name, start_date, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?)",
          ).run(id, req.params.id, name.trim(), startDate, req.player.id, now());
          const ins = db.prepare("INSERT INTO holes (course_id, hole, date, par) VALUES (?, ?, ?, ?)");
          for (let i = 0; i < HOLES_PER_COURSE; i++) {
            const date = addDays(startDate, i);
            ins.run(id, i + 1, date, isWeekend(date) ? weekendPar : weekdayPar);
          }
        });
        return reply.code(201).send({ course: courseInfo({ id, name, start_date: startDate }, localToday(req)) });
      },
    );

    authed.patch<{ Params: { id: string; hole: string }; Body: { par: number } }>(
      "/api/courses/:id/holes/:hole",
      { schema: { body: { type: "object", required: ["par"], properties: { par: parSchema } } } },
      async (req) => {
        const c = courseRow(req.params.id);
        requireMember(c.group_id, req.player.id);
        if (c.created_by !== req.player.id) throw new HttpError(403, "Only the course creator can change par");
        const hole = Number(req.params.hole);
        const r = db
          .prepare("UPDATE holes SET par = ? WHERE course_id = ? AND hole = ?")
          .run(req.body.par, c.id, hole);
        if (r.changes === 0) throw new HttpError(404, "Hole not found");
        return { hole, par: req.body.par };
      },
    );

    authed.get<{ Params: { id: string } }>("/api/courses/:id/leaderboard", async (req) => {
      const c = courseRow(req.params.id);
      requireMember(c.group_id, req.player.id);
      const holes = db
        .prepare("SELECT hole, date, par FROM holes WHERE course_id = ? ORDER BY hole")
        .all(c.id) as unknown as HoleDef[];
      const first = holes[0].date;
      const last = holes[holes.length - 1].date;
      const members = db
        .prepare(
          `SELECT p.id, p.name FROM memberships m JOIN players p ON p.id = m.player_id WHERE m.group_id = ?`,
        )
        .all(c.group_id) as { id: string; name: string }[];
      const hcpStmt = db.prepare(
        `SELECT effective_date AS effectiveDate, value FROM handicaps
         WHERE group_id = ? AND player_id = ? ORDER BY effective_date`,
      );
      const scoreStmt = db.prepare(
        "SELECT date, count, metric, source FROM scores WHERE player_id = ? AND date BETWEEN ? AND ?",
      );
      const cards = members.map((m) => {
        const history = hcpStmt.all(c.group_id, m.id) as unknown as HandicapRow[];
        const scores = scoreStmt.all(m.id, first, last) as unknown as ScoreRow[];
        return buildCard(m, holes, history, new Map(scores.map((s) => [s.date, s])));
      });
      return {
        course: { ...courseInfo(c, localToday(req)), createdBy: c.created_by, groupId: c.group_id },
        holes,
        leaderboard: rankCards(cards),
      };
    });
  });

  // ---- web leaderboard (static files) ----------------------------------

  if (opts.webDir && existsSync(opts.webDir)) {
    app.register(fastifyStatic, { root: opts.webDir });
  }

  return { app, db };
}
