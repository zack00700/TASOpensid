export type HoldType =
  | 'Customs'
  | 'Operational'
  | 'Financial'
  | 'Security'
  | 'Documentation'
  | 'Other';

export const HOLD_TYPES: HoldType[] = [
  'Customs',
  'Operational',
  'Financial',
  'Security',
  'Documentation',
  'Other',
];

export interface Hold {
  id: string;
  visitId: string;
  type: HoldType;
  reason: string;
  openedAt: string;
  openedBy: string;
  releasedAt: string | null;
  releasedBy: string | null;
  releaseNotes: string | null;
  active: boolean;
}
