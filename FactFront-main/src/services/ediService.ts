import api from '../plugin/axios';
import type { EdiMessage, EdiMessageListParams } from '../types/edi';

const BASE = '/edi';

const getMessages = async (params: EdiMessageListParams = {}): Promise<any> => {
  try {
    const response = await api.get(BASE, { params });
    return response.data;
  } catch (error) {
    console.error('[EdiService] Failed to list EDI messages:', error);
    throw error;
  }
};

const getMessage = async (id: string): Promise<EdiMessage> => {
  try {
    const response = await api.get(`${BASE}/${encodeURIComponent(id)}`);
    return response.data;
  } catch (error) {
    console.error(`[EdiService] Failed to get EDI message ${id}:`, error);
    throw error;
  }
};

const receiveMessage = async (message: Partial<EdiMessage>): Promise<EdiMessage> => {
  try {
    const response = await api.post(`${BASE}/inbound`, message);
    return response.data;
  } catch (error) {
    console.error('[EdiService] Failed to ingest EDI message:', error);
    throw error;
  }
};

export const ediService = {
  getMessages,
  getMessage,
  receiveMessage,
};

export default ediService;
