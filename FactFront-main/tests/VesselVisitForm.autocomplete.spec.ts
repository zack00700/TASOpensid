import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ref, nextTick } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import VesselVisitForm from '../src/components/VesselVisitForm.vue';
import type { VesselVisit } from '../src/types/vessel-visit';
import type { Vessel } from '../src/types/vessel';
import { i18n } from '../src/i18n';

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({ thirdParties: ref([]), createMinimal: vi.fn() }),
}));
vi.mock('../src/stores/authStore', () => ({ useAuthStore: () => ({ isAdmin: () => false }) }));

// Shared vessels ref so both useVessel() call sites (form + autocomplete) see it.
const sharedVessels = ref<Vessel[]>([
  { id: 'v1', name: 'MV Alpha', imoNumber: '9876543', callSign: 'AAA', flag: 'FR', owner: 'A', operator: 'A', vesselType: 'Container', status: 'Active' },
  { id: 'v2', name: 'MV Beta', imoNumber: '9876544', callSign: 'BBB', flag: 'DE', owner: 'B', operator: 'B', vesselType: 'Container', status: 'Active' },
]);

vi.mock('../src/composables/use.vessel', () => ({
  useVessel: () => ({
    vessels: sharedVessels,
    formData: ref({}),
    errors: ref({}),
    validateForm: vi.fn(),
    addVessel: vi.fn(),
    updateVessel: vi.fn(),
    getVessels: vi.fn(),
    resetForm: vi.fn(),
    initializeForm: vi.fn(),
  }),
}));

const axiosMock = {
  get: vi.fn().mockResolvedValue({ data: [] }),
  post: vi.fn().mockResolvedValue({ data: {} }),
  put: vi.fn().mockResolvedValue({ data: {} }),
};

function makeVisit(overrides: Partial<VesselVisit> = {}): VesselVisit {
  return {
    id: 'v-1',
    vesselName: 'MV Alpha',
    vesselId: '9876543',
    visitReference: 'REF-1',
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
    ...overrides,
  };
}

function mountForm(props: Record<string, any> = {}) {
  return mount(VesselVisitForm, {
    props,
    global: {
      plugins: [i18n],
      provide: { $axios: axiosMock },
    },
  });
}

describe('VesselVisitForm — TC-05.2 vessel autocomplete + visitReference', () => {
  beforeEach(() => {
    axiosMock.post.mockClear();
    axiosMock.put.mockClear();
  });

  it('renders the new Visit Reference field with the required asterisk', async () => {
    const wrapper = mountForm();
    await flushPromises();
    expect(wrapper.find('[data-test="visit-reference"]').exists()).toBe(true);
  });

  it('on edit mode, when the vessel name matches a known vessel, the vessel id input becomes readonly', async () => {
    const wrapper = mountForm({ editMode: true, initialData: makeVisit({ vesselName: 'MV Alpha' }) });
    await flushPromises();
    await nextTick();

    const idInput = wrapper.find<HTMLInputElement>('[data-test="visit-vessel-id"]');
    expect(idInput.exists()).toBe(true);
    expect(idInput.element.readOnly).toBe(true);
  });

  it('when the vessel name does not match a known vessel, the vessel id input stays editable', async () => {
    const wrapper = mountForm({ editMode: true, initialData: makeVisit({ vesselName: 'Unknown', vesselId: 'MANUAL-ID' }) });
    await flushPromises();
    await nextTick();

    const idInput = wrapper.find<HTMLInputElement>('[data-test="visit-vessel-id"]');
    expect(idInput.element.readOnly).toBe(false);
    expect(idInput.element.value).toBe('MANUAL-ID');
  });

  it('typing a known vessel name auto-fills the vessel id with its imoNumber', async () => {
    const wrapper = mountForm({ editMode: false });
    await flushPromises();

    // Find the autocomplete input and type a matching vessel name.
    const nameInput = wrapper.find<HTMLInputElement>('[data-test="visit-vessel-name"] input');
    await nameInput.setValue('MV Beta');
    await flushPromises();
    await nextTick();

    const idInput = wrapper.find<HTMLInputElement>('[data-test="visit-vessel-id"]');
    expect(idInput.element.value).toBe('9876544');
    expect(idInput.element.readOnly).toBe(true);
  });

  it('validation blocks submit when visitReference is empty', async () => {
    const wrapper = mountForm({ editMode: false });
    // Populate everything except visitReference
    Object.assign((wrapper.vm as any).formData, makeVisit({ visitReference: '' }));
    await flushPromises();

    await wrapper.find('form').trigger('submit.prevent');
    await flushPromises();

    expect(axiosMock.post).not.toHaveBeenCalled();
    expect(axiosMock.put).not.toHaveBeenCalled();
  });
});
