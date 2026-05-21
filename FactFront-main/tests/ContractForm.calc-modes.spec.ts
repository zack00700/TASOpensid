import { describe, it, expect, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ContractForm from '../src/components/ContractForm.vue';
import { i18n } from '../src/i18n';
import { makeContract, makeEvent, makeRate } from './fixtures/contract';
import type { RateManagementExtended } from '../src/types/contrat';

// ----------------------------------------------------------------
// Type local to this test (matches ContractForm's internal string type)
// ----------------------------------------------------------------
type CalculationModeType = 'Date' | 'Quantity' | 'DateByTEU' | 'Special' | 'Tiered' | 'Banded';

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
// Per-mode data table
// ----------------------------------------------------------------
const MODES: Array<{
  mode: CalculationModeType;
  subType: string;
  rates: RateManagementExtended[];
}> = [
  {
    mode: 'Date',
    subType: 'call_date',
    rates: [
      makeRate({ startQuantity: undefined, endQuantity: undefined, startDate: '2026-06-01', endDate: '2026-06-30', amount: 100 }),
    ],
  },
  {
    mode: 'Quantity',
    subType: 'quantity',
    rates: [
      makeRate({ startQuantity: 0, endQuantity: 100, amount: 50 }),
      makeRate({ startQuantity: 100, endQuantity: 500, amount: 40 }),
    ],
  },
  {
    mode: 'DateByTEU',
    subType: 'call_date_teu',
    rates: [makeRate({ startDate: '2026-06-01', endDate: '2026-06-30', amount: 25 })],
  },
  {
    mode: 'Special',
    subType: 'latest_in_date_bl',
    rates: [makeRate({ amount: 200, startQuantity: undefined, endQuantity: undefined })],
  },
  {
    mode: 'Tiered',
    subType: 'quantity',
    rates: [
      makeRate({ startQuantity: 0, endQuantity: 100, amount: 50, rateType: 'TIERED' }),
      makeRate({ startQuantity: 100, endQuantity: 500, amount: 40, rateType: 'TIERED' }),
      makeRate({ startQuantity: 500, endQuantity: 1000, amount: 30, rateType: 'TIERED' }),
    ],
  },
  {
    mode: 'Banded',
    subType: 'quantity',
    rates: [
      makeRate({ startQuantity: 0, endQuantity: 100, amount: 50, rateType: 'BANDED' }),
      makeRate({ startQuantity: 100, endQuantity: 500, amount: 40, rateType: 'BANDED' }),
      makeRate({ startQuantity: 500, endQuantity: 1000, amount: 30, rateType: 'BANDED' }),
    ],
  },
];

describe('ContractForm — calculation modes', () => {
  it.each(MODES)(
    'emits correct calculationMode.type "$mode" on submit',
    async ({ mode, subType, rates }) => {
      // Build a fully-valid contract in editMode shape.
      // startDate/endDate as strings — ContractForm.formatDate handles both strings and Dates.
      const eventConfig = makeEvent();

      const initialData = {
        ...makeContract({
          calculationMode: {
            type: mode,
            subType,
            eventConfig,
            parameters: { gracePeriod: 0, minimumDays: 1 },
            filters: [],
          },
        }),
        // Override dates as ISO strings (ContractForm accepts both)
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

      // Let onMounted run (it sets formData and advances to step 4 when eventConfig is valid)
      await flushPromises();

      // Trigger the form's submit event (the form uses @submit.prevent="handleSubmit")
      await w.find('form').trigger('submit');
      await flushPromises();

      const emitted = w.emitted('submit');
      expect(emitted, `Expected "submit" to be emitted for mode "${mode}"`).toBeTruthy();

      const payload = emitted![0][0] as Record<string, any>;

      // Core assertion: the calculation mode type is preserved end-to-end
      expect(payload.calculationMode.type).toBe(mode);

      // The eventConfig in the payload is normalised to { id } only by normalizeContractFormSubmit
      expect(payload.calculationMode.eventConfig.id).toBe(eventConfig.id);
    },
  );
});
