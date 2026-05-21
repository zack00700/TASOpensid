export type PaymentMethod = 'WIRE_TRANSFER' | 'CHECK' | 'CASH' | 'CREDIT_CARD' | 'DIRECT_DEBIT' | 'ACH' | 'CRYPTO' | 'OTHER';
export type PaymentStatus = 'PENDING' | 'CLEARED' | 'FAILED' | 'REVERSED' | 'CANCELLED';

export interface PaymentAllocation {
  allocationId?: string;
  invoiceId: string;
  invoiceNumber?: string;
  allocatedAmount: number;
  allocationDate?: string;
}

export interface Payment {
  id?: string;
  paymentReference?: string;
  customerId?: string;
  customerName: string;
  paymentDate: string;
  receivedDate?: string;
  amount: number;
  currency: string;
  paymentMethod: PaymentMethod;
  status?: PaymentStatus;
  bankReference?: string;
  checkNumber?: string;
  notes?: string;
  allocations?: PaymentAllocation[];
  unallocatedAmount?: number;
  processedBy?: string;
  reversalReason?: string;
}
