<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  Search,
  Plus,
  Pencil,
  Trash2,
  CheckCircle,
  XCircle,
  Calendar,
  DollarSign,
  Filter,
} from 'lucide-vue-next';
import { tariffService } from '../services/tariffService';
import type { Tariff, RateManagementExtended, ServiceType } from '../types/contrat';
import PageHeader from './ui/PageHeader.vue';
import Button from './ui/Button.vue';

const { t } = useI18n();

// ── State ──────────────────────────────────────────────────────────────────
const tariffs = ref<Tariff[]>([]);
const searchQuery = ref('');
const serviceTypeFilter = ref<string>('');
const showForm = ref(false);
const editingTariff = ref<Tariff | null>(null);
const showDeleteConfirm = ref(false);
const tariffToDelete = ref<Tariff | null>(null);
const isSaving = ref(false);

// ── Form model ─────────────────────────────────────────────────────────────
const emptyForm = (): Tariff => ({
  name: '',
  description: '',
  serviceType: undefined,
  status: 'Active',
  startDate: undefined,
  endDate: undefined,
  notes: '',
  rates: [],
});

const form = ref<Tariff>(emptyForm());

// ── Constants ──────────────────────────────────────────────────────────────
const SERVICE_TYPES: ServiceType[] = [
  'STORAGE', 'HANDLING', 'THC', 'DEMURRAGE', 'DETENTION',
  'CLEANING', 'INSPECTION', 'WEIGHING', 'SCANNING', 'REEFER',
  'HAZMAT', 'OOG', 'ADMIN', 'CUSTOMS', 'OTHER',
];

const RATE_TYPES = ['SIMPLE', 'TIERED', 'BANDED', 'VOLUME', 'CUSTOM'] as const;

const CURRENCIES = ['USD', 'EUR', 'GBP', 'XOF', 'MAD'];

const tableHeaders = computed(() => [
  t('tariffs.column.name'),
  t('tariffs.column.serviceType'),
  t('tariffs.column.status'),
  t('tariffs.column.startDate'),
  t('tariffs.column.endDate'),
  t('tariffs.column.actions'),
]);

const rateHeaders = computed(() => [
  t('tariffs.rateColumn.amount'),
  t('tariffs.rateColumn.currency'),
  t('tariffs.rateColumn.unitOfMeasure'),
  t('tariffs.rateColumn.rateType'),
  t('tariffs.rateColumn.glCode'),
  '',
]);

// ── Computed ───────────────────────────────────────────────────────────────
const filteredTariffs = computed(() => {
  return tariffs.value.filter(t => {
    const matchesSearch =
      !searchQuery.value ||
      t.name.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
      (t.description ?? '').toLowerCase().includes(searchQuery.value.toLowerCase());
    const matchesType =
      !serviceTypeFilter.value || t.serviceType === serviceTypeFilter.value;
    return matchesSearch && matchesType;
  });
});

const activeTariffsCount = computed(() =>
  tariffs.value.filter(t => t.status === 'Active').length
);

const subtitleText = computed(() =>
  t('tariffs.subtitle', {
    active: activeTariffsCount.value,
    inactive: tariffs.value.length - activeTariffsCount.value,
    total: tariffs.value.length,
  })
);

// ── API calls ──────────────────────────────────────────────────────────────
const fetchTariffs = async () => {
  try {
    tariffs.value = await tariffService.getTariffs();
  } catch {
    alert(t('tariffs.dialog.failedToLoadTariffs'));
  }
};

onMounted(fetchTariffs);

// ── Helpers ────────────────────────────────────────────────────────────────
const getStatusBadgeClasses = (status?: string) => {
  const base = 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium';
  return status === 'Active'
    ? `${base} bg-green-100 text-green-800 border border-green-200`
    : `${base} bg-gray-100 text-gray-800 border border-gray-200`;
};

const formatDate = (d?: Date | string) => {
  if (!d) return '—';
  return new Date(d as string).toLocaleDateString();
};

