import axios from 'axios';
import { useAuthStore } from '../stores/authStore';

// Use the VITE_API_URL environment variable when available. Otherwise
// fall back to the local development API.
const baseURL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL,
  timeout: 60000, // Increased to 60s for Azure backend cold start
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add authentication headers
api.interceptors.request.use(
  async (config) => {
    // Get auth store and add authorization headers
    const authStore = useAuthStore();

    // If using Azure AD, try to get a fresh token
    if (authStore.useAzureAD && authStore.azureAccount) {
      try {
        const token = await authStore.getAzureAccessToken();
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        } else {
          console.warn('⚠️ [AUTH] No Azure AD token available');
        }
      } catch (error) {
        console.error('❌ [AUTH] Failed to get Azure AD token:', error);
        // Fallback to stored token
        const authHeaders = authStore.getAuthHeaders();
        if (authHeaders) {
          config.headers = { ...config.headers, ...authHeaders };
        }
      }
    } else {
      // Use traditional auth headers
      const authHeaders = authStore.getAuthHeaders();
      if (authHeaders) {
        config.headers = { ...config.headers, ...authHeaders };
      } else {
        console.warn('⚠️ [AUTH] No auth headers available');
      }
    }

    return config;
  },
  (error) => {
    console.error('❌ [REQUEST] Request interceptor error:', error);
    return Promise.reject(error);
  }
);

// Response interceptor to handle authentication errors
api.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error) => {

    const originalRequest = error.config;

    // Handle 401 Unauthorized errors
    if (error.response?.status === 401 && !originalRequest._retry) {
      console.warn('⚠️ [AUTH] Authentication failed (401) - Attempting token refresh');

      const authStore = useAuthStore();

      // If using Azure AD, try to refresh the token and retry
      if (authStore.useAzureAD && authStore.azureAccount) {
        originalRequest._retry = true;

        try {
          // Try to get a fresh token
          const token = await authStore.getAzureAccessToken();
          if (token) {
            // Update the authorization header and retry
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          }
        } catch (refreshError) {
          console.error('❌ [AUTH] Token refresh failed:', refreshError);
          // Token refresh failed, logout user
          await authStore.logout();
        }
      } else {
        // Traditional auth - logout immediately
        await authStore.logout();
      }

      // Emit event for any listeners
      window.dispatchEvent(new CustomEvent('auth-error', {
        detail: {
          message: authStore.useAzureAD
            ? 'Backend not configured for Azure AD. See console for details.'
            : 'Session expired',
          status: 401
        }
      }));
    }

    return Promise.reject(error);
  }
);

export default api;