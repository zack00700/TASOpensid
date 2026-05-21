import { describe, it, expect, afterEach, vi } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import DataTable from '../src/components/ui/DataTable.vue';
import type { Column } from '../src/components/ui/DataTable.types';

interface Row {
  id: string;
  name: string;
  amount: number | null;
  status: string;
}

const baseRows: Row[] = [
  { id: '1', name: 'Alpha', amount: 100, status: 'DRAFT' },
  { id: '2', name: 'Beta',  amount: null, status: 'FINAL' },
];

const baseColumns: Column<Row>[] = [
  { key: 'name',   label: 'Name',   sortable: true },
  { key: 'amount', label: 'Amount', sortable: true, align: 'right',
    format: (v) => (v == null ? '—' : `$${(v as number).toFixed(2)}`) },
  { key: 'status', label: 'Status' },
];

const mountedWrappers: VueWrapper[] = [];

function trackedMount(...args: Parameters<typeof mount>): ReturnType<typeof mount> {
  const wrapper = mount(...args);
  mountedWrappers.push(wrapper as VueWrapper);
  return wrapper;
}

afterEach(() => {
  for (const w of mountedWrappers) w.unmount();
  mountedWrappers.length = 0;
  document.body.querySelectorAll('[data-datatable-mobile-card]').forEach((n) => n.remove());
});

