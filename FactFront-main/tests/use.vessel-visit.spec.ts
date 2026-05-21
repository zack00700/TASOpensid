import { describe, it, expect, vi, beforeEach } from 'vitest';
import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { useVesselVisit } from '../src/composables/use.vessel-visit';
import type { VesselVisit } from '../src/types/vessel-visit';

const axiosMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
};

function makeVisit(overrides: Partial<VesselVisit> = {}): VesselVisit {
  return {
    id: 'v-1',
    vesselName: 'MV Test',
    vesselId: 'IMO123',
    visitReference: 'REF-1',
    phase: 'Active',
    service: 'WCCA',
    serviceName: 'West Coast',
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

// Harness component so we can call useVesselVisit() inside a Vue setup() with $axios provided.
function harness() {
  let api: ReturnType<typeof useVesselVisit> | null = null;
  const Comp = defineComponent({
    setup() {
      api = useVesselVisit();
      return () => h('div');
    },
  });
  mount(Comp, { global: { provide: { $axios: axiosMock } } });
  return api!;
}

describe('useVesselVisit', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.post.mockReset();
    axiosMock.put.mockReset();
    axiosMock.get.mockResolvedValue({ data: [] });
    axiosMock.post.mockResolvedValue({ data: {} });
    axiosMock.put.mockResolvedValue({ data: {} });
  });

  it('exposes getVesselVisits, updateVesselVisit, addVesselVisit', () => {
    const api = harness();
    expect(typeof api.getVesselVisits).toBe('function');
    expect(typeof api.updateVesselVisit).toBe('function');
    expect(typeof api.addVesselVisit).toBe('function');
  });

  it('updateVesselVisit issues PUT visit/{id} with the current formData', async () => {
    const api = harness();
    const v = makeVisit({ id: 'v-42', vesselName: 'Updated Name' });
    Object.assign(api.formData.value, v);

    await api.updateVesselVisit('v-42');

    expect(axiosMock.put).toHaveBeenCalledTimes(1);
    expect(axiosMock.put).toHaveBeenCalledWith('visit/v-42', expect.objectContaining({ vesselName: 'Updated Name' }));
  });

  it('updateVesselVisit swallows errors via console.error (matches addVesselVisit precedent)', async () => {
    const api = harness();
    axiosMock.put.mockRejectedValueOnce(new Error('network down'));
    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await expect(api.updateVesselVisit('v-1')).resolves.toBeUndefined();
    expect(errSpy).toHaveBeenCalled();
    errSpy.mockRestore();
  });
});
