import type { VesselVisit, VesselVisitPhase } from '../types/vessel-visit';

export function countByPhase(visits: VesselVisit[]): Record<VesselVisitPhase, number> {
  const result: Record<VesselVisitPhase, number> = {
    Created: 0,
    Active: 0,
    Completed: 0,
    Canceled: 0,
  };
  for (const v of visits) {
    if (v.phase in result) {
      result[v.phase] += 1;
    }
  }
  return result;
}

export function countByService(
  visits: VesselVisit[],
): { service: string; count: number }[] {
  const counts = new Map<string, number>();
  for (const v of visits) {
    const key = v.service || '';
    if (!key) continue;
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return [...counts.entries()]
    .map(([service, count]) => ({ service, count }))
    .sort((a, b) => b.count - a.count || a.service.localeCompare(b.service));
}

export function topVesselsByVisitCount(
  visits: VesselVisit[],
  limit = 5,
): { vesselName: string; vesselId: string; count: number }[] {
  const counts = new Map<string, { vesselName: string; vesselId: string; count: number }>();
  for (const v of visits) {
    const key = `${v.vesselId}|${v.vesselName}`;
    if (!counts.has(key)) {
      counts.set(key, { vesselName: v.vesselName, vesselId: v.vesselId, count: 0 });
    }
    counts.get(key)!.count += 1;
  }
  return [...counts.values()]
    .sort((a, b) => b.count - a.count || a.vesselName.localeCompare(b.vesselName))
    .slice(0, limit);
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;
const MS_PER_HOUR = 60 * 60 * 1000;

function parseTs(s: string | null | undefined): number | null {
  if (!s) return null;
  const t = Date.parse(s);
  return Number.isFinite(t) ? t : null;
}

export function visitsPerMonth(
  visits: VesselVisit[],
  months = 12,
): { yearMonth: string; count: number }[] {
  const validEtas = visits
    .map((v) => parseTs(v.eta))
    .filter((t): t is number => t !== null);
  if (validEtas.length === 0) return [];
  const latest = new Date(Math.max(...validEtas));
  const buckets: { yearMonth: string; count: number }[] = [];
  for (let i = months - 1; i >= 0; i--) {
    const d = new Date(Date.UTC(latest.getUTCFullYear(), latest.getUTCMonth() - i, 1));
    const yearMonth = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
    buckets.push({ yearMonth, count: 0 });
  }
  const index = new Map(buckets.map((b, i) => [b.yearMonth, i] as const));
  for (const t of validEtas) {
    const d = new Date(t);
    const ym = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
    const idx = index.get(ym);
    if (idx !== undefined) buckets[idx].count += 1;
  }
  return buckets;
}

export function averageDwellDays(visits: VesselVisit[]): number | null {
  const dwells: number[] = [];
  for (const v of visits) {
    const ata = parseTs(v.ata);
    const atd = parseTs(v.atd);
    if (ata !== null && atd !== null) {
      dwells.push((atd - ata) / MS_PER_DAY);
    }
  }
  if (dwells.length === 0) return null;
  return dwells.reduce((a, b) => a + b, 0) / dwells.length;
}

export function onTimeRate(
  visits: VesselVisit[],
  toleranceHours = 2,
): number | null {
  const completed = visits.filter((v) => parseTs(v.eta) !== null && parseTs(v.ata) !== null);
  if (completed.length === 0) return null;
  const onTime = completed.filter((v) => {
    const eta = parseTs(v.eta)!;
    const ata = parseTs(v.ata)!;
    return ata - eta <= toleranceHours * MS_PER_HOUR;
  }).length;
  return onTime / completed.length;
}

export function computeDwell(visit: VesselVisit): {
  plannedDays: number | null;
  actualDays: number | null;
  deltaHours: number | null;
  etaAtaDeltaHours: number | null;
  etdAtdDeltaHours: number | null;
} {
  const eta = parseTs(visit.eta);
  const etd = parseTs(visit.etd);
  const ata = parseTs(visit.ata);
  const atd = parseTs(visit.atd);
  const plannedDays = eta !== null && etd !== null ? (etd - eta) / MS_PER_DAY : null;
  const actualDays = ata !== null && atd !== null ? (atd - ata) / MS_PER_DAY : null;
  const deltaHours =
    plannedDays !== null && actualDays !== null
      ? (actualDays - plannedDays) * 24
      : null;
  const etaAtaDeltaHours = eta !== null && ata !== null ? (ata - eta) / MS_PER_HOUR : null;
  const etdAtdDeltaHours = etd !== null && atd !== null ? (atd - etd) / MS_PER_HOUR : null;
  return { plannedDays, actualDays, deltaHours, etaAtaDeltaHours, etdAtdDeltaHours };
}
