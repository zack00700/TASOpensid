<script setup lang="ts">
import { ref, inject, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  Search,
  Filter,
  Download,
  Plus,
  Pencil,
  Trash2,
  CheckCircle,
  XCircle,
  ArrowDownCircle,
  ArrowUpCircle,
  CircleDot,
  Eye,
  MoreHorizontal,
  Calendar,
  Settings
} from 'lucide-vue-next';
import AdvancedFilter from './AdvancedFilter.vue';
import ContractForm from './ContractForm.vue';
import type { EventConfig } from '../types/event-config';
import { useKeyboardShortcut } from '../composables/useKeyboardShortcut';
import PageHeader from './ui/PageHeader.vue';
import Button from './ui/Button.vue';
import { isDateExpiringSoon, isDateExpired } from '../utils/contract';

const { t } = useI18n();

interface Contract {
  id: string;
  name: string;
  description: string;
  calculationMode: {
    type: string;
    subType: string;
    eventConfig: EventConfig;
    parameters?: Record<string, any>;
  };
  status: 'Active' | 'Disable';
  startDate: string;
  endDate: string;
  // N4 extensions
  customerId?: string;
  customerName?: string;
  priority?: number;
  tariffId?: string;
}

const showForm = ref(false);
const editingContract = ref<Contract | null>(null);
const showDeleteConfirm = ref(false);
const contractToDelete = ref<Contract | null>(null);

useKeyboardShortcut('Escape', () => {
  if (showDeleteConfirm.value) showDeleteConfirm.value = false;
}, { ignoreInInputs: false });
const isSaving = ref(false);
const searchQuery = ref('');
const showAdvancedFilter = ref(false);
const selectedContracts = ref<string[]>([]);
const viewMode = ref<'grid' | 'table'>('table');

const $axios = inject<any>('$axios');
const contracts = ref<Contract[]>([]);

// Computed properties for better data handling
const filteredContracts = computed(() => {
  if (!searchQuery.value) return contracts.value;
  const q = searchQuery.value.toLowerCase();
  return contracts.value.filter(contract =>
    contract.name.toLowerCase().includes(q) ||
    contract.description.toLowerCase().includes(q) ||
    contract.calculationMode.eventConfig?.eventName.toLowerCase().includes(q) ||
    (contract.customerName ?? '').toLowerCase().includes(q) ||
    (contract.customerId ?? '').toLowerCase().includes(q)
  );
});

const activeContractsCount = computed(() => 
  contracts.value.filter(c => c.status === 'Active').length
);

const fetchContracts = async () => {
  try {
    const response = await $axios.get('/contract');
    contracts.value = response.data;
  } catch (error) {
    alert(t('contracts.dialog.failedToLoadContracts'));
  }
};

onMounted(fetchContracts);

const handleFilter = (filters: any[]) => {
  showAdvancedFilter.value = false;
};

const getStatusBadgeClasses = (status: string) => {
  const baseClasses = "inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium";
  switch (status) {
    case 'Active':
      return `${baseClasses} bg-green-100 text-green-800 border border-green-200`;
    case 'Disable':
      return `${baseClasses} bg-gray-100 text-gray-800 border border-gray-200`;
    default:
      return baseClasses;
  }
};

const getEventTypeIcon = (type: string | undefined) => {
  switch (type) {
    case 'IN': return ArrowDownCircle;
    case 'OUT': return ArrowUpCircle;
    default: return CircleDot;
  }
};

const getEventTypeClasses = (type: string | undefined) => {
  const baseClasses = "flex items-center space-x-1.5";
  switch (type) {
    case 'IN': return `${baseClasses} text-green-600`;
    case 'OUT': return `${baseClasses} text-red-600`;
    case 'INTERMEDIATE': return `${baseClasses} text-blue-600`;
    default: return baseClasses;
  }
};

const handleAdd = () => {
  editingContract.value = null;
  showForm.value = true;
};