const isDateExpiringSoon = (endDate?: Date | string) => {
  if (!endDate) return false;
  const diff = (new Date(endDate as string).getTime() - Date.now()) / (1000 * 3600 * 24);
  return diff <= 30 && diff > 0;
};

const isDateExpired = (endDate?: Date | string) => {
  if (!endDate) return false;
  return new Date(endDate as string) < new Date();
};

// ── CRUD actions ───────────────────────────────────────────────────────────
const handleAdd = () => {
  editingTariff.value = null;
  form.value = emptyForm();
  showForm.value = true;
};

const handleEdit = (tariff: Tariff) => {
  editingTariff.value = { ...tariff };
  form.value = {
    ...tariff,
    rates: tariff.rates ? tariff.rates.map(r => ({ ...r })) : [],
  };
  showForm.value = true;
};

const handleDelete = (tariff: Tariff) => {
  tariffToDelete.value = tariff;
  showDeleteConfirm.value = true;
};

const confirmDelete = async () => {
  if (!tariffToDelete.value?.id) return;
  try {
    await tariffService.deleteTariff(tariffToDelete.value.id);
    alert(t('tariffs.dialog.tariffDeletedSuccessfully'));
    await fetchTariffs();
  } catch {
    alert(t('tariffs.dialog.failedToDeleteTariff'));
  } finally {
    showDeleteConfirm.value = false;
    tariffToDelete.value = null;
  }
};

const handleFormCancel = () => {
  showForm.value = false;
  editingTariff.value = null;
};

const handleFormSubmit = async () => {
  if (!form.value.name.trim()) {
    alert(t('tariffs.dialog.nameIsRequired'));
    return;
  }
  isSaving.value = true;
  try {
    if (editingTariff.value?.id) {
      await tariffService.updateTariff(editingTariff.value.id, form.value);
      alert(t('tariffs.dialog.tariffUpdatedSuccessfully'));
    } else {
      await tariffService.createTariff(form.value);
      alert(t('tariffs.dialog.tariffCreatedSuccessfully'));
    }
    showForm.value = false;
    editingTariff.value = null;
    await fetchTariffs();
  } catch (error: any) {
    if (error?.response?.status === 404) {
      alert(t('tariffs.dialog.tariffNotFound'));
    } else {
      alert(editingTariff.value ? t('tariffs.dialog.failedToUpdateTariff') : t('tariffs.dialog.failedToCreateTariff'));
    }
  } finally {
    isSaving.value = false;
  }
};

// ── Rate row helpers ───────────────────────────────────────────────────────
const addRate = () => {
  if (!form.value.rates) form.value.rates = [];
  form.value.rates.push({
    amount: undefined,
    currency: 'USD',
    unitOfMeasurement: '',
    rateType: 'SIMPLE',
    glCode: '',
  });
};

const removeRate = (index: number) => {
  form.value.rates?.splice(index, 1);
};
</script>

