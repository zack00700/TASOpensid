import api from '../plugin/axios';
import type {
  CustomsDeclaration,
  CustomsDeclarationStatus,
  CustomsItemStatus,
} from '../types/customs';

const BASE = '/customs/declarations';

export const listCustomsDeclarations = async (params?: {
  status?: CustomsDeclarationStatus;
  billOfLadingId?: string;
  itemId?: string;
}): Promise<CustomsDeclaration[]> => {
  try {
    const response = await api.get(BASE, { params });
    return response.data;
  } catch (error) {
    console.error('[CustomsService] Failed to list declarations:', error);
    throw error;
  }
};

export const getCustomsDeclaration = async (id: string): Promise<CustomsDeclaration> => {
  try {
    const response = await api.get(`${BASE}/${encodeURIComponent(id)}`);
    return response.data;
  } catch (error) {
    console.error(`[CustomsService] Failed to load declaration ${id}:`, error);
    throw error;
  }
};

export const createCustomsDeclaration = async (
  declaration: CustomsDeclaration
): Promise<CustomsDeclaration> => {
  try {
    const response = await api.post(BASE, declaration);
    return response.data;
  } catch (error) {
    console.error('[CustomsService] Failed to create declaration:', error);
    throw error;
  }
};

export const submitCustomsDeclaration = async (id: string): Promise<CustomsDeclaration> => {
  try {
    const response = await api.post(`${BASE}/${encodeURIComponent(id)}/submit`);
    return response.data;
  } catch (error) {
    console.error(`[CustomsService] Failed to submit declaration ${id}:`, error);
    throw error;
  }
};

export const holdCustomsDeclaration = async (
  id: string,
  reason: string
): Promise<CustomsDeclaration> => {
  try {
    const response = await api.post(`${BASE}/${encodeURIComponent(id)}/hold`, { reason });
    return response.data;
  } catch (error) {
    console.error(`[CustomsService] Failed to hold declaration ${id}:`, error);
    throw error;
  }
};

export const clearCustomsDeclaration = async (
  id: string,
  assessedDuties?: number
): Promise<CustomsDeclaration> => {
  try {
    const body = assessedDuties != null ? { assessedDuties } : {};
    const response = await api.post(`${BASE}/${encodeURIComponent(id)}/clear`, body);
    return response.data;
  } catch (error) {
    console.error(`[CustomsService] Failed to clear declaration ${id}:`, error);
    throw error;
  }
};

export const rejectCustomsDeclaration = async (
  id: string,
  reason: string
): Promise<CustomsDeclaration> => {
  try {
    const response = await api.post(`${BASE}/${encodeURIComponent(id)}/reject`, { reason });
    return response.data;
  } catch (error) {
    console.error(`[CustomsService] Failed to reject declaration ${id}:`, error);
    throw error;
  }
};

export const getItemCustomsStatus = async (itemId: string): Promise<CustomsItemStatus> => {
  try {
    const response = await api.get(`/customs/items/${encodeURIComponent(itemId)}/status`);
    return response.data;
  } catch (error) {
    console.error(`[CustomsService] Failed to get customs status for item ${itemId}:`, error);
    throw error;
  }
};

export const customsService = {
  listCustomsDeclarations,
  getCustomsDeclaration,
  createCustomsDeclaration,
  submitCustomsDeclaration,
  holdCustomsDeclaration,
  clearCustomsDeclaration,
  rejectCustomsDeclaration,
  getItemCustomsStatus,
};

export default customsService;
