import { createRouter, createWebHistory } from 'vue-router';
import { h, defineAsyncComponent } from 'vue';
import { useAuthStore } from '../stores/authStore';

/** Wraps a lazy component with AuthGuard (admin role), preserving the existing access-denied UI. */
function admin(lazyComponent: () => Promise<any>, message: string) {
  return defineAsyncComponent({
    loader: async () => {
      const [{ default: comp }, { default: AuthGuard }] = await Promise.all([
        lazyComponent(),
        import('../components/AuthGuard.vue'),
      ]);
      return {
        render() {
          return h(
            AuthGuard,
            { requireRole: 'admin', fallbackMessage: message },
            { default: () => h(comp) }
          );
        },
      };
    },
  });
}

const routes = [
  {
    path: '/',
    redirect: '/invoices',
  },
  {
    path: '/invoices',
    component: () => import('../components/Invoices.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/items',
    component: () => import('../components/Items.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/events-config',
    component: admin(
      () => import('../components/EventsConfiguration.vue'),
      'Seuls les administrateurs peuvent configurer les événements.'
    ),
    meta: { requiresAuth: true },
  },
  {
    path: '/vessels',
    component: () => import('../components/VesselVisits.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/vessels/statistics',
    component: () => import('../components/VesselVisitStatistics.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/bills',
    component: () => import('../components/BillOfLading.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/events',
    component: () => import('../components/Events.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/vessel-registry',
    component: () => import('../components/Vessels.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/third-parties',
    component: admin(
      () => import('../components/ThirdParties.vue'),
      'Seuls les administrateurs peuvent gérer les tiers.'
    ),
    meta: { requiresAuth: true },
  },
  {
    path: '/admin/users',
    component: admin(
      () => import('../components/ApplicationUsers.vue'),
      'Seuls les administrateurs peuvent gérer les utilisateurs applicatifs.'
    ),
    meta: { requiresAuth: true },
  },
  {
    path: '/iso-codes',
    component: () => import('../components/IsoCodes.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/archetypes',
    component: () => import('../components/ContainerArchetypes.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/contracts',
    component: () => import('../components/Contracts.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/tariffs',
    component: () => import('../components/Tariffs.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/template-designer',
    component: admin(
      () => import('../components/InvoiceTemplateDesigner.vue'),
      'Seuls les administrateurs peuvent utiliser le designer de templates.'
    ),
    meta: { requiresAuth: true },
  },
  {
    path: '/i18n',
    component: () => import('../pages/I18nAdmin.vue'),
    meta: { requiresAuth: true, roles: ['ROLE_ADMIN'] },
  },
  {
    path: '/configuration/sequences',
    name: 'sequences',
    component: () => import('../components/Sequences.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/edi',
    component: () => import('../components/EdiMessages.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/payments',
    component: () => import('../components/Payments.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/dd',
    component: () => import('../components/DdDashboard.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/customs',
    component: () => import('../components/CustomsDeclarations.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/dd/rules',
    component: () => import('../components/DdRules.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/forecasting',
    component: () => import('../components/CapacityForecastDashboard.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/fr-dashboard',
    component: () => import('../components/FeatureRequestDashboard.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/backlog',
    component: () => import('../components/FeatureRequestBacklog.vue'),
    meta: { requiresAuth: true },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// Navigation guard: redirect unauthenticated users to root (App.vue shows Login there).
router.beforeEach((to, _from, next) => {
  if (to.meta.requiresAuth) {
    const authStore = useAuthStore();
    if (!authStore.isAuthenticated) {
      // Save the intended destination so Login.vue can restore it after login.
      if (to.fullPath !== '/') {
        sessionStorage.setItem('redirectAfterLogin', to.fullPath);
      }
      // Cancel the navigation — App.vue always shows Login when !isAuthenticated,
      // regardless of route. Using next(false) avoids an infinite redirect loop
      // caused by '/' → redirect '/invoices' → guard → next('/') → repeat.
      next(false);
      return;
    }
  }
  next();
});

export default router;
