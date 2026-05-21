import { describe, it, expect } from 'vitest';
import {
  countByPhase,
  countByService,
  topVesselsByVisitCount,
  visitsPerMonth,
  averageDwellDays,
  onTimeRate,
  computeDwell,
} from '../../src/utils/vessel-visit-stats';
import type { VesselVisit } from '../../src/types/vessel-visit';

function v(over: Partial<VesselVisit> = {}): VesselVisit {
  return {
    id: Math.random().toString(36).slice(2, 8),
    vesselName: 'MV Test',
    vesselId: 'IMO000',
    visitReference: 'REF',
    phase: 'Created',
    service: 'SVC',
    serviceName: 'Service',
    facility: 'F',
    eta: '',
    etd: '',
    ata: '',
    atd: '',
    pod: '',
    pol: '',
    finalDestination: '',
    beginReceive: '',
    dryCutoff: '',
    reeferCutoff: '',
    hazCutoff: '',
    emptyPickup: '',
    inboundVoyage: '',
    outboundVoyage: '',
    inboundCaptain: '',
    outboundCaptain: '',
    lineOperator: '',
    notes: '',
    ...over,
  };
}

describe('countByPhase', () => {
  it('counts all four phases with zeros for unrepresented ones', () => {
    const result = countByPhase([
      v({ phase: 'Created' }),
      v({ phase: 'Created' }),
      v({ phase: 'Active' }),
    ]);
    expect(result).toEqual({ Created: 2, Active: 1, Completed: 0, Canceled: 0 });
  });

  it('returns all zeros on empty input', () => {
    expect(countByPhase([])).toEqual({ Created: 0, Active: 0, Completed: 0, Canceled: 0 });
  });
});

describe('countByService', () => {
  it('groups by service descending, empty input returns []', () => {
    expect(countByService([])).toEqual([]);
    const result = countByService([
      v({ service: 'WCCA' }),
      v({ service: 'WCCA' }),
      v({ service: 'WCCA' }),
      v({ service: 'ECNA' }),
    ]);
    expect(result).toEqual([
      { service: 'WCCA', count: 3 },
      { service: 'ECNA', count: 1 },
    ]);
  });
});

describe('topVesselsByVisitCount', () => {
  it('respects the limit, ties broken by vesselName ascending', () => {
    const result = topVesselsByVisitCount(
      [
        v({ vesselName: 'MV Bravo', vesselId: 'IMO2' }),
        v({ vesselName: 'MV Alpha', vesselId: 'IMO1' }),
        v({ vesselName: 'MV Bravo', vesselId: 'IMO2' }),
        v({ vesselName: 'MV Charlie', vesselId: 'IMO3' }),
        v({ vesselName: 'MV Alpha', vesselId: 'IMO1' }),
        v({ vesselName: 'MV Delta', vesselId: 'IMO4' }),
      ],
      3,
    );
    expect(result).toEqual([
      { vesselName: 'MV Alpha', vesselId: 'IMO1', count: 2 },
      { vesselName: 'MV Bravo', vesselId: 'IMO2', count: 2 },
      { vesselName: 'MV Charlie', vesselId: 'IMO3', count: 1 },
    ]);
  });
});

describe('visitsPerMonth', () => {
  it('produces the requested number of months ending at the latest ETA, includes zero-count months', () => {
    const visits = [
      v({ eta: '2026-04-10T08:00:00Z' }),
      v({ eta: '2026-04-15T08:00:00Z' }),
      v({ eta: '2026-06-01T08:00:00Z' }),
      v({ eta: '' }),
    ];
    const result = visitsPerMonth(visits, 3);
    expect(result).toEqual([
      { yearMonth: '2026-04', count: 2 },
      { yearMonth: '2026-05', count: 0 },
      { yearMonth: '2026-06', count: 1 },
    ]);
  });
});

describe('averageDwellDays + onTimeRate', () => {
  it('averages from visits with both ATA and ATD only; null when none qualify', () => {
    expect(averageDwellDays([])).toBeNull();
    expect(averageDwellDays([v({ ata: '', atd: '' })])).toBeNull();
    const result = averageDwellDays([
      v({ ata: '2026-01-01T00:00:00Z', atd: '2026-01-03T00:00:00Z' }),
      v({ ata: '2026-02-01T00:00:00Z', atd: '2026-02-04T00:00:00Z' }),
    ]);
    expect(result).toBeCloseTo(2.5, 2);
  });

  it('on-time rate is null when no completed visits, otherwise % within tolerance', () => {
    expect(onTimeRate([])).toBeNull();
    expect(onTimeRate([v({ eta: '2026-01-01T00:00:00Z', ata: '' })])).toBeNull();
    const result = onTimeRate(
      [
        v({ eta: '2026-01-01T00:00:00Z', ata: '2026-01-01T01:00:00Z' }),
        v({ eta: '2026-01-02T00:00:00Z', ata: '2026-01-02T05:00:00Z' }),
        v({ eta: '2026-01-03T00:00:00Z', ata: '2026-01-03T01:30:00Z' }),
      ],
      2,
    );
    expect(result).toBeCloseTo(2 / 3, 2);
  });
});

describe('computeDwell', () => {
  it('returns null fields when timestamps are missing, populated values when present', () => {
    expect(computeDwell(v({}))).toEqual({
      plannedDays: null,
      actualDays: null,
      deltaHours: null,
      etaAtaDeltaHours: null,
      etdAtdDeltaHours: null,
    });
    const result = computeDwell(
      v({
        eta: '2026-01-01T00:00:00Z',
        ata: '2026-01-01T01:30:00Z',
        etd: '2026-01-03T00:00:00Z',
        atd: '2026-01-03T10:00:00Z',
      }),
    );
    expect(result.plannedDays).toBeCloseTo(2, 2);
    expect(result.actualDays).toBeCloseTo(2 + 8.5 / 24, 2);
    expect(result.deltaHours).toBeCloseTo(8.5, 2);
    expect(result.etaAtaDeltaHours).toBeCloseTo(1.5, 2);
    expect(result.etdAtdDeltaHours).toBeCloseTo(10, 2);
  });
});
