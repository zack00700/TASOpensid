import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, DOMWrapper } from '@vue/test-utils';
import ContractRateManagement from '../src/components/ContractRateManagement.vue';
import type { Tax } from '../src/types/tax';
import { i18n } from '../src/i18n';

const tva20: Tax = {
  id: 'tax-1', code: 'TVA20', name: 'TVA 20 %',
  type: 'PERCENTAGE', rate: 20, isActive: true, validFrom: null, validTo: null,
};
const tva55: Tax = {
  id: 'tax-2', code: 'TVA5_5', name: 'TVA 5,5 %',
  type: 'PERCENTAGE', rate: 5.5, isActive: true, validFrom: null, validTo: null,
};
const inactiveTx: Tax = {
  id: 'tax-3', code: 'TVA_OLD', name: 'TVA archived',
  type: 'PERCENTAGE', rate: 19.6, isActive: false, validFrom: null, validTo: null,
};

const axiosMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
};

function mountRM(rates: any[] = [], calculationType = 'Special') {
  return mount(ContractRateManagement, {
    attachTo: document.body,
    props: { contractId: 'c-1', calculationType, rates },
    global: { plugins: [i18n], provide: { $axios: axiosMock } },
  });
}

async function openAddRateModal(wrapper: ReturnType<typeof mountRM>) {
  // The "Add Rate" trigger in the component is the global add button.
  // Buttons that open the modal call handleAddRate, so trigger via the
  // first Plus button on the page.
  const addBtns = wrapper.findAll('button');
  const addBtn = addBtns.find((b) => b.text().toLowerCase().includes('add'));
  expect(addBtn, 'Add Rate button').toBeTruthy();
  await addBtn!.trigger('click');
  await flushPromises();
}

describe('ContractRateManagement — applicable taxes (TC follow-up)', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.get.mockResolvedValue({ data: [tva20, tva55, inactiveTx] });
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('renders one checkbox row per ACTIVE tax in the rate modal', async () => {
    const wrapper = mountRM([]);
    await flushPromises();
    await openAddRateModal(wrapper);

    expect(document.body.querySelector('[data-test="rate-tax-row-tax-1"]')).not.toBeNull();
    expect(document.body.querySelector('[data-test="rate-tax-row-tax-2"]')).not.toBeNull();
    // Inactive must be filtered out.
    expect(document.body.querySelector('[data-test="rate-tax-row-tax-3"]')).toBeNull();
  });

  it('shows the empty-state when no active taxes are defined', async () => {
    axiosMock.get.mockReset();
    axiosMock.get.mockResolvedValue({ data: [] });
    const wrapper = mountRM([]);
    await flushPromises();
    await openAddRateModal(wrapper);

    expect(document.body.querySelector('[data-test="rate-taxes-none"]')).not.toBeNull();
  });

  it('toggling a checkbox adds the tax to newRate.taxes (exclusive by default)', async () => {
    const wrapper = mountRM([]);
    await flushPromises();
    await openAddRateModal(wrapper);

    const cb = new DOMWrapper(
      document.body.querySelector<HTMLInputElement>('[data-test="rate-tax-toggle-tax-1"]')!,
    );
    await cb.setValue(true);

    expect((wrapper.vm as any).newRate.taxes).toEqual([
      { taxId: 'tax-1', inclusive: false },
    ]);
  });

  it('clicking the inclusive button toggles the inclusive flag', async () => {
    const wrapper = mountRM([]);
    await flushPromises();
    await openAddRateModal(wrapper);

    const cb = new DOMWrapper(
      document.body.querySelector<HTMLInputElement>('[data-test="rate-tax-toggle-tax-1"]')!,
    );
    await cb.setValue(true);

    const incBtn = document.body.querySelector<HTMLButtonElement>('[data-test="rate-tax-inclusive-tax-1"]');
    expect(incBtn).not.toBeNull();
    incBtn!.click();
    await flushPromises();

    expect((wrapper.vm as any).newRate.taxes[0].inclusive).toBe(true);
  });

  it('unchecking removes the tax from the list', async () => {
    const wrapper = mountRM([]);
    await flushPromises();
    await openAddRateModal(wrapper);

    const cb = new DOMWrapper(
      document.body.querySelector<HTMLInputElement>('[data-test="rate-tax-toggle-tax-1"]')!,
    );
    await cb.setValue(true);
    expect((wrapper.vm as any).newRate.taxes.length).toBe(1);

    await cb.setValue(false);
    expect((wrapper.vm as any).newRate.taxes).toEqual([]);
  });

  it('editing an existing rate pre-checks the attached taxes', async () => {
    const existing = {
      id: 'r-1',
      amount: 100,
      currency: 'EUR',
      defaultRate: false,
      priority: 0,
      startDate: '2024-01-01',
      endDate: '2024-12-31',
      taxes: [{ taxId: 'tax-2', inclusive: true }],
    };
    const wrapper = mountRM([existing]);
    await flushPromises();

    // Open edit via the dropdown — easier path: directly call the exposed
    // handler on the component instance.
    (wrapper.vm as any).handleEditRate(existing);
    await flushPromises();

    const cb = document.body.querySelector<HTMLInputElement>('[data-test="rate-tax-toggle-tax-2"]');
    expect(cb).not.toBeNull();
    expect(cb!.checked).toBe(true);

    const incBtn = document.body.querySelector<HTMLButtonElement>('[data-test="rate-tax-inclusive-tax-2"]');
    expect(incBtn).not.toBeNull();
    expect(incBtn!.textContent?.trim()).toBe('Inclusive');
  });

  it('saving a rate emits update:rates with the taxes array', async () => {
    const wrapper = mountRM([]);
    await flushPromises();
    await openAddRateModal(wrapper);

    // Fill the bare minimum to pass validation.
    const amount = new DOMWrapper(
      document.body.querySelector<HTMLInputElement>('input[type="number"]')!,
    );
    await amount.setValue('100');

    // Tick a tax.
    const cb = new DOMWrapper(
      document.body.querySelector<HTMLInputElement>('[data-test="rate-tax-toggle-tax-1"]')!,
    );
    await cb.setValue(true);

    // Submit the modal form.
    const form = document.body.querySelector<HTMLFormElement>('form');
    form!.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
    await flushPromises();

    const emitted = wrapper.emitted('update:rates');
    expect(emitted, 'update:rates emit').toBeTruthy();
    const lastEmit = emitted![emitted!.length - 1][0] as any[];
    expect(lastEmit[0].taxes).toEqual([{ taxId: 'tax-1', inclusive: false }]);
  });
});
