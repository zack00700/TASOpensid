import { inject, ref } from 'vue';
import type { AxiosInstance } from 'axios';
import type { Tax } from '../types/tax';

/**
 * Thin CRUD wrapper around /api/taxes. Matches the IsoCodes composable
 * shape so the admin page can follow the same template (list / errors /
 * getAll-getOne-create-update-remove).
 *
 * The backend lives at /api/taxes and the routes used here:
 *   GET    /api/taxes                  → list  (supports ?activeAt= and ?code=)
 *   GET    /api/taxes/{id}             → get one
 *   POST   /api/taxes                  → create
 *   PUT    /api/taxes/{id}             → update
 *   DELETE /api/taxes/{id}             → delete
 */
export function useTax() {
  const $axios = inject<AxiosInstance>('$axios') as AxiosInstance;
  const taxes = ref<Tax[]>([]);
  const loading = ref(false);
  const errors = ref<Record<string, string>>({});

  async function getAll(): Promise<void> {
    loading.value = true;
    errors.value = {};
    try {
      const resp = await $axios.get('taxes');
      taxes.value = (resp.data ?? []) as Tax[];
    } catch (e: any) {
      console.error('useTax.getAll failed', e?.message, e?.response?.status);
      errors.value.network = e?.message ?? 'fetch_failed';
    } finally {
      loading.value = false;
    }
  }

  async function getOne(id: string): Promise<Tax | null> {
    try {
      const resp = await $axios.get(`taxes/${encodeURIComponent(id)}`);
      return resp.data as Tax;
    } catch (e: any) {
      if (e?.response?.status === 404) return null;
      console.error('useTax.getOne failed', id, e?.message, e?.response?.status);
      return null;
    }
  }

  async function create(payload: Tax): Promise<Tax | null> {
    try {
      const resp = await $axios.post('taxes', payload);
      return resp.data as Tax;
    } catch (e: any) {
      const key = e?.response?.data?.error ?? 'create_failed';
      errors.value.create = key;
      console.error('useTax.create failed', e?.message, e?.response?.status);
      return null;
    }
  }

  async function update(id: string, payload: Tax): Promise<Tax | null> {
    try {
      const resp = await $axios.put(`taxes/${encodeURIComponent(id)}`, payload);
      return resp.data as Tax;
    } catch (e: any) {
      const key = e?.response?.data?.error ?? 'update_failed';
      errors.value.update = key;
      console.error('useTax.update failed', id, e?.message, e?.response?.status);
      return null;
    }
  }

  async function remove(id: string): Promise<boolean> {
    try {
      await $axios.delete(`taxes/${encodeURIComponent(id)}`);
      return true;
    } catch (e: any) {
      const key = e?.response?.data?.error ?? 'delete_failed';
      errors.value.delete = key;
      console.error('useTax.remove failed', id, e?.message, e?.response?.status);
      return false;
    }
  }

  return { taxes, loading, errors, getAll, getOne, create, update, remove };
}
