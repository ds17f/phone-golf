import { DatabaseSync } from "node:sqlite";
import { mkdirSync } from "node:fs";
import { dirname } from "node:path";

export type DB = DatabaseSync;

const SCHEMA = `
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS players (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  token_hash  TEXT NOT NULL UNIQUE,
  platform    TEXT NOT NULL,            -- ios | android | web
  created_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS groups (
  id           TEXT PRIMARY KEY,
  name         TEXT NOT NULL,
  invite_code  TEXT NOT NULL UNIQUE,
  owner_id     TEXT NOT NULL REFERENCES players(id),
  created_at   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS memberships (
  group_id   TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  player_id  TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  joined_at  TEXT NOT NULL,
  PRIMARY KEY (group_id, player_id)
);

-- Handicap history. The value that applies to a day is the latest row
-- with effective_date <= that day. Changes take effect the next day.
CREATE TABLE IF NOT EXISTS handicaps (
  group_id        TEXT NOT NULL,
  player_id       TEXT NOT NULL,
  effective_date  TEXT NOT NULL,        -- YYYY-MM-DD
  value           INTEGER NOT NULL,
  source          TEXT NOT NULL,        -- start | manual | suggestion
  created_at      TEXT NOT NULL,
  PRIMARY KEY (group_id, player_id, effective_date),
  FOREIGN KEY (group_id, player_id) REFERENCES memberships(group_id, player_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS courses (
  id          TEXT PRIMARY KEY,
  group_id    TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  start_date  TEXT NOT NULL,            -- hole 1 date, YYYY-MM-DD
  created_by  TEXT NOT NULL REFERENCES players(id),
  created_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS holes (
  course_id  TEXT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  hole       INTEGER NOT NULL,          -- 1..18
  date       TEXT NOT NULL,
  par        INTEGER NOT NULL,
  PRIMARY KEY (course_id, hole)
);

-- One number per player per local day, shared by every group the player is in.
CREATE TABLE IF NOT EXISTS scores (
  player_id   TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  date        TEXT NOT NULL,
  count       INTEGER NOT NULL,
  metric      TEXT NOT NULL,            -- unlocks | pickups
  source      TEXT NOT NULL,            -- auto | manual
  updated_at  TEXT NOT NULL,
  PRIMARY KEY (player_id, date)
);
`;

export function openDb(path: string): DB {
  if (path !== ":memory:") mkdirSync(dirname(path), { recursive: true });
  const db = new DatabaseSync(path);
  db.exec(SCHEMA);
  return db;
}
