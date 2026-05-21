export interface Invoice {
  id: string;
  draftNumber: string;
  finalNumber: string | null;
  status: 'DRAFT' | 'FINAL' | 'CANCELLED';
  customerName: string;
  amount: number;
  totalAmount?: number;
  TotalAmount?: number;
  facility: string;
  createdDate: string;
  // New fields (always returned by /invoices now that the backend populates them).
  customerKey?: string;
  dueDate?: string;            // ISO date string
  paymentTerms?: string;       // e.g. 'NET30'
  invoiceDate?: string;
  grandTotalAmount?: number;
  currency?: string;
  // New fields (only present when ?includePayments=true).
  paidAmount?: number;
  lastPaymentDate?: string;
}

export type PaymentStatus = 'PAID' | 'PARTIAL' | 'UNPAID' | 'OVERDUE';

/** Effective invoice total. Prefers backend-canonical fields over legacy aliases. */
export function getInvoiceTotal(inv: Invoice): number {
  return inv.grandTotalAmount ?? inv.totalAmount ?? inv.TotalAmount ?? inv.amount ?? 0;
}

/** Outstanding = total − paid. Returns negative numbers if overpaid (caller decides). */
export function getInvoiceOutstanding(inv: Invoice): number {
  return getInvoiceTotal(inv) - (inv.paidAmount ?? 0);
}

/** Derives a four-state payment status. PAID > PARTIAL > OVERDUE > UNPAID. */
export function derivePaymentStatus(inv: Invoice, today: Date = new Date()): PaymentStatus {
  const total = getInvoiceTotal(inv);
  const paid = inv.paidAmount ?? 0;
  if (total > 0 && paid >= total) return 'PAID';
  if (paid > 0) return 'PARTIAL';
  if (inv.dueDate) {
    const due = new Date(inv.dueDate);
    if (!Number.isNaN(due.getTime()) && due < today) return 'OVERDUE';
  }
  return 'UNPAID';
}

/**
 * Represents a single line within an invoice.  These lines are displayed in
 * the invoice HTML preview and include basic pricing information that can be
 * summarised to a grand total.
 */
export interface InvoiceLineDto {
  /**
   * Human readable description of the line item, e.g. the event name or a
   * contract line label.
   */
  description: string;
  /** Quantity for the line (days, units, etc.). */
  quantity: number;
  /** Unit of measure associated with the quantity. */
  uom: string;
  /** Agreed rate for the item. */
  unitPrice: number;
  /** Calculated amount (quantity * unitPrice). */
  amount: number;
}

/**
 * Invoice enriched with line items.  The backend rendering logic can use this
 * structure when building the HTML preview so that the template has a list to
 * iterate over.
 */
export interface InvoiceWithLines extends Invoice {
  lines: InvoiceLineDto[];
}
