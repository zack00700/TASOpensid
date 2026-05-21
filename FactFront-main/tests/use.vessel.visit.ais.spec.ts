import { describe, it, expect, vi, beforeEach } from 'vitest';
import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { useVesselVisitAis } from '../src/composables/use.vessel.visit.ais';
import type { AisSuggestion } from '../src/types/ais';

const axiosMock = {
  get: vi.fn(),
};

function harness() {
  let api: ReturnType<typeof useVesselVisitAis> | null = null;
  const Comp = defineComponent({
    setup() {
      api = useVesselVisitAis();
      return () => h('div');
    },
  });
  mount(Comp, { global: { provide: { $axios: axiosMock } } });
  return api!;
}

const fullSuggestion: AisSuggestion = {
  suggestedEta: '2026-05-12T14:30:00Z',
  suggestedAta: null,
  sourceTimestamp: '2026-05-09T07:00:00Z',
  navStatus: 0,
  position: { lat: 14.6841, lon: -17.4258 },
};

describe('useVesselVisitAis', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
  });

  it('loadFor(undefined) does NOT call axios and leaves suggestion null', async () => {
    const api = harness();
    await api.loadFor(undefined);
    expect(axiosMock.get).not.toHaveBeenCalled();
    expect(api.suggestion.value).toBeNull();
  });

  it('loadFor handles 204 No Content as null', async () => {
    const api = harness();
    axiosMock.get.mockResolvedValueOnce({ status: 204, data: '' });
    await api.loadFor('v-7');
    expect(axiosMock.get).toHaveBeenCalledWith('ais/by-visit/v-7');
    expect(api.suggestion.value).toBeNull();
  });

  it('loadFor populates suggestion with the returned DTO', async () => {
    const api = harness();
    axiosMock.get.mockResolvedValueOnce({ status: 200, data: fullSuggestion });
    await api.loadFor('v-7');
    expect(api.suggestion.value).toEqual(fullSuggestion);
  });
});
