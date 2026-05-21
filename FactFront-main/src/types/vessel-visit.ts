export type VesselVisitPhase = 'Created' | 'Active' | 'Completed' | 'Canceled';

export interface VesselVisit {
  id?: string;
  vesselName: string;
  vesselId: string;
  visitReference: string;
  phase: VesselVisitPhase;
  service: string;
  serviceName: string;
  facility: string;
  eta: string;
  etd: string;
  ata: string;
  atd: string;
  pod: string;
  pol: string;
  finalDestination: string;
  beginReceive: string;
  dryCutoff: string;
  reeferCutoff: string;
  hazCutoff: string;
  emptyPickup: string;
  inboundVoyage: string;
  outboundVoyage: string;
  inboundCaptain: string;
  outboundCaptain: string;
  lineOperator: string;
  notes: string;
}
