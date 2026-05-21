import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { checkRateOverlap, type RateLike } from '../../src/utils/contract';

function r(over: Partial<RateLike> = {}): RateLike {
  return { id: Math.random().toString(36).slice(2, 8), ...over };
}

describe('checkRateOverlap', () => {
  it('returns false when no other rates exist', () => {
    expect(checkRateOverlap([], r({ startQuantity: 0, endQuantity: 100 }))).toBe(false);
  });

  it('ignores comparison against the rate with the same id', () => {
    const a = r({ id: 'x', startQuantity: 0, endQuantity: 100 });
    expect(checkRateOverlap([a], { ...a })).toBe(false);
  });

  it('detects exact quantity overlap', () => {
    const a = r({ id: 'a', startQuantity: 0, endQuantity: 100 });
    const b = r({ id: 'b', startQuantity: 0, endQuantity: 100 });
    expect(checkRateOverlap([a], b)).toBe(true);
  });

  it('detects partial quantity overlap', () => {
    const a = r({ id: 'a', startQuantity: 0, endQuantity: 100 });
    const b = r({ id: 'b', startQuantity: 50, endQuantity: 150 });
    expect(checkRateOverlap([a], b)).toBe(true);
  });

  it('treats touching boundaries as non-overlapping (end exclusive, start inclusive)', () => {
    const a = r({ id: 'a', startQuantity: 0, endQuantity: 100 });
    const b = r({ id: 'b', startQuantity: 100, endQuantity: 200 });
    expect(checkRateOverlap([a], b)).toBe(false);
  });

  it('detects exact date overlap', () => {
    const a = r({ id: 'a', startDate: '2026-01-01', endDate: '2026-02-01' });
    const b = r({ id: 'b', startDate: '2026-01-01', endDate: '2026-02-01' });
    expect(checkRateOverlap([a], b)).toBe(true);
  });

  it('treats touching date boundaries as non-overlapping', () => {
    const a = r({ id: 'a', startDate: '2026-01-01', endDate: '2026-02-01' });
    const b = r({ id: 'b', startDate: '2026-02-01', endDate: '2026-03-01' });
    expect(checkRateOverlap([a], b)).toBe(false);
  });

  it('detects overlap when both rates lack an id (the self-exclusion guard requires both ids)', () => {
    const a: RateLike = { startQuantity: 0, endQuantity: 100 };
    const b: RateLike = { startQuantity: 0, endQuantity: 100 };
    expect(checkRateOverlap([a], b)).toBe(true);
  });
});

import { sortRatesDefaultFirst, type SortableRate } from '../../src/utils/contract';

function sr(over: Partial<SortableRate> = {}): SortableRate {
  return {
    id: Math.random().toString(36).slice(2, 8),
    defaultRate: false,
    priority: 0,
    amount: 0,
    startQuantity: 0,
    startDate: '',
    ...over,
  };
}

describe('sortRatesDefaultFirst', () => {
  it('places the default rate first regardless of sortBy/sortOrder', () => {
    const rates = [
      sr({ id: '1', defaultRate: false, priority: 5 }),
      sr({ id: '2', defaultRate: true, priority: 1 }),
      sr({ id: '3', defaultRate: false, priority: 10 }),
    ];
    const out = sortRatesDefaultFirst(rates, 'priority', 'desc');
    expect(out[0].id).toBe('2');
  });

  it('sorts by priority desc when sortBy=priority, sortOrder=desc', () => {
    const rates = [
      sr({ id: 'a', priority: 1 }),
      sr({ id: 'b', priority: 9 }),
      sr({ id: 'c', priority: 5 }),
    ];
    expect(sortRatesDefaultFirst(rates, 'priority', 'desc').map((r) => r.id)).toEqual(['b', 'c', 'a']);
  });

  it('sorts by quantity asc when sortBy=quantity, sortOrder=asc', () => {
    const rates = [
      sr({ id: 'a', startQuantity: 100 }),
      sr({ id: 'b', startQuantity: 0 }),
      sr({ id: 'c', startQuantity: 50 }),
    ];
    expect(sortRatesDefaultFirst(rates, 'quantity', 'asc').map((r) => r.id)).toEqual(['b', 'c', 'a']);
  });

  it('sorts by startDate asc when sortBy=date, sortOrder=asc', () => {
    const rates = [
      sr({ id: 'a', startDate: '2026-03-01' }),
      sr({ id: 'b', startDate: '2026-01-01' }),
      sr({ id: 'c', startDate: '2026-02-01' }),
    ];
    expect(sortRatesDefaultFirst(rates, 'date', 'asc').map((r) => r.id)).toEqual(['b', 'c', 'a']);
  });

  it('does not mutate the input array', () => {
    const rates = [sr({ id: 'a', priority: 5 }), sr({ id: 'b', priority: 1 })];
    sortRatesDefaultFirst(rates, 'priority', 'desc');
    expect(rates.map((r) => r.id)).toEqual(['a', 'b']);
  });
});

