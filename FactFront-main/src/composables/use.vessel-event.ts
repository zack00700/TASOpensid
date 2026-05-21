import { inject, ref } from 'vue';
import type { AxiosInstance } from 'axios';
import type { VesselEvent } from '../types/vessel-event';

export function useVesselEvent() {
  const $axios = inject<AxiosInstance>('$axios') as AxiosInstance;
  const events = ref<VesselEvent[]>([]);

  async function getVesselEvents(visitId: string): Promise<void> {
    try {
      const response = await $axios.get(`/visit/${encodeURIComponent(visitId)}/event`);
      events.value = response.data ?? [];
    } catch (error) {
      console.error('Failed to fetch vessel events:', error);
      events.value = [];
    }
  }

  async function addVesselEvent(
    visitId: string,
    payload: { eventId: string; eventDate: string; notes: string },
  ): Promise<{ id: string; message: string }> {
    const response = await $axios.post(`/visit/${encodeURIComponent(visitId)}/event`, payload);
    return response.data;
  }

  return { events, getVesselEvents, addVesselEvent };
}
