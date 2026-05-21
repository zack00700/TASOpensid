import { inject, ref } from 'vue';
import type { AxiosInstance } from 'axios';
import type { AisSuggestion } from '../types/ais';

export function useVesselVisitAis() {
  const $axios = inject<AxiosInstance>('$axios') as AxiosInstance;
  const suggestion = ref<AisSuggestion | null>(null);
  let activeRequest = 0;

  async function loadFor(visitId: string | undefined): Promise<void> {
    const myRequest = ++activeRequest;
    suggestion.value = null;
    if (!visitId) return;
    try {
      const resp = await $axios.get(`ais/by-visit/${visitId}`);
      if (myRequest !== activeRequest) return;
      suggestion.value = resp.status === 204 ? null : resp.data;
    } catch (e: any) {
      if (myRequest !== activeRequest) return;
      if (e?.response?.status === 404) {
        suggestion.value = null;
        return;
      }
      console.error('AIS suggestion fetch failed', visitId, e);
      suggestion.value = null;
    }
  }

  return { suggestion, loadFor };
}
