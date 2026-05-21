import api from '../plugin/axios';
import type { DdAccrual, DdDashboardSummary, DdRule, DdWaiver } from '../types/dd';

const BASE_RULES = '/dd/rules';
const BASE_ACCRUALS = '/dd/accruals';
const BASE_DASHBOARD = '/dd/dashboard';

// ── Dashboard ──────────────────────────────────────────────────────────────

export const getDdSummary = async (): Promise<DdDashboardSummary> => {
  try {
    const response = await api.get(`${BASE_DASHBOARD}/summary`);
    return response.data;
  } catch (error) {
    console.error('[DdService] Failed to get dashboard summary:', error);
    throw error;
  }
};

// ── Rules ─────────────────────────────────────────────────────────────────

export const getDdRules = async (ddType?: string): Promise<DdRule[]> => {
  try {
    const params = ddType ? { ddType } : {};
    const response = await api.get(BASE_RULES, { params });
    return response.data;
  } catch (error) {
    console.error('[DdService] Failed to list DD rules:', error);
    throw error;
  }
};

export const getDdRule = async (id: string): Promise<DdRule> => {
  try {
    const response = await api.get(`${BASE_RULES}/${encodeURIComponent(id)}`);
    return response.data;
  } catch (error) {
    console.error(`[DdService] Failed to get DD rule ${id}:`, error);
    throw error;
  }
};

export const createDdRule = async (rule: DdRule): Promise<DdRule> => {
  try {
    const response = await api.post(BASE_RULES, rule);
    return response.data;
  } catch (error) {
    console.error('[DdService] Failed to create DD rule:', error);
    throw error;
  }
};

export const updateDdRule = async (id: string, rule: DdRule): Promise<DdRule> => {
  try {
    const response = await api.put(`${BASE_RULES}/${encodeURIComponent(id)}`, rule);
    return response.data;
  } catch (error) {
    console.error(`[DdService] Failed to update DD rule ${id}:`, error);
    throw error;
  }
};

export const deleteDdRule = async (id: string): Promise<void> => {
  try {
    await api.delete(`${BASE_RULES}/${encodeURIComponent(id)}`);
  } catch (error) {
    console.error(`[DdService] Failed to delete DD rule ${id}:`, error);
    throw error;
  }
};

// ── Accruals ──────────────────────────────────────────────────────────────

export const getDdAccruals = async (params?: {
  itemId?: string;
  status?: string;
  ddType?: string;
  page?: number;
  size?: number;
}): Promise<DdAccrual[]> => {
  try {
    const response = await api.get(BASE_ACCRUALS, { params });
    return response.data;
  } catch (error) {
    console.error('[DdService] Failed to list DD accruals:', error);
    throw error;
  }
};

export const getDdAccrual = async (id: string): Promise<DdAccrual> => {
  try {
    const response = await api.get(`${BASE_ACCRUALS}/${encodeURIComponent(id)}`);
    return response.data;
  } catch (error) {
    console.error(`[DdService] Failed to get DD accrual ${id}:`, error);
    throw error;
  }
};

export const applyWaiver = async (accrualId: string, waiver: DdWaiver): Promise<DdAccrual> => {
  try {
    const response = await api.post(
      `${BASE_ACCRUALS}/${encodeURIComponent(accrualId)}/waiver`,
      waiver
    );
    return response.data;
  } catch (error) {
    console.error(`[DdService] Failed to apply waiver to accrual ${accrualId}:`, error);
    throw error;
  }
};

export const recomputeAccrual = async (accrualId: string): Promise<DdAccrual> => {
  try {
    const response = await api.post(
      `${BASE_ACCRUALS}/${encodeURIComponent(accrualId)}/recompute`
    );
    return response.data;
  } catch (error) {
    console.error(`[DdService] Failed to recompute accrual ${accrualId}:`, error);
    throw error;
  }
};

export const ddService = {
  getDdSummary,
  getDdRules,
  getDdRule,
  createDdRule,
  updateDdRule,
  deleteDdRule,
  getDdAccruals,
  getDdAccrual,
  applyWaiver,
  recomputeAccrual,
};

export default ddService;
