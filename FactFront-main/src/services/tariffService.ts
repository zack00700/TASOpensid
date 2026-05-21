import api from '../plugin/axios';
import type { Tariff } from '../types/contrat';

const BASE = '/tariffs';

const getTariffs = async (serviceType?: string): Promise<Tariff[]> => {
  try {
    const params = serviceType ? { serviceType } : {};
    const response = await api.get(BASE, { params });
    return response.data;
  } catch (error) {
    console.error('[TariffService] Failed to list tariffs:', error);
    throw error;
  }
};

const getTariff = async (id: string): Promise<Tariff> => {
  try {
    const response = await api.get(`${BASE}/${encodeURIComponent(id)}`);
    return response.data;
  } catch (error) {
    console.error(`[TariffService] Failed to get tariff ${id}:`, error);
    throw error;
  }
};

const createTariff = async (tariff: Tariff): Promise<Tariff> => {
  try {
    const response = await api.post(BASE, tariff);
    return response.data;
  } catch (error) {
    console.error('[TariffService] Failed to create tariff:', error);
    throw error;
  }
};

const updateTariff = async (id: string, tariff: Tariff): Promise<Tariff> => {
  try {
    const response = await api.put(`${BASE}/${encodeURIComponent(id)}`, tariff);
    return response.data;
  } catch (error) {
    console.error(`[TariffService] Failed to update tariff ${id}:`, error);
    throw error;
  }
};

const deleteTariff = async (id: string): Promise<void> => {
  try {
    await api.delete(`${BASE}/${encodeURIComponent(id)}`);
  } catch (error) {
    console.error(`[TariffService] Failed to delete tariff ${id}:`, error);
    throw error;
  }
};

export const tariffService = {
  getTariffs,
  getTariff,
  createTariff,
  updateTariff,
  deleteTariff,
};

export default tariffService;
