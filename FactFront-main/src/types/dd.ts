export type DdType = 'DEMURRAGE' | 'DETENTION';
export type DdClockAnchor = 'DISCHARGE' | 'GATE_IN' | 'DOCS_READY' | 'CUSTOMS_CLEARED';
export type DdAccrualStatus = 'RUNNING' | 'STOPPED' | 'INVOICED' | 'WAIVED' | 'CANCELLED';
export type WaiverType = 'FULL' | 'PARTIAL' | 'FREE_DAYS_EXTENSION' | 'RATE_REDUCTION';

export interface DdDayEntry {
  date: string;          // ISO instant
  dayNumber: number;
  isFreeDay: boolean;
  isHoliday: boolean;
  chargeAmount: number;
  rateBandLabel: string;
  note?: string;
}

export interface DdWaiver {
  waiverId?: string;
  waiverType: WaiverType;
  waivedAmount?: number;
  extensionDays?: number;
  reason?: string;
  approvedBy?: string;
  approvedAt?: string;
}

export interface DdRule {
  id?: string;
  ruleName: string;
  ddType: DdType;
  clockAnchor: DdClockAnchor;
  carrierId?: string;
  containerTypeCode?: string;
  freeDays: number;
  tiers?: any[];
  includeHolidays?: boolean;
  includeWeekends?: boolean;
  status?: string;
  notes?: string;
}

export interface DdAccrual {
  id?: string;
  itemId: string;
  containerNumber?: string;
  ddType: DdType;
  clockAnchor?: DdClockAnchor;
  ruleId?: string;
  carrierId?: string;
  freeDaysGranted?: number;
  clockStart?: string;
  clockStop?: string;
  totalDaysElapsed?: number;
  chargeableDays?: number;
  holidayDays?: number;
  totalAccruedAmount?: number;
  status: DdAccrualStatus;
  dailyLog?: DdDayEntry[];
  waivers?: DdWaiver[];
  invoiceId?: string;
  notes?: string;
}

export interface DdDashboardSummary {
  runningDemurrage: number;
  runningDetention: number;
  totalExposure: number;
  overdueCount: number;
  waivedCount: number;
  invoicedCount: number;
}