describe('DataTable', () => {
  it('renders one <th> per column and one <tr> per row', () => {
    const wrapper = trackedMount(DataTable, { props: { rows: baseRows, columns: baseColumns } });
    const ths = wrapper.findAll('thead th');
    // 3 columns + the actions cell column (always present even when slot is empty)
    expect(ths).toHaveLength(4);
    expect(ths[0].text()).toContain('Name');
    expect(wrapper.findAll('tbody tr')).toHaveLength(2);
  });

  it('renders raw row[key] with em-dash fallback for null/undefined/empty', () => {
    const wrapper = trackedMount(DataTable, {
      props: {
        rows: [{ id: '1', name: 'Alpha', amount: null, status: '' }],
        columns: [
          { key: 'name', label: 'Name' },
          { key: 'status', label: 'Status' },
        ] as Column<Row>[],
      },
    });
    const tds = wrapper.findAll('tbody td');
    expect(tds[0].text()).toBe('Alpha');
    expect(tds[1].text()).toBe('—'); // empty string
  });

  it('applies column.format when no slot is provided', () => {
    const wrapper = trackedMount(DataTable, { props: { rows: baseRows, columns: baseColumns } });
    const tds = wrapper.findAll('tbody tr:first-child td');
    // amount column uses format: $100.00
    expect(tds[1].text()).toBe('$100.00');
    // amount = null → format returns '—'
    const td2 = wrapper.findAll('tbody tr:nth-child(2) td');
    expect(td2[1].text()).toBe('—');
  });

  it('renders #cell-<key> slot, overriding column.format', () => {
    const wrapper = trackedMount(DataTable, {
      props: { rows: baseRows, columns: baseColumns },
      slots: {
        'cell-amount': `<template #cell-amount="{ row }"><span data-test="custom">!{{ row.amount }}!</span></template>`,
      },
    });
    expect(wrapper.find('[data-test="custom"]').text()).toBe('!100!');
  });

  it('emits update:sort with dir=asc when clicking a new sortable header', async () => {
    const wrapper = trackedMount(DataTable, {
      props: { rows: baseRows, columns: baseColumns, sortBy: 'amount', sortDir: 'asc' },
    });
    await wrapper.find('thead th:first-child button').trigger('click'); // Name header
    expect(wrapper.emitted('update:sort')?.[0]).toEqual([{ by: 'name', dir: 'asc' }]);
  });

  it('flips dir when clicking the current sort column', async () => {
    const wrapper = trackedMount(DataTable, {
      props: { rows: baseRows, columns: baseColumns, sortBy: 'name', sortDir: 'asc' },
    });
    await wrapper.find('thead th:first-child button').trigger('click');
    expect(wrapper.emitted('update:sort')?.[0]).toEqual([{ by: 'name', dir: 'desc' }]);
  });

  it('does not emit on click for non-sortable headers', async () => {
    const wrapper = trackedMount(DataTable, { props: { rows: baseRows, columns: baseColumns } });
    // status column is non-sortable (3rd index)
    await wrapper.findAll('thead th')[2].trigger('click');
    expect(wrapper.emitted('update:sort')).toBeUndefined();
  });

  it('shows SkeletonTable and hides body rows when loading=true', () => {
    const wrapper = trackedMount(DataTable, {
      props: { rows: baseRows, columns: baseColumns, loading: true },
    });
    expect(wrapper.find('[data-test="datatable-skeleton"]').exists()).toBe(true);
    // No data rows during loading
    expect(wrapper.findAll('tbody tr[data-test="datatable-row"]')).toHaveLength(0);
  });

  it('renders EmptyState with title/description when rows is empty and not loading', () => {
    const wrapper = trackedMount(DataTable, {
      props: {
        rows: [],
        columns: baseColumns,
        empty: { title: 'No data yet', description: 'Try widening filters.' },
      },
    });
    expect(wrapper.text()).toContain('No data yet');
    expect(wrapper.text()).toContain('Try widening filters.');
  });

  it('emits empty-action when EmptyState CTA is clicked', async () => {
    const wrapper = trackedMount(DataTable, {
      props: {
        rows: [],
        columns: baseColumns,
        empty: { title: 'Nothing here', actionLabel: 'Reset' },
      },
    });
    // EmptyState's CTA — find by its label text (sortable headers also render <button>s)
    const buttons = wrapper.findAll('button');
    const cta = buttons.find((b) => b.text() === 'Reset')!;
    await cta.trigger('click');
    expect(wrapper.emitted('empty-action')).toHaveLength(1);
  });

  it('renders #row-actions slot in the last cell of each row', () => {
    const wrapper = trackedMount(DataTable, {
      props: { rows: baseRows, columns: baseColumns },
      slots: {
        'row-actions': `<template #row-actions="{ row }"><button :data-row-id="row.id">x</button></template>`,
      },
    });
    expect(wrapper.findAll('tbody button[data-row-id]')).toHaveLength(2);
    expect(wrapper.find('tbody button[data-row-id="1"]').exists()).toBe(true);
  });

  it('renders #mobile-card slot when provided', () => {
    const wrapper = trackedMount(DataTable, {
      props: { rows: baseRows, columns: baseColumns },
      slots: {
        'mobile-card': `<template #mobile-card="{ row }"><div data-mob :data-row-id="row.id">{{ row.name }}</div></template>`,
      },
    });
    expect(wrapper.findAll('[data-mob]')).toHaveLength(2);
    expect(wrapper.find('[data-mob][data-row-id="2"]').text()).toBe('Beta');
  });

  it('auto-renders mobile cards when a column has mobile=title', () => {
    const wrapper = trackedMount(DataTable, {
      props: {
        rows: baseRows,
        columns: [
          { key: 'name',   label: 'Name',   mobile: 'title' },
          { key: 'status', label: 'Status', mobile: 'meta'  },
        ] as Column<Row>[],
      },
    });
    const cards = wrapper.findAll('[data-datatable-mobile-card]');
    expect(cards).toHaveLength(2);
    // Title is the column with mobile='title'
    expect(cards[0].find('[data-mobile-title]').text()).toBe('Alpha');
  });

  it('does not render mobile DOM when no slot and no mobile hints', () => {
    const wrapper = trackedMount(DataTable, { props: { rows: baseRows, columns: baseColumns } });
    expect(wrapper.find('[data-datatable-mobile-card]').exists()).toBe(false);
  });

  it('emits row-click and row-dblclick with the row object', async () => {
    const wrapper = trackedMount(DataTable, { props: { rows: baseRows, columns: baseColumns } });
    const row1 = wrapper.findAll('tbody tr')[0];
    await row1.trigger('click');
    expect(wrapper.emitted('row-click')?.[0]).toEqual([baseRows[0]]);
    await row1.trigger('dblclick');
    expect(wrapper.emitted('row-dblclick')?.[0]).toEqual([baseRows[0]]);
  });

  it('applies the canonical visual style classes', () => {
    const wrapper = trackedMount(DataTable, { props: { rows: baseRows, columns: baseColumns } });
    const container = wrapper.find('[data-test="datatable-container"]');
    expect(container.classes()).toContain('rounded-xl');
    expect(container.classes()).toContain('border-slate-200');
    const thead = wrapper.find('thead');
    expect(thead.classes()).toContain('bg-slate-50');
    expect(thead.classes()).toContain('sticky');
    const firstRow = wrapper.find('tbody tr');
    expect(firstRow.classes().some((c) => c.includes('hover:bg-blue-50'))).toBe(true);
  });

  it('applies sticky CSS classes to a column flagged sticky="left"', () => {
    const wrapper = trackedMount(DataTable, {
      props: {
        rows: baseRows,
        columns: [
          { key: 'name',   label: 'Name',   sticky: 'left' },
          { key: 'status', label: 'Status' },
        ] as Column<Row>[],
      },
    });
    const ths = wrapper.findAll('thead th');
    const cls0 = ths[0].attributes('class') || '';
    expect(cls0).toMatch(/sticky/);
    expect(cls0).toMatch(/left-0/);
    const tds = wrapper.findAll('tbody tr:first-child td');
    const tdcls = tds[0].attributes('class') || '';
    expect(tdcls).toMatch(/sticky/);
    expect(tdcls).toMatch(/left-0/);
  });

  it('does not apply sticky classes when no column is flagged sticky', () => {
    const wrapper = trackedMount(DataTable, { props: { rows: baseRows, columns: baseColumns } });
    const cls = wrapper.find('thead th').attributes('class') || '';
    expect(cls).not.toMatch(/sticky left/);
  });

  it('warns and renders only the first sticky column when multiple are flagged', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    trackedMount(DataTable, {
      props: {
        rows: baseRows,
        columns: [
          { key: 'name',   label: 'Name',   sticky: 'left' },
          { key: 'amount', label: 'Amount', sticky: 'left' },
        ] as Column<Row>[],
      },
    });
    expect(warn).toHaveBeenCalledWith(
      expect.stringContaining('Multiple columns flagged sticky'),
      expect.any(Array),
    );
    warn.mockRestore();
  });

  it('renders a checkbox column when selectable=true', () => {
    const wrapper = trackedMount(DataTable, {
      props: { rows: baseRows, columns: baseColumns, selectable: true, selected: [] },
    });
    const firstTh = wrapper.find('thead th:first-child');
    expect(firstTh.find('input[type="checkbox"]').exists()).toBe(true);
    const firstTdRow1 = wrapper.find('tbody tr:first-child td:first-child');
    expect(firstTdRow1.find('input[type="checkbox"]').exists()).toBe(true);
  });

  it('omits the row checkbox when isSelectable returns false', () => {
    const wrapper = trackedMount(DataTable, {
      props: {
        rows: baseRows,
        columns: baseColumns,
        selectable: true,
        selected: [],
        isSelectable: (row: Row) => row.id !== '2',
      },
    });
    const td1 = wrapper.find('tbody tr:first-child td:first-child');
    expect(td1.find('input[type="checkbox"]').exists()).toBe(true);
    const td2 = wrapper.find('tbody tr:nth-child(2) td:first-child');
    expect(td2.find('input[type="checkbox"]').exists()).toBe(false);
  });

  it('emits update:selected when a row checkbox is clicked', async () => {
    const wrapper = trackedMount(DataTable, {
      props: { rows: baseRows, columns: baseColumns, selectable: true, selected: [] },
    });
    const cb = wrapper.find('tbody tr:first-child td:first-child input[type="checkbox"]');
    await cb.trigger('click');
    expect(wrapper.emitted('update:selected')?.[0]).toEqual([['1']]);
  });

  it('does not emit row-click when a checkbox is clicked', async () => {
    const wrapper = trackedMount(DataTable, {
      props: { rows: baseRows, columns: baseColumns, selectable: true, selected: [] },
    });
    const cb = wrapper.find('tbody tr:first-child td:first-child input[type="checkbox"]');
    await cb.trigger('click');
    expect(wrapper.emitted('row-click')).toBeUndefined();
  });

  it('header checkbox toggles all selectable rows on the page', async () => {
    const wrapper = trackedMount(DataTable, {
      props: { rows: baseRows, columns: baseColumns, selectable: true, selected: [] },
    });
    const headerCb = wrapper.find('thead th:first-child input[type="checkbox"]');
    await headerCb.trigger('click');
    expect(wrapper.emitted('update:selected')?.[0]).toEqual([['1', '2']]);

    await wrapper.setProps({ selected: ['1', '2'] });
    await headerCb.trigger('click');
    const emissions = wrapper.emitted('update:selected') as string[][][];
    expect(emissions[emissions.length - 1]).toEqual([[]]);
  });
});
