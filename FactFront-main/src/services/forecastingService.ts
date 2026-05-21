import api from '../plugin/axios';
import type { CapacityForecast } from '../types/forecasting';

const BASE = '/forecasting';

export const listForecastMetrics = async (): Promise<string[]> => {
  try {
    const response = await api.get(`${BASE}/metrics`);
    return response.data;
  } catch (error) {
    console.error('[ForecastingService] Failed to list metrics:', error);
    throw error;
  }
};

export const getThroughputForecast = async (params: {
  metric: string;
  lookbackMonths?: number;
  horizonMonths?: number;
}): Promise<CapacityForecast> => {
  try {
    const response = await api.get(`${BASE}/throughput`, { params });
    return response.data;
  } catch (error) {
    console.error(`[ForecastingService] Failed to forecast ${params.metric}:`, error);
    throw error;
  }
};

export const getForecastOverview = async (params?: {
  lookbackMonths?: number;
  horizonMonths?: number;
}): Promise<CapacityForecast[]> => {
  try {
    const response = await api.get(`${BASE}/overview`, { params });
    return response.data;
  } catch (error) {
    console.error('[ForecastingService] Failed to load overview:', error);
    throw error;
  }
};

export const forecastingService = {
  listForecastMetrics,
  getThroughputForecast,
  getForecastOverview,
};

export default forecastingService;