const handleEdit = (contract: any) => {
  const id = contract?.id ?? contract?._id ?? contract?.contractId;
  editingContract.value = { ...contract, id };
  showForm.value = true;
};

const handleView = (contract: Contract) => {
  // Reuse the edit form to show the contract (now opens on step 1 with all fields
  // pre-populated — see TC-06 fix in ContractForm). The user can either inspect
  // the data or modify it in place.
  handleEdit(contract);
};

const handleDelete = (contract: Contract) => {
  contractToDelete.value = contract;
  showDeleteConfirm.value = true;
};

const handleBulkDelete = () => {
  if (selectedContracts.value.length === 0) return;
  // Implement bulk delete
};

const confirmDelete = async () => {
  if (contractToDelete.value) {
    try {
      await $axios.delete(`/contract/${contractToDelete.value.id}`);
      alert(t('contracts.dialog.contractDeletedSuccessfully'));
      await fetchContracts();
    } catch (error) {
      console.error('Failed to delete contract:', error);
      alert(t('contracts.dialog.failedToDeleteContract'));
    } finally {
      showDeleteConfirm.value = false;
      contractToDelete.value = null;
    }
  }
};

const handleFormSubmit = async (formData: Contract) => {
  if (editingContract.value) {
    const id = editingContract.value.id;
    if (!id) {
      alert(t('contracts.dialog.cannotUpdateContractMissingIdentifier'));
      return;
    }
    isSaving.value = true;
    try {
      await $axios.put(`/contract/${id}`, formData);
      alert(t('contracts.dialog.contractUpdatedSuccessfully'));
      showForm.value = false;
      editingContract.value = null;
      await fetchContracts();
    } catch (error: any) {
      console.error('Failed to update contract:', error);
      if (error?.response?.status === 404) {
        alert(t('contracts.dialog.contractNotFound'));
      } else {
        alert(t('contracts.dialog.failedToUpdateContract'));
      }
    } finally {
      isSaving.value = false;
    }
  } else {
    isSaving.value = true;
    try {
      await $axios.post('/contract', formData);
      alert(t('contracts.dialog.contractCreatedSuccessfully'));
      showForm.value = false;
      await fetchContracts();
    } catch (error) {
      console.error('Failed to create contract:', error);
      alert(t('contracts.dialog.failedToCreateContract'));
    } finally {
      isSaving.value = false;
      editingContract.value = null;
    }
  }
};

const handleFormCancel = () => {
  showForm.value = false;
  editingContract.value = null;
};

const toggleSelectAll = () => {
  if (selectedContracts.value.length === contracts.value.length) {
    selectedContracts.value = [];
  } else {
    selectedContracts.value = contracts.value.map(c => c.id);
  }
};


const tableHeaders = computed(() => [
  t('contracts.column.contractDetails'),
  t('contracts.column.eventAndType'),
  t('contracts.column.calculationMode'),
  t('contracts.column.statusAndDates'),
  t('contracts.column.actions'),
]);

const getEventTypeLabel = (type: string | undefined) => {
  switch (type) {
    case 'IN': return t('contracts.eventType.in');
    case 'OUT': return t('contracts.eventType.out');
    case 'INTERMEDIATE': return t('contracts.eventType.intermediate');
    default: return t('contracts.eventType.standard');
  }
};

