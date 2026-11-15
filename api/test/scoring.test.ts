import { describe, expect, it } from "vitest";
import { buildCard, handicapOn, rankCards, suggestHandicap } from "../src/scoring.js";
import { addDays, isDate, isWeekend } from "../src/dates.js";

describe("dates", () => {
  it("validates and adds days", () => {
    expect(isDate("2026-02-29")).toBe(false);
    expect(isDate("2028-02-29")).toBe(true);
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
    expect(isWeekend("2026-11-14")).toBe(true); // Saturday
    expect(isWeekend("2026-11-16")).toBe(false);
  });
});

describe("handicapOn", () => {
  const h = [
    { effectiveDate: "0000-01-01", value: 40 },
    { effectiveDate: "2026-11-10", value: 30 },
  ];
  it("uses the latest value that has started", () => {
    expect(handicapOn(h, "2026-11-09")).toBe(40);
    expect(handicapOn(h, "2026-11-10")).toBe(30);
    expect(handicapOn([], "2026-11-10")).toBe(0);
  });
});

describe("buildCard", () => {
  const holes = [
    { hole: 1, date: "2026-11-09", par: 10 },
    { hole: 2, date: "2026-11-10", par: 10 },
    { hole: 3, date: "2026-11-11", par: 5 },
  ];
  const hist = [
    { effectiveDate: "0000-01-01", value: 40 },
    { effectiveDate: "2026-11-10", value: 30 },
  ];
  it("computes net and to-par with literal counts", () => {
    const scores = new Map([
      ["2026-11-09", { date: "2026-11-09", count: 55, metric: "unlocks", source: "auto" }],
      ["2026-11-10", { date: "2026-11-10", count: 35, metric: "unlocks", source: "manual" }],
    ]);
    const card = buildCard({ id: "p", name: "P" }, holes, hist, scores);
    expect(card.holes[0]).toMatchObject({ gross: 55, handicap: 40, net: 15, toPar: 5 });
    expect(card.holes[1]).toMatchObject({ gross: 35, handicap: 30, net: 5, toPar: -5 });
    expect(card.holes[2]).toMatchObject({ gross: null, toPar: null });
    expect(card).toMatchObject({ thru: 2, totalGross: 90, totalNet: 20, totalToPar: 0, handicap: 30 });
  });
});

describe("rankCards", () => {
  const card = (name: string, thru: number, totalToPar: number) =>
    ({ playerId: name, name, handicap: 0, thru, totalGross: 0, totalNet: 0, totalToPar, holes: [] });
  it("sorts by to-par, non-players last", () => {
    const r = rankCards([card("a", 0, 0), card("b", 2, 5), card("c", 2, -3), card("d", 3, -3)]);
    expect(r.map((c) => c.name)).toEqual(["d", "c", "b", "a"]);
  });
});

describe("suggestHandicap", () => {
  it("needs three days", () => {
    expect(suggestHandicap([10, 20])).toBeNull();
  });
  it("averages the best half", () => {
    // best 3 of 5: 10, 20, 30
    expect(suggestHandicap([50, 10, 40, 20, 30])).toEqual({ suggested: 20, used: 3 });
  });
});
