import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, DOMWrapper } from '@vue/test-utils';
import Taxes from '../src/components/Taxes.vue';
import type { Tax } from '../src/types/tax';
import { i18n } from '../src/i18n';

vi.mock('../src/stores/authStore', () => ({
  useAuthStore: () => ({ hasRole: (r: string) => r === 'ROLE_ADMIN' }),
}));

const tva20: Tax = {
  id: 'tax-1', code: 'TVA20', name: 'TVA 20 %',
  type: 'PERCENTAGE', rate: 20, validFrom: null, validTo: null, isActive: true,
};
const tva55: Tax = {
  id: 'tax-2', code: 'TVA5_5', name: 'TVA 5,5 %',
  type: 'PERCENTAGE', rate: 5.5, validFrom: null, validTo: null, isActive: false,
};

const axiosMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
};

function mountPage(initialTaxes: Tax[] = [tva20, tva55]) {
  axiosMock.get.mockResolvedValue({ data: initialTaxes });
  return mount(Taxes, {
    attachTo: document.body,
    global: { plugins: [i18n], provide: { $axios: axiosMock } },
  });
}

describe('Taxes admin page', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.post.mockReset();
    axiosMock.put.mockReset();
    axiosMock.delete.mockReset();
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('lists existing taxes from GET /taxes', async () => {
    const wrapper = mountPage();
    await flushPromises();
    expect(wrapper.find('[data-test="tax-row-tax-1"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="tax-row-tax-2"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="tax-row-tax-1"]').text()).toContain('TVA 20 %');
  });

  it('filters out inactive taxes when "include inactive" is off', async () => {
    const wrapper = mountPage();
    await flushPromises();
    // Default is includeInactive=true; uncheck it.
    await wrapper.find<HTMLInputElement>('[data-test="taxes-include-inactive"]').setValue(false);
    expect(wrapper.find('[data-test="tax-row-tax-1"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="tax-row-tax-2"]').exists()).toBe(false);
  });

  it('search filters by code or name', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find<HTMLInputElement>('[data-test="taxes-search"]').setValue('5,5');
    expect(wrapper.find('[data-test="tax-row-tax-1"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="tax-row-tax-2"]').exists()).toBe(true);
  });

  it('"New tax" opens the modal with empty defaults', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('[data-test="taxes-add"]').trigger('click');
    await flushPromises();
    const codeInput = document.body.querySelector<HTMLInputElement>('[data-test="tax-form-code"]');
    expect(codeInput).not.toBeNull();
    expect(codeInput!.value).toBe('');
  });

  it('saving a new tax POSTs /taxes and refreshes the list', async () => {
    const wrapper = mountPage();
    await flushPromises();

    axiosMock.post.mockResolvedValueOnce({ data: { ...tva20, id: 'tax-new' } });
    axiosMock.get.mockResolvedValueOnce({ data: [tva20, tva55, { ...tva20, id: 'tax-new', code: 'TVA10' }] });

    await wrapper.find('[data-test="taxes-add"]').trigger('click');
    await flushPromises();

    await new DOMWrapper(document.body.querySelector('[data-test="tax-form-code"]')!).setValue('TVA10');
    await new DOMWrapper(document.body.querySelector('[data-test="tax-form-name"]')!).setValue('TVA 10 %');
    await new DOMWrapper(document.body.querySelector('[data-test="tax-form-rate"]')!).setValue('10');

    const form = document.body.querySelector<HTMLFormElement>('form');
    form!.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
    await flushPromises();

    expect(axiosMock.post).toHaveBeenCalledWith('taxes', expect.objectContaining({
      code: 'TVA10',
      name: 'TVA 10 %',
      rate: 10,
      type: 'PERCENTAGE',
    }));
    expect(axiosMock.get).toHaveBeenCalledTimes(2); // initial + refresh after save
  });

  it('clicking Edit pre-fills the modal with the tax row', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('[data-test="tax-edit-tax-1"]').trigger('click');
    await flushPromises();

    const codeInput = document.body.querySelector<HTMLInputElement>('[data-test="tax-form-code"]');
    expect(codeInput!.value).toBe('TVA20');
  });

  it('saving an edit PUTs /taxes/{id}', async () => {
    const wrapper = mountPage();
    await flushPromises();

    axiosMock.put.mockResolvedValueOnce({ data: { ...tva20, rate: 19.6 } });
    axiosMock.get.mockResolvedValueOnce({ data: [{ ...tva20, rate: 19.6 }, tva55] });

    await wrapper.find('[data-test="tax-edit-tax-1"]').trigger('click');
    await flushPromises();

    await new DOMWrapper(document.body.querySelector('[data-test="tax-form-rate"]')!).setValue('19.6');

    const form = document.body.querySelector<HTMLFormElement>('form');
    form!.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
    await flushPromises();

    expect(axiosMock.put).toHaveBeenCalledWith('taxes/tax-1', expect.objectContaining({ rate: 19.6 }));
  });

  it('delete flow: ask confirm → DELETE → refresh', async () => {
    const wrapper = mountPage();
    await flushPromises();

    axiosMock.delete.mockResolvedValueOnce({ status: 204 });
    axiosMock.get.mockResolvedValueOnce({ data: [tva55] });

    await wrapper.find('[data-test="tax-delete-tax-1"]').trigger('click');
    await flushPromises();

    const confirmBtn = document.body.querySelector<HTMLButtonElement>('[data-test="tax-delete-confirm"]');
    confirmBtn!.click();
    await flushPromises();

    expect(axiosMock.delete).toHaveBeenCalledWith('taxes/tax-1');
    expect(axiosMock.get).toHaveBeenCalledTimes(2); // initial + refresh after delete
  });
});
