import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ref } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import VesselVisitForm from '../src/components/VesselVisitForm.vue';
import type { VesselVisit } from '../src/types/vessel-visit';
import type { AisSuggestion } from '../src/types/ais';
import { i18n } from '../src/i18n';

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({
    thirdParties: ref([]),
    createMinimal: vi.fn(),
  }),
}));
vi.mock('../src/stores/authStore', () => ({ useAuthStore: () => ({ isAdmin: () => false }) }));

function makeVisit(over: Partial<VesselVisit> = {}): VesselVisit {
  return {
    id: 'v-7',
    vesselName: 'MV Hydrate',
    vesselId: 'IMO777',
    visitReference: 'REF-7',
    phase: 'Active',
    service: 'WCCA',
    serviceName: 'West Coast Central America',
    facility: 'Terminal A',
    eta: '2026-05-10T08:00',
    etd: '2026-05-11T08:00',
    ata: '',
    atd: '',
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

const etaSuggestion: AisSuggestion = {
  suggestedEta: '2026-05-12T14:30:00Z',
  suggestedAta: null,
  sourceTimestamp: '2026-05-09T07:00:00Z',
  navStatus: 0,
  position: null,
};

const fullSuggestion: AisSuggestion = {
  suggestedEta: '2026-05-12T14:30:00Z',
  suggestedAta: '2026-05-09T07:00:00Z',
  sourceTimestamp: '2026-05-09T07:00:00Z',
  navStatus: 5,
  position: { lat: 14.6841, lon: -17.4258 },
};

const ataOnlySuggestion: AisSuggestion = {
  suggestedEta: null,
  suggestedAta: '2026-05-09T07:00:00Z',
  sourceTimestamp: '2026-05-09T07:00:00Z',
  navStatus: 5,
  position: null,
};

const axiosMock = {
  get: vi.fn(),
  post: vi.fn().mockResolvedValue({ data: {} }),
  put: vi.fn().mockResolvedValue({ data: {} }),
};

function mountForm(props: Record<string, any>, suggestion: AisSuggestion | null) {
  axiosMock.get.mockImplementation((url: string) => {
    if (url.startsWith('ais/by-visit/')) {
      return suggestion === null
        ? Promise.resolve({ status: 204, data: '' })
        : Promise.resolve({ status: 200, data: suggestion });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });
  return mount(VesselVisitForm, {
    props,
    global: {
      plugins: [i18n],
      provide: { $axios: axiosMock },
    },
  });
}

describe('VesselVisitForm — AIS suggestions', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.post.mockReset();
    axiosMock.put.mockReset();
    axiosMock.post.mockResolvedValue({ data: {} });
    axiosMock.put.mockResolvedValue({ data: {} });
  });

  it('renders the ETA badge when suggestedEta is non-null', async () => {
    const wrapper = mountForm({ editMode: true, initialData: makeVisit() }, etaSuggestion);
    await flushPromises();
    const badge = wrapper.find('[data-test="ais-suggestion-eta"]');
    expect(badge.exists()).toBe(true);
    expect(badge.text()).toContain('AIS suggests ETA');
  });

  it('clicking [Apply] on the ETA badge populates formData.eta and hides the badge', async () => {
    const wrapper = mountForm({ editMode: true, initialData: makeVisit() }, etaSuggestion);
    await flushPromises();
    await wrapper.find('[data-test="ais-suggestion-eta-apply"]').trigger('click');
    await wrapper.vm.$nextTick();
    const fd = (wrapper.vm as any).formData;
    expect(fd.eta).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(wrapper.find('[data-test="ais-suggestion-eta"]').exists()).toBe(false);
  });

  it('renders no badge when the API returns 204 (no MMSI / no snapshot)', async () => {
    const wrapper = mountForm({ editMode: true, initialData: makeVisit() }, null);
    await flushPromises();
    expect(wrapper.find('[data-test="ais-suggestion-eta"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="ais-suggestion-ata"]').exists()).toBe(false);
  });

  it('renders the ATA badge only when suggestedAta is non-null', async () => {
    const wrapperA = mountForm({ editMode: true, initialData: makeVisit() }, fullSuggestion);
    await flushPromises();
    expect(wrapperA.find('[data-test="ais-suggestion-eta"]').exists()).toBe(true);
    expect(wrapperA.find('[data-test="ais-suggestion-ata"]').exists()).toBe(true);

    const wrapperB = mountForm({ editMode: true, initialData: makeVisit() }, ataOnlySuggestion);
    await flushPromises();
    expect(wrapperB.find('[data-test="ais-suggestion-eta"]').exists()).toBe(false);
    expect(wrapperB.find('[data-test="ais-suggestion-ata"]').exists()).toBe(true);
  });
});
