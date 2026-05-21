import { describe, it, expect, vi, beforeEach } from 'vitest';
import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { useContainerArchetype } from '../src/composables/use.container-archetype';
import type { ContainerArchetype } from '../src/types/container-archetype';

const axiosMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
};

function harness() {
  let api: ReturnType<typeof useContainerArchetype> | null = null;
  const Comp = defineComponent({
    setup() {
      api = useContainerArchetype();
      return () => h('div');
    },
  });
  mount(Comp, { global: { provide: { $axios: axiosMock } } });
  return api!;
}

const sampleArchetype: ContainerArchetype = {
  id: 'arch-1',
  code: 'DRY_20',
  name: '20\' Dry Container',
  description: 'Standard 20-foot dry container',
  isActive: true,
};

describe('useContainerArchetype', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.post.mockReset();
    axiosMock.put.mockReset();
    axiosMock.delete.mockReset();
  });

  it('getAll populates the archetypes ref', async () => {
    const api = harness();
    axiosMock.get.mockResolvedValueOnce({ status: 200, data: [sampleArchetype] });
    await api.getAll();
    expect(axiosMock.get).toHaveBeenCalledWith('archetypes');
    expect(api.archetypes.value).toEqual([sampleArchetype]);
  });

  it('create POSTs the payload', async () => {
    const api = harness();
    axiosMock.post.mockResolvedValueOnce({ status: 201, data: sampleArchetype });
    const result = await api.create(sampleArchetype);
    expect(axiosMock.post).toHaveBeenCalledWith('archetypes', sampleArchetype);
    expect(result).toEqual(sampleArchetype);
  });

  it('update PUTs to the id path', async () => {
    const api = harness();
    const updated = { ...sampleArchetype, name: 'Edited' };
    axiosMock.put.mockResolvedValueOnce({ status: 200, data: updated });
    const result = await api.update('arch-1', updated);
    expect(axiosMock.put).toHaveBeenCalledWith('archetypes/arch-1', updated);
    expect(result).toEqual(updated);
  });

  it('getIsoCodesFor calls GET /archetypes/{id}/iso-codes and returns the list', async () => {
    const api = harness();
    axiosMock.get.mockResolvedValueOnce({ status: 200, data: [] });
    const result = await api.getIsoCodesFor('arch-1');
    expect(axiosMock.get).toHaveBeenCalledWith('archetypes/arch-1/iso-codes');
    expect(result).toEqual([]);
  });
});
