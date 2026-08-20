<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { RefreshCw, UserPlus, X, AlertCircle, CheckCircle, XCircle, Mail, Pencil } from 'lucide-vue-next';
import {
  listEntraUsers,
  listEntraRoles,
  addRoleToUser,
  removeRoleFromUser,
  setEntraUserEnabled,
  inviteEntraUser,
  updateEntraUserProfile,
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

// ── Edit profile (displayName + jobTitle) ───────────────────────────────────
const showEdit = ref(false);
const isEditing = ref(false);
const editingUser = ref<EntraUser | null>(null);
const editForm = ref<{ displayName: string; jobTitle: string }>({ displayName: '', jobTitle: '' });

const openEdit = (user: EntraUser) => {
  editingUser.value = user;
  editForm.value = {
    displayName: user.displayName ?? '',
    jobTitle: user.jobTitle ?? '',
  };
  showEdit.value = true;
};

const submitEdit = async () => {
  if (!editingUser.value) return;
  isEditing.value = true;
  try {
    const updated = await updateEntraUserProfile(editingUser.value.id, {
      displayName: editForm.value.displayName,
      jobTitle: editForm.value.jobTitle,
    });
    replaceUser(updated);
    showEdit.value = false;
    editingUser.value = null;
  } catch {
    errorMessage.value = t('applicationUsers.error.editFailed');
  } finally {
    isEditing.value = false;
  }
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
  <div class="min-h-screen bg-[rgba(252,247,238,0.55)]">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <!-- Header -->
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold leading-7 text-tide-ink sm:text-3xl">{{ t('applicationUsers.title') }}</h1>
          <p class="mt-1 text-sm text-tide-ink/55">
            {{ availableRoles.length === 1
              ? t('applicationUsers.subtitle.one', { count: availableRoles.length })
              : t('applicationUsers.subtitle.other', { count: availableRoles.length }) }}
          </p>
        </div>
        <div class="flex items-center gap-3">
          <button
            @click="fetchAll"
            :disabled="isLoading"
            class="inline-flex items-center px-4 py-2 border border-[rgba(60,50,35,0.16)] rounded-lg text-sm font-medium text-tide-ink/80 bg-[rgba(255,253,247,0.92)] hover:bg-[rgba(252,247,238,0.55)] disabled:opacity-50"
          >
            <RefreshCw class="h-4 w-4 mr-2" :class="{ 'animate-spin': isLoading }" />
            {{ t('applicationUsers.button.refresh') }}
          </button>
          <button
            @click="openInvite"
            class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg text-sm font-medium text-white bg-tide-blue-btn-deep hover:bg-tide-blue-deep"
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
      <div class="bg-[rgba(255,253,247,0.92)] rounded-lg shadow-sm border border-[rgba(60,50,35,0.12)] p-4 mb-6">
        <input
          v-model="searchQuery"
          type="text"
          :placeholder="t('applicationUsers.placeholder.search')"
          class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue focus:border-tide-blue"
        />
      </div>

      <!-- Table -->
      <div class="bg-[rgba(255,253,247,0.92)] shadow-sm rounded-lg border border-[rgba(60,50,35,0.12)] overflow-hidden">
        <div class="overflow-x-auto">
          <table class="min-w-full divide-y divide-[rgba(42,36,30,0.08)]">
            <thead class="bg-[rgba(252,247,238,0.55)]">
              <tr>
                <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">{{ t('applicationUsers.column.user') }}</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">{{ t('applicationUsers.column.type') }}</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">{{ t('applicationUsers.column.status') }}</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">{{ t('applicationUsers.column.roles') }}</th>
                <th class="px-4 py-3 text-right text-xs font-medium text-tide-ink/55 uppercase tracking-wider">{{ t('applicationUsers.column.actions') }}</th>
              </tr>
            </thead>
            <tbody class="bg-[rgba(255,253,247,0.92)] divide-y divide-[rgba(42,36,30,0.08)]">
              <tr v-if="isLoading" v-for="i in 4" :key="`skel-${i}`">
                <td v-for="j in 5" :key="j" class="px-4 py-3">
                  <div class="h-4 bg-gray-200 rounded animate-pulse w-3/4"></div>
                </td>
              </tr>

              <tr v-else v-for="user in filteredUsers" :key="user.id" class="hover:bg-[rgba(252,247,238,0.55)]">
                <td class="px-4 py-3 whitespace-nowrap">
                  <div class="text-sm font-medium text-tide-ink">{{ user.displayName || '—' }}</div>
                  <div class="text-sm text-tide-ink/55">{{ user.mail || user.userPrincipalName || '—' }}</div>
                  <div v-if="user.jobTitle" class="text-xs text-tide-ink/40 mt-0.5">{{ user.jobTitle }}</div>
                </td>
                <td class="px-4 py-3 whitespace-nowrap text-sm text-tide-ink/80">
                  <span
                    class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border"
                    :class="user.userType === 'Guest'
                      ? 'bg-purple-50 text-purple-700 border-purple-200'
                      : 'bg-[rgba(252,247,238,0.55)] text-tide-ink/80 border-[rgba(60,50,35,0.12)]'"
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
                    class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-[rgba(42,36,30,0.05)] text-tide-ink/70 border border-[rgba(60,50,35,0.12)]"
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
                        ? 'bg-[rgba(90,138,171,0.10)] text-tide-blue-deep border-blue-200 hover:bg-blue-100'
                        : 'bg-[rgba(255,253,247,0.92)] text-tide-ink/55 border-[rgba(60,50,35,0.12)] hover:bg-[rgba(252,247,238,0.55)]'"
                      :title="user.roles.includes(role) ? t('applicationUsers.role.remove', { role }) : t('applicationUsers.role.assign', { role })"
                    >
                      {{ role }}
                    </button>
                  </div>
                </td>
                <td class="px-4 py-3 whitespace-nowrap text-right text-sm">
                  <div class="flex items-center justify-end gap-3">
                    <button
                      @click="openEdit(user)"
                      class="inline-flex items-center text-sm font-medium text-tide-blue-deep hover:text-blue-800"
                      :title="t('applicationUsers.action.edit')"
                    >
                      <Pencil class="h-4 w-4 mr-1" />
                      {{ t('applicationUsers.action.edit') }}
                    </button>
                    <button
                      @click="toggleEnabled(user)"
                      class="text-sm font-medium"
                      :class="user.accountEnabled ? 'text-red-600 hover:text-red-800' : 'text-green-600 hover:text-green-800'"
                    >
                      {{ user.accountEnabled ? t('applicationUsers.action.disable') : t('applicationUsers.action.reactivate') }}
                    </button>
                  </div>
                </td>
              </tr>

              <tr v-if="!isLoading && filteredUsers.length === 0">
                <td colspan="5" class="px-4 py-12 text-center text-sm text-tide-ink/55">
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
        <div class="bg-[rgba(255,253,247,0.92)] rounded-xl shadow-xl w-full max-w-md">
          <div class="flex items-center justify-between p-6 border-b border-[rgba(60,50,35,0.12)]">
            <div class="flex items-center gap-2">
              <Mail class="h-5 w-5 text-tide-blue-deep" />
              <h3 class="text-lg font-semibold text-tide-ink">{{ t('applicationUsers.inviteModal.title') }}</h3>
            </div>
            <button @click="showInvite = false" class="text-tide-ink/40 hover:text-tide-ink/70">
              <X class="h-5 w-5" />
            </button>
          </div>

          <form @submit.prevent="submitInvite" class="p-6 space-y-4">
            <div>
              <label class="block text-xs font-medium text-tide-ink/80 mb-1">
                {{ t('applicationUsers.inviteModal.email') }} <span class="text-red-500">*</span>
              </label>
              <input
                v-model="inviteForm.email"
                type="email"
                required
                :placeholder="t('applicationUsers.placeholder.emailExample')"
                class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue focus:border-tide-blue"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-tide-ink/80 mb-1">{{ t('applicationUsers.inviteModal.displayName') }}</label>
              <input
                v-model="inviteForm.displayName"
                type="text"
                :placeholder="t('applicationUsers.placeholder.displayNameExample')"
                class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue focus:border-tide-blue"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-tide-ink/80 mb-1">{{ t('applicationUsers.inviteModal.initialRoles') }}</label>
              <div class="flex flex-wrap gap-1.5 mt-1">
                <button
                  v-for="role in availableRoles"
                  :key="role"
                  type="button"
                  @click="toggleInviteRole(role)"
                  class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border"
                  :class="(inviteForm.roles ?? []).includes(role)
                    ? 'bg-[rgba(90,138,171,0.10)] text-tide-blue-deep border-blue-200'
                    : 'bg-[rgba(255,253,247,0.92)] text-tide-ink/55 border-[rgba(60,50,35,0.12)] hover:bg-[rgba(252,247,238,0.55)]'"
                >{{ role }}</button>
              </div>
              <p class="mt-1 text-xs text-tide-ink/55">
                {{ t('applicationUsers.inviteModal.rolesHint') }}
              </p>
            </div>

            <div class="flex justify-end gap-2 pt-2">
              <button
                type="button"
                @click="showInvite = false"
                :disabled="isInviting"
                class="px-4 py-2 border border-[rgba(60,50,35,0.16)] rounded-lg text-sm font-medium text-tide-ink/80 hover:bg-[rgba(252,247,238,0.55)] disabled:opacity-50"
              >
                {{ t('common.cancel') }}
              </button>
              <button
                type="submit"
                :disabled="isInviting"
                class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg text-sm font-medium text-white bg-tide-blue-btn-deep hover:bg-tide-blue-deep disabled:opacity-50"
              >
                <RefreshCw v-if="isInviting" class="h-4 w-4 mr-2 animate-spin" />
                {{ isInviting ? t('applicationUsers.inviteModal.sending') : t('applicationUsers.inviteModal.send') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

    <!-- Edit profile Modal (TC-12) -->
    <Teleport to="body">
      <div
        v-if="showEdit && editingUser"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4"
        @click.self="showEdit = false"
      >
        <div class="bg-[rgba(255,253,247,0.92)] rounded-xl shadow-xl w-full max-w-md">
          <div class="flex items-center justify-between p-6 border-b border-[rgba(60,50,35,0.12)]">
            <div class="flex items-center gap-2">
              <Pencil class="h-5 w-5 text-tide-blue-deep" />
              <h3 class="text-lg font-semibold text-tide-ink">{{ t('applicationUsers.editModal.title') }}</h3>
            </div>
            <button @click="showEdit = false" class="text-tide-ink/40 hover:text-tide-ink/70">
              <X class="h-5 w-5" />
            </button>
          </div>

          <form @submit.prevent="submitEdit" class="p-6 space-y-4">
            <p class="text-xs text-tide-ink/55">
              {{ t('applicationUsers.editModal.hint', { upn: editingUser.userPrincipalName ?? editingUser.id }) }}
            </p>
            <div>
              <label class="block text-xs font-medium text-tide-ink/80 mb-1">{{ t('applicationUsers.editModal.displayName') }}</label>
              <input
                v-model="editForm.displayName"
                type="text"
                class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue focus:border-tide-blue"
              />
            </div>
            <div>
              <label class="block text-xs font-medium text-tide-ink/80 mb-1">{{ t('applicationUsers.editModal.jobTitle') }}</label>
              <input
                v-model="editForm.jobTitle"
                type="text"
                class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue focus:border-tide-blue"
              />
            </div>
            <div class="flex justify-end gap-2 pt-2">
              <button
                type="button"
                @click="showEdit = false"
                :disabled="isEditing"
                class="px-4 py-2 border border-[rgba(60,50,35,0.16)] rounded-lg text-sm font-medium text-tide-ink/80 hover:bg-[rgba(252,247,238,0.55)] disabled:opacity-50"
              >
                {{ t('common.cancel') }}
              </button>
              <button
                type="submit"
                :disabled="isEditing"
                class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg text-sm font-medium text-white bg-tide-blue-btn-deep hover:bg-tide-blue-deep disabled:opacity-50"
              >
                <RefreshCw v-if="isEditing" class="h-4 w-4 mr-2 animate-spin" />
                {{ isEditing ? t('common.saving') : t('common.save') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </div>
</template>