import { validateContractDates } from '../../src/utils/contract';

describe('validateContractDates', () => {
  it('returns null when both dates are empty', () => {
    expect(validateContractDates(undefined, undefined)).toBeNull();
  });

  it('returns null when only one side is set', () => {
    expect(validateContractDates('2026-01-01', undefined)).toBeNull();
    expect(validateContractDates(undefined, '2026-01-01')).toBeNull();
  });

  it('returns null for a valid range', () => {
    expect(validateContractDates('2026-01-01', '2026-12-31')).toBeNull();
  });

  it('returns "endAfterStart" error when end is before start', () => {
    expect(validateContractDates('2026-12-31', '2026-01-01')).toBe('endAfterStart');
  });

  it('returns "endAfterStart" error when end equals start (same-day not allowed)', () => {
    expect(validateContractDates('2026-01-01', '2026-01-01')).toBe('endAfterStart');
  });
});

import { normalizeContractFormSubmit } from '../../src/utils/contract';

function baseForm(over: Record<string, any> = {}) {
  return {
    name: 'C1',
    description: '',
    status: 'Active',
    startDate: '2026-01-01',
    endDate: '2026-12-31',
    calculationMode: {
      type: 'Quantity',
      subType: '',
      eventConfig: { id: 'evt1', eventName: 'Storage' },
      parameters: { gracePeriod: 0, minimumDays: 1 },
    },
    priority: 5,
    customerId: '',
    customerName: '',
    tariffId: '',
    ...over,
  };
}

describe('normalizeContractFormSubmit', () => {
  it('formats startDate and endDate as ISO-day strings', () => {
    const out = normalizeContractFormSubmit(baseForm()) as any;
    expect(out.startDate).toBe('2026-01-01');
    expect(out.endDate).toBe('2026-12-31');
  });

  it('reduces eventConfig to its id', () => {
    const out = normalizeContractFormSubmit(baseForm()) as any;
    expect(out.calculationMode.eventConfig).toEqual({ id: 'evt1' });
  });

  it('defaults priority to 0 when not provided', () => {
    const out = normalizeContractFormSubmit(baseForm({ priority: undefined }));
    expect(out.priority).toBe(0);
  });

  it('omits empty customerId, customerName, tariffId', () => {
    const out = normalizeContractFormSubmit(baseForm());
    expect(out).not.toHaveProperty('customerId');
    expect(out).not.toHaveProperty('customerName');
    expect(out).not.toHaveProperty('tariffId');
  });

  it('preserves trimmed customerId, customerName, tariffId', () => {
    const out = normalizeContractFormSubmit(
      baseForm({ customerId: ' c1 ', customerName: ' Acme ', tariffId: ' t1 ' }),
    );
    expect(out.customerId).toBe('c1');
    expect(out.customerName).toBe('Acme');
    expect(out.tariffId).toBe('t1');
  });
});

import { isDateExpiringSoon, isDateExpired } from '../../src/utils/contract';

describe('isDateExpiringSoon', () => {
  const now = new Date('2026-05-14T12:00:00Z');
  beforeEach(() => vi.useFakeTimers().setSystemTime(now));
  afterEach(() => vi.useRealTimers());

  it('returns true for dates within 30 days in the future', () => {
    expect(isDateExpiringSoon('2026-06-01')).toBe(true);
  });

  it('returns false for dates more than 30 days away', () => {
    expect(isDateExpiringSoon('2026-12-31')).toBe(false);
  });

  it('returns false for already-expired dates', () => {
    expect(isDateExpiringSoon('2026-05-01')).toBe(false);
  });

  it('accepts a custom daysAhead window', () => {
    expect(isDateExpiringSoon('2026-08-01', 90)).toBe(true);
    expect(isDateExpiringSoon('2026-08-01', 30)).toBe(false);
  });
});

describe('isDateExpired', () => {
  const now = new Date('2026-05-14T12:00:00Z');
  beforeEach(() => vi.useFakeTimers().setSystemTime(now));
  afterEach(() => vi.useRealTimers());

  it('returns true for past dates', () => {
    expect(isDateExpired('2026-05-01')).toBe(true);
  });

  it('returns false for future dates', () => {
    expect(isDateExpired('2026-06-01')).toBe(false);
  });
});