const getStatusLabel = (status: string) => {
  switch (status) {
    case 'Active': return t('contracts.status.active');
    case 'Disable': return t('contracts.status.disable');
    default: return status;
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <!-- List View -->
    <div v-if="!showForm">
      <PageHeader
        :title="t('contracts.label.contractManagement')"
        :subtitle="t('contracts.subtitle.summary', { active: activeContractsCount, inactive: contracts.length - activeContractsCount, total: contracts.length })"
        :count="contracts.length"
      >
        <template #actions>
          <Button
            variant="secondary"
            :class="showAdvancedFilter && 'bg-blue-50 text-blue-700 border-blue-300'"
            @click="showAdvancedFilter = !showAdvancedFilter"
          >
            <Filter class="h-4 w-4" />
            {{ t('contracts.button.filters') }}
          </Button>
          <Button variant="secondary">
            <Download class="h-4 w-4" />
            {{ t('common.export') }}
          </Button>
          <Button @click="handleAdd">
            <Plus class="h-4 w-4" />
            {{ t('contracts.button.newContract') }}
          </Button>
        </template>
      </PageHeader>

      <!-- Enhanced Search and Filters Section -->
      <div class="px-6 py-6 space-y-6">
      <div class="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
        <div class="p-6">
          <div class="flex flex-col sm:flex-row gap-4">
            <div class="flex-1 relative">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Search class="h-5 w-5 text-gray-400" />
              </div>
              <input
                v-model="searchQuery"
                type="text"
                :placeholder="t('contracts.placeholder.search')"
                class="block w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg leading-5 bg-white placeholder-gray-500 focus:outline-none focus:placeholder-gray-400 focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
            <div class="flex items-center space-x-2">
              <span class="text-sm text-gray-500">{{ t('contracts.label.viewLabel') }}</span>
              <div class="flex rounded-lg border border-gray-200 p-1">
                <button
                  @click="viewMode = 'table'"
                  :class="[
                    'px-3 py-1 text-sm font-medium rounded-lg',
                    viewMode === 'table'
                      ? 'bg-blue-100 text-blue-700'
                      : 'text-gray-500 hover:text-gray-700'
                  ]"
                >
                  {{ t('contracts.viewMode.table') }}
                </button>
                <button
                  @click="viewMode = 'grid'"
                  :class="[
                    'px-3 py-1 text-sm font-medium rounded-lg',
                    viewMode === 'grid'
                      ? 'bg-blue-100 text-blue-700'
                      : 'text-gray-500 hover:text-gray-700'
                  ]"
                >
                  {{ t('contracts.viewMode.grid') }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Advanced Filter Panel -->
        <div v-if="showAdvancedFilter" class="border-t border-gray-200 px-4 py-3 bg-gray-50">
          <AdvancedFilter type="contracts" @filter="handleFilter" />
        </div>
      </div>

      <!-- Bulk Actions Bar -->
      <div v-if="selectedContracts.length > 0" class="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-6">
        <div class="flex items-center justify-between">
          <div class="flex items-center">
            <span class="text-sm font-medium text-blue-900">
              {{ selectedContracts.length > 1
                ? t('contracts.bulk.selectedPlural', { count: selectedContracts.length })
                : t('contracts.bulk.selectedSingular', { count: selectedContracts.length }) }}
            </span>
          </div>
          <div class="flex items-center space-x-3">
            <button
              @click="selectedContracts = []"
              class="text-sm text-blue-700 hover:text-blue-900"
            >
              {{ t('contracts.bulk.clearSelection') }}
            </button>
            <button
              @click="handleBulkDelete"
              class="inline-flex items-center px-3 py-1.5 border border-transparent text-sm font-medium rounded-lg text-white bg-red-600 hover:bg-red-700"
            >
              <Trash2 class="h-4 w-4 mr-1" />
              {{ t('contracts.bulk.deleteSelected') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Enhanced Data Display -->
      <div class="bg-white shadow-sm rounded-lg border border-gray-200 overflow-hidden">
        <!-- Table View -->
        <div v-if="viewMode === 'table'">
        <div class="hidden sm:block overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50">
              <tr>
                <th class="w-4 px-4 py-3">
                  <input
                    type="checkbox"
                    :checked="selectedContracts.length === contracts.length && contracts.length > 0"
                    :indeterminate="selectedContracts.length > 0 && selectedContracts.length < contracts.length"
                    @change="toggleSelectAll"
                    class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                  />
                </th>
                <th v-for="header in tableHeaders" :key="header" class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {{ header }}
                </th>
              </tr>
            </thead>
            <tbody class="bg-white divide-y divide-gray-200">
              <tr 
                v-for="contract in filteredContracts" 
                :key="contract.id" 
                :class="[
                  'hover:bg-gray-50 transition-colors duration-150',
                  selectedContracts.includes(contract.id) ? 'bg-blue-50' : ''
                ]"
              >
                <td class="px-4 py-3">
                  <input
                    v-model="selectedContracts"
                    :value="contract.id"
                    type="checkbox"
                    class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                  />
                </td>
                
                <!-- Contract Details -->
                <td class="px-4 py-3">
                  <div class="max-w-xs">
                    <div class="text-sm font-semibold text-gray-900 truncate">
                      {{ contract.name }}
                    </div>
                    <div class="text-sm text-gray-500 truncate">
                      {{ contract.description }}
                    </div>
                    <div class="flex flex-wrap gap-1.5 mt-1.5">
                      <span
                        v-if="contract.customerName"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-indigo-50 text-indigo-700 border border-indigo-200"
                        :title="contract.customerId ? t('contracts.label.idWithValue', { id: contract.customerId }) : undefined"
                      >
                        {{ contract.customerName }}
                      </span>
                      <span
                        v-if="contract.priority != null && contract.priority > 0"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200"
                        :title="t('contracts.label.priority')"
                      >
                        {{ t('contracts.label.priorityShort', { value: contract.priority }) }}
                      </span>
                      <span
                        v-if="contract.tariffId"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-teal-50 text-teal-700 border border-teal-200"
                        :title="t('contracts.label.linkedToTariff')"
                      >
                        {{ t('contracts.label.tariff') }}
                      </span>
                    </div>
                  </div>
                </td>

                <!-- Event & Type -->
                <td class="px-4 py-3">
                  <div :class="getEventTypeClasses(contract.calculationMode.eventConfig?.eventType)">
                    <component
                      :is="getEventTypeIcon(contract.calculationMode.eventConfig?.eventType)"
                      class="h-4 w-4 flex-shrink-0"
                    />
                    <div class="min-w-0">
                      <div class="text-sm font-medium text-gray-900 truncate">
                        {{ contract.calculationMode.eventConfig?.eventName }}
                      </div>
                      <div class="text-xs text-gray-500 capitalize">
                        {{ getEventTypeLabel(contract.calculationMode.eventConfig?.eventType) }}
                      </div>
                    </div>
                  </div>
                </td>
                
                <!-- Calculation Mode -->
                <td class="px-4 py-3">
                  <div class="text-sm">
                    <div class="font-medium text-gray-900">{{ contract.calculationMode.type }}</div>
                    <div class="text-gray-500 capitalize">
                      {{ contract.calculationMode.subType.replace(/_/g, ' ') }}
                    </div>
                  </div>
                </td>
                
                <!-- Status & Dates -->
                <td class="px-4 py-3">
                  <div class="space-y-2">
                    <span :class="getStatusBadgeClasses(contract.status)">
                      {{ getStatusLabel(contract.status) }}
                    </span>
                    <div class="text-xs text-gray-500">
                      <div class="flex items-center space-x-1">
                        <span>{{ new Date(contract.startDate).toLocaleDateString() }}</span>
                        <span>→</span>
                        <span 
                          :class="[
                            isDateExpired(contract.endDate) ? 'text-red-600 font-medium' :
                            isDateExpiringSoon(contract.endDate) ? 'text-orange-600 font-medium' :
                            'text-gray-500'
                          ]"
                        >
                          {{ new Date(contract.endDate).toLocaleDateString() }}
                        </span>
                      </div>
                    </div>
                  </div>
                </td>
                
                <!-- Actions -->
                <td class="px-4 py-3">
                  <div class="flex items-center space-x-2">
                    <button
                      @click="handleView(contract)"
                      class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                      :title="t('contracts.action.viewContract')"
                      :aria-label="t('contracts.action.viewContract')"
                    >
                      <Eye class="h-4 w-4" />
                    </button>
                    <button
                      @click="handleEdit(contract)"
                      class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                      :title="t('contracts.action.editContract')"
                      :aria-label="t('common.edit')"
                    >
                      <Pencil class="h-4 w-4" />
                    </button>
                    <button
                      @click="handleDelete(contract)"
                      class="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                      :title="t('contracts.action.deleteContract')"
                      :aria-label="t('common.delete')"
                    >
                      <Trash2 class="h-4 w-4" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Mobile card view (inside table branch) -->
        <div class="sm:hidden p-4">
          <div v-if="!filteredContracts || filteredContracts.length === 0" class="bg-white rounded-xl border border-slate-200 p-8 text-center">
            <p class="text-sm text-slate-500">{{ t('contracts.empty.noContracts') }}</p>
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="contract in filteredContracts"
              :key="contract.id"
              class="bg-white rounded-xl border border-slate-200 p-4 shadow-sm"
            >
              <div class="flex items-start justify-between gap-2 mb-2">
                <div>
                  <p class="font-semibold text-slate-900 text-sm">{{ contract.name }}</p>
                  <p class="text-xs text-slate-500 mt-0.5">{{ contract.description || '—' }}</p>
                </div>
                <span v-if="contract.status" :class="[
                  'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border shrink-0',
                  contract.status === 'Active' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-slate-100 text-slate-600 border-slate-200'
                ]">{{ getStatusLabel(contract.status) }}</span>
              </div>
              <div class="flex items-center justify-between text-xs text-slate-500">
                <span>{{ contract.startDate ? new Date(contract.startDate).toLocaleDateString() : '' }}</span>
                <span>{{ contract.endDate ? new Date(contract.endDate).toLocaleDateString() : '' }}</span>
              </div>
            </div>
          </div>
        </div>
        </div>

        <!-- Grid View -->
        <div v-else class="p-6">
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            <div
              v-for="contract in filteredContracts"
              :key="contract.id"
              class="bg-white border border-gray-200 rounded-lg hover:shadow-md transition-shadow duration-200 overflow-hidden"
            >
              <div class="p-5">
                <div class="flex items-start justify-between mb-3">
                  <div class="flex-1 min-w-0">
                    <h3 class="text-sm font-semibold text-gray-900 truncate">
                      {{ contract.name }}
                    </h3>
                    <p class="text-xs text-gray-500 mt-1 line-clamp-2">
                      {{ contract.description }}
                    </p>
                  </div>
                  <span :class="getStatusBadgeClasses(contract.status)">
                    {{ getStatusLabel(contract.status) }}
                  </span>
                </div>

                <div class="space-y-3">
                  <div :class="getEventTypeClasses(contract.calculationMode.eventConfig?.eventType)">
                    <component
                      :is="getEventTypeIcon(contract.calculationMode.eventConfig?.eventType)"
                      class="h-4 w-4 flex-shrink-0"
                    />
                    <span class="text-sm truncate">
                      {{ contract.calculationMode.eventConfig?.eventName }}
                    </span>
                  </div>

                  <!-- Customer / Priority / Tariff badges -->
                  <div v-if="contract.customerName || (contract.priority != null && contract.priority > 0) || contract.tariffId" class="flex flex-wrap gap-1.5">
                    <span
                      v-if="contract.customerName"
                      class="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-indigo-50 text-indigo-700 border border-indigo-200 truncate max-w-full"
                      :title="contract.customerId ? t('contracts.label.idWithValue', { id: contract.customerId }) : undefined"
                    >
                      {{ contract.customerName }}
                    </span>
                    <span
                      v-if="contract.priority != null && contract.priority > 0"
                      class="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200"
                      :title="t('contracts.label.priority')"
                    >
                      {{ t('contracts.label.priorityShort', { value: contract.priority }) }}
                    </span>
                    <span
                      v-if="contract.tariffId"
                      class="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-teal-50 text-teal-700 border border-teal-200"
                      :title="t('contracts.label.linkedToTariff')"
                    >
                      {{ t('contracts.label.tariff') }}
                    </span>
                  </div>

                  <div class="text-xs text-gray-500">
                    <div>{{ contract.calculationMode.type }} • {{ contract.calculationMode.subType.replace(/_/g, ' ') }}</div>
                    <div class="mt-1">
                      {{ new Date(contract.startDate).toLocaleDateString() }} →
                      <span
                        :class="[
                          isDateExpired(contract.endDate) ? 'text-red-600 font-medium' :
                          isDateExpiringSoon(contract.endDate) ? 'text-orange-600 font-medium' :
                          'text-gray-500'
                        ]"
                      >
                        {{ new Date(contract.endDate).toLocaleDateString() }}
                      </span>
                    </div>
                  </div>
                </div>
                
                <div class="flex justify-between items-center mt-4 pt-3 border-t border-gray-100">
                  <div class="flex space-x-1">
                    <button
                      @click="handleView(contract)"
                      class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                      :aria-label="t('contracts.action.viewContract')"
                    >
                      <Eye class="h-4 w-4" />
                    </button>
                    <button
                      @click="handleEdit(contract)"
                      class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                      :aria-label="t('common.edit')"
                    >
                      <Pencil class="h-4 w-4" />
                    </button>
                  </div>
                  <button
                    @click="handleDelete(contract)"
                    class="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                    :aria-label="t('common.delete')"
                  >
                    <Trash2 class="h-4 w-4" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div v-if="filteredContracts.length === 0" class="text-center py-12">
          <Settings class="mx-auto h-12 w-12 text-gray-400" />
          <h3 class="mt-2 text-sm font-medium text-gray-900">
            {{ searchQuery ? t('contracts.empty.noContractsFound') : t('contracts.empty.noContractsTitle') }}
          </h3>
          <p class="mt-1 text-sm text-gray-500">
            {{ searchQuery ? t('contracts.empty.tryAdjustingSearch') : t('contracts.empty.getStarted') }}
          </p>
          <div class="mt-6">
            <button
              v-if="!searchQuery"
              @click="handleAdd"
              class="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700"
            >
              <Plus class="h-4 w-4 mr-2" />
              {{ t('contracts.button.newContract') }}
            </button>
          </div>
        </div>
      </div>
      </div>
    </div>

    <!-- Form View -->
    <ContractForm
      v-else
      :edit-mode="!!editingContract"
      :initial-data="editingContract"
      :loading="isSaving"
      @submit="handleFormSubmit"
      @cancel="handleFormCancel"
    />

    <!-- Enhanced Delete Confirmation Modal -->
    <Teleport to="body">
      <div v-if="showDeleteConfirm" class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4" @click.self="showDeleteConfirm = false">
        <div class="bg-white rounded-xl shadow-xl max-w-md w-full">
          <div class="p-6">
            <div class="flex items-center space-x-3">
              <div class="flex-shrink-0">
                <XCircle class="h-10 w-10 text-red-500" />
              </div>
              <div class="flex-1">
                <h3 class="text-lg font-semibold text-gray-900">{{ t('contracts.deleteModal.title') }}</h3>
                <p class="mt-1 text-sm text-gray-600">
                  {{ t('contracts.deleteModal.confirmPrefix') }} <strong>{{ contractToDelete?.name }}</strong>{{ t('contracts.deleteModal.confirmSuffix') }}
                </p>
              </div>
            </div>
          </div>
          <div class="px-4 py-3 bg-gray-50 rounded-b-xl flex justify-end space-x-3">
            <Button variant="secondary" @click="showDeleteConfirm = false">{{ t('common.cancel') }}</Button>
            <Button variant="danger" @click="confirmDelete">{{ t('contracts.deleteModal.confirm') }}</Button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>