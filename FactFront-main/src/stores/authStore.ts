import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { PublicClientApplication, AccountInfo } from '@azure/msal-browser';
import { msalConfig, loginRequest } from '@/config/msal';

interface User {
  id: string;
  username: string;
  email?: string;
  fullName?: string;
  roles?: string[];
  role?: string;
}

interface LoginCredentials {
  username: string;
  password: string;
}

interface LoginResponse {
  token: string;
  user: User;
  expiresIn?: number;
}

/**
 * Centralized Authentication Store
 *
 * This is the single source of truth for all authentication state.
 * All components, services, and interceptors should use this store.
 */
export const useAuthStore = defineStore('auth', () => {
  // Configuration
  const TOKEN_KEY = 'terminal_billing_token';
  const USER_KEY = 'terminal_billing_user';
  const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

  // State
  const token = ref<string | null>(null);
  const user = ref<User | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  // MSAL State
  let msalInstance: PublicClientApplication | null = null;
  const azureAccount = ref<AccountInfo | null>(null);
  const useAzureAD = ref(import.meta.env.VITE_AZURE_AD_CLIENT_ID ? true : false);

  // Computed
  const isAuthenticated = computed(() => {
    if (!token.value) {
      return false;
    }

    // Check if token is expired
    try {
      const payload = parseJWT(token.value);
      if (payload.exp && payload.exp * 1000 < Date.now()) {
        clearAuth();
        return false;
      }
      return true;
    } catch (error) {
      return !!token.value; // Fallback if token is not JWT
    }
  });

  const userRole = computed(() => user.value?.role);
  const userName = computed(() => user.value?.username);
  const userRoles = computed(() => user.value?.roles || []);

  // Actions

  /**
   * Initialize auth state from localStorage
   */
  async function initialize() {
    loading.value = true;
    try {
      // Initialize MSAL if Azure AD is enabled
      if (useAzureAD.value) {
        // Wrap with a timeout so a hanging MSAL call never blocks the UI forever
        await Promise.race([
          initializeMsal(),
          new Promise<void>(resolve => setTimeout(resolve, 10_000)),
        ]);
      }

      // Fallback to traditional auth if no Azure AD token
      if (!token.value) {
        const storedToken = localStorage.getItem(TOKEN_KEY);
        const storedUser = localStorage.getItem(USER_KEY);

        if (storedToken) {
          token.value = storedToken;
        }

        if (storedUser) {
          try {
            user.value = JSON.parse(storedUser);
          } catch (e) {
            console.error('Failed to parse stored user data:', e);
            localStorage.removeItem(USER_KEY);
          }
        }

        // Validate authentication on initialization
        if (token.value && !isAuthenticated.value) {
          clearAuth();
        }
      }
    } finally {
      loading.value = false;
    }
  }

  /**
   * Login user
   */
  async function login(credentials: LoginCredentials): Promise<boolean> {
    loading.value = true;
    error.value = null;

    try {
      const loginUrl = `${API_BASE_URL}/auth/login`;

      const response = await fetch(loginUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(credentials),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || 'Login failed');
      }

      const data: LoginResponse = await response.json();

      // Adapt user data structure
      let userData = data.user;
      if (!userData && (data as any).username) {
        // If user data is directly in response
        userData = {
          id: (data as any).id || (data as any).userId || '1',
          username: (data as any).username,
          email: (data as any).email,
          role: (data as any).role || (data as any).userRole || 'user',
        };
      }

      // Update state
      setToken(data.token);
      setUser(userData);

      return true;
    } catch (err: any) {
      console.error('❌ Login error:', err);
      error.value = err.message || 'Connection error';
      return false;
    } finally {
      loading.value = false;
    }
  }

  /**
   * Login with Azure AD using MSAL redirect flow.
   * The page navigates to Azure AD; the result is processed by initializeMsal()
   * on the next page load via handleRedirectPromise().
   */
  async function loginWithAzureAD(): Promise<boolean> {
    loading.value = true;
    error.value = null;

    try {
      if (!msalInstance) {
        await initializeMsal();
      }
      if (!msalInstance) {
        throw new Error('MSAL instance not initialized');
      }

      // Redirect flow — page navigates away to Azure AD
      await msalInstance.loginRedirect(loginRequest);
      return true; // unreachable in practice; page navigates away
    } catch (err: any) {
      console.error('❌ Azure AD login error:', err);
      error.value = err.message || 'Azure AD login failed';
      loading.value = false;
      return false;
    }
  }

  /**
   * Get a fresh Azure AD access token (silently if possible, redirect if not).
   */
  async function getAzureAccessToken(): Promise<string | null> {
    if (!msalInstance || !azureAccount.value) {
      return token.value;
    }

    try {
      const response = await msalInstance.acquireTokenSilent({
        ...loginRequest,
        account: azureAccount.value,
      });
      setToken(response.accessToken);
      return response.accessToken;
    } catch (err: any) {
      console.warn('Silent token acquisition failed, redirecting to login:', err?.errorCode);
      // Fall back to redirect — page navigates to Azure AD
      try {
        await msalInstance.acquireTokenRedirect(loginRequest);
      } catch (redirectErr) {
        console.error('Token redirect failed:', redirectErr);
      }
      return null;
    }
  }

  /**
   * Initialize MSAL instance
   * Must be called on every page load to process redirect responses.
   */
  async function initializeMsal(): Promise<void> {
    if (!useAzureAD.value) {
      return;
    }

    try {
      msalInstance = new PublicClientApplication(msalConfig);
      await msalInstance.initialize();

      // Process any pending redirect response first (return from Azure AD login/logout)
      const redirectResult = await msalInstance.handleRedirectPromise();
      if (redirectResult) {
        msalInstance.setActiveAccount(redirectResult.account);
        azureAccount.value = redirectResult.account;

        let cachedRoles: string[] = [];
        try {
          const payload = parseJWT(redirectResult.accessToken);
          cachedRoles = payload.roles || payload['realm_access']?.roles || payload.groups || [];
        } catch { /* ignore */ }

        setToken(redirectResult.accessToken);
        setUser({
          id: redirectResult.account?.localAccountId || redirectResult.account?.homeAccountId || '',
          username: redirectResult.account?.username || '',
          email: redirectResult.account?.username || '',
          fullName: redirectResult.account?.name || '',
          roles: cachedRoles,
        });
        return;
      }

      // No redirect response — check for a cached account and try silent token renewal
      const accounts = msalInstance.getAllAccounts();
      if (accounts.length > 0) {
        msalInstance.setActiveAccount(accounts[0]);
        azureAccount.value = accounts[0];

        try {
          const response = await msalInstance.acquireTokenSilent({
            ...loginRequest,
            account: accounts[0],
          });

          let cachedRoles: string[] = [];
          try {
            const payload = parseJWT(response.accessToken);
            cachedRoles = payload.roles || payload['realm_access']?.roles || payload.groups || [];
          } catch { /* ignore */ }

          setToken(response.accessToken);
          setUser({
            id: accounts[0].localAccountId || accounts[0].homeAccountId,
            username: accounts[0].username,
            email: accounts[0].username,
            fullName: accounts[0].name || '',
            roles: cachedRoles,
          });
        } catch (err: any) {
          // Session expired or interaction required — clear stale account so user must log in again
          console.warn('Silent token renewal failed, clearing stale session:', err?.errorCode || err?.name);
          msalInstance.setActiveAccount(null);
          azureAccount.value = null;
        }
      }
    } catch (err) {
      console.error('Failed to initialize MSAL:', err);
    }
  }

  /**
   * Logout user
   */
  async function logout(): Promise<void> {
    loading.value = true;

    // Azure AD logout via redirect (avoids COOP popup issues)
    if (msalInstance && azureAccount.value && useAzureAD.value) {
      clearAuth(); // clear tokens before navigating away
      try {
        await msalInstance.logoutRedirect({
          account: azureAccount.value,
          postLogoutRedirectUri: window.location.origin,
        });
      } catch {
        window.location.href = '/';
      }
      return; // page navigates away
    }

    // Traditional auth logout
    try {
      if (token.value) {
        await fetch(`${API_BASE_URL}/auth/logout`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${token.value}`,
            'Content-Type': 'application/json',
          },
        }).catch(() => {});
      }
    } finally {
      clearAuth();
      loading.value = false;
      window.location.href = '/';
    }
  }

  /**
   * Check if user has a specific role
   */
  function hasRole(roleName: string): boolean {
    if (!user.value) return false;

    // Check in roles array
    if (user.value.roles && Array.isArray(user.value.roles)) {
      return user.value.roles.includes(roleName);
    }

    // Fallback to simple role
    return user.value.role === roleName;
  }

  /**
   * Check if user is admin.
   * Supports multiple naming conventions from different identity providers:
   * - Keycloak: ROLE_ADMIN, ROLE_TEMPLATES_ADMIN
   * - Azure AD app roles: Admin, admin, Administrator, BillingAdmin
   * - Legacy: role === 'admin'
   * - Fallback: any Azure AD authenticated user with at least one role
   */
  function isAdmin(): boolean {
    const adminRoleNames = [
      'ROLE_ADMIN', 'ROLE_TEMPLATES_ADMIN',
      'Admin', 'admin', 'Administrator', 'administrator',
      'BillingAdmin', 'billing_admin',
    ];
    if (adminRoleNames.some(r => hasRole(r))) return true;
    if (userRole.value === 'admin') return true;
    // Fallback: Azure AD authenticated users with any role are treated as admin
    // (this is an internal tool — any provisioned role implies authorization)
    if (useAzureAD.value && userRoles.value.length > 0) return true;
    return false;
  }

  /**
   * Get authorization headers for requests
   */
  function getAuthHeaders(): Record<string, string> {
    return token.value ? { 'Authorization': `Bearer ${token.value}` } : {};
  }

  /**
   * Clear error state
   */
  function clearError() {
    error.value = null;
  }

  // Private helpers

  /**
   * Set token in state and localStorage
   */
  function setToken(newToken: string) {
    token.value = newToken;
    localStorage.setItem(TOKEN_KEY, newToken);
  }

  /**
   * Set user in state and localStorage
   */
  function setUser(newUser: User) {
    user.value = newUser;
    localStorage.setItem(USER_KEY, JSON.stringify(newUser));
  }

  /**
   * Clear all auth data
   */
  function clearAuth() {
    token.value = null;
    user.value = null;
    error.value = null;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  /**
   * Parse JWT token
   */
  function parseJWT(jwtToken: string): any {
    try {
      const base64Url = jwtToken.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch {
      throw new Error('Invalid token');
    }
  }

  return {
    // State
    token,
    user,
    loading,
    error,
    azureAccount,
    useAzureAD,

    // Computed
    isAuthenticated,
    userRole,
    userName,
    userRoles,

    // Actions
    initialize,
    login,
    loginWithAzureAD,
    logout,
    hasRole,
    isAdmin,
    getAuthHeaders,
    getAzureAccessToken,
    clearError,
  };
});
