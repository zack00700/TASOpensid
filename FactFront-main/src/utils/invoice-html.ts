import { InvoiceLineDto } from '../types/invoice';

export interface InvoiceHtmlOptions {
  /** Identifier only used in debug logging */
  id: string;
  /** Currency code used for formatting */
  currency?: string;
  /** Locale for number formatting */
  locale?: string;
  /** Expected total from invoice header, used to verify against line totals */
  expectedTotal?: number;
}

/**
 * Render a simple HTML table representing the invoice lines.  This mirrors the
 * server side template used in production but is implemented in TypeScript so
 * it can be unit tested in isolation.
 */
export function renderInvoiceLines(
  lines: InvoiceLineDto[],
  opts: InvoiceHtmlOptions
): string {
  const locale = opts.locale ?? 'en-US';
  const currency = opts.currency ?? 'USD';
  const nf = new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
  });
  const format = (v: number) => nf.format(v);

  // Compute grand total based on line amounts
  const grandTotal = lines.reduce((sum, l) => sum + (l.amount || 0), 0);
  if (
    typeof opts.expectedTotal === 'number' &&
    Math.abs(grandTotal - opts.expectedTotal) > 0.009
  ) {
    console.warn(
      `[InvoiceHTML] id=${opts.id} total mismatch header=${opts.expectedTotal} lines=${grandTotal}`
    );
  }
  console.debug(
    `[InvoiceHTML] id=${opts.id}, lines=${lines.length}, grandTotal=${grandTotal}`
  );

  const body = lines.length
    ? lines
        .map(
          (l) =>
            `<tr>` +
            `<td>${escapeHtml(l.description)}</td>` +
            `<td style="text-align:right;">${l.quantity} ${escapeHtml(l.uom)}</td>` +
            `<td style="text-align:right;">${format(l.unitPrice)}</td>` +
            `<td style="text-align:right;">${format(l.amount)}</td>` +
            `</tr>`
        )
        .join('\n')
    : `<tr><td colspan="4" style="text-align:center;color:#666;">No items</td></tr>`;

  const html =
    `<table>` +
    `<thead><tr>` +
    `<th>Description</th>` +
    `<th style="text-align:right;">Qty</th>` +
    `<th style="text-align:right;">Unit Price</th>` +
    `<th style="text-align:right;">Amount</th>` +
    `</tr></thead>` +
    `<tbody>${body}</tbody>` +
    `<tfoot><tr><td colspan="3" style="text-align:right;font-weight:bold;">Total</td>` +
    `<td style="text-align:right;font-weight:bold;">${format(grandTotal)}</td></tr></tfoot>` +
    `</table>`;

  return html;
}

// Basic HTML escaping to prevent template injection when we generate the table
function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
