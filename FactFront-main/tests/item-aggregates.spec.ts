import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  getDwellDays,
  dwellColorClass,
  getChargingStatus,
  getLastEvent,
  formatRelativeDate,
} from '../src/utils/itemAggregates';
import type { Item, Lifecycle, Event as ItemEvent } from '../src/types/item';

const TODAY = new Date('2026-05-02T12:00:00Z');

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(TODAY);
});

afterEach(() => {
  vi.useRealTimers();
});

function makeItem(overrides: Partial<Item>): Item {
  return {
    lifeCycles: [],
    ...overrides,
  } as Item;
}

describe('getDwellDays', () => {
  it('returns null when gateInDate is missing', () => {
    expect(getDwellDays(makeItem({}))).toBe(null);
  });

  it('returns days since gateInDate when no gateOutDate', () => {
    expect(getDwellDays(makeItem({ gateInDate: '2026-04-25T00:00:00Z' }))).toBe(7);
  });

  it('returns historical dwell when gateOutDate is set', () => {
    expect(
      getDwellDays(
        makeItem({
          gateInDate: '2026-04-20T00:00:00Z',
          gateOutDate: '2026-04-25T00:00:00Z',
        }),
      ),
    ).toBe(5);
  });
});

describe('dwellColorClass', () => {
  it('returns slate for low dwell', () => {
    expect(dwellColorClass(0)).toContain('text-slate-700');
    expect(dwellColorClass(6)).toContain('text-slate-700');
  });
  it('returns amber for warning dwell', () => {
    expect(dwellColorClass(7)).toContain('text-amber-600');
    expect(dwellColorClass(13)).toContain('text-amber-600');
  });
  it('returns red for alert dwell', () => {
    expect(dwellColorClass(14)).toContain('text-red-600');
    expect(dwellColorClass(30)).toContain('text-red-600');
  });
});

describe('getChargingStatus', () => {
  it('returns null when chargingStartDate is missing', () => {
    expect(getChargingStatus(makeItem({}))).toBe(null);
  });
  it('returns free when today is before chargingStartDate', () => {
    expect(
      getChargingStatus(makeItem({ chargingStartDate: '2026-05-10T00:00:00Z' })),
    ).toEqual({ state: 'free' });
  });
  it('returns grace when today is between start and expiry', () => {
    expect(
      getChargingStatus(
        makeItem({
          chargingStartDate: '2026-04-25T00:00:00Z',
          gracePeriodExpiryDate: '2026-05-10T00:00:00Z',
        }),
      ),
    ).toEqual({ state: 'grace' });
  });
  it('returns past with day count when today is after expiry', () => {
    expect(
      getChargingStatus(
        makeItem({
          chargingStartDate: '2026-04-01T00:00:00Z',
          gracePeriodExpiryDate: '2026-04-25T00:00:00Z',
        }),
      ),
    ).toEqual({ state: 'past', days: 7 });
  });
  it('returns null when chargingStartDate is past but no expiry is set', () => {
    expect(
      getChargingStatus(makeItem({ chargingStartDate: '2026-04-01T00:00:00Z' })),
    ).toBe(null);
  });
});

describe('getLastEvent', () => {
  it('returns null when lifeCycles is empty', () => {
    expect(getLastEvent(makeItem({ lifeCycles: [] }))).toBe(null);
  });

  it('returns null when the active lifecycle has no events', () => {
    const lc: Lifecycle = { id: 'l1', status: 'In Progress', events: [] };
    expect(getLastEvent(makeItem({ lifeCycles: [lc] }))).toBe(null);
  });

  it('returns the last event of the In-Progress lifecycle', () => {
    const ev1: ItemEvent = { id: 'e1', timestamp: '2026-04-30T10:00:00Z', eventType: 'IN' };
    const ev2: ItemEvent = { id: 'e2', timestamp: '2026-05-01T10:00:00Z', eventType: 'INTERMEDIATE', location: 'Y4' };
    const lc: Lifecycle = { id: 'l1', status: 'In Progress', events: [ev1, ev2] };
    expect(getLastEvent(makeItem({ lifeCycles: [lc] }))).toEqual(ev2);
  });

  it('falls back to the first lifecycle when no In-Progress one exists', () => {
    const ev: ItemEvent = { id: 'e', timestamp: '2026-04-25T10:00:00Z', eventType: 'OUT' };
    const lc: Lifecycle = { id: 'l1', status: 'Completed', events: [ev] };
    expect(getLastEvent(makeItem({ lifeCycles: [lc] }))).toEqual(ev);
  });
});

describe('formatRelativeDate', () => {
  it('returns "now" for very recent timestamps', () => {
    expect(formatRelativeDate(new Date(TODAY.getTime() - 30_000).toISOString())).toBe('now');
  });
  it('returns minutes for under 1 hour', () => {
    expect(formatRelativeDate(new Date(TODAY.getTime() - 5 * 60_000).toISOString())).toBe('5m ago');
  });
  it('returns hours for under 24 hours', () => {
    expect(formatRelativeDate(new Date(TODAY.getTime() - 2 * 3_600_000).toISOString())).toBe('2h ago');
  });
  it('returns days for under 7 days', () => {
    expect(formatRelativeDate(new Date(TODAY.getTime() - 3 * 86_400_000).toISOString())).toBe('3d ago');
  });
  it('returns absolute date for older than 7 days', () => {
    const old = new Date(TODAY.getTime() - 30 * 86_400_000);
    const result = formatRelativeDate(old.toISOString());
    expect(result).not.toBe('now');
    expect(result).not.toMatch(/[mhd] ago$/);
    expect(result.length).toBeGreaterThan(0);
  });
  it('returns em-dash for invalid input', () => {
    expect(formatRelativeDate('not-a-date')).toBe('—');
  });
});
