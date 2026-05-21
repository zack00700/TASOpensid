import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import BillOfLading from '../src/components/BillOfLading.vue';

vi.mock('../src/services/billOfLadingService', () => {
  return {
    default: {
      list: vi.fn().mockResolvedValue([
        {
          id: '1',
          blNumber: 'BL001',
          status: 'Draft',
          shipper: 'Acme',
          consignee: '',
          notifyParty: '',
          transportType: 'Vessel',
          vessel: '',
          voyage: '',
          portOfLoading: '',
          portOfDischarge: '',
          placeOfDelivery: '',
          driver: '',
          trainNumber: '',
          truckNumber: '',
          commodity: { description: '', weightKg: 0, volumeM3: 0, packagesNumber: 0, hazardous: false },
          items: [],
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        },
      ]),
      delete: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
    },
  };
});

const mocks = vi.hoisted(() => ({
  generateDraftMock: vi.fn(),
}));
const { generateDraftMock } = mocks;
vi.mock('../src/services/invoiceService', () => {
  return {
    default: {
      generateDraft: mocks.generateDraftMock,
      getInvoicePreviewUrl: (id: string) =>
        `${window.location.origin}/api/invoice/${id}/html`,
    },
  };
});

describe('BillOfLading - generate invoice', () => {
  beforeEach(() => {
    generateDraftMock.mockReset();
  });

  it('shows toast when invoice already exists', async () => {
    generateDraftMock.mockRejectedValueOnce({ response: { status: 409 } });
    const wrapper = mount(BillOfLading, { attachTo: document.body });
    await new Promise((r) => setTimeout(r));
    await wrapper.vm.$nextTick();

    const btn = wrapper.find('button[aria-label="Generate invoice"]');
    expect(btn.exists()).toBe(true);
    await btn.trigger('click');
    await new Promise((r) => setTimeout(r));
    await wrapper.vm.$nextTick();

    expect(document.body.textContent).toContain('Invoice already exists for this bill of lading.');
    expect(wrapper.find('button[aria-label="Generate invoice"]').exists()).toBe(false);
  });

  it('opens preview when invoice is generated', async () => {
    generateDraftMock.mockResolvedValueOnce({ invoiceId: 'inv1' });
    const wrapper = mount(BillOfLading, { attachTo: document.body });
    await new Promise((r) => setTimeout(r));
    await wrapper.vm.$nextTick();

    const btn = wrapper.find('button[aria-label="Generate invoice"]');
    await btn.trigger('click');
    await new Promise((r) => setTimeout(r));
    await wrapper.vm.$nextTick();

    const iframe = document.body.querySelector('iframe');
    expect(iframe?.getAttribute('src')).toBe(
      `${window.location.origin}/api/invoice/inv1/html`
    );
    // ensure preview header is rendered
    expect(document.body.textContent).toContain('Invoice inv1');
  });
});
