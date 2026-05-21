import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import Vessels from '../src/components/Vessels.vue';
import type { Vessel } from '../src/types/vessel';
import type { VesselAisSnapshot } from '../src/types/ais';
import { i18n } from '../src/i18n';

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

const fixtures = {
  vessels: [
    makeVessel({ id: 'v-live', name: 'MV Live', mmsi: '111111111' }),
    makeVessel({ id: 'v-no-mmsi', name: 'MV NoMmsi', mmsi: '' }),
  ],
  liveSnapshot: <VesselAisSnapshot>{
    mmsi: '111111111',
    lat: 14.6841,
    lon: -17.4258,
    sog: 8.4,
    lastSeen: new Date().toISOString(),
  },
  health: { connected: true, lastMessageAt: new Date().toISOString(), snapshotCount: 1 },
};

const axiosMock = {
  get: vi.fn(),
  post: vi.fn().mockResolvedValue({ data: {} }),
  put: vi.fn().mockResolvedValue({ data: {} }),
};

function mountPage() {
  return mount(Vessels, {
    global: {
      plugins: [i18n],
      provide: { $axios: axiosMock },
      stubs: {
        AdvancedFilter: true,
        SearchInput: true,
      },
    },
  });
}

describe('Vessels — AIS enrichment', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.get.mockImplementation((url: string) => {
      if (url === '/vessel') return Promise.resolve({ status: 200, data: fixtures.vessels });
      if (url === 'ais/health') return Promise.resolve({ status: 200, data: fixtures.health });
      if (url === 'ais/by-vessel/v-live') return Promise.resolve({ status: 200, data: fixtures.liveSnapshot });
      if (url === 'ais/by-vessel/v-no-mmsi') return Promise.resolve({ status: 204, data: '' });
      return Promise.reject(new Error('unexpected url ' + url));
    });
  });

  it('renders the OpenSeaMap link and live badge for a vessel with a fresh snapshot', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const live = wrapper.find('[data-test="row-v-live"]');
    expect(live.exists()).toBe(true);
    const link = live.find('a[href*="map.openseamap.org"]');
    expect(link.exists()).toBe(true);
    expect(link.attributes('href')).toContain('lat=14.6841');
    expect(link.attributes('href')).toContain('lon=-17.4258');
    expect(live.text()).toContain('AIS live');
  });

  it('renders the no-MMSI badge for a vessel without an MMSI and skips position', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const nm = wrapper.find('[data-test="row-v-no-mmsi"]');
    expect(nm.text()).toContain('No MMSI');
    expect(nm.find('a[href*="map.openseamap.org"]').exists()).toBe(false);
  });

  it('does NOT call /api/ais/by-vessel for a vessel without MMSI', async () => {
    mountPage();
    await flushPromises();
    const calls = axiosMock.get.mock.calls.map(c => c[0]);
    expect(calls).toContain('ais/by-vessel/v-live');
    expect(calls).not.toContain('ais/by-vessel/v-no-mmsi');
  });

  it('renders the AIS-offline banner when health.connected is false', async () => {
    axiosMock.get.mockImplementation((url: string) => {
      if (url === '/vessel') return Promise.resolve({ status: 200, data: [] });
      if (url === 'ais/health') return Promise.resolve({ status: 200, data: { connected: false, lastMessageAt: null, snapshotCount: 0 } });
      return Promise.resolve({ status: 204, data: '' });
    });
    const wrapper = mountPage();
    await flushPromises();
    expect(wrapper.find('[data-test="ais-offline-banner"]').exists()).toBe(true);
  });

  it('Refresh button re-runs vessels + snapshots + health calls', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const baselineCalls = axiosMock.get.mock.calls.length;
    await wrapper.find('[data-test="vessels-refresh"]').trigger('click');
    await flushPromises();
    expect(axiosMock.get.mock.calls.length - baselineCalls).toBeGreaterThanOrEqual(3);
  });
});
