export interface EdiMessage {
  id?: string;
  direction: 'INBOUND' | 'OUTBOUND';
  format: 'EDIFACT' | 'X12' | 'CSV' | 'JSON' | 'XML';
  messageType?: string;
  partnerId?: string;
  rawPayload?: string;
  status: 'RECEIVED' | 'PROCESSING' | 'PROCESSED' | 'FAILED' | 'SKIPPED';
  relatedEntityId?: string;
  processingNote?: string;
  messageDate?: string;
  processedAt?: string;
  attempts?: number;
}

export interface EdiMessageListParams {
  status?: string;
  partnerId?: string;
  page?: number;
  size?: number;
}
