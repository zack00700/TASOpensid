import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import BillOfLading from '../src/components/BillOfLading.vue';
import { i18n } from '../src/i18n';

// `vi.mock` factories are hoisted above module-level consts, so the fixture has
// to live inside `vi.hoisted` to be initialised in time.
const { BILL_FIXTURE } = vi.hoisted(() => ({
  BILL_FIXTURE: {
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
}));

// The component loads rows through billOfLadingStore, which calls the *named*
// export `fetchPaginated` — not `default.list`. Mocking only the default export
// left fetchPaginated undefined, the store swallowed the resulting TypeError
// into its error state, and the table rendered zero rows (hence no row-action
// buttons for the spec to click).
vi.mock('../src/services/billOfLadingService', () => ({
  default: {
    list: vi.fn().mockResolvedValue([BILL_FIXTURE]),
    delete: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    bulkImport: vi.fn(),
  },
  fetchPaginated: vi.fn().mockResolvedValue({
    items: [BILL_FIXTURE],
    pagination: {
      currentPage: 1,
      pageSize: 20,
      totalItems: 1,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    },
  }),
}));

const mocks = vi.hoisted(() => ({
  generateDraftMock: vi.fn(),
  fetchInvoiceHtmlMock: vi.fn(() => Promise.resolve('<p>INVOICE BODY</p>')),
}));
const { generateDraftMock } = mocks;
vi.mock('../src/services/invoiceService', () => ({
  default: {
    generateDraft: mocks.generateDraftMock,
    getInvoicePreviewUrl: (id: string) =>
      `${window.location.origin}/api/invoice/${id}/html`,
  },
  // InvoicePreview imports this named export directly. It renders the returned
  // markup through the iframe's `srcdoc`, not through `src`: an iframe cannot
  // carry the Bearer token, so the HTML is fetched by axios and inlined.
  fetchInvoiceHtml: mocks.fetchInvoiceHtmlMock,
}));

describe('BillOfLading - generate invoice', () => {
  beforeEach(() => {
    generateDraftMock.mockReset();
    mocks.fetchInvoiceHtmlMock.mockClear();
    // InvoicePreview teleports to body; drop leftovers from a previous test.
    document.body.querySelectorAll('[role="dialog"]').forEach((n) => n.remove());
  });

  it('shows toast when invoice already exists', async () => {
    generateDraftMock.mockRejectedValueOnce({ response: { status: 409 } });
    const wrapper = mount(BillOfLading, { attachTo: document.body, global: { plugins: [i18n] } });
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
    const wrapper = mount(BillOfLading, { attachTo: document.body, global: { plugins: [i18n] } });
    await new Promise((r) => setTimeout(r));
    await wrapper.vm.$nextTick();

    const btn = wrapper.find('button[aria-label="Generate invoice"]');
    await btn.trigger('click');
    await new Promise((r) => setTimeout(r));
    await wrapper.vm.$nextTick();

    const iframe = document.body.querySelector('iframe');
    expect(iframe).not.toBeNull();
    expect(mocks.fetchInvoiceHtmlMock).toHaveBeenCalledWith('inv1');
    expect(iframe?.getAttribute('srcdoc')).toContain('INVOICE BODY');
    // ensure preview header is rendered
    expect(document.body.textContent).toContain('Invoice inv1');
  });
});
