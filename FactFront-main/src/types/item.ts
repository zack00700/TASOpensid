
// Base Event Types
export type EventType = 'IN' | 'OUT' | 'INTERMEDIATE';
export type ItemStatus = 'Available' | 'In Use' | 'Maintenance' | 'Out of Service';
export type LifecycleStatus = 'In Progress' | 'Completed' | 'Cancelled';

// Billing / commercial
export type ItemCategory = 'Import' | 'Export' | 'Transship';
export type FreightKind  = 'FCL' | 'LCL' | 'Empty' | 'Breakbulk' | 'Ro-Ro';

export interface Event {
  id: string;
  timestamp?: string;
  eventType?: EventType;
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
  itemNumber: string;
  itemType: string;
  type: string;
  ownerId: string;
  position: string;
  status: "Available" | "In Use" | "Maintenance" | "Out of Service";
  lastInspection: string;
  nextInspection: string;
  notes: string;
  lifeCycles: Lifecycle[];
}
