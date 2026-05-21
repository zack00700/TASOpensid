import { describe, it, expect } from 'vitest';
import {
  derivePaymentStatus,
  getInvoiceTotal,
  getInvoiceOutstanding,
  type Invoice,
} from '../src/types/invoice';

const TODAY = new Date('2026-05-02T12:00:00Z');

function make(overrides: Partial<Invoice>): Invoice {
  return {
    id: '1',
    draftNumber: 'D1',
    finalNumber: null,
    status: 'DRAFT',
    customerName: 'ACME',
    amount: 100,
    facility: 'X',
    createdDate: '2026-04-01',
    ...overrides,
  };
}

describe('getInvoiceTotal', () => {
  it('prefers grandTotalAmount when present', () => {
    expect(getInvoiceTotal(make({ grandTotalAmount: 250, totalAmount: 200, amount: 100 }))).toBe(250);
  });
  it('falls back to totalAmount, then TotalAmount, then amount, then 0', () => {
    expect(getInvoiceTotal(make({ totalAmount: 200, amount: 100 }))).toBe(200);
    expect(getInvoiceTotal(make({ TotalAmount: 150, amount: 100 }))).toBe(150);
    expect(getInvoiceTotal(make({ amount: 100 }))).toBe(100);
    expect(getInvoiceTotal(make({ amount: 0 }))).toBe(0);
  });
});

describe('getInvoiceOutstanding', () => {
  it('subtracts paidAmount from total', () => {
    expect(getInvoiceOutstanding(make({ totalAmount: 100, paidAmount: 30 }))).toBe(70);
  });
  it('treats missing paidAmount as 0', () => {
    expect(getInvoiceOutstanding(make({ totalAmount: 100 }))).toBe(100);
  });
});

describe('derivePaymentStatus', () => {
  it('returns PAID when paid >= total', () => {
    expect(derivePaymentStatus(make({ totalAmount: 100, paidAmount: 100 }), TODAY)).toBe('PAID');
    expect(derivePaymentStatus(make({ totalAmount: 100, paidAmount: 150 }), TODAY)).toBe('PAID');
  });
  it('returns PARTIAL when 0 < paid < total', () => {
    expect(derivePaymentStatus(make({ totalAmount: 100, paidAmount: 30 }), TODAY)).toBe('PARTIAL');
  });
  it('returns OVERDUE when unpaid and past dueDate', () => {
    expect(derivePaymentStatus(make({ totalAmount: 100, paidAmount: 0, dueDate: '2026-04-30' }), TODAY)).toBe('OVERDUE');
  });
  it('returns UNPAID when unpaid and within dueDate', () => {
    expect(derivePaymentStatus(make({ totalAmount: 100, paidAmount: 0, dueDate: '2026-05-30' }), TODAY)).toBe('UNPAID');
  });
  it('returns UNPAID when unpaid and dueDate missing', () => {
    expect(derivePaymentStatus(make({ totalAmount: 100, paidAmount: 0 }), TODAY)).toBe('UNPAID');
  });
  it('returns UNPAID for an empty draft (total=0, paid=0)', () => {
    expect(derivePaymentStatus(make({ amount: 0, totalAmount: 0, paidAmount: 0 }), TODAY)).toBe('UNPAID');
  });
});