<template>
  <div class="min-h-screen bg-gray-50">

    <!-- ── List View ──────────────────────────────────────────────────── -->
    <div v-if="!showForm">
      <PageHeader
        :title="t('tariffs.label.tariffManagement')"
        :subtitle="subtitleText"
        :count="tariffs.length"
      >
        <template #actions>
          <Button @click="handleAdd">
            <Plus class="h-4 w-4" />
            {{ t('tariffs.button.newTariff') }}
          </Button>
        </template>
      </PageHeader>

      <div class="px-6 py-6 space-y-6">

      <!-- Search & Filter Bar -->
      <div class="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
        <div class="p-6">
          <div class="flex flex-col sm:flex-row gap-4">
            <!-- Search -->
            <div class="flex-1 relative">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Search class="h-5 w-5 text-gray-400" />
              </div>
              <input
                v-model="searchQuery"
                type="text"
                :placeholder="t('tariffs.placeholder.search')"
                class="block w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg leading-5 bg-white placeholder-gray-500 focus:outline-none focus:placeholder-gray-400 focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
            <!-- Service Type filter -->
            <div class="flex items-center space-x-2">
              <Filter class="h-4 w-4 text-gray-400 flex-shrink-0" />
              <select
                v-model="serviceTypeFilter"
                class="block pl-3 pr-8 py-3 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              >
                <option value="">{{ t('tariffs.filter.allServiceTypes') }}</option>
                <option v-for="st in SERVICE_TYPES" :key="st" :value="st">{{ st }}</option>
              </select>
            </div>
          </div>
        </div>
      </div>

      <!-- Table -->
      <div class="bg-white shadow-sm rounded-lg border border-gray-200 overflow-hidden">

        <!-- Desktop table -->
        <div class="hidden sm:block overflow-x-auto">
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
              <tr
                v-for="tariff in filteredTariffs"
                :key="tariff.id"
                class="hover:bg-gray-50 transition-colors duration-150"
              >
                <!-- Name -->
                <td class="px-4 py-3">
                  <div class="max-w-xs">
                    <div class="text-sm font-semibold text-gray-900 truncate">{{ tariff.name }}</div>
                    <div class="text-sm text-gray-500 truncate">{{ tariff.description }}</div>
                  </div>
                </td>

                <!-- Service Type -->
                <td class="px-4 py-3">
                  <span
                    v-if="tariff.serviceType"
                    class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800 border border-blue-200"
                  >
                    {{ tariff.serviceType }}
                  </span>
                  <span v-else class="text-gray-400 text-sm">—</span>
                </td>

                <!-- Status -->
                <td class="px-4 py-3">
                  <span :class="getStatusBadgeClasses(tariff.status)">
                    {{ tariff.status ?? '—' }}
                  </span>
                </td>

                <!-- Start Date -->
                <td class="px-4 py-3 text-sm text-gray-700">
                  {{ formatDate(tariff.startDate) }}
                </td>

                <!-- End Date -->
                <td class="px-4 py-3 text-sm">
                  <span
                    :class="[
                      isDateExpired(tariff.endDate) ? 'text-red-600 font-medium' :
                      isDateExpiringSoon(tariff.endDate) ? 'text-orange-600 font-medium' :
                      'text-gray-700'
                    ]"
                  >
                    {{ formatDate(tariff.endDate) }}
                  </span>
                </td>

                <!-- Actions -->
                <td class="px-4 py-3">
                  <div class="flex items-center space-x-2">
                    <button
                      @click="handleEdit(tariff)"
                      class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                      :title="t('tariffs.action.editTariff')"
                      :aria-label="t('tariffs.action.editTariff')"
                    >
                      <Pencil class="h-4 w-4" />
                    </button>
                    <button
                      @click="handleDelete(tariff)"
                      class="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                      :title="t('tariffs.action.deleteTariff')"
                      :aria-label="t('tariffs.action.deleteTariff')"
                    >
                      <Trash2 class="h-4 w-4" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Mobile cards -->
        <div class="sm:hidden p-4">
          <div v-if="filteredTariffs.length === 0" class="bg-white rounded-xl border border-slate-200 p-8 text-center">
            <p class="text-sm text-slate-500">{{ t('tariffs.empty.noTariffsFound') }}</p>
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="tariff in filteredTariffs"
              :key="tariff.id"
              class="bg-white rounded-xl border border-slate-200 p-4 shadow-sm"
            >
              <div class="flex items-start justify-between gap-2 mb-2">
                <div>
                  <p class="font-semibold text-slate-900 text-sm">{{ tariff.name }}</p>
                  <p class="text-xs text-slate-500 mt-0.5">{{ tariff.description || '—' }}</p>
                </div>
                <span :class="getStatusBadgeClasses(tariff.status)">{{ tariff.status ?? '—' }}</span>
              </div>
              <div class="flex items-center justify-between text-xs text-slate-500 mb-3">
                <span v-if="tariff.serviceType" class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                  {{ tariff.serviceType }}
                </span>
                <span>{{ formatDate(tariff.startDate) }} → {{ formatDate(tariff.endDate) }}</span>
              </div>
              <div class="flex justify-end space-x-2">
                <button
                  @click="handleEdit(tariff)"
                  class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                  :aria-label="t('tariffs.action.editTariff')"
                >
                  <Pencil class="h-4 w-4" />
                </button>
                <button
                  @click="handleDelete(tariff)"
                  class="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                  :aria-label="t('tariffs.action.deleteTariff')"
                >
                  <Trash2 class="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div v-if="filteredTariffs.length === 0" class="text-center py-12">
          <DollarSign class="mx-auto h-12 w-12 text-gray-400" />
          <h3 class="mt-2 text-sm font-medium text-gray-900">
            {{ searchQuery || serviceTypeFilter ? t('tariffs.empty.noTariffsFound') : t('tariffs.empty.noTariffs') }}
          </h3>
          <p class="mt-1 text-sm text-gray-500">
            {{ searchQuery || serviceTypeFilter ? t('tariffs.empty.adjustSearchOrFilter') : t('tariffs.empty.getStarted') }}
          </p>
          <div class="mt-6">
            <button
              v-if="!searchQuery && !serviceTypeFilter"
              @click="handleAdd"
              class="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700"
            >
              <Plus class="h-4 w-4 mr-2" />
              {{ t('tariffs.button.newTariff') }}
            </button>
          </div>
        </div>
      </div>
      </div>
    </div>

    <!-- ── Create / Edit Form View ────────────────────────────────────── -->
    <div v-else class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <!-- Form Header -->
      <div class="mb-8">
        <h1 class="text-2xl font-bold text-gray-900">
          {{ editingTariff ? t('tariffs.header.editTitle') : t('tariffs.header.newTitle') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500">
          {{ editingTariff ? t('tariffs.header.editSubtitle') : t('tariffs.header.newSubtitle') }}
        </p>
      </div>

      <form @submit.prevent="handleFormSubmit" class="space-y-6">

        <!-- Basic Info Card -->
        <div class="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
          <h2 class="text-base font-semibold text-gray-900 mb-4">{{ t('tariffs.section.basicInformation') }}</h2>
          <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">

            <!-- Name -->
            <div class="sm:col-span-2">
              <label class="block text-sm font-medium text-gray-700 mb-1">
                {{ t('tariffs.field.name') }} <span class="text-red-500">*</span>
              </label>
              <input
                v-model="form.name"
                type="text"
                required
                :placeholder="t('tariffs.placeholder.name')"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <!-- Description -->
            <div class="sm:col-span-2">
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('tariffs.field.description') }}</label>
              <textarea
                v-model="form.description"
                rows="2"
                :placeholder="t('tariffs.placeholder.description')"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <!-- Service Type -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('tariffs.field.serviceType') }}</label>
              <select
                v-model="form.serviceType"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              >
                <option value="">{{ t('tariffs.option.select') }}</option>
                <option v-for="st in SERVICE_TYPES" :key="st" :value="st">{{ st }}</option>
              </select>
            </div>

            <!-- Status -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('tariffs.field.status') }}</label>
              <select
                v-model="form.status"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              >
                <option value="Active">{{ t('tariffs.status.active') }}</option>
                <option value="Disable">{{ t('tariffs.status.disable') }}</option>
              </select>
            </div>

            <!-- Start Date -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('tariffs.field.startDate') }}</label>
              <input
                v-model="(form as any).startDate"
                type="date"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <!-- End Date -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('tariffs.field.endDate') }}</label>
              <input
                v-model="(form as any).endDate"
                type="date"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <!-- Notes -->
            <div class="sm:col-span-2">
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('tariffs.field.notes') }}</label>
              <textarea
                v-model="form.notes"
                rows="3"
                :placeholder="t('tariffs.placeholder.notes')"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
          </div>
        </div>

        <!-- Rates Card -->
        <div class="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-base font-semibold text-gray-900">{{ t('tariffs.section.rates') }}</h2>
            <button
              type="button"
              @click="addRate"
              class="inline-flex items-center px-3 py-1.5 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              <Plus class="h-4 w-4 mr-1" />
              {{ t('tariffs.button.addRate') }}
            </button>
          </div>

          <div v-if="!form.rates || form.rates.length === 0" class="text-center py-8 text-sm text-gray-400">
            {{ t('tariffs.empty.noRatesDefined') }}
          </div>

          <div v-else class="overflow-x-auto">
            <table class="min-w-full divide-y divide-gray-200">
              <thead class="bg-gray-50">
                <tr>
                  <th
                    v-for="h in rateHeaders"
                    :key="h"
                    class="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
                  >
                    {{ h }}
                  </th>
                </tr>
              </thead>
              <tbody class="bg-white divide-y divide-gray-100">
                <tr v-for="(rate, idx) in form.rates" :key="idx">
                  <!-- Amount -->
                  <td class="px-3 py-2">
                    <input
                      v-model.number="rate.amount"
                      type="number"
                      min="0"
                      step="0.01"
                      :placeholder="t('tariffs.placeholder.amount')"
                      class="block w-24 px-2 py-1.5 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                    />
                  </td>
                  <!-- Currency -->
                  <td class="px-3 py-2">
                    <select
                      v-model="rate.currency"
                      class="block w-24 px-2 py-1.5 border border-gray-300 rounded-md text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                    >
                      <option v-for="c in CURRENCIES" :key="c" :value="c">{{ c }}</option>
                    </select>
                  </td>
                  <!-- Unit of Measurement -->
                  <td class="px-3 py-2">
                    <input
                      v-model="rate.unitOfMeasurement"
                      type="text"
                      :placeholder="t('tariffs.placeholder.unitOfMeasure')"
                      class="block w-28 px-2 py-1.5 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                    />
                  </td>
                  <!-- Rate Type -->
                  <td class="px-3 py-2">
                    <select
                      v-model="rate.rateType"
                      class="block w-28 px-2 py-1.5 border border-gray-300 rounded-md text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                    >
                      <option v-for="rt in RATE_TYPES" :key="rt" :value="rt">{{ rt }}</option>
                    </select>
                  </td>
                  <!-- GL Code -->
                  <td class="px-3 py-2">
                    <input
                      v-model="rate.glCode"
                      type="text"
                      :placeholder="t('tariffs.placeholder.glCode')"
                      class="block w-24 px-2 py-1.5 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                    />
                  </td>
                  <!-- Remove -->
                  <td class="px-3 py-2">
                    <button
                      type="button"
                      @click="removeRate(idx)"
                      class="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                      :aria-label="t('tariffs.action.removeRate')"
                    >
                      <Trash2 class="h-4 w-4" />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Form Actions -->
        <div class="flex justify-end space-x-3 pb-8">
          <button
            type="button"
            @click="handleFormCancel"
            class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="submit"
            :disabled="isSaving"
            class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <span v-if="isSaving">{{ t('tariffs.button.saving') }}</span>
            <span v-else>{{ editingTariff ? t('tariffs.button.updateTariff') : t('tariffs.button.createTariff') }}</span>
          </button>
        </div>
      </form>
    </div>

    <!-- ── Delete Confirmation Modal ──────────────────────────────────── -->
    <Teleport to="body">
      <div v-if="showDeleteConfirm" class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4">
        <div class="bg-white rounded-xl shadow-xl max-w-md w-full">
          <div class="p-6">
            <div class="flex items-center space-x-3">
              <div class="flex-shrink-0">
                <XCircle class="h-10 w-10 text-red-500" />
              </div>
              <div class="flex-1">
                <h3 class="text-lg font-semibold text-gray-900">{{ t('tariffs.deleteModal.title') }}</h3>
                <p class="mt-1 text-sm text-gray-600">
                  <i18n-t keypath="tariffs.deleteModal.confirm" tag="span">
                    <template #name><strong>{{ tariffToDelete?.name }}</strong></template>
                  </i18n-t>
                </p>
              </div>
            </div>
          </div>
          <div class="px-4 py-3 bg-gray-50 rounded-b-xl flex justify-end space-x-3">
            <button
              @click="showDeleteConfirm = false"
              class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              @click="confirmDelete"
              class="px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500"
            >
              {{ t('tariffs.deleteModal.confirmButton') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

  </div>
</template>
