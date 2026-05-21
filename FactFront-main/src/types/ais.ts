export interface VesselAisSnapshot {
  id?: string;
  mmsi: string;
  imoNumber?: number | null;
  name?: string | null;
  callSign?: string | null;
  lat?: number | null;
  lon?: number | null;
  sog?: number | null;
  cog?: number | null;
  trueHeading?: number | null;
  navigationalStatus?: number | null;
  positionTimestamp?: string | null;
  destination?: string | null;
  etaMonth?: number | null;
  etaDay?: number | null;
  etaHour?: number | null;
  etaMinute?: number | null;
  resolvedEta?: string | null;
  lastSeen: string;
}

export type AisStatus = 'live' | 'lost' | 'no-mmsi';

export interface AisHealth {
  connected: boolean;
  lastMessageAt: string | null;
  snapshotCount: number;
}

export interface AisSuggestion {
  suggestedEta: string | null;
  suggestedAta: string | null;
  sourceTimestamp: string;
  navStatus: number | null;
  position: { lat: number; lon: number } | null;
}
