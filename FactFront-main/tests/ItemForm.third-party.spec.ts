import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ref } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ItemForm from '../src/components/ItemForm.vue';
import { i18n } from '../src/i18n';

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({
    thirdParties: ref([
      { id: '1', companyName: 'Acme Corp', industryType: 'Other', companyAddress: 'NYC' },
      { id: '2', companyName: 'Maersk', industryType: 'Shipping Line', companyAddress: 'Copenhagen' },
    ]),
    createMinimal: vi.fn(),
  }),
}));
vi.mock('../src/stores/authStore', () => ({ useAuthStore: () => ({ isAdmin: () => false }) }));

describe('ItemForm — third-party autocomplete', () => {
  beforeEach(() => { setActivePinia(createPinia()); });

  it('shipperName and consigneeName in portDetails are autocompletes showing all tiers', async () => {
    const w = mount(ItemForm, { global: { plugins: [i18n] }, props: {} });
    await flushPromises();
    (w.vm as any).activeTab = 'portDetails';
    await flushPromises();

    const combos = w.findAll('[role="combobox"]');
    expect(combos.length).toBeGreaterThanOrEqual(2);
    const labels = combos.map(
      (c) => c.element.closest('.tp-ac')?.querySelector('label')?.textContent || '',
    );
    expect(labels.some((l) => l.toLowerCase().includes('shipper'))).toBe(true);
    expect(labels.some((l) => l.toLowerCase().includes('consignee'))).toBe(true);
  });
});
