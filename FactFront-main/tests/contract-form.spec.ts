import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ContractForm from '../src/components/ContractForm.vue';
import { i18n } from '../src/i18n';

describe('ContractForm', () => {
  it('shows existing rates when editing', async () => {
    const initialData = {
      id: '1',
      name: 'Contract',
      description: 'Test',
      calculationMode: {
        type: 'Quantity',
        subType: 'quantity',
        eventConfig: { id: 'e', eventName: 'event', eventType: 'IN', billedEvent: true },
        parameters: {},
        filters: []
      },
      status: 'Active',
      startDate: '2024-01-01T00:00:00Z',
      endDate: '2024-12-31T00:00:00Z',
      rates: [
        {
          id: 'r1',
          amount: 10,
          currency: 'USD',
          defaultRate: false,
          priority: 0,
          startQuantity: 0,
          endQuantity: 10,
          unitOfMeasurement: 'Items',
          startDate: '2024-01-01',
          endDate: '2024-12-31'
        }
      ]
    };

    const wrapper = mount(ContractForm, {
      props: { editMode: true, initialData },
      global: {
        plugins: [i18n],
        provide: { $axios: { get: () => Promise.resolve({ data: [] }) } }
      }
    });

    await new Promise((r) => setTimeout(r));
    await wrapper.vm.$nextTick();

    // Rate amount, currency and unit appear as adjacent inline elements;
    // wrapper.text() concatenates them without spaces.
    expect(wrapper.text()).toContain('10USD');
    expect(wrapper.text()).toContain('per Items');
  });
});

