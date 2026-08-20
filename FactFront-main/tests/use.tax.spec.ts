import { describe, it, expect, vi, beforeEach } from 'vitest';
import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { useTax } from '../src/composables/use.tax';
import type { Tax } from '../src/types/tax';

const axiosMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
};

function harness() {
  let api: ReturnType<typeof useTax> | null = null;
  const Comp = defineComponent({
    setup() {
      api = useTax();
      return () => h('div');
    },
  });
  mount(Comp, { global: { provide: { $axios: axiosMock } } });
  return api!;
}

const sampleTax: Tax = {
  id: 'tax-1',
  code: 'TVA20',
  name: 'TVA 20 %',
  type: 'PERCENTAGE',
  rate: 20,
  validFrom: '2024-01-01T00:00:00Z',
  validTo: null,
  isActive: true,
};

describe('useTax', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.post.mockReset();
    axiosMock.put.mockReset();
    axiosMock.delete.mockReset();
  });

  it('getAll GETs /taxes and stores the result', async () => {
    const api = harness();
    axiosMock.get.mockResolvedValueOnce({ data: [sampleTax] });
    await api.getAll();
    expect(axiosMock.get).toHaveBeenCalledWith('taxes');
    expect(api.taxes.value).toEqual([sampleTax]);
  });

  it('getAll surfaces network errors into errors.network', async () => {
    const api = harness();
    axiosMock.get.mockRejectedValueOnce(new Error('boom'));
    await api.getAll();
    expect(api.errors.value.network).toBe('boom');
  });

  it('getOne GETs /taxes/{id} and returns the body', async () => {
    const api = harness();
    axiosMock.get.mockResolvedValueOnce({ data: sampleTax });
    const got = await api.getOne('tax-1');
    expect(axiosMock.get).toHaveBeenCalledWith('taxes/tax-1');
    expect(got).toEqual(sampleTax);
  });

  it('getOne returns null on 404', async () => {
    const api = harness();
    axiosMock.get.mockRejectedValueOnce({ response: { status: 404 } });
    const got = await api.getOne('missing');
    expect(got).toBeNull();
  });

  it('create POSTs /taxes with payload', async () => {
    const api = harness();
    axiosMock.post.mockResolvedValueOnce({ data: sampleTax });
    const created = await api.create({
      code: 'TVA20',
      name: 'TVA 20 %',
      type: 'PERCENTAGE',
      rate: 20,
      isActive: true,
    });
    expect(axiosMock.post).toHaveBeenCalledWith('taxes', expect.objectContaining({ code: 'TVA20', rate: 20 }));
    expect(created).toEqual(sampleTax);
  });

  it('create captures backend error code into errors.create', async () => {
    const api = harness();
    axiosMock.post.mockRejectedValueOnce({ response: { data: { error: 'duplicate_code' } } });
    const created = await api.create({ code: 'TVA20', name: 'x', type: 'PERCENTAGE', rate: 0 });
    expect(created).toBeNull();
    expect(api.errors.value.create).toBe('duplicate_code');
  });

  it('update PUTs /taxes/{id}', async () => {
    const api = harness();
    axiosMock.put.mockResolvedValueOnce({ data: { ...sampleTax, rate: 19.6 } });
    const updated = await api.update('tax-1', { ...sampleTax, rate: 19.6 });
    expect(axiosMock.put).toHaveBeenCalledWith('taxes/tax-1', expect.objectContaining({ rate: 19.6 }));
    expect(updated?.rate).toBe(19.6);
  });

  it('remove DELETEs /taxes/{id} and returns true on success', async () => {
    const api = harness();
    axiosMock.delete.mockResolvedValueOnce({ status: 204 });
    const ok = await api.remove('tax-1');
    expect(axiosMock.delete).toHaveBeenCalledWith('taxes/tax-1');
    expect(ok).toBe(true);
  });

  it('remove returns false on failure', async () => {
    const api = harness();
    axiosMock.delete.mockRejectedValueOnce({ response: { data: { error: 'not_found' } } });
    const ok = await api.remove('tax-1');
    expect(ok).toBe(false);
    expect(api.errors.value.delete).toBe('not_found');
  });

  it('url-encodes the id on get/update/remove', async () => {
    const api = harness();
    axiosMock.get.mockResolvedValueOnce({ data: sampleTax });
    await api.getOne('weird id/with chars');
    expect(axiosMock.get).toHaveBeenCalledWith('taxes/weird%20id%2Fwith%20chars');
  });
});
