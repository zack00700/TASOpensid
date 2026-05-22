<script setup lang="ts">
import { ref, computed } from "vue";
import { useI18n } from 'vue-i18n';
import ThirdPartyForm from "./ThirdPartyForm.vue";
import ThirdPartyHistory from "./ThirdPartyHistory.vue";
import { Download, Plus, Pencil, Trash2, XCircle, History, X } from "lucide-vue-next";
import AdvancedFilter from "./AdvancedFilter.vue";
import { useThirdParty } from "../composables/use.third-party";
import PageHeader from "./ui/PageHeader.vue";
import Button from "./ui/Button.vue";
import SearchInput from "./ui/SearchInput.vue";

const { t } = useI18n();

interface ThirdParty {
  id: string;
  fullName: string;
  email: string;
  companyName: string;
  accessType: string;
  status: "Active" | "Inactive" | "Pending";
  createdAt: string;
  updatedAt: string;
}

const showForm = ref(false);
const searchQuery = ref('');
const editing = ref<ThirdParty | null>(null);
const formViewOnly = ref(false);
const showDeleteConfirm = ref(false);
const thirdPartyToDelete = ref<ThirdParty | null>(null);
const showHistory = ref(false);
const historyThirdParty = ref<ThirdParty | null>(null);

const { thirdParties, fetchThirdParties } = useThirdParty();

const tableHeaders = computed(() => [
  t('thirdParties.table.contact'),
  t('thirdParties.table.company'),
  t('thirdParties.table.accessType'),
  t('thirdParties.table.status'),
  t('thirdParties.table.created'),
  t('thirdParties.table.updated'),
  t('thirdParties.table.actions'),
]);

const handleFilter = (filters: any[]) => {
  // Implement filter logic here
};

// Defensive date formatting: `new Date(null|undefined|"")` resolves to the Unix
// epoch (01/01/1970), which was leaking into the list view for parties without
// a createdAt/updatedAt yet. Falls back to "—" for any unparseable input.
function formatDate(raw: string | number | null | undefined): string {
  if (raw === null || raw === undefined || raw === '') return '—';
  const d = new Date(raw);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString();
}

const getStatusBadgeClasses = (status: string) => {
  const baseClasses = "px-2 py-1 text-xs font-medium rounded-full";
  switch (status) {
    case "Active":
      return `${baseClasses} bg-green-100 text-green-800`;
    case "Inactive":
      return `${baseClasses} bg-gray-100 text-gray-800`;
    case "Pending":
      return `${baseClasses} bg-yellow-100 text-yellow-800`;
    default:
      return baseClasses;
  }
};

const getStatusLabel = (status: string) => {
  switch (status) {
    case "Active":
      return t('thirdParties.status.active');
    case "Inactive":
      return t('thirdParties.status.inactive');
    case "Pending":
      return t('thirdParties.status.pending');
    default:
      return status;
  }
};

const handleAdd = () => {
  editing.value = null;
  showForm.value = true;
  formViewOnly.value = false;
};

const handleEdit = (party: ThirdParty) => {
  editing.value = party;
  showForm.value = true;
  formViewOnly.value = true;
};

const handleDelete = (party: ThirdParty) => {
  thirdPartyToDelete.value = party;
  showDeleteConfirm.value = true;
};

const handleShowHistory = (party: ThirdParty) => {
  historyThirdParty.value = party;
  showHistory.value = true;
};

const confirmDelete = () => {
  if (thirdPartyToDelete.value) {
    thirdParties.value = thirdParties.value.filter((u) => u.id !== thirdPartyToDelete.value?.id);
    showDeleteConfirm.value = false;
    thirdPartyToDelete.value = null;
  }
};

const handleFormSubmit = async (formData: any) => {
  try {
    await fetchThirdParties();
    showForm.value = false;
    editing.value = null;
    formViewOnly.value = false;
  } catch (error) {
    console.error("Failed to save third party:", error);
    alert(t('thirdParties.dialog.failedToSaveThirdParty'));
  }
};

const handleFormCancel = () => {
  showForm.value = false;
  editing.value = null;
  formViewOnly.value = false;
};

