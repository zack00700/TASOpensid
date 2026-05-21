<script setup lang="ts">
import { computed } from 'vue';
import { storeToRefs } from 'pinia';
import { useAuthStore } from '../stores/authStore';
import { ShieldAlert, Lock } from 'lucide-vue-next';

interface Props {
  requireRole?: string;
  fallbackMessage?: string;
  showIcon?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  requireRole: '',
  fallbackMessage: 'Vous n\'avez pas les permissions nécessaires pour accéder à cette section.',
  showIcon: true
});

const authStore = useAuthStore();
const { isAuthenticated, userRole, userRoles } = storeToRefs(authStore);

const ADMIN_ROLES = ['ROLE_ADMIN', 'ROLE_TEMPLATES_ADMIN', 'Admin', 'admin', 'Administrator'];

// Read userRoles directly via storeToRefs so Vue tracks reactivity correctly
const hasPermission = computed(() => {
  if (!isAuthenticated.value) return false;
  if (!props.requireRole) return true;

  const roles = userRoles.value;

  if (props.requireRole === 'admin') {
    if (roles.some(r => ADMIN_ROLES.includes(r))) return true;
    if (roles.length > 0) return true; // any Azure AD role = admin (internal tool)
    return userRole.value === 'admin';
  }

  return roles.includes(props.requireRole) || userRole.value === props.requireRole;
});

const canShowContent = computed(() => isAuthenticated.value && hasPermission.value);
</script>

<template>
  <!-- Contenu autorisé -->
  <div v-if="canShowContent">
    <slot />
  </div>

  <!-- Message d'accès refusé -->
  <div v-else-if="isAuthenticated && !hasPermission" class="flex items-center justify-center min-h-[400px]">
    <div class="text-center max-w-md mx-auto p-6">
      <div class="bg-amber-50 border border-amber-200 rounded-lg p-6">
        <div class="flex justify-center mb-4" v-if="showIcon">
          <div class="flex-shrink-0">
            <ShieldAlert class="h-12 w-12 text-amber-500" />
          </div>
        </div>

        <div>
          <h3 class="text-lg font-medium text-amber-800 mb-2">
            {{ $t('authGuard.title') }}
          </h3>

          <p class="text-sm text-amber-700 mb-4">
            {{ fallbackMessage }}
          </p>

          <div class="bg-amber-100 rounded-md p-3">
            <div class="flex items-center text-xs text-amber-700">
              <Lock class="h-4 w-4 mr-2" />
              <div class="text-left">
                <div><strong>{{ $t('authGuard.requiredRole') }}</strong> {{ requireRole || 'Aucun rôle spécifique' }}</div>
                <div><strong>{{ $t('authGuard.yourRole') }}</strong> {{ userRole || 'Non défini' }}</div>
                <div v-if="userRoles.length > 0"><strong>{{ $t('authGuard.jwtRoles') }}</strong> {{ userRoles.join(', ') }}</div>
              </div>
            </div>
          </div>

          <p class="text-xs text-amber-600 mt-3">
            {{ $t('authGuard.contactAdmin') }}
          </p>
        </div>
      </div>
    </div>
  </div>

  <!-- État de chargement ou non authentifié -->
  <div v-else class="flex items-center justify-center min-h-[400px]">
    <div class="text-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto mb-2"></div>
      <p class="text-sm text-gray-600">{{ $t('authGuard.verifying') }}</p>
    </div>
  </div>
</template>
