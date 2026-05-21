import api from '../plugin/axios';
import type { Payment, PaymentAllocation } from '../types/payment';

const BASE = '/payments';

const getPayments = async (params: { customerId?: string; status?: string; page?: number; size?: number } = {}): Promise<any> => {
  try {
    const response = await api.get(BASE, { params });
    return response.data;
  } catch (error) {
    console.error('[PaymentService] Failed to list payments:', error);
    throw error;
  }
};

const getPayment = async (id: string): Promise<Payment> => {
  try {
    const response = await api.get(`${BASE}/${encodeURIComponent(id)}`);
    return response.data;
  } catch (error) {
    console.error(`[PaymentService] Failed to get payment ${id}:`, error);
    throw error;
  }
};

const createPayment = async (payment: Omit<Payment, 'id'>): Promise<Payment> => {
  try {
    const response = await api.post(BASE, payment);
    return response.data;
  } catch (error) {
    console.error('[PaymentService] Failed to create payment:', error);
    throw error;
  }
};

const updatePayment = async (id: string, payment: Payment): Promise<Payment> => {
  try {
    const response = await api.put(`${BASE}/${encodeURIComponent(id)}`, payment);
    return response.data;
  } catch (error) {
    console.error(`[PaymentService] Failed to update payment ${id}:`, error);
    throw error;
  }
};

const deletePayment = async (id: string): Promise<void> => {
  try {
    await api.delete(`${BASE}/${encodeURIComponent(id)}`);
  } catch (error) {
    console.error(`[PaymentService] Failed to delete payment ${id}:`, error);
    throw error;
  }
};

const allocatePayment = async (id: string, allocation: PaymentAllocation): Promise<Payment> => {
  try {
    const response = await api.post(`${BASE}/${encodeURIComponent(id)}/allocate`, allocation);
    return response.data;
  } catch (error) {
    console.error(`[PaymentService] Failed to allocate payment ${id}:`, error);
    throw error;
  }
};

const reversePayment = async (id: string, reason: string): Promise<Payment> => {
  try {
    const response = await api.post(`${BASE}/${encodeURIComponent(id)}/reverse`, { reason });
    return response.data;
  } catch (error) {
    console.error(`[PaymentService] Failed to reverse payment ${id}:`, error);
    throw error;
  }
};

export const paymentService = {
  getPayments,
  getPayment,
  createPayment,
  updatePayment,
  deletePayment,
  allocatePayment,
  reversePayment,
};

export default paymentService;
