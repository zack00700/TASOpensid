import { describe, it, expect } from 'vitest';
import {
  flattenConditions,
  matchesCondition,
  applyConditions,
} from '../src/utils/advancedFilter';

/** Shape emitted by AdvancedFilter.vue: groups, each holding its conditions. */
const group = (...conditions: any[]) => ({
  id: 'g1',
  logicalOperator: 'AND' as const,
  conditions,
});

describe('flattenConditions', () => {
  it('pulls conditions out of the emitted groups', () => {
    const result = flattenConditions([
      group({ field: 'status', operator: 'equals', value: 'Draft' }),
      group({ field: 'shipper', operator: 'contains', value: 'Acme' }),
    ]);
    expect(result.map((c) => c.field)).toEqual(['status', 'shipper']);
  });

  it('drops conditions with no field or no value', () => {
    const result = flattenConditions([
      group(
        { field: 'status', operator: 'equals', value: '' },
        { field: '', operator: 'equals', value: 'x' },
        { field: 'shipper', operator: 'equals', value: null },
        { field: 'vessel', operator: 'equals', value: 'Ever Given' },
      ),
    ]);
    expect(result).toHaveLength(1);
    expect(result[0].field).toBe('vessel');
  });

  it('returns an empty list for the cleared-filters payload', () => {
    expect(flattenConditions([])).toEqual([]);
    expect(flattenConditions(null)).toEqual([]);
  });
});

describe('matchesCondition', () => {
  const row = {
    blNumber: 'MAEU-2024-00182',
    status: 'Draft',
    weight: 1500,
    createdAt: '2026-03-15T00:00:00Z',
    commodity: { description: 'Coffee beans' },
  };

  it('matches text operators case-insensitively', () => {
    expect(matchesCondition(row, { field: 'blNumber', operator: 'contains', value: 'maeu' })).toBe(true);
    expect(matchesCondition(row, { field: 'blNumber', operator: 'starts_with', value: 'MAEU' })).toBe(true);
    expect(matchesCondition(row, { field: 'blNumber', operator: 'ends_with', value: '00182' })).toBe(true);
    expect(matchesCondition(row, { field: 'status', operator: 'equals', value: 'draft' })).toBe(true);
    expect(matchesCondition(row, { field: 'status', operator: 'not_equals', value: 'Draft' })).toBe(false);
  });

  it('compares numbers numerically, not as strings', () => {
    // '1500' < '900' as text, so a string comparison would get this wrong.
    expect(matchesCondition(row, { field: 'weight', operator: 'greater_than', value: 900 })).toBe(true);
    expect(matchesCondition(row, { field: 'weight', operator: 'less_than', value: 900 })).toBe(false);
  });

  it('compares dates chronologically', () => {
    expect(matchesCondition(row, { field: 'createdAt', operator: 'before', value: '2026-06-01' })).toBe(true);
    expect(matchesCondition(row, { field: 'createdAt', operator: 'after', value: '2026-06-01' })).toBe(false);
  });

  it('supports between for both numbers and dates', () => {
    expect(matchesCondition(row, { field: 'weight', operator: 'between', value: 1000, value2: 2000 })).toBe(true);
    expect(matchesCondition(row, { field: 'weight', operator: 'between', value: 2000, value2: 3000 })).toBe(false);
    expect(
      matchesCondition(row, { field: 'createdAt', operator: 'between', value: '2026-01-01', value2: '2026-12-31' }),
    ).toBe(true);
  });

  it('reads nested fields through dots', () => {
    expect(
      matchesCondition(row, { field: 'commodity.description', operator: 'contains', value: 'coffee' }),
    ).toBe(true);
  });

  it('does not match a field the row does not have', () => {
    expect(matchesCondition(row, { field: 'missing', operator: 'equals', value: 'x' })).toBe(false);
  });
});

describe('applyConditions', () => {
  const rows = [
    { blNumber: 'BL001', status: 'Draft', shipper: 'Acme' },
    { blNumber: 'BL002', status: 'Final', shipper: 'Acme' },
    { blNumber: 'BL003', status: 'Draft', shipper: 'Globex' },
  ];

  it('returns every row when there is no condition', () => {
    expect(applyConditions(rows, [])).toHaveLength(3);
  });

  it('requires all conditions to hold', () => {
    const result = applyConditions(rows, [
      { field: 'status', operator: 'equals', value: 'Draft' },
      { field: 'shipper', operator: 'equals', value: 'Acme' },
    ]);
    expect(result.map((r) => r.blNumber)).toEqual(['BL001']);
  });

  it('handles an empty or missing collection', () => {
    expect(applyConditions([], [{ field: 'status', operator: 'equals', value: 'Draft' }])).toEqual([]);
    expect(applyConditions(null, [])).toEqual([]);
  });
});
