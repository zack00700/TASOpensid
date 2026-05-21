import api from '../plugin/axios';
import type { EntraUser, InviteRequest } from '../types/entra-user';

const BASE = '/admin/users';

export const listEntraUsers = async (): Promise<EntraUser[]> => {
  try {
    const response = await api.get(BASE);
    return response.data;
  } catch (error) {
    console.error('[UserAdminService] Failed to list users:', error);
    throw error;
  }
};

export const listEntraRoles = async (): Promise<string[]> => {
  try {
    const response = await api.get(`${BASE}/roles`);
    return response.data;
  } catch (error) {
    console.error('[UserAdminService] Failed to list roles:', error);
    throw error;
  }
};

export const inviteEntraUser = async (request: InviteRequest): Promise<EntraUser> => {
  try {
    const response = await api.post(`${BASE}/invite`, request);
    return response.data;
  } catch (error) {
    console.error('[UserAdminService] Failed to invite user:', error);
    throw error;
  }
};

export const addRoleToUser = async (userId: string, role: string): Promise<EntraUser> => {
  try {
    const response = await api.post(
      `${BASE}/${encodeURIComponent(userId)}/roles/${encodeURIComponent(role)}`
    );
    return response.data;
  } catch (error) {
    console.error(`[UserAdminService] Failed to add role ${role} to user ${userId}:`, error);
    throw error;
  }
};

export const removeRoleFromUser = async (userId: string, role: string): Promise<EntraUser> => {
  try {
    const response = await api.delete(
      `${BASE}/${encodeURIComponent(userId)}/roles/${encodeURIComponent(role)}`
    );
    return response.data;
  } catch (error) {
    console.error(`[UserAdminService] Failed to remove role ${role} from user ${userId}:`, error);
    throw error;
  }
};

export const setEntraUserEnabled = async (userId: string, enabled: boolean): Promise<EntraUser> => {
  try {
    const action = enabled ? 'enable' : 'disable';
    const response = await api.post(`${BASE}/${encodeURIComponent(userId)}/${action}`);
    return response.data;
  } catch (error) {
    console.error(`[UserAdminService] Failed to toggle user ${userId}:`, error);
    throw error;
  }
};

export const userAdminService = {
  listEntraUsers,
  listEntraRoles,
  inviteEntraUser,
  addRoleToUser,
  removeRoleFromUser,
  setEntraUserEnabled,
};

export default userAdminService;
