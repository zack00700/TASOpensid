export type FeatureRequestStatus =
  | 'DRAFT'
  | 'CLARIFYING'
  | 'READY_FOR_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'IN_PROGRESS'
  | 'DONE';

export type TicketCategory =
  | 'UI_UX'
  | 'BILLING'
  | 'EDI'
  | 'REPORTING'
  | 'PERFORMANCE'
  | 'INTEGRATION'
  | 'OPERATIONS'
  | 'COMPLIANCE'
  | 'OTHER';

export interface ConversationMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp?: string;
}

export interface StatusChange {
  fromStatus?: string;
  toStatus: string;
  changedBy?: string;
  changedAt?: string;
  reason?: string;
}

export interface InternalComment {
  commentId?: string;
  authorId?: string;
  content: string;
  createdAt?: string;
}

export interface StructuredInput {
  name: string;
  type?: string;
  source?: string;
}

export interface StructuredOutput {
  name: string;
  description?: string;
}

export interface StructuredTrigger {
  event?: string;
  source?: string;
}

export interface StructuredEdgeCase {
  case: string;
  handling?: string;
}

export interface StructuredSummaryData {
  title?: string;
  category?: string;
  actor?: string;
  businessPain?: string;
  frequency?: string;
  trigger?: StructuredTrigger;
  inputs?: StructuredInput[];
  outputs?: StructuredOutput[];
  formula?: string | null;
  edgeCases?: StructuredEdgeCase[];
  integrations?: string[];
  acceptanceCriteria?: string[];
  assumptions?: string[];
  openQuestions?: string[];
}

export interface FeatureRequest {
  id?: string;
  title: string;
  description: string;
  status: FeatureRequestStatus;
  createdBy?: string;
  createdAt?: string;
  conversation?: ConversationMessage[];
  clarificationsDone?: boolean;
  priority?: number;
  estimatedEffort?: 'S' | 'M' | 'L' | 'XL';
  tags?: string[];
  approvedBy?: string;
  approvedAt?: string;
  rejectedReason?: string;
  structuredSummary?: string;
  structuredSummaryData?: StructuredSummaryData;
  // Ticketing fields
  ticketNumber?: string;
  category?: TicketCategory;
  assignedTo?: string;
  dueDate?: string;
  milestone?: string;
  statusHistory?: StatusChange[];
  internalComments?: InternalComment[];
}
