import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ref } from 'vue';
import { mount } from '@vue/test-utils';
import VesselVisitForm from '../src/components/VesselVisitForm.vue';
import type { VesselVisit } from '../src/types/vessel-visit';
import { i18n } from '../src/i18n';

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({
    thirdParties: ref([]),
    createMinimal: vi.fn(),
  }),
}));
vi.mock('../src/stores/authStore', () => ({ useAuthStore: () => ({ isAdmin: () => false }) }));

const axiosMock = {
  get: vi.fn().mockResolvedValue({ data: [] }),
  post: vi.fn().mockResolvedValue({ data: {} }),
  put: vi.fn().mockResolvedValue({ data: {} }),
};

function makeVisit(overrides: Partial<VesselVisit> = {}): VesselVisit {
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

describe('VesselVisitForm — edit mode', () => {
  beforeEach(() => {
    axiosMock.put.mockReset();
    axiosMock.post.mockReset();
    axiosMock.put.mockResolvedValue({ data: {} });
    axiosMock.post.mockResolvedValue({ data: {} });
  });

  it('hydrates formData from initialData when editMode is true', async () => {
    const wrapper = mountForm({ editMode: true, initialData: makeVisit() });
    await wrapper.vm.$nextTick();
    const fd = (wrapper.vm as any).formData;
    expect(fd.vesselName).toBe('MV Hydrate');
    expect(fd.pol).toBe('USNYC');
    expect(fd.service).toBe('WCCA');
  });

  it('uses PUT (not POST) on submit when editMode + initialData.id are present', async () => {
    const wrapper = mountForm({ editMode: true, initialData: makeVisit({ id: 'v-9' }) });
    await wrapper.vm.$nextTick();
    await wrapper.find('form').trigger('submit.prevent');
    await wrapper.vm.$nextTick();
    expect(axiosMock.put).toHaveBeenCalledWith('visit/v-9', expect.objectContaining({ vesselName: 'MV Hydrate' }));
    expect(axiosMock.post).not.toHaveBeenCalled();
  });

  it('falls back to POST when not in editMode', async () => {
    const wrapper = mountForm({ editMode: false });
    // populate the minimum required fields to pass validateForm
    Object.assign((wrapper.vm as any).formData, makeVisit({ id: undefined }));
    await wrapper.vm.$nextTick();
    await wrapper.find('form').trigger('submit.prevent');
    await wrapper.vm.$nextTick();
    expect(axiosMock.post).toHaveBeenCalledWith('visit', expect.any(Object));
    expect(axiosMock.put).not.toHaveBeenCalled();
  });

  it('does NOT fire any HTTP call when validation fails', async () => {
    const wrapper = mountForm({ editMode: false });
    // formData starts empty → validateForm should fail (missing vesselName, etc.)
    await wrapper.find('form').trigger('submit.prevent');
    await wrapper.vm.$nextTick();
    expect(axiosMock.post).not.toHaveBeenCalled();
    expect(axiosMock.put).not.toHaveBeenCalled();
  });

  it('emits "submit" with formData after a successful edit', async () => {
    const wrapper = mountForm({ editMode: true, initialData: makeVisit({ id: 'v-9' }) });
    await wrapper.vm.$nextTick();
    await wrapper.find('form').trigger('submit.prevent');
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted('submit')).toBeTruthy();
    expect(wrapper.emitted('submit')![0][0]).toMatchObject({ vesselName: 'MV Hydrate' });
  });

  it('re-hydrates formData when initialData prop changes; id never leaks into formData', async () => {
    const wrapper = mountForm({
      editMode: true,
      initialData: makeVisit({ id: 'v-1', vesselName: 'Alpha' }),
    });
    await wrapper.vm.$nextTick();
    expect((wrapper.vm as any).formData.vesselName).toBe('Alpha');

    await wrapper.setProps({ initialData: makeVisit({ id: 'v-2', vesselName: 'Beta' }) });
    await wrapper.vm.$nextTick();
    expect((wrapper.vm as any).formData.vesselName).toBe('Beta');
    expect((wrapper.vm as any).formData.id).toBeUndefined();
  });
});
