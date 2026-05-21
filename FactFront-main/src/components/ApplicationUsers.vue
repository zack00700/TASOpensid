<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { RefreshCw, UserPlus, X, AlertCircle, CheckCircle, XCircle, Mail } from 'lucide-vue-next';
import {
  listEntraUsers,
  listEntraRoles,
  addRoleToUser,
  removeRoleFromUser,
  setEntraUserEnabled,
  inviteEntraUser,
} from '../services/userAdminService';
import type { EntraUser, InviteRequest } from '../types/entra-user';

const { t } = useI18n();

// ── State ────────────────────────────────────────────────────────────────────

const users = ref<EntraUser[]>([]);
const availableRoles = ref<string[]>([]);
const isLoading = ref(false);
const errorMessage = ref('');
const searchQuery = ref('');
const rolePending = ref<Record<string, boolean>>({});

// Invite modal
const showInvite = ref(false);
const isInviting = ref(false);
const inviteForm = ref<InviteRequest>({ email: '', displayName: '', roles: [] });

// ── Data fetching ─────────────────────────────────────────────────────────────

const fetchAll = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  try {
    const [u, r] = await Promise.all([listEntraUsers(), listEntraRoles()]);
    users.value = u;
    availableRoles.value = r;
  } catch (e: any) {
    if (e?.response?.status === 503) {
      errorMessage.value = t('applicationUsers.error.entraNotConfigured');
    } else if (e?.response?.status === 403) {
      errorMessage.value = t('applicationUsers.error.accessDenied');
    } else {
      errorMessage.value = t('applicationUsers.error.loadFailed');
    }
  } finally {
    isLoading.value = false;
  }
};

onMounted(fetchAll);

// ── Derived ───────────────────────────────────────────────────────────────────

const filteredUsers = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  if (!q) return users.value;
  return users.value.filter((u) =>
    [u.displayName, u.mail, u.userPrincipalName, u.jobTitle]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(q))
  );
});

// ── Role toggling ─────────────────────────────────────────────────────────────

const roleKey = (userId: string, role: string) => `${userId}::${role}`;

const toggleRole = async (user: EntraUser, role: string) => {
  const key = roleKey(user.id, role);
  if (rolePending.value[key]) return;
  rolePending.value = { ...rolePending.value, [key]: true };
  try {
    const updated = user.roles.includes(role)
      ? await removeRoleFromUser(user.id, role)
      : await addRoleToUser(user.id, role);
    replaceUser(updated);
  } catch {
    errorMessage.value = t('applicationUsers.error.roleUpdateFailed', { role, name: user.displayName ?? user.id });
  } finally {
    const next = { ...rolePending.value };
    delete next[key];
    rolePending.value = next;
  }
};

const toggleEnabled = async (user: EntraUser) => {
  try {
    const updated = await setEntraUserEnabled(user.id, !user.accountEnabled);
    replaceUser(updated);
  } catch {
    errorMessage.value = user.accountEnabled
      ? t('applicationUsers.error.disableFailed', { name: user.displayName ?? user.id })
      : t('applicationUsers.error.enableFailed', { name: user.displayName ?? user.id });
  }
};

const replaceUser = (updated: EntraUser) => {
  const idx = users.value.findIndex((u) => u.id === updated.id);
  if (idx >= 0) users.value[idx] = updated;
  else users.value.push(updated);
};

// ── Invite ────────────────────────────────────────────────────────────────────

const openInvite = () => {
  inviteForm.value = { email: '', displayName: '', roles: ['ROLE_USER'] };
  showInvite.value = true;
};

const submitInvite = async () => {
  if (!inviteForm.value.email || !inviteForm.value.email.includes('@')) {
    errorMessage.value = t('applicationUsers.error.invalidEmail');
    return;
  }
  isInviting.value = true;
  try {
    const created = await inviteEntraUser(inviteForm.value);
    replaceUser(created);
    showInvite.value = false;
  } catch {
    errorMessage.value = t('applicationUsers.error.inviteFailed');
  } finally {
    isInviting.value = false;
  }
};

