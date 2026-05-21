import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PaymentStatusBadge from '../src/components/ui/PaymentStatusBadge.vue';

describe('PaymentStatusBadge', () => {
  it('renders the Paid label and emerald palette', () => {
    const w = mount(PaymentStatusBadge, { props: { status: 'PAID' } });
    expect(w.text()).toBe('Paid');
    const cls = w.find('span').attributes('class') || '';
    expect(cls).toMatch(/bg-emerald-50/);
    expect(cls).toMatch(/text-emerald-700/);
  });

  it('renders the Partial label and blue palette', () => {
    const w = mount(PaymentStatusBadge, { props: { status: 'PARTIAL' } });
    expect(w.text()).toBe('Partial');
    expect(w.find('span').attributes('class') || '').toMatch(/bg-blue-50/);
  });

  it('renders the Unpaid label and slate palette', () => {
    const w = mount(PaymentStatusBadge, { props: { status: 'UNPAID' } });
    expect(w.text()).toBe('Unpaid');
    expect(w.find('span').attributes('class') || '').toMatch(/bg-slate-100/);
  });

  it('renders the Overdue label and red palette', () => {
    const w = mount(PaymentStatusBadge, { props: { status: 'OVERDUE' } });
    expect(w.text()).toBe('Overdue');
    expect(w.find('span').attributes('class') || '').toMatch(/bg-red-50/);
    expect(w.find('span').attributes('class') || '').toMatch(/text-red-700/);
  });
});
