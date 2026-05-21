import { inject, ref } from 'vue';
import type { AxiosInstance } from 'axios';
import type { Vessel } from '../types/vessel';
import type { VesselAisSnapshot, AisHealth, AisStatus } from '../types/ais';

const LIVE_THRESHOLD_MS = 5 * 60 * 1000;

export function useVesselAis() {
  const $axios = inject<AxiosInstance>('$axios') as AxiosInstance;
  const snapshots = ref<Map<string, VesselAisSnapshot | null>>(new Map());
  const health = ref<AisHealth | null>(null);

  async function getAisSnapshot(vesselId: string): Promise<VesselAisSnapshot | null> {
    try {
      const resp = await $axios.get(`ais/by-vessel/${vesselId}`);
      return resp.status === 204 ? null : resp.data;
    } catch (e: any) {
      if (e?.response?.status === 404) return null;
      console.error('AIS snapshot fetch failed', vesselId, e);
      return null;
    }
  }

  async function loadSnapshotsForVessels(vessels: Vessel[]): Promise<void> {
    const targets = vessels.filter(v => v.id && v.mmsi && v.mmsi.trim() !== '');
    const results = await Promise.all(
      targets.map(async v => [v.id!, await getAisSnapshot(v.id!)] as const)
    );
    const next = new Map<string, VesselAisSnapshot | null>();
    for (const [id, snap] of results) next.set(id, snap);
    snapshots.value = next;
  }

  async function getHealth(): Promise<void> {
    try {
      const resp = await $axios.get('ais/health');
      health.value = resp.data;
    } catch {
      health.value = { connected: false, lastMessageAt: null, snapshotCount: 0 };
    }
  }

  function statusFor(vessel: Vessel, now: number = Date.now()): AisStatus {
    if (!vessel.mmsi || vessel.mmsi.trim() === '') return 'no-mmsi';
    const snap = vessel.id ? snapshots.value.get(vessel.id) : null;
    if (!snap) return 'lost';
    const age = now - new Date(snap.lastSeen).getTime();
    return age < LIVE_THRESHOLD_MS ? 'live' : 'lost';
  }

  return { snapshots, health, loadSnapshotsForVessels, getHealth, statusFor };
}