const closeHistory = () => {
  showHistory.value = false;
  historyThirdParty.value = null;
};
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <!-- List View -->
    <div v-if="!showForm">
      <PageHeader :title="t('nav.thirdParties')" :count="thirdParties?.length ?? null">
        <template #actions>
          <SearchInput v-model="searchQuery" :placeholder="t('thirdParties.placeholder.searchThirdParties')" />
          <Button variant="secondary">
            <Download class="h-4 w-4" />
            {{ t('common.export') }}
          </Button>
          <Button @click="handleAdd">
            <Plus class="h-4 w-4" />
            {{ t('thirdParties.addThirdParty') }}
          </Button>
        </template>
      </PageHeader>

      <div class="px-6 py-6 space-y-4 bg-white shadow rounded-lg mx-6">

      <!-- Advanced Filter -->
      <AdvancedFilter type="users" @filter="handleFilter" />

      <!-- Table -->
      <div class="overflow-x-auto">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th
                v-for="header in tableHeaders"
                :key="header"
                class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
              >
                {{ header }}
              </th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-for="party in thirdParties" :key="party.id" class="hover:bg-gray-50">
              <td class="px-4 py-3 whitespace-nowrap">
                <div class="text-sm font-medium text-gray-900">{{ party.fullName }}</div>
                <div class="text-sm text-gray-500">{{ party.email }}</div>
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                {{ party.companyName }}
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                {{ party.accessType }}
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <span :class="getStatusBadgeClasses(party.status)">
                  {{ getStatusLabel(party.status) }}
                </span>
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                {{ formatDate(party.createdAt) }}
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                {{ formatDate(party.updatedAt) }}
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-right text-sm font-medium">
                <button
                  @click="handleEdit(party)"
                  class="text-blue-600 hover:text-blue-900 mr-3"
                  :aria-label="t('common.edit')"
                >
                  <Pencil class="h-5 w-5" />
                </button>
                <button
                  @click="handleShowHistory(party)"
                  class="text-blue-600 hover:text-blue-900 mr-3"
                  :aria-label="t('thirdParties.action.viewHistory')"
                >
                  <History class="h-5 w-5" />
                </button>
                <button
                  @click="handleDelete(party)"
                  class="text-red-600 hover:text-red-900"
                  :aria-label="t('common.delete')"
                >
                  <Trash2 class="h-5 w-5" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      </div>
    </div>

    <!-- Form View -->
    <ThirdPartyForm
      v-else
      :edit-mode="!!editing"
      :initial-data="editing"
      :view-only="formViewOnly"
      @submit="handleFormSubmit"
      @cancel="handleFormCancel"
    />
    <ThirdPartyHistory
      v-if="editing"
      :id="editing.id"
      class="mt-4"
    />

    <!-- History Modal -->
    <Teleport to="body">
      <div
        v-if="showHistory"
        class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50"
      >
        <div class="bg-white rounded-lg p-6 max-w-2xl w-full max-h-[90vh] overflow-y-auto">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-medium text-gray-900">{{ t('thirdParties.history.title') }}</h3>
            <button @click="closeHistory" class="text-gray-400 hover:text-gray-500" :aria-label="t('common.close')">
              <X class="h-6 w-6" />
            </button>
          </div>
          <ThirdPartyHistory v-if="historyThirdParty" :id="historyThirdParty.id" />
        </div>
      </div>
    </Teleport>

    <!-- Delete Confirmation Modal -->
    <Teleport to="body">
      <div
        v-if="showDeleteConfirm"
        class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50"
      >
        <div class="bg-white rounded-lg p-6 max-w-md w-full">
          <div class="text-center">
            <XCircle class="mx-auto h-12 w-12 text-red-500" />
            <h3 class="mt-4 text-lg font-medium text-gray-900">{{ t('thirdParties.dialog.deleteTitle') }}</h3>
            <p class="mt-2 text-sm text-gray-500">
              {{ t('thirdParties.dialog.deleteConfirm', { name: thirdPartyToDelete?.fullName }) }}
            </p>
          </div>
          <div class="mt-6 flex justify-end space-x-3">
            <button
              @click="showDeleteConfirm = false"
              class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              @click="confirmDelete"
              class="px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-red-600 hover:bg-red-700"
            >
              {{ t('common.delete') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
