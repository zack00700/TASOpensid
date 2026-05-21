import { useAuthStore } from '../stores/authStore';

/**
 * Middleware to protect routes requiring authentication
 * Call at the beginning of protected components or pages
 */
export function useRouteGuard() {
  const authStore = useAuthStore();

  /**
   * Check authentication on component mount
   */
  const checkAuthentication = () => {
    if (!authStore.isAuthenticated) {
      redirectToLogin();
      return false;
    }
    return true;
  };

  return {
    checkAuthentication,
  };
}

/**
 * Utility function to check authentication synchronously
 * Useful for quick checks in components
 */
export function isUserAuthenticated(): boolean {
  const authStore = useAuthStore();
  return authStore.isAuthenticated;
}

/**
 * Function to call at application startup to check auth
 * Integrate into your App.vue or main.ts
 */
export function initializeAuthGuard() {
  // Check if on root/home page (login is shown automatically by App.vue)
  const isHomePage = window.location.pathname === '/';

  // If not on home page, check authentication
  if (!isHomePage) {
    const authStore = useAuthStore();
    if (!authStore.isAuthenticated) {
      redirectToLogin();
    }
  }
}

/**
 * Vue directive to protect elements based on authentication
 * Usage: v-auth or v-auth:role="'admin'"
 */
export const authDirective = {
  mounted(el: HTMLElement, binding: any) {
    const authStore = useAuthStore();

    // Hide element if not authenticated
    if (!authStore.isAuthenticated) {
      el.style.display = 'none';
      return;
    }

    // Check for specific role requirement
    if (binding.arg === 'role' && binding.value) {
      const requiredRole = binding.value;
      if (authStore.userRole !== requiredRole) {
        el.style.display = 'none';
        return;
      }
    }

    // Show element if all conditions are met
    el.style.display = '';
  },

  updated(el: HTMLElement, binding: any) {
    // Re-run logic on updates
    authDirective.mounted(el, binding);
  },
};

/**
 * Helper function to redirect to login page
 */
function redirectToLogin(): void {
  // Save current URL for redirect after login
  const currentPath = window.location.pathname + window.location.search;
  if (currentPath !== '/') {
    sessionStorage.setItem('redirectAfterLogin', currentPath);
  }

  // Redirect to root - App.vue will show Login component when not authenticated
  window.location.href = '/';
}