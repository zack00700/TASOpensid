import { describe, it, expect, vi } from 'vitest';
import { ref } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import BillOfLadingForm from '../src/components/BillOfLadingForm.vue';
import { i18n } from '../src/i18n';

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({
    thirdParties: ref([
      { id: '1', companyName: 'CMA CGM', industryType: 'Shipping Line', companyAddress: 'Marseille' },
      { id: '2', companyName: 'Acme Corp', industryType: 'Other', companyAddress: 'NYC' },
    ]),
    createMinimal: vi.fn(),
  }),
}));
vi.mock('../src/stores/authStore', () => ({ useAuthStore: () => ({ isAdmin: () => false }) }));

vi.mock('../src/services/billOfLadingService', () => ({
  default: {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    update: vi.fn(),
    updateTransport: vi.fn(),
    refreshTransport: vi.fn(),
    mapVisitToSnapshot: vi.fn(),
  },
}));
vi.mock('../src/services/vesselVisitService', () => ({
  default: { list: vi.fn().mockResolvedValue([]), search: vi.fn().mockResolvedValue([]) },
  sanitizeVesselQuery: (q: string) => q,
}));
vi.mock('../src/services/itemService', () => ({
  default: { list: vi.fn().mockResolvedValue([]), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}));
vi.mock('../src/services/invoiceService', () => ({ default: { create: vi.fn() } }));

describe('BillOfLadingForm — third-party autocomplete', () => {
  it('Shipping Line field only shows Shipping Line tiers', async () => {
    const w = mount(BillOfLadingForm, { global: { plugins: [i18n] }, props: {} });
    await flushPromises();
    const combos = w.findAll('[role="combobox"]');
    const shippingLine = combos.find((c) => {
      const label = c.element.closest('.tp-ac')?.querySelector('label')?.textContent || '';
      return label.toLowerCase().includes('shipping');
    });
    expect(shippingLine).toBeTruthy();
    await shippingLine!.setValue('a');
    const items = w.findAll('.ta-item').map((n) => n.text());
    expect(items).toContain('CMA CGM');
    expect(items).not.toContain('Acme Corp');
  });

  it('Shipper field shows all tiers (no industry filter)', async () => {
    const w = mount(BillOfLadingForm, { global: { plugins: [i18n] }, props: {} });
    await flushPromises();
    const combos = w.findAll('[role="combobox"]');
    const shipper = combos.find((c) => {
      const label = c.element.closest('.tp-ac')?.querySelector('label')?.textContent || '';
      return label.toLowerCase().includes('shipper');
    });
    expect(shipper).toBeTruthy();
    await shipper!.setValue('a');
    const items = w.findAll('.ta-item').map((n) => n.text());
    expect(items).toContain('Acme Corp');
  });

  it('Notify Party autocomplete preselects party name and pre-fills the address textarea on selection', async () => {
    const w = mount(BillOfLadingForm, { global: { plugins: [i18n] }, props: {} });
    await flushPromises();
    (w.vm as any).activeStep = 'parties';
    await flushPromises();

    const combos = w.findAll('[role="combobox"]');
    const notifyCombo = combos.find((c) => {
      const lbl = c.element.closest('.tp-ac')?.querySelector('label')?.textContent || '';
      return lbl.toLowerCase().includes('notify');
    });
    expect(notifyCombo).toBeTruthy();

    await notifyCombo!.setValue('Acm');
    await flushPromises();
    await w.findAll('.ta-item')[0].trigger('mousedown');
    await flushPromises();

    expect((w.vm as any).formData.notifyParty.name).toBe('Acme Corp');
    expect((w.vm as any).formData.notifyParty.address).toBe('NYC');

    const textarea = w.find('textarea[data-testid="notify-party-address"]');
    expect((textarea.element as HTMLTextAreaElement).value).toBe('NYC');
  });
});