const toggleInviteRole = (role: string) => {
  const current = inviteForm.value.roles ?? [];
  inviteForm.value.roles = current.includes(role)
    ? current.filter((r) => r !== role)
    : [...current, role];
};
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <!-- Header -->
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl">{{ t('applicationUsers.title') }}</h1>
          <p class="mt-1 text-sm text-gray-500">
            {{ availableRoles.length === 1
              ? t('applicationUsers.subtitle.one', { count: availableRoles.length })
              : t('applicationUsers.subtitle.other', { count: availableRoles.length }) }}
          </p>
        </div>
        <div class="flex items-center gap-3">
          <button
            @click="fetchAll"
            :disabled="isLoading"
            class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50"
          >
            <RefreshCw class="h-4 w-4 mr-2" :class="{ 'animate-spin': isLoading }" />
            {{ t('applicationUsers.button.refresh') }}
          </button>
          <button
            @click="openInvite"
            class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg text-sm font-medium text-white bg-blue-600 hover:bg-blue-700"
          >
            <UserPlus class="h-4 w-4 mr-2" />
            {{ t('applicationUsers.button.invite') }}
          </button>
        </div>
      </div>

      <!-- Error -->
      <div v-if="errorMessage" class="mb-6 bg-red-50 border border-red-200 rounded-lg p-4 flex items-start gap-3">
        <AlertCircle class="h-5 w-5 text-red-500 flex-shrink-0 mt-0.5" />
        <span class="text-sm text-red-700 flex-1">{{ errorMessage }}</span>
        <button @click="errorMessage = ''" class="text-red-500 hover:text-red-700">
          <X class="h-4 w-4" />
        </button>
      </div>

      <!-- Search -->
      <div class="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-6">
        <input
          v-model="searchQuery"
          type="text"
          :placeholder="t('applicationUsers.placeholder.search')"
          class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
        />
      </div>

      <!-- Table -->
      <div class="bg-white shadow-sm rounded-lg border border-gray-200 overflow-hidden">
        <div class="overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('applicationUsers.column.user') }}</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('applicationUsers.column.type') }}</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('applicationUsers.column.status') }}</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('applicationUsers.column.roles') }}</th>
                <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('applicationUsers.column.actions') }}</th>
              </tr>
            </thead>
            <tbody class="bg-white divide-y divide-gray-200">
              <tr v-if="isLoading" v-for="i in 4" :key="`skel-${i}`">
                <td v-for="j in 5" :key="j" class="px-4 py-3">
                  <div class="h-4 bg-gray-200 rounded animate-pulse w-3/4"></div>
                </td>
              </tr>

              <tr v-else v-for="user in filteredUsers" :key="user.id" class="hover:bg-gray-50">
                <td class="px-4 py-3 whitespace-nowrap">
                  <div class="text-sm font-medium text-gray-900">{{ user.displayName || '—' }}</div>
                  <div class="text-sm text-gray-500">{{ user.mail || user.userPrincipalName || '—' }}</div>
                  <div v-if="user.jobTitle" class="text-xs text-gray-400 mt-0.5">{{ user.jobTitle }}</div>
                </td>
                <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-700">
                  <span
                    class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border"
                    :class="user.userType === 'Guest'
                      ? 'bg-purple-50 text-purple-700 border-purple-200'
                      : 'bg-gray-50 text-gray-700 border-gray-200'"
                  >{{ user.userType || t('applicationUsers.userType.member') }}</span>
                </td>
                <td class="px-4 py-3 whitespace-nowrap">
                  <span
                    v-if="user.accountEnabled"
                    class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-50 text-green-700 border border-green-200"
                  >
                    <CheckCircle class="h-3 w-3 mr-1" />
                    {{ t('applicationUsers.status.active') }}
                  </span>
                  <span
                    v-else
                    class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600 border border-gray-200"
                  >
                    <XCircle class="h-3 w-3 mr-1" />
                    {{ t('applicationUsers.status.disabled') }}
                  </span>
                </td>
                <td class="px-4 py-3">
                  <div class="flex flex-wrap gap-1.5">
                    <button
                      v-for="role in availableRoles"
                      :key="role"
                      :disabled="rolePending[`${user.id}::${role}`]"
                      @click="toggleRole(user, role)"
                      class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border transition-colors disabled:opacity-50"
                      :class="user.roles.includes(role)
                        ? 'bg-blue-50 text-blue-700 border-blue-200 hover:bg-blue-100'
                        : 'bg-white text-gray-500 border-gray-200 hover:bg-gray-50'"
                      :title="user.roles.includes(role) ? t('applicationUsers.role.remove', { role }) : t('applicationUsers.role.assign', { role })"
                    >
                      {{ role }}
                    </button>
                  </div>
                </td>
                <td class="px-4 py-3 whitespace-nowrap text-right text-sm">
                  <button
                    @click="toggleEnabled(user)"
                    class="text-sm font-medium"
                    :class="user.accountEnabled ? 'text-red-600 hover:text-red-800' : 'text-green-600 hover:text-green-800'"
                  >
                    {{ user.accountEnabled ? t('applicationUsers.action.disable') : t('applicationUsers.action.reactivate') }}
                  </button>
                </td>
              </tr>

              <tr v-if="!isLoading && filteredUsers.length === 0">
                <td colspan="5" class="px-4 py-12 text-center text-sm text-gray-500">
                  {{ searchQuery ? t('applicationUsers.empty.noMatch') : t('applicationUsers.empty.noUsers') }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </div>

    <!-- Invite Modal -->
    <Teleport to="body">
      <div
        v-if="showInvite"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4"
        @click.self="showInvite = false"
      >
        <div class="bg-white rounded-xl shadow-xl w-full max-w-md">
          <div class="flex items-center justify-between p-6 border-b border-gray-200">
            <div class="flex items-center gap-2">
              <Mail class="h-5 w-5 text-blue-600" />
              <h3 class="text-lg font-semibold text-gray-900">{{ t('applicationUsers.inviteModal.title') }}</h3>
            </div>
            <button @click="showInvite = false" class="text-gray-400 hover:text-gray-600">
              <X class="h-5 w-5" />
            </button>
          </div>

          <form @submit.prevent="submitInvite" class="p-6 space-y-4">
            <div>
              <label class="block text-xs font-medium text-gray-700 mb-1">
                {{ t('applicationUsers.inviteModal.email') }} <span class="text-red-500">*</span>
              </label>
              <input
                v-model="inviteForm.email"
                type="email"
                required
                :placeholder="t('applicationUsers.placeholder.emailExample')"
                class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-gray-700 mb-1">{{ t('applicationUsers.inviteModal.displayName') }}</label>
              <input
                v-model="inviteForm.displayName"
                type="text"
                :placeholder="t('applicationUsers.placeholder.displayNameExample')"
                class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-gray-700 mb-1">{{ t('applicationUsers.inviteModal.initialRoles') }}</label>
              <div class="flex flex-wrap gap-1.5 mt-1">
                <button
                  v-for="role in availableRoles"
                  :key="role"
                  type="button"
                  @click="toggleInviteRole(role)"
                  class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border"
                  :class="(inviteForm.roles ?? []).includes(role)
                    ? 'bg-blue-50 text-blue-700 border-blue-200'
                    : 'bg-white text-gray-500 border-gray-200 hover:bg-gray-50'"
                >{{ role }}</button>
              </div>
              <p class="mt-1 text-xs text-gray-500">
                {{ t('applicationUsers.inviteModal.rolesHint') }}
              </p>
            </div>

            <div class="flex justify-end gap-2 pt-2">
              <button
                type="button"
                @click="showInvite = false"
                :disabled="isInviting"
                class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
              >
                {{ t('common.cancel') }}
              </button>
              <button
                type="submit"
                :disabled="isInviting"
                class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50"
              >
                <RefreshCw v-if="isInviting" class="h-4 w-4 mr-2 animate-spin" />
                {{ isInviting ? t('applicationUsers.inviteModal.sending') : t('applicationUsers.inviteModal.send') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </div>
</template>
