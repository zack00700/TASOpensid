import { inject, ref } from 'vue';
import type { AxiosInstance } from 'axios';
import type { Hold, HoldType } from '../types/hold';

export function useHold() {
  const $axios = inject<AxiosInstance>('$axios') as AxiosInstance;
  const holds = ref<Hold[]>([]);
  const loading = ref(false);

  async function listForVisit(visitId: string): Promise<Hold[]> {
    loading.value = true;
    try {
      const response = await $axios.get(`visit/${encodeURIComponent(visitId)}/holds`);
      holds.value = (response.data ?? []) as Hold[];
      return holds.value;
    } finally {
      loading.value = false;
    }
  }

  async function createHold(
    visitId: string,
    payload: { type: HoldType; reason: string },
  ): Promise<Hold> {
    const response = await $axios.post(
      `visit/${encodeURIComponent(visitId)}/holds`,
      payload,
    );
    return response.data as Hold;
  }

  async function releaseHold(holdId: string, releaseNotes?: string): Promise<Hold> {
    const body = releaseNotes != null && releaseNotes.trim().length > 0
      ? { releaseNotes: releaseNotes.trim() }
      : {};
    const response = await $axios.patch(`holds/${encodeURIComponent(holdId)}/release`, body);
    return response.data as Hold;
  }

  return { holds, loading, listForVisit, createHold, releaseHold };
}
