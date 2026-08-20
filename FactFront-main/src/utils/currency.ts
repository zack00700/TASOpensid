/**
 * Money formatting for every amount the UI renders.
 *
 * Amounts carry their own currency in the domain model (`Invoice.currency`,
 * `Payment.currency`, `Rate.currency`, …). Always pass that value through:
 * `DEFAULT_CURRENCY` is only the fallback for rows that predate the field.
 *
 * This module exists because the currency used to be hardcoded independently in
 * each screen — EUR in Invoices/Payments/InvoiceDataSelector/AskAiFab, USD in
 * DdDashboard — so the same amount rendered as `1 234 €` on one page and
 * `$1,234.00` on another. Format through here, never with a local
 * `Intl.NumberFormat` carrying a literal currency code.
 */

/** Fallback for records stored before `currency` became part of the model. */
export const DEFAULT_CURRENCY = 'EUR';

export interface FormatCurrencyOptions {
  /**
   * Drop the decimal part. Reserved for compact KPI tiles where the cents add
   * noise; never use it for a line amount, a total, or anything a user has to
   * reconcile against a real invoice.
   */
  compact?: boolean;
  /** Rendered when the amount is null/undefined/unparseable. */
  fallback?: string;
}

/**
 * Formats an amount in its own currency, using the viewer's locale for
 * separators and symbol placement.
 *
 * Unparseable input yields `options.fallback` (default `'—'`) rather than
 * `NaN €`, and an invalid ISO currency code degrades to `CODE 1234.56` instead
 * of throwing — `Intl.NumberFormat` rejects unknown codes.
 */
export function formatCurrency(
  amount: unknown,
  currency: string | null | undefined = DEFAULT_CURRENCY,
  options: FormatCurrencyOptions = {},
): string {
  const { compact = false, fallback = '—' } = options;

  const num = typeof amount === 'number' ? amount : parseFloat(String(amount ?? ''));
  if (!Number.isFinite(num)) return fallback;

  const code = (currency || DEFAULT_CURRENCY).toUpperCase();
  const fractionDigits = compact ? 0 : 2;

  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: code,
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits,
    }).format(num);
  } catch {
    return `${code} ${num.toFixed(fractionDigits)}`;
  }
}

/**
 * Formats an amount that may legitimately be absent, rendering `fallback`
 * (default `'—'`) for null/undefined instead of a misleading zero. Use it for
 * optional columns; use {@link formatCurrency} with an explicit `0` where the
 * business meaning of "no value" really is zero.
 */
export function formatOptionalCurrency(
  amount: number | null | undefined,
  currency?: string | null,
  options: FormatCurrencyOptions = {},
): string {
  if (amount == null) return options.fallback ?? '—';
  return formatCurrency(amount, currency, options);
}
