// Pure scoring rules. No database access here, so this file is easy to test.
//
//   net    = gross - handicap        (handicap = the player's daily baseline)
//   toPar  = net - par
//
// Lower is better. A day with no score is not counted ("thru" shows how many
// holes a player has played).

export interface HoleDef {
  hole: number;
  date: string;
  par: number;
}

export interface HandicapRow {
  effectiveDate: string;
  value: number;
}

export interface ScoreRow {
  date: string;
  count: number;
  metric: string;
  source: string;
}

export interface HoleResult {
  hole: number;
  date: string;
  par: number;
  handicap: number;
  gross: number | null;
  net: number | null;
  toPar: number | null;
  metric: string | null;
  source: string | null;
}

export interface PlayerCard {
  playerId: string;
  name: string;
  handicap: number; // current (latest) handicap
  thru: number;
  totalGross: number;
  totalNet: number;
  totalToPar: number;
  holes: HoleResult[];
}

/** Handicap that applies on `date`. History must be sorted by effectiveDate ascending. */
export function handicapOn(history: HandicapRow[], date: string): number {
  let value = 0;
  for (const h of history) {
    if (h.effectiveDate <= date) value = h.value;
    else break;
  }
  return value;
}

export function buildCard(
  player: { id: string; name: string },
  holes: HoleDef[],
  handicapHistory: HandicapRow[],
  scoresByDate: Map<string, ScoreRow>,
): PlayerCard {
  const results: HoleResult[] = holes.map((h) => {
    const handicap = handicapOn(handicapHistory, h.date);
    const s = scoresByDate.get(h.date);
    if (!s) {
      return { ...h, handicap, gross: null, net: null, toPar: null, metric: null, source: null };
    }
    const net = s.count - handicap;
    return {
      ...h,
      handicap,
      gross: s.count,
      net,
      toPar: net - h.par,
      metric: s.metric,
      source: s.source,
    };
  });
  const played = results.filter((r) => r.gross !== null);
  return {
    playerId: player.id,
    name: player.name,
    handicap: handicapHistory.length ? handicapHistory[handicapHistory.length - 1].value : 0,
    thru: played.length,
    totalGross: played.reduce((a, r) => a + (r.gross ?? 0), 0),
    totalNet: played.reduce((a, r) => a + (r.net ?? 0), 0),
    totalToPar: played.reduce((a, r) => a + (r.toPar ?? 0), 0),
    holes: results,
  };
}

/** Best (lowest) to-par first. Players who have not played sort last. */
export function rankCards(cards: PlayerCard[]): PlayerCard[] {
  return [...cards].sort((a, b) => {
    if ((a.thru === 0) !== (b.thru === 0)) return a.thru === 0 ? 1 : -1;
    if (a.totalToPar !== b.totalToPar) return a.totalToPar - b.totalToPar;
    if (a.thru !== b.thru) return b.thru - a.thru;
    return a.name.localeCompare(b.name);
  });
}

export const SUGGESTION_WINDOW_DAYS = 14;
export const SUGGESTION_MIN_DAYS = 3;

/**
 * Suggested handicap: the average of the player's best (lowest) half of the
 * days in the window, rounded. Like a golf handicap index, it reflects what
 * the player does on a good day, not an average day.
 */
export function suggestHandicap(counts: number[]): { suggested: number; used: number } | null {
  if (counts.length < SUGGESTION_MIN_DAYS) return null;
  const sorted = [...counts].sort((a, b) => a - b);
  const used = Math.ceil(sorted.length / 2);
  const best = sorted.slice(0, used);
  return { suggested: Math.round(best.reduce((a, b) => a + b, 0) / used), used };
}
