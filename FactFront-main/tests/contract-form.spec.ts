import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import ContractForm from '../src/components/ContractForm.vue';
import { i18n } from '../src/i18n';

// ContractForm now calls useRouter() at setup time for the "open Third Parties in a
// new tab" link (TC-06). Provide a light stub so mounting doesn't warn about a
// missing router injection.
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
    resolve: (to: unknown) => ({ href: typeof to === 'string' ? to : '/third-parties' }),
  }),
}));

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

const mountEdit = () =>
  mount(ContractForm, {
    props: { editMode: true, initialData },
    global: {
      plugins: [i18n],
      provide: { $axios: { get: () => Promise.resolve({ data: [] }) } }
    }
  });

// onMounted hydrates the form and prefetches tariffs asynchronously; let the
// microtask/timer queue drain before asserting.
const flush = async (wrapper: { vm: { $nextTick: () => Promise<void> } }) => {
  await new Promise((r) => setTimeout(r));
  await wrapper.vm.$nextTick();
};

describe('ContractForm edit mode (TC-06)', () => {
  it('opens on the first wizard step, not the rates step', async () => {
    const wrapper = mountEdit();
    await flush(wrapper);

    // Editing now lands on step 1 (event selection) with data pre-populated,
    // instead of jumping straight to step 4. The rate row (step 4 body) must
    // therefore NOT be rendered on mount.
    expect(wrapper.text()).toContain(i18n.global.t('contractForm.step.eventSelection'));
    expect(wrapper.text()).not.toContain('10USD');
  });

  it('preloads existing rate data, visible once the rates step is opened', async () => {
    const wrapper = mountEdit();
    await flush(wrapper);

    // On step 1 the only rendered grid is the stepper (4 clickable step cards).
    const stepCards = wrapper.findAll('.grid > div');
    expect(stepCards).toHaveLength(4);

    // Jump to step 4 (Rate Management) and confirm the existing rate hydrated.
    await stepCards[3].trigger('click');
    await wrapper.vm.$nextTick();

    // Rate amount, currency and unit appear as adjacent inline elements;
    // wrapper.text() concatenates them without spaces.
    expect(wrapper.text()).toContain('10USD');
    expect(wrapper.text()).toContain('per Items');
  });
});
