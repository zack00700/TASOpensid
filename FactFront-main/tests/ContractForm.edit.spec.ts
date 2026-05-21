import { describe, it, expect, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ContractForm from '../src/components/ContractForm.vue';
import { i18n } from '../src/i18n';
import { makeContract, makeEvent, makeRate } from './fixtures/contract';

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

// ----------------------------------------------------------------
// Pre-fill (edit mode) test
// ----------------------------------------------------------------
describe('ContractForm — edit mode pre-fill', () => {
  it('populates formData from initialData when editMode is true', async () => {
    const eventConfig = makeEvent({ id: 'evt-edit', eventName: 'Demurrage' });
    const rates = [
      makeRate({ startQuantity: 0, endQuantity: 50, amount: 75 }),
    ];

    const initialData = {
      ...makeContract({
        name: 'Existing C',
        description: 'pre-filled description',
        status: 'Active' as const,
        customerId: 'cust1',
        customerName: 'Acme',
        priority: 7,
        calculationMode: {
          type: 'Quantity',
          subType: 'quantity',
          eventConfig,
          parameters: { gracePeriod: 0, minimumDays: 1 },
          filters: [],
        },
      }),
      startDate: '2026-01-01',
      endDate: '2026-12-31',
      rates,
    };

    const w = mount(ContractForm, {
      props: { editMode: true, initialData },
      global: {
        plugins: [i18n],
        provide: { $axios: axiosMock },
      },
    });

    // Let onMounted complete (sets formData from initialData)
    await flushPromises();

    const fd = (w.vm as any).formData;

    // Basic fields
    expect(fd.name).toBe('Existing C');
    expect(fd.description).toBe('pre-filled description');
    expect(fd.status).toBe('Active');

    // Dates (formatted to ISO yyyy-MM-dd by formatDate)
    expect(fd.startDate).toBe('2026-01-01');
    expect(fd.endDate).toBe('2026-12-31');

    // N4 extensions
    expect(fd.customerId).toBe('cust1');
    expect(fd.customerName).toBe('Acme');
    expect(fd.priority).toBe(7);

    // Calculation mode
    expect(fd.calculationMode.type).toBe('Quantity');
    expect(fd.calculationMode.subType).toBe('quantity');
    expect(fd.calculationMode.eventConfig?.id).toBe('evt-edit');
    expect(fd.calculationMode.eventConfig?.eventName).toBe('Demurrage');

    // Rates
    expect(fd.rates).toHaveLength(1);
    expect(fd.rates[0].amount).toBe(75);
  });

  it('emits pre-filled data correctly on submit in edit mode', async () => {
    const eventConfig = makeEvent({ id: 'evt-submit', eventName: 'Storage' });

    const initialData = {
      ...makeContract({
        name: 'Submit Test Contract',
        calculationMode: {
          type: 'Date',
          subType: 'call_date',
          eventConfig,
          parameters: { gracePeriod: 0, minimumDays: 1 },
          filters: [],
        },
      }),
      startDate: '2026-03-01',
      endDate: '2026-09-30',
      rates: [],
    };

    const w = mount(ContractForm, {
      props: { editMode: true, initialData },
      global: {
        plugins: [i18n],
        provide: { $axios: axiosMock },
      },
    });

    await flushPromises();

    // Trigger submit
    await w.find('form').trigger('submit');
    await flushPromises();

    const emitted = w.emitted('submit');
    expect(emitted, 'Expected "submit" to be emitted').toBeTruthy();

    const payload = emitted![0][0] as Record<string, any>;
    expect(payload.name).toBe('Submit Test Contract');
    expect(payload.calculationMode.type).toBe('Date');
    expect(payload.calculationMode.subType).toBe('call_date');
    expect(payload.calculationMode.eventConfig.id).toBe('evt-submit');
  });
});
