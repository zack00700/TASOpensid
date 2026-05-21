import { describe, it, expect, vi, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { computed, reactive } from 'vue';
import { i18n } from '../src/i18n';

const mocks = vi.hoisted(() => ({
  fetchInvoicesMock: vi.fn(),
  getInvoicePreviewUrlMock: vi.fn(
    (id: string) => `${window.location.origin}/api/invoice/${id}/html`
  ),
  finalizeMock: vi.fn(() => Promise.resolve()),
  deleteMock: vi.fn(() => Promise.resolve()),
  hydrateFromUrlMock: vi.fn(),
}));
const {
  fetchInvoicesMock,
  getInvoicePreviewUrlMock,
  finalizeMock,
  deleteMock,
  hydrateFromUrlMock,
} = mocks;

vi.mock('../src/composables/use.invoice', () => {
  const state = reactive({
    filters: {
      status: [],
      customerName: '',
      facility: '',
      draftNumber: '',
      finalNumber: '',
      createdDateFrom: null,
      createdDateTo: null,
    },
    page: 1,
    pageSize: 50,
    sort: 'createdDate:desc',
    items: [
      {
        _id: '1',
        id: '1',
        draftNumber: 'D1',
        finalNumber: null,
        status: 'DRAFT',
        customerName: 'Test Customer',
        facility: 'Facility',
        createdDate: '2024-01-01',
        amount: undefined,
      },
    ],
    totalCount: 1,
    totalAmount: 0,
    statusCounts: { DRAFT: 1, FINAL: 0 },
    statusAmounts: { DRAFT: 0, FINAL: 0 },
    trends: { totalAmount: 0, status: { DRAFT: 0, FINAL: 0 }, displayed: 0 },
    loading: false,
    error: null,
    updatedAt: null,
  });
  return {
    SORTABLE_FIELDS: [
      'createdDate',
      'TotalAmount',
      'customerName',
      'facility',
      'status',
      'draftNumber',
      'finalNumber',
    ],
    useInvoice: () => ({
      state,
      displayedCount: computed(() => state.items.length),
      fetchInvoices: mocks.fetchInvoicesMock,
      clearFilters: vi.fn(),
      hydrateFromUrl: mocks.hydrateFromUrlMock,
    }),
  };
});

vi.mock('../src/services/invoiceService', () => ({
  default: {
    getInvoicePreviewUrl: mocks.getInvoicePreviewUrlMock,
    finalize: mocks.finalizeMock,
    delete: mocks.deleteMock,
  },
}));

import Invoices from '../src/components/Invoices.vue';

describe('Invoices', () => {
  afterEach(() => {
    // Clean up teleported modal nodes that may leak between tests when wrappers
    // are unmounted but jsdom keeps stale teleport targets in <body>.
    document.body.querySelectorAll('[data-modal-panel], [data-modal-scrim]').forEach((n) => n.remove());
    // Also remove stale kebab-menu teleports if any (defensive).
    document.body.querySelectorAll('[role="menu"]').forEach((n) => n.remove());
    // Remove stale teleported dialogs (e.g. InvoicePreview) from prior tests.
    document.body.querySelectorAll('[role="dialog"]').forEach((n) => n.remove());
  });

  it('shows $0.00 when amount is undefined', async () => {
    const wrapper = mount(Invoices, { global: { plugins: [i18n], provide: { $axios: {} } } });
    await wrapper.vm.$nextTick();
    const cells = wrapper.findAll('tbody tr td');
    expect(cells[6].text()).toBe('$0.00');
  });

  it('allows sorting by status and updates state', async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(Invoices, { global: { plugins: [i18n], provide: { $axios: {} } } });
      await wrapper.vm.$nextTick();
      // The DataTable migration replaced toggleSort with onSortChange({ by, dir }).
      wrapper.vm.onSortChange({ by: 'status', dir: 'asc' });
      vi.runAllTimers();
      await wrapper.vm.$nextTick();
      expect(wrapper.vm.state.sort).toBe('status:asc');
    } finally {
      // Always restore real timers, even if an assertion fails — prevents
      // cascading timeouts in later tests that depend on real setTimeout.
      vi.useRealTimers();
    }
  });

  it('builds preview url when preview is clicked', async () => {
    const wrapper = mount(Invoices, { global: { plugins: [i18n], provide: { $axios: {} } } });
    await wrapper.vm.$nextTick();
    const actionsBtn = wrapper.find('button[aria-label="Invoice actions"]');
    await actionsBtn.trigger('click');
    await wrapper.vm.$nextTick();
    const btn = document.body.querySelector(
      'button[aria-label="Preview invoice"]'
    ) as HTMLButtonElement | null;
    expect(btn).not.toBeNull();
    btn!.click();
    expect(getInvoicePreviewUrlMock).toHaveBeenCalledWith('1');
    expect(wrapper.vm.previewInvoice).not.toBeNull();
  });

  it('finalizes invoice when finalize is clicked', async () => {
    const wrapper = mount(Invoices, { attachTo: document.body, global: { plugins: [i18n], provide: { $axios: {} } } });
    await wrapper.vm.$nextTick();
    const actionsBtn = wrapper.find('button[aria-label="Invoice actions"]');
    await actionsBtn.trigger('click');
    await wrapper.vm.$nextTick();
    const btn = document.body.querySelector(
      'button[aria-label="Finalize invoice"]'
    ) as HTMLButtonElement | null;
    expect(btn).not.toBeNull();
    btn!.click();
    await wrapper.vm.$nextTick();
    await new Promise((r) => setTimeout(r));
    await wrapper.vm.$nextTick();
    const confirmBtn = document.body.querySelector(
      '[data-confirm-button]'
    ) as HTMLButtonElement | null;
    expect(confirmBtn).not.toBeNull();
    await confirmBtn!.dispatchEvent(new Event('click'));
    await wrapper.vm.$nextTick();
    expect(finalizeMock).toHaveBeenCalledWith('1');
    wrapper.unmount();
  });

  it('deletes invoice when delete is confirmed', async () => {
    const wrapper = mount(Invoices, { attachTo: document.body, global: { plugins: [i18n], provide: { $axios: {} } } });
    await wrapper.vm.$nextTick();
    // open kebab menu
    const actionsBtn = wrapper.find('button[aria-label="Invoice actions"]');
    await actionsBtn.trigger('click');
    await wrapper.vm.$nextTick();
    const deleteBtn = document.body.querySelector(
      'button[aria-label="Delete invoice"]'
    ) as HTMLButtonElement | null;
    expect(deleteBtn).not.toBeNull();
    await deleteBtn!.dispatchEvent(new Event('click', { bubbles: true }));
    await wrapper.vm.$nextTick();
    const confirmDelete = document.body.querySelector(
      '[data-confirm-button]'
    ) as HTMLButtonElement | null;
    expect(confirmDelete).not.toBeNull();
    await confirmDelete!.dispatchEvent(new Event('click'));
    await wrapper.vm.$nextTick();
    expect(deleteMock).toHaveBeenCalledWith('1');
    wrapper.unmount();
  });

  it('shows toast when invoice id is missing', async () => {
    const wrapper = mount(Invoices, { attachTo: document.body, global: { plugins: [i18n], provide: { $axios: {} } } });
    await wrapper.vm.$nextTick();
    getInvoicePreviewUrlMock.mockClear();
    wrapper.vm.state.items = [
      {
        draftNumber: 'D2',
        finalNumber: null,
        status: 'DRAFT',
        customerName: 'Test',
        facility: 'Facility',
        createdDate: '2024-01-02',
      },
    ];
    await wrapper.vm.$nextTick();
    const actionsBtn = wrapper.find('button[aria-label="Invoice actions"]');
    await actionsBtn.trigger('click');
    await wrapper.vm.$nextTick();
    const buttons = Array.from(
      document.body.querySelectorAll('button[aria-label="Preview invoice"]')
    ) as HTMLButtonElement[];
    const btn = buttons[buttons.length - 1];
    const err = vi.spyOn(console, 'error').mockImplementation(() => {});
    btn.click();
    await wrapper.vm.$nextTick();
    expect(getInvoicePreviewUrlMock).not.toHaveBeenCalled();
    expect(document.body.textContent).toContain('Invoice ID missing for this row.');
    err.mockRestore();
    wrapper.unmount();
  });
});

