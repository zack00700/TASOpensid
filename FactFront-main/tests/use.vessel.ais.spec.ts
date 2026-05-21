import { describe, it, expect, vi, beforeEach } from 'vitest';
import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { useVesselAis } from '../src/composables/use.vessel.ais';
import type { Vessel } from '../src/types/vessel';
import type { VesselAisSnapshot } from '../src/types/ais';

const axiosMock = {
  get: vi.fn(),
};

function harness() {
  let api: ReturnType<typeof useVesselAis> | null = null;
  const Comp = defineComponent({
    setup() {
      api = useVesselAis();
      return () => h('div');
    },
  });
  mount(Comp, { global: { provide: { $axios: axiosMock } } });
  return api!;
}

function makeVessel(over: Partial<Vessel> = {}): Vessel {
  return {
    id: 'v-1',
    name: 'MV Alpha',
    imoNumber: 'IMO1234567',
    mmsi: '211281000',
    callSign: 'TEST',
    flag: 'FR',
    owner: 'Owner',
    operator: 'Operator',
    vesselType: 'Container Ship',
    status: 'Active',
    ...over,
  };
}

function makeSnap(over: Partial<VesselAisSnapshot> = {}): VesselAisSnapshot {
  return {
    mmsi: '211281000',
    lastSeen: new Date().toISOString(),
    ...over,
  };
}

describe('useVesselAis', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
  });

  it('statusFor returns "no-mmsi" when vessel.mmsi is empty, null, or whitespace', () => {
    const api = harness();
    expect(api.statusFor(makeVessel({ mmsi: '' }))).toBe('no-mmsi');
    expect(api.statusFor(makeVessel({ mmsi: undefined }))).toBe('no-mmsi');
    expect(api.statusFor(makeVessel({ mmsi: '   ' }))).toBe('no-mmsi');
  });

  it('statusFor returns "live" when snapshot is fresher than 5 min', () => {
    const api = harness();
    const now = Date.parse('2026-05-08T10:00:00Z');
    const snap = makeSnap({ lastSeen: '2026-05-08T09:57:00Z' });
    api.snapshots.value.set('v-1', snap);
    expect(api.statusFor(makeVessel(), now)).toBe('live');
  });

  it('statusFor returns "lost" when snapshot is older than 5 min', () => {
    const api = harness();
    const now = Date.parse('2026-05-08T10:00:00Z');
    const snap = makeSnap({ lastSeen: '2026-05-08T09:50:00Z' });
    api.snapshots.value.set('v-1', snap);
    expect(api.statusFor(makeVessel(), now)).toBe('lost');
  });

  it('statusFor returns "lost" when MMSI is set but no snapshot was loaded', () => {
    const api = harness();
    expect(api.statusFor(makeVessel())).toBe('lost');
  });

  it('loadSnapshotsForVessels skips vessels without MMSI (no axios call)', async () => {
    const api = harness();
    axiosMock.get.mockResolvedValue({ status: 200, data: makeSnap() });
    const vessels = [
      makeVessel({ id: 'v-1', mmsi: '' }),
      makeVessel({ id: 'v-2', mmsi: undefined }),
    ];
    await api.loadSnapshotsForVessels(vessels);
    expect(axiosMock.get).not.toHaveBeenCalled();
    expect(api.snapshots.value.size).toBe(0);
  });

  it('loadSnapshotsForVessels treats 204 No Content as null', async () => {
    const api = harness();
    axiosMock.get.mockResolvedValueOnce({ status: 204, data: '' });
    await api.loadSnapshotsForVessels([makeVessel({ id: 'v-7' })]);
    expect(axiosMock.get).toHaveBeenCalledWith('ais/by-vessel/v-7');
    expect(api.snapshots.value.get('v-7')).toBeNull();
  });
});
