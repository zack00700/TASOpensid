import api from '../plugin/axios';
import { InvoiceLineDto } from '../types/invoice';
import { renderInvoiceLines } from '../utils/invoice-html';
import { DEFAULT_CURRENCY } from '../utils/currency';

export const generateDraft = async (blId: string, customerName: string): Promise<any> => {
  try {
    const encodedName = encodeURIComponent(customerName);
    const response = await api.post(`/invoice/bl/${blId}/draft/customer/${encodedName}`);
    return response.data;
  } catch (error) {
    console.error(`[InvoiceService] Failed to generate draft for BL ${blId}:`, error);
    throw error;
  }
};

export function getInvoicePreviewUrl(id: string): string {
  if (!id) {
    throw new Error('getInvoicePreviewUrl: invoiceId is required');
  }
  return `${window.location.origin}/api/invoice/${encodeURIComponent(id)}/html`;
}

export const fetchInvoiceHtml = async (invoiceId: string): Promise<string> => {
  if (!invoiceId) {
    throw new Error('fetchInvoiceHtml: invoiceId is required');
  }
  try {
    const response = await api.get(
      `invoice/${encodeURIComponent(invoiceId)}/html`,
      {
        responseType: 'text',
      }
    );
    return response.data;
  } catch (error) {
    console.error(`[InvoiceService] Failed to fetch HTML for invoice ${invoiceId}:`, error);
    throw error;
  }
};

export const finalize = async (invoiceId: string): Promise<any> => {
  if (!invoiceId) {
    throw new Error('finalize: invoiceId is required');
  }
  try {
    const response = await api.put(`/invoice/${encodeURIComponent(invoiceId)}/finalize`);
    return response.data;
  } catch (error) {
    console.error(`[InvoiceService] Failed to finalize invoice ${invoiceId}:`, error);
    throw error;
  }
};

/**
 * Most recent invoice, or `null` when the tenant has none yet.
 *
 * Goes through `api` (and therefore the Bearer interceptor) — a bare `fetch`
 * here reaches the backend anonymously and is rejected by the deny-by-default
 * `/api/*` policy.
 */
export const fetchMostRecent = async (): Promise<any | null> => {
  try {
    const response = await api.get('/invoices', {
      params: { page: 1, pageSize: 1, sort: 'createdDate:desc' },
    });
    const items = response.data?.items;
    return Array.isArray(items) && items.length > 0 ? items[0] : null;
  } catch (error) {
    console.error('[InvoiceService] Failed to fetch the most recent invoice:', error);
    throw error;
  }
};

/** Single invoice by id. */
export const fetchById = async (invoiceId: string): Promise<any> => {
  if (!invoiceId) {
    throw new Error('fetchById: invoiceId is required');
  }
  try {
    const response = await api.get(`/invoices/${encodeURIComponent(invoiceId)}`);
    return response.data;
  } catch (error) {
    console.error(`[InvoiceService] Failed to fetch invoice ${invoiceId}:`, error);
    throw error;
  }
};

const remove = async (invoiceId: string): Promise<void> => {
  if (!invoiceId) {
    throw new Error('delete: invoiceId is required');
  }
  try {
    await api.delete(`/invoice/${encodeURIComponent(invoiceId)}`);
  } catch (error) {
    console.error(`[InvoiceService] Failed to delete invoice ${invoiceId}:`, error);
    throw error;
  }
};

/**
 * Build an HTML table for the provided invoice lines.  This mirrors the output
 * of the backend HTML preview and is primarily used in unit tests where the
 * real API is not available.
 */
export const buildInvoiceLinesHtml = (
  invoiceId: string,
  lines: InvoiceLineDto[],
  currency = DEFAULT_CURRENCY
): string => {
  return renderInvoiceLines(lines, { id: invoiceId, currency });
};

export default {
  generateDraft,
  getInvoicePreviewUrl,
  fetchInvoiceHtml,
  fetchMostRecent,
  fetchById,
  finalize,
  delete: remove,
  buildInvoiceLinesHtml,
};
