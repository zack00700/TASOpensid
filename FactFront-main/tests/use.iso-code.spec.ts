import { describe, it, expect, vi, beforeEach } from 'vitest';
import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { useIsoCode } from '../src/composables/use.iso-code';
import type { IsoContainerCode } from '../src/types/iso-code';

const axiosMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
};

function harness() {
  let api: ReturnType<typeof useIsoCode> | null = null;
  const Comp = defineComponent({
    setup() {
      api = useIsoCode();
      return () => h('div');
    },
  });
  mount(Comp, { global: { provide: { $axios: axiosMock } } });
  return api!;
}

const sampleCode: IsoContainerCode = {
  code: '22G1',
  description: '20\' GP',
  lengthFt: 20,
  heightFt: 8.5,
  typeGroup: 'G',
  isReefer: false,
  isHazmatCapable: false,
  isTank: false,
  isOpenTop: false,
  isStandard: true,
  isActive: true,
  archetypeId: null,
  tareKg: 2300,
  maxPayloadKg: 28180,
  maxGrossKg: 30480,
};

describe('useIsoCode', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.post.mockReset();
    axiosMock.put.mockReset();
    axiosMock.delete.mockReset();
  });

  it('getAll populates the isoCodes ref', async () => {
    const api = harness();
    axiosMock.get.mockResolvedValueOnce({ status: 200, data: [sampleCode] });
    await api.getAll();
    expect(axiosMock.get).toHaveBeenCalledWith('iso-codes', { params: { includeInactive: false } });
    expect(api.isoCodes.value).toEqual([sampleCode]);
  });

  it('getAll forwards includeInactive=true when requested', async () => {
    const api = harness();
    axiosMock.get.mockResolvedValueOnce({ status: 200, data: [] });
    await api.getAll(true);
    expect(axiosMock.get).toHaveBeenCalledWith('iso-codes', { params: { includeInactive: true } });
  });

  it('create POSTs the payload and returns the created code', async () => {
    const api = harness();
    axiosMock.post.mockResolvedValueOnce({ status: 201, data: sampleCode });
    const result = await api.create(sampleCode);
    expect(axiosMock.post).toHaveBeenCalledWith('iso-codes', sampleCode);
    expect(result).toEqual(sampleCode);
  });

  it('update PUTs to the code path and returns the updated code', async () => {
    const api = harness();
    const updated = { ...sampleCode, description: 'Edited' };
    axiosMock.put.mockResolvedValueOnce({ status: 200, data: updated });
    const result = await api.update('22G1', updated);
    expect(axiosMock.put).toHaveBeenCalledWith('iso-codes/22G1', updated);
    expect(result).toEqual(updated);
  });
});
