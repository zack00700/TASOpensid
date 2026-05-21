interface ItemLike {
  weight?: number;
  hazmatFlag?: boolean;
  customsStatus?: string;
}

interface BoLLike {
  items?: ItemLike[];
}

/** Number of items linked to this BoL. */
export function getContainerCount(bol: BoLLike): number {
  return bol.items?.length ?? 0;
}

/** Sum of items[].weight, treating missing weights as 0. */
export function getTotalWeight(bol: BoLLike): number {
  return (bol.items ?? []).reduce((sum, it) => sum + (it.weight ?? 0), 0);
}

/** True when at least one item has hazmatFlag === true. */
export function getHazmatFlag(bol: BoLLike): boolean {
  return (bol.items ?? []).some((it) => it.hazmatFlag === true);
}

/**
 * Worst-case priority across items[].customsStatus.
 * Returns null when no item has a customsStatus set.
 *
 * Priority order: REFUSED > HELD > INSPECTED > PENDING > RELEASED > CLEARED.
 */
const CUSTOMS_PRIORITY: Record<string, number> = {
  REFUSED: 6,
  HELD: 5,
  INSPECTED: 4,
  PENDING: 3,
  RELEASED: 2,
  CLEARED: 1,
};

export function getCustomsRollup(bol: BoLLike): string | null {
  const items = bol.items ?? [];
  if (items.length === 0) return null;
  let worst: string | null = null;
  let worstP = 0;
  for (const it of items) {
    const s = it.customsStatus;
    if (!s) continue;
    const p = CUSTOMS_PRIORITY[s] ?? 0;
    if (p > worstP) {
      worstP = p;
      worst = s;
    }
  }
  return worst;
}

/** Formats a kg value: 0 → '—', <1000 → 'N kg', >=1000 → 'N.N t'. */
export function formatWeight(kg: number): string {
  if (kg === 0) return '—';
  if (kg < 1000) return `${kg.toFixed(0)} kg`;
  return `${(kg / 1000).toFixed(1)} t`;
}
