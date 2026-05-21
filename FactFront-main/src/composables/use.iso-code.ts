import { inject, ref } from 'vue';
import type { AxiosInstance } from 'axios';
import type { IsoContainerCode } from '../types/iso-code';

export function useIsoCode() {
  const $axios = inject<AxiosInstance>('$axios') as AxiosInstance;
  const isoCodes = ref<IsoContainerCode[]>([]);
  const loading = ref(false);
  const errors = ref<Record<string, string>>({});

  async function getAll(includeInactive = false): Promise<void> {
    loading.value = true;
    errors.value = {};
    try {
      const resp = await $axios.get('iso-codes', { params: { includeInactive } });
      isoCodes.value = resp.data ?? [];
    } catch (e: any) {
      console.error('useIsoCode.getAll failed', e?.message, e?.response?.status);
      errors.value.network = e?.message ?? 'fetch_failed';
    } finally {
      loading.value = false;
    }
  }

  async function getOne(code: string): Promise<IsoContainerCode | null> {
    try {
      const resp = await $axios.get(`iso-codes/${code}`);
      return resp.data;
    } catch (e: any) {
      if (e?.response?.status === 404) return null;
      console.error('useIsoCode.getOne failed', code, e?.message, e?.response?.status);
      return null;
    }
  }

  async function create(payload: IsoContainerCode): Promise<IsoContainerCode | null> {
    try {
      const resp = await $axios.post('iso-codes', payload);
      return resp.data;
    } catch (e: any) {
      const key = e?.response?.data?.error ?? 'create_failed';
      errors.value.create = key;
      console.error('useIsoCode.create failed', e?.message, e?.response?.status);
      return null;
    }
  }

  async function update(code: string, payload: IsoContainerCode): Promise<IsoContainerCode | null> {
    try {
      const resp = await $axios.put(`iso-codes/${code}`, payload);
      return resp.data;
    } catch (e: any) {
      const key = e?.response?.data?.error ?? 'update_failed';
      errors.value.update = key;
      console.error('useIsoCode.update failed', code, e?.message, e?.response?.status);
      return null;
    }
  }

  async function remove(code: string): Promise<boolean> {
    try {
      await $axios.delete(`iso-codes/${code}`);
      return true;
    } catch (e: any) {
      const key = e?.response?.data?.error ?? 'delete_failed';
      errors.value.delete = key;
      console.error('useIsoCode.remove failed', code, e?.message, e?.response?.status);
      return false;
    }
  }

  return { isoCodes, loading, errors, getAll, getOne, create, update, remove };
}
