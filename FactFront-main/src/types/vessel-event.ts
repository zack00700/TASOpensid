export interface VesselEvent {
  id?: string;
  visitId: string;
  eventId: string;
  eventDate: string; // ISO 8601
  notes: string;
}
