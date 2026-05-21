import { Configuration, PopupRequest } from '@azure/msal-browser';

/**
 * Configuration for Azure AD MSAL authentication
 * Reads from environment variables set in .env files
 */
export const msalConfig: Configuration = {
  auth: {
    clientId: import.meta.env.VITE_AZURE_AD_CLIENT_ID || '',
    authority: import.meta.env.VITE_AZURE_AD_AUTHORITY || '',
    redirectUri: import.meta.env.VITE_AZURE_AD_REDIRECT_URI || window.location.origin,
  },
  cache: {
    cacheLocation: 'localStorage', // Store tokens in localStorage
    storeAuthStateInCookie: false, // Set to true for IE11/Edge support
  },
};

/**
 * Scopes for requesting access tokens
 * Using the client ID to request default scopes
 */
export const loginRequest: PopupRequest = {
  scopes: [
    import.meta.env.VITE_AZURE_AD_SCOPES ||
    `api://${import.meta.env.VITE_AZURE_AD_CLIENT_ID}/.default`
  ],
};

/**
 * Scopes for silent token acquisition
 */
export const tokenRequest = {
  scopes: [
    import.meta.env.VITE_AZURE_AD_SCOPES ||
    `api://${import.meta.env.VITE_AZURE_AD_CLIENT_ID}/.default`
  ],
};
