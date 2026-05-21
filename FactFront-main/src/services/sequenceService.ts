import api from '../plugin/axios';
import type { InvoiceSequence, InvoiceSequenceForm } from '../types/sequence';

const BASE = '/invoice-sequences';

const list = async (): Promise<InvoiceSequence[]> => {
  try {
    const response = await api.get(BASE);
    return response.data;
  } catch (error) {
    console.error('[SequenceService] Failed to list sequences:', error);
    throw error;
  }
};

const get = async (sequenceId: string): Promise<InvoiceSequence> => {
  try {
    const response = await api.get(`${BASE}/${encodeURIComponent(sequenceId)}`);
    return response.data;
  } catch (error) {
    console.error(`[SequenceService] Failed to get sequence ${sequenceId}:`, error);
    throw error;
  }
};

const preview = async (sequenceId: string): Promise<{ preview: string }> => {
  try {
    const response = await api.get(`${BASE}/${encodeURIComponent(sequenceId)}/preview`);
    return response.data;
  } catch (error) {
    console.error(`[SequenceService] Failed to preview sequence ${sequenceId}:`, error);
    throw error;
  }
};

const create = async (form: InvoiceSequenceForm): Promise<InvoiceSequence> => {
  try {
    const response = await api.post(BASE, form);
    return response.data;
  } catch (error) {
    console.error('[SequenceService] Failed to create sequence:', error);
    throw error;
  }
};

const update = async (sequenceId: string, form: InvoiceSequenceForm): Promise<InvoiceSequence> => {
  try {
    const response = await api.put(`${BASE}/${encodeURIComponent(sequenceId)}`, form);
    return response.data;
  } catch (error) {
    console.error(`[SequenceService] Failed to update sequence ${sequenceId}:`, error);
    throw error;
  }
};

export const sequenceService = {
  list,
  get,
  preview,
  create,
  update,
};

export default sequenceService;
