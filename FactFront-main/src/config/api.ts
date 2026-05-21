// Configuration de l'API selon l'environnement
export const API_CONFIG = {
    // URL de base depuis les variables d'environnement
    BASE_URL: import.meta.env.VITE_API_URL || '/api',
    
    // Timeout pour les requêtes
    TIMEOUT: 10000,
    
    // Headers par défaut
    DEFAULT_HEADERS: {
      'Content-Type': 'application/json',
    },
    
    // Endpoints
    ENDPOINTS: {
      LOGIN: '/auth/login',
      LOGOUT: '/auth/logout',
      REFRESH: '/auth/refresh',
      USER_PROFILE: '/auth/me',
    }
  };
  
  // Helper pour construire les URLs complètes
  export function buildApiUrl(endpoint: string): string {
    return `${API_CONFIG.BASE_URL}${endpoint}`;
  }
  
  // Export des URLs courantes
  export const API_URLS = {
    LOGIN: buildApiUrl(API_CONFIG.ENDPOINTS.LOGIN),
    LOGOUT: buildApiUrl(API_CONFIG.ENDPOINTS.LOGOUT),
    REFRESH: buildApiUrl(API_CONFIG.ENDPOINTS.REFRESH),
    USER_PROFILE: buildApiUrl(API_CONFIG.ENDPOINTS.USER_PROFILE),
  };