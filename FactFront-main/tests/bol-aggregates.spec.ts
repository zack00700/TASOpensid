import { describe, it, expect } from 'vitest';
import {
  getContainerCount,
  getTotalWeight,
  getHazmatFlag,
  getCustomsRollup,
  formatWeight,
} from '../src/utils/bolAggregates';

interface PartialItem {
  weight?: number;
  hazmatFlag?: boolean;
  customsStatus?: string;
}
interface PartialBoL {
  items?: PartialItem[];
}

describe('getContainerCount', () => {
  it('returns 0 when items is missing', () => {
    expect(getContainerCount({} as PartialBoL)).toBe(0);
  });
  it('returns 0 when items is empty', () => {
    expect(getContainerCount({ items: [] })).toBe(0);
  });
  it('returns the count of items', () => {
    expect(getContainerCount({ items: [{}, {}, {}] })).toBe(3);
  });
});

describe('getTotalWeight', () => {
  it('returns 0 for empty items', () => {
    expect(getTotalWeight({ items: [] })).toBe(0);
  });
  it('sums item weights', () => {
    expect(getTotalWeight({ items: [{ weight: 1000 }, { weight: 2500 }] })).toBe(3500);
  });
  it('treats missing weights as 0', () => {
    expect(getTotalWeight({ items: [{ weight: 1000 }, {}, { weight: 500 }] })).toBe(1500);
  });
});

describe('getHazmatFlag', () => {
  it('returns false when items is empty', () => {
    expect(getHazmatFlag({ items: [] })).toBe(false);
  });
  it('returns true when at least one item has hazmatFlag=true', () => {
    expect(getHazmatFlag({ items: [{}, { hazmatFlag: true }, {}] })).toBe(true);
  });
  it('returns false when all items have hazmatFlag=false or missing', () => {
    expect(getHazmatFlag({ items: [{ hazmatFlag: false }, {}] })).toBe(false);
  });
});

describe('getCustomsRollup', () => {
  it('returns null for empty items', () => {
    expect(getCustomsRollup({ items: [] })).toBe(null);
  });
  it('returns null when all items have no customsStatus', () => {
    expect(getCustomsRollup({ items: [{}, {}] })).toBe(null);
  });
  it('returns CLEARED when all items are CLEARED', () => {
    expect(getCustomsRollup({ items: [{ customsStatus: 'CLEARED' }, { customsStatus: 'CLEARED' }] })).toBe('CLEARED');
  });
  it('returns the worst status when mixed (REFUSED wins)', () => {
    expect(getCustomsRollup({
      items: [{ customsStatus: 'CLEARED' }, { customsStatus: 'REFUSED' }, { customsStatus: 'PENDING' }],
    })).toBe('REFUSED');
  });
  it('returns HELD when mix is HELD + INSPECTED + CLEARED (HELD has highest priority)', () => {
    expect(getCustomsRollup({
      items: [{ customsStatus: 'HELD' }, { customsStatus: 'INSPECTED' }, { customsStatus: 'CLEARED' }],
    })).toBe('HELD');
  });
});

describe('formatWeight', () => {
  it('returns em-dash for 0 kg', () => {
    expect(formatWeight(0)).toBe('—');
  });
  it('returns kg for values under 1000', () => {
    expect(formatWeight(500)).toBe('500 kg');
    expect(formatWeight(999)).toBe('999 kg');
  });
  it('returns tons for values >= 1000 kg', () => {
    expect(formatWeight(1500)).toBe('1.5 t');
    expect(formatWeight(10500)).toBe('10.5 t');
  });
});
