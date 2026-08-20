
// Base Event Types
export type EventType = 'IN' | 'OUT' | 'INTERMEDIATE';
export type ItemStatus = 'Available' | 'In Use' | 'Maintenance' | 'Out of Service';
export type LifecycleStatus = 'In Progress' | 'Completed' | 'Cancelled';

// Billing / commercial
export type ItemCategory = 'Import' | 'Export' | 'Transship';
export type FreightKind  = 'FCL' | 'LCL' | 'Empty' | 'Breakbulk' | 'Ro-Ro';

export interface Event {
  id: string;
  /**
   * Set by the item lifecycle endpoints. The item-event lookup endpoints
   * (ItemEventLookupResource, ItemResource) return `eventDate` instead, so
   * readers must accept either — hence both fields being optional here.
   */
  timestamp?: string;
  eventDate?: string;
  eventType?: EventType;
  /** Human-readable label from the event configuration. */
  eventName?: string;
  itemId?: string;
  lifecycleId?: string;
  location?: string;
  notes?: string;
  metadata?: Record<string, unknown>;
}

export interface Lifecycle {
  id: string;
  lifecycleId?: string; // for compatibility
  itemId?: string;
  startTime?: string;
  endTime?: string;
  status?: LifecycleStatus; // Updated to use backend enum values
  eventIds?: string[];
  events?: Event[]; // Now populated when expanded
}


export interface Item {
  id?: string;
  /**
   * Raw Mongo identifier. `normalizeItem` folds it into `id`, but payloads read
   * straight off the API still carry it, and several call sites fall back to it.
   */
  _id?: string;
  itemType?: string;
  itemNumber?: string;
  type?: string;
  ownerId?: string;
  position?: string;
  itemStatus?: string;
  lastInspectionDate?: Date | string;
  nextInspectionDate?: Date | string;
  notes?: string;
  status?: string;
  lifeCycles: Lifecycle[]; // Now always full objects (not IDs)
  relatedInvoiceId?: string | null;
  relatedInvoice?: string;
  billOfLadingId?: string;
  weight?: number;
  volume?: number;
  // Port / container details
  containerNumber?: string;
  containerType?: string;
  sealNumbers?: string[];
  hazmatFlag?: boolean;
  hazmatClass?: string;
  unNumber?: string;
  reeferFlag?: boolean;
  reeferTemperature?: number;
  oogFlag?: boolean;
  weightVerified?: boolean;
  verifiedWeight?: number;
  emptyStatus?: 'FULL' | 'EMPTY' | 'UNKNOWN';
  condition?: 'GOOD' | 'DAMAGED' | 'NEEDS_REPAIR';
  damageCodes?: string[];
  customsStatus?: 'PENDING' | 'CLEARED' | 'HELD' | 'INSPECTED' | 'RELEASED' | 'REFUSED';
  gateInDate?: string;
  gateOutDate?: string;
  chargingStartDate?: string;
  gracePeriodExpiryDate?: string;
  inboundVoyage?: string;
  outboundVoyage?: string;
  handlingCode?: string;
  hsCode?: string;
  countryOfOrigin?: string;
  dangerousGoodsDeclarationRef?: string;
  // Billing / commercial
  category?: ItemCategory;
  freightKind?: FreightKind;
  bookingNumber?: string;
  consigneeName?: string;
  shipperName?: string;
}

export interface ItemFormData {
  _id?: string;
  /** Normalised identifier, mirrored from `_id` when the form is opened. */
  id?: string;
  itemNumber: string;
  itemType: string;
  type: string;
  ownerId: string;
  position: string;
  status: "Available" | "In Use" | "Maintenance" | "Out of Service";
  /** Legacy alias the API still returns on some payloads; read as a fallback. */
  itemStatus?: string;
  lastInspection: string;
  nextInspection: string;
  notes: string;
  lifeCycles: Lifecycle[];
}
