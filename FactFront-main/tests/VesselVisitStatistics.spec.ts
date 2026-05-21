import { describe, it, expect, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createMemoryHistory } from 'vue-router';
import VesselVisitStatistics from '../src/components/VesselVisitStatistics.vue';
import type { VesselVisit } from '../src/types/vessel-visit';
import { i18n } from '../src/i18n';

function makeVisit(over: Partial<VesselVisit> = {}): VesselVisit {
  return {
    id: Math.random().toString(36).slice(2, 8),
    vesselName: 'MV A',
    vesselId: 'IMO1',
    visitReference: 'REF',
    phase: 'Active',
    service: 'WCCA',
    serviceName: 'West Coast',
    facility: 'F',
    eta: '2026-04-10T08:00:00Z',
    etd: '2026-04-12T08:00:00Z',
    ata: '2026-04-10T09:00:00Z',
    atd: '2026-04-12T18:00:00Z',
    pod: 'FRLEH',
    pol: 'USNYC',
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

const visitsFixture = [
  makeVisit({ phase: 'Active' }),
  makeVisit({ phase: 'Completed' }),
];

const axiosMock = {
  get: vi.fn().mockResolvedValue({ data: visitsFixture }),
};

async function mountStats(query: Record<string, string> = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/vessels/statistics', component: VesselVisitStatistics }],
  });
  await router.push({ path: '/vessels/statistics', query });
  await router.isReady();
  return mount(VesselVisitStatistics, {
    global: {
      plugins: [i18n, router],
      provide: { $axios: axiosMock },
      stubs: { Pie: true, Bar: true, RouterLink: true },
    },
  });
}

describe('VesselVisitStatistics — KPIs', () => {
  it('renders the 4 KPI cards with computed values from visits.value', async () => {
    axiosMock.get.mockResolvedValueOnce({ data: visitsFixture });
    const wrapper = await mountStats();
    await flushPromises();

    expect(wrapper.text()).toContain('Total visits');
    expect(wrapper.text()).toContain('Active');
    expect(wrapper.text()).toContain('Avg dwell (days)');
    expect(wrapper.text()).toContain('On-time rate');
    expect(wrapper.find('[data-test="kpi-active-visits"]').text()).toContain('1');
  });
});

describe('VesselVisitStatistics — drill-down + empty state', () => {
  it('renders drill-down panel when ?visit=<id> matches a visit', async () => {
    const target = makeVisit({ id: 'pinned', vesselName: 'MV Pinned' });
    axiosMock.get.mockResolvedValueOnce({ data: [target, makeVisit()] });
    const wrapper = await mountStats({ visit: 'pinned' });
    await flushPromises();

    expect(wrapper.find('[data-test="drill-down"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('MV Pinned');
    expect(wrapper.text()).toContain('Selected visit');
  });

  it('renders empty state when visits.value is empty', async () => {
    axiosMock.get.mockResolvedValueOnce({ data: [] });
    const wrapper = await mountStats();
    await flushPromises();

    expect(wrapper.text()).toContain('No visits available');
    expect(wrapper.find('[data-test="kpi-total-visits"]').exists()).toBe(false);
  });
});
