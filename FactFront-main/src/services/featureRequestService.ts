import api from '../plugin/axios';
import type { FeatureRequest, FeatureRequestStatus, TicketCategory } from '../types/featureRequest';

const BASE = '/feature-requests';

export async function getFeatureRequests(params?: {
  status?: FeatureRequestStatus;
  page?: number;
  size?: number;
}): Promise<FeatureRequest[]> {
  try {
    const response = await api.get(BASE, { params: params ?? {} });
    return response.data;
  } catch (error) {
    console.error('[FeatureRequestService] Failed to list feature requests:', error);
    throw error;
  }
}

export async function getFeatureRequest(id: string): Promise<FeatureRequest> {
  try {
    const response = await api.get(`${BASE}/${encodeURIComponent(id)}`);
    return response.data;
  } catch (error) {
    console.error(`[FeatureRequestService] Failed to get feature request ${id}:`, error);
    throw error;
  }
}

export async function createFeatureRequest(data: {
  title: string;
  description: string;
  category?: TicketCategory;
}): Promise<FeatureRequest> {
  try {
    const response = await api.post(BASE, data);
    return response.data;
  } catch (error) {
    console.error('[FeatureRequestService] Failed to create feature request:', error);
    throw error;
  }
}

export async function sendChatMessage(
  id: string,
  message: string
): Promise<FeatureRequest> {
  try {
    const response = await api.post(`${BASE}/${encodeURIComponent(id)}/chat`, { message });
    return response.data;
  } catch (error) {
    console.error(`[FeatureRequestService] Failed to send chat message for ${id}:`, error);
    throw error;
  }
}

export async function updateStatus(
  id: string,
  data: {
    status: FeatureRequestStatus;
    reason?: string;
    priority?: number;
    estimatedEffort?: string;
    assignedTo?: string;
    milestone?: string;
    dueDate?: string;
  }
): Promise<FeatureRequest> {
  try {
    const response = await api.patch(`${BASE}/${encodeURIComponent(id)}/status`, data);
    return response.data;
  } catch (error) {
    console.error(`[FeatureRequestService] Failed to update status for ${id}:`, error);
    throw error;
  }
}

export async function getBacklog(): Promise<FeatureRequest[]> {
  try {
    const response = await api.get(`${BASE}/backlog`);
    return response.data;
  } catch (error) {
    console.error('[FeatureRequestService] Failed to get backlog:', error);
    throw error;
  }
}

export async function addComment(id: string, content: string): Promise<FeatureRequest> {
  try {
    const response = await api.post(`${BASE}/${encodeURIComponent(id)}/comments`, { content });
    return response.data;
  } catch (error) {
    console.error(`[FeatureRequestService] Failed to add comment for ${id}:`, error);
    throw error;
  }
}

export async function assignRequest(id: string, assignedTo: string): Promise<FeatureRequest> {
  try {
    const response = await api.post(`${BASE}/${encodeURIComponent(id)}/assign`, { assignedTo });
    return response.data;
  } catch (error) {
    console.error(`[FeatureRequestService] Failed to assign request ${id}:`, error);
    throw error;
  }
}

export async function getMilestones(): Promise<string[]> {
  try {
    const response = await api.get(`${BASE}/milestones`);
    return response.data;
  } catch (error) {
    console.error('[FeatureRequestService] Failed to get milestones:', error);
    throw error;
  }
}
