export interface RateLike {
  id?: string;
  startQuantity?: number;
  endQuantity?: number;
  startDate?: string | Date;
  endDate?: string | Date;
}

export function checkRateOverlap(rates: RateLike[], candidate: RateLike): boolean {
  return rates.some((r) => {
    if (r.id !== undefined && candidate.id !== undefined && r.id === candidate.id) {
      return false;
    }

    const quantityOverlap =
      candidate.startQuantity !== undefined &&
      candidate.endQuantity !== undefined &&
      r.startQuantity !== undefined &&
      r.endQuantity !== undefined &&
      ((candidate.startQuantity >= r.startQuantity &&
        candidate.startQuantity < r.endQuantity) ||
        (candidate.endQuantity > r.startQuantity &&
          candidate.endQuantity <= r.endQuantity) ||
        (candidate.startQuantity <= r.startQuantity &&
          candidate.endQuantity >= r.endQuantity));

    const dateOverlap =
      candidate.startDate &&
      candidate.endDate &&
      r.startDate &&
      r.endDate &&
      ((new Date(candidate.startDate) >= new Date(r.startDate) &&
        new Date(candidate.startDate) < new Date(r.endDate)) ||
        (new Date(candidate.endDate) > new Date(r.startDate) &&
          new Date(candidate.endDate) <= new Date(r.endDate)) ||
        (new Date(candidate.startDate) <= new Date(r.startDate) &&
          new Date(candidate.endDate) >= new Date(r.endDate)));

    return Boolean(quantityOverlap || dateOverlap);
  });
}

export type RateSortBy = 'priority' | 'amount' | 'quantity' | 'date';
export type RateSortOrder = 'asc' | 'desc';

export interface SortableRate {
  id?: string;
  defaultRate?: boolean;
  priority?: number;
  amount?: number;
  startQuantity?: number;
  startDate?: string | Date;
}

export function sortRatesDefaultFirst<R extends SortableRate>(
  rates: R[],
  sortBy: RateSortBy,
  sortOrder: RateSortOrder,
): R[] {
  return [...rates].sort((a, b) => {
    if (a.defaultRate !== b.defaultRate) {
      return a.defaultRate ? -1 : 1;
    }

    let comparison = 0;
    switch (sortBy) {
      case 'priority':
        comparison = (a.priority ?? 0) - (b.priority ?? 0);
        break;
      case 'amount':
        comparison = (a.amount ?? 0) - (b.amount ?? 0);
        break;
      case 'quantity':
        comparison = (a.startQuantity ?? 0) - (b.startQuantity ?? 0);
        break;
      case 'date':
        comparison =
          new Date(a.startDate || '').getTime() - new Date(b.startDate || '').getTime();
        break;
    }

    return sortOrder === 'asc' ? comparison : -comparison;
  });
}

export interface ContractFormState {
  name?: string;
  description?: string;
  status?: string;
  startDate?: string | Date;
  endDate?: string | Date;
  calculationMode?: {
    type?: string;
    subType?: string;
    eventConfig?: { id?: string; eventName?: string };
    parameters?: Record<string, unknown>;
  };
  priority?: number;
  customerId?: string;
  customerName?: string;
  tariffId?: string;
  [k: string]: unknown;
}

export function formatDay(d: string | Date | undefined): string | undefined {
  if (!d) return undefined;
  // If it's already an ISO day string (YYYY-MM-DD), return as-is to avoid timezone shifts
  if (typeof d === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(d)) return d;
  const date = new Date(d);
  if (Number.isNaN(date.getTime())) return String(d);
  return date.toISOString().slice(0, 10);
}

export function normalizeContractFormSubmit(state: ContractFormState): Record<string, unknown> {
  const payload: Record<string, any> = {
    ...state,
    startDate: formatDay(state.startDate),
    endDate: formatDay(state.endDate),
    calculationMode: {
      ...state.calculationMode,
      eventConfig: { id: state.calculationMode?.eventConfig?.id },
    },
    priority: state.priority ?? 0,
  };

  for (const key of ['customerId', 'customerName', 'tariffId'] as const) {
    const v = state[key];
    if (typeof v === 'string' && v.trim()) {
      payload[key] = v.trim();
    } else {
      delete payload[key];
    }
  }

  return payload;
}

export function isDateExpiringSoon(endDate: string | Date, daysAhead = 30): boolean {
  const end = new Date(endDate);
  const now = new Date();
  const diffInDays = (end.getTime() - now.getTime()) / (1000 * 3600 * 24);
  return diffInDays <= daysAhead && diffInDays > 0;
}

export function isDateExpired(endDate: string | Date): boolean {
  const end = new Date(endDate);
  const now = new Date();
  return end < now;
}

export type ContractDateError = 'endAfterStart' | null;

export function validateContractDates(
  start: string | Date | undefined | null,
  end: string | Date | undefined | null,
): ContractDateError {
  if (!start || !end) return null;
  const s = new Date(start);
  const e = new Date(end);
  if (e <= s) return 'endAfterStart';
  return null;
}
