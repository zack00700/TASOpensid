import api from '../plugin/axios';
import { InvoiceLineDto } from '../types/invoice';
import { renderInvoiceLines } from '../utils/invoice-html';

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
  currency = 'USD'
): string => {
  return renderInvoiceLines(lines, { id: invoiceId, currency });
};

export default {
  generateDraft,
  getInvoicePreviewUrl,
  fetchInvoiceHtml,
  finalize,
  delete: remove,
  buildInvoiceLinesHtml,
};
