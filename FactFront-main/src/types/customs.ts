export type CustomsDeclarationStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'HELD'
  | 'CLEARED'
  | 'REJECTED';

export type CustomsDeclarationType = 'IMPORT' | 'EXPORT' | 'TRANSIT';

export interface CustomsDeclaration {
  id?: string;
  type: CustomsDeclarationType;
  status: CustomsDeclarationStatus;
  billOfLadingId: string;
  itemIds: string[];
  declarantName?: string | null;
  declarantTaxId?: string | null;
  declarationReference?: string | null;
  portOfEntryCode?: string | null;
  totalDeclaredValue?: number | null;
  currency?: string | null;
  assessedDuties?: number | null;
  submittedAt?: string | null;
  heldAt?: string | null;
  holdReason?: string | null;
  clearedAt?: string | null;
  rejectedAt?: string | null;
  rejectionReason?: string | null;
  notes?: string | null;
}

export interface CustomsItemStatus {
  itemId: string;
  cleared: boolean;
  blockReason: string;
}
