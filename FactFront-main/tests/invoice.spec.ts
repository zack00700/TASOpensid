import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { useInvoice } from '../src/composables/use.invoice';

const TestComponent = defineComponent({
  template: '<div></div>',
  setup() {
    return useInvoice();
  },
});

describe('useInvoice fetchInvoices', () => {
  it('handles invoices missing amount by treating them as zero', async () => {
    const invoices = [
      { id: '1', status: 'DRAFT', amount: undefined },
      { id: '2', status: 'FINAL', amount: 50 },
      { id: '3', status: 'FINAL' },
    ];
    const wrapper = mount(TestComponent, {
      global: {
        provide: {
          $axios: {
            get: () =>
              Promise.resolve({
                data: { items: invoices, totalCount: 3, aggregates: { totalAmount: 50 } },
              }),
          },
        },
      },
    });

    await wrapper.vm.fetchInvoices();

    expect(wrapper.vm.state.items.map((i: any) => i.amount)).toEqual([0, 50, 0]);
    expect(wrapper.vm.state.statusCounts).toEqual({ DRAFT: 1, FINAL: 2 });
    expect(wrapper.vm.state.statusAmounts).toEqual({ DRAFT: 0, FINAL: 50 });
    expect(wrapper.vm.state.totalAmount).toBe(50);
  });

  it('maps invoiceId to id when id is missing', async () => {
    const invoices = [
      { invoiceId: 'abc', status: 'DRAFT', amount: 10 },
    ];
    const wrapper = mount(TestComponent, {
      global: {
        provide: {
          $axios: {
            get: () =>
              Promise.resolve({
                data: { items: invoices, totalCount: 1, aggregates: { totalAmount: 10 } },
              }),
          },
        },
      },
    });

    await wrapper.vm.fetchInvoices();
    expect(wrapper.vm.state.items[0].id).toBe('abc');
  });
});

describe('useInvoice sort normalization', () => {
  it('converts comma-delimited sort parameters to colon', () => {
    const wrapper = mount(TestComponent, { global: { provide: { $axios: {} } } });
    wrapper.vm.hydrateFromUrl('?sort=createdDate,asc');
    expect(wrapper.vm.state.sort).toBe('createdDate:asc');
  });

  it('warns and defaults when sort direction is malformed', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const wrapper = mount(TestComponent, { global: { provide: { $axios: {} } } });
    wrapper.vm.hydrateFromUrl('?sort=createdDate:up');
    expect(wrapper.vm.state.sort).toBe('createdDate:asc');
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });

  it('normalizes sort before sending request', async () => {
    const get = vi.fn(() =>
      Promise.resolve({ data: { items: [], totalCount: 0, aggregates: { totalAmount: 0 } } })
    );
    const wrapper = mount(TestComponent, {
      global: {
        provide: { $axios: { get } },
      },
    });
    wrapper.vm.state.sort = 'createdDate,desc';
    await wrapper.vm.fetchInvoices();
    expect(get).toHaveBeenCalledWith('/invoices', {
      params: expect.objectContaining({ sort: 'createdDate:desc' }),
    });
  });

  it('falls back to default sort when field is not allowed', async () => {
    const get = vi.fn(() =>
      Promise.resolve({ data: { items: [], totalCount: 0, aggregates: { totalAmount: 0 } } })
    );
    const wrapper = mount(TestComponent, {
      global: {
        provide: { $axios: { get } },
      },
    });
    wrapper.vm.state.sort = 'foo:desc';
    await wrapper.vm.fetchInvoices();
    expect(wrapper.vm.state.sort).toBe('createdDate:desc');
    expect(get).toHaveBeenCalledWith('/invoices', {
      params: expect.objectContaining({ sort: 'createdDate:desc' }),
    });
  });

  it('uses TotalAmount sort for API', async () => {
    const get = vi.fn(() =>
      Promise.resolve({ data: { items: [], totalCount: 0, aggregates: { totalAmount: 0 } } })
    );
    const wrapper = mount(TestComponent, {
      global: {
        provide: { $axios: { get } },
      },
    });
    wrapper.vm.state.sort = 'TotalAmount:asc';
    await wrapper.vm.fetchInvoices();
    expect(get).toHaveBeenCalledWith('/invoices', {
      params: expect.objectContaining({ sort: 'TotalAmount:asc' }),
    });
  });

  it('uses pageSize when fetching invoices', async () => {
    const get = vi.fn(() =>
      Promise.resolve({ data: { items: [], totalCount: 0, aggregates: { totalAmount: 0 } } })
    );
    const wrapper = mount(TestComponent, {
      global: {
        provide: { $axios: { get } },
      },
    });
    wrapper.vm.state.pageSize = 25;
    await wrapper.vm.fetchInvoices();
    expect(get).toHaveBeenCalledWith('/invoices', {
      params: expect.objectContaining({ page: '1', pageSize: '25' }),
    });
  });
});
