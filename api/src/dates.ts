// All game dates are plain local calendar days ("YYYY-MM-DD").
// The device decides which day a count belongs to; the server never converts time zones.

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export function isDate(s: unknown): s is string {
  if (typeof s !== "string" || !DATE_RE.test(s)) return false;
  const d = new Date(s + "T00:00:00Z");
  return !Number.isNaN(d.getTime()) && d.toISOString().slice(0, 10) === s;
}

export function addDays(date: string, days: number): string {
  const d = new Date(date + "T00:00:00Z");
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

export function isWeekend(date: string): boolean {
  const dow = new Date(date + "T00:00:00Z").getUTCDay();
  return dow === 0 || dow === 6;
}

export function utcToday(): string {
  return new Date().toISOString().slice(0, 10);
}
