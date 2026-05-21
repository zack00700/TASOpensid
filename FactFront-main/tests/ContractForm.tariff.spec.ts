import { describe, it, expect, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ContractForm from '../src/components/ContractForm.vue';
import { i18n } from '../src/i18n';
import { makeContract, makeEvent } from './fixtures/contract';

// ----------------------------------------------------------------
// Axios mock: handle /tariffs and /event routes
// ----------------------------------------------------------------
const axiosMock = {
  get: vi.fn().mockImplementation((url: string) => {
    if (url === '/tariffs') return Promise.resolve({ data: [] });
    if (url.startsWith('/event')) return Promise.resolve({ data: [] });
    return Promise.resolve({ data: [] });
  }),
};

describe('ContractForm — tariff link', () => {
  it('includes tariffId in the submit payload when a tariff is selected', async () => {
    const contract = makeContract({
      tariffId: 'tar1',
      calculationMode: {
        type: 'Quantity',
        subType: 'quantity',
        eventConfig: makeEvent(),
        parameters: { gracePeriod: 0, minimumDays: 1 },
        filters: [],
      },
    });

    const w = mount(ContractForm, {
      global: {
        plugins: [i18n],
        provide: { $axios: axiosMock },
      },
      props: { editMode: true, initialData: contract },
    });
    await flushPromises();

    await w.find('form').trigger('submit');
    await flushPromises();

    const emitted = w.emitted('submit');
    expect(emitted).toBeTruthy();
    const payload = emitted![0][0] as any;
    expect(payload.tariffId).toBe('tar1');
  });
});
