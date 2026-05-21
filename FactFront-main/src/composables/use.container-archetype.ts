import { inject, ref } from 'vue';
import type { AxiosInstance } from 'axios';
import type { ContainerArchetype } from '../types/container-archetype';
import type { IsoContainerCode } from '../types/iso-code';

export function useContainerArchetype() {
  const $axios = inject<AxiosInstance>('$axios') as AxiosInstance;
  const archetypes = ref<ContainerArchetype[]>([]);
  const loading = ref(false);
  const errors = ref<Record<string, string>>({});

  async function getAll(): Promise<void> {
    loading.value = true;
    errors.value = {};
    try {
      const resp = await $axios.get('archetypes');
      archetypes.value = resp.data ?? [];
    } catch (e: any) {
      console.error('useContainerArchetype.getAll failed', e?.message, e?.response?.status);
      errors.value.network = e?.message ?? 'fetch_failed';
    } finally {
      loading.value = false;
    }
  }

  async function getOne(id: string): Promise<ContainerArchetype | null> {
    try {
      const resp = await $axios.get(`archetypes/${id}`);
      return resp.data;
    } catch (e: any) {
      if (e?.response?.status === 404) return null;
      console.error('useContainerArchetype.getOne failed', id, e?.message, e?.response?.status);
      return null;
    }
  }

  async function create(payload: ContainerArchetype): Promise<ContainerArchetype | null> {
    try {
      const resp = await $axios.post('archetypes', payload);
      return resp.data;
    } catch (e: any) {
      const key = e?.response?.data?.error ?? 'create_failed';
      errors.value.create = key;
      console.error('useContainerArchetype.create failed', e?.message, e?.response?.status);
      return null;
    }
  }

  async function update(id: string, payload: ContainerArchetype): Promise<ContainerArchetype | null> {
    try {
      const resp = await $axios.put(`archetypes/${id}`, payload);
      return resp.data;
    } catch (e: any) {
      const key = e?.response?.data?.error ?? 'update_failed';
      errors.value.update = key;
      console.error('useContainerArchetype.update failed', id, e?.message, e?.response?.status);
      return null;
    }
  }

  async function remove(id: string): Promise<boolean> {
    try {
      await $axios.delete(`archetypes/${id}`);
      return true;
    } catch (e: any) {
      const key = e?.response?.data?.error ?? 'delete_failed';
      errors.value.delete = key;
      console.error('useContainerArchetype.remove failed', id, e?.message, e?.response?.status);
      return false;
    }
  }

  async function getIsoCodesFor(archetypeId: string): Promise<IsoContainerCode[]> {
    try {
      const resp = await $axios.get(`archetypes/${archetypeId}/iso-codes`);
      return resp.data ?? [];
    } catch (e: any) {
      console.error('useContainerArchetype.getIsoCodesFor failed', archetypeId, e?.message, e?.response?.status);
      return [];
    }
  }

  return { archetypes, loading, errors, getAll, getOne, create, update, remove, getIsoCodesFor };
}
