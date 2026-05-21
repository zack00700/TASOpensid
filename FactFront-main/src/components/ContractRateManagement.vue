<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { checkRateOverlap, sortRatesDefaultFirst } from '../utils/contract';
import { useI18n } from 'vue-i18n';
import { v4 as uuidv4 } from "uuid";
import {
  Plus,
  Trash2,
  DollarSign,
  Star,
  AlertCircle,
  X,
  Edit3,
  Calendar,
  Hash,
  Package,
  Clock,
  Copy,
  MoreHorizontal,
  ArrowUpDown
} from "lucide-vue-next";

const { t } = useI18n();

interface Rate {
  id: string;
  amount: number;
  flatCost?: number;
  currency: string;
  defaultRate: boolean;
  priority: number;

  // Quantity fields
  startQuantity?: number;
  endQuantity?: number;
  unitOfMeasurement?: string;

  // Date fields
  startDate?: string;
  endDate?: string;

  // Item filtering
  applicableCategory?: string;
  applicableFreightKind?: string;
}

const props = defineProps<{
  contractId?: string;
  calculationType?: string;
  rates?: Rate[];
}>();

const emit = defineEmits<{
  (e: "update:rates", rates: Rate[]): void;
}>();

const rates = ref<Rate[]>([]);
const showAddRate = ref(false);
const editingRate = ref<Rate | null>(null);
const showDeleteConfirm = ref(false);
const rateToDelete = ref<Rate | null>(null);
const selectedRates = ref<string[]>([]);
const sortBy = ref<'priority' | 'amount' | 'quantity' | 'date'>('priority');
const sortOrder = ref<'asc' | 'desc'>('desc');

watch(
  () => props.rates,
  (newRates) => {
    rates.value = [...(newRates || [])];
  },
  { immediate: true }
);

const currencies = ["USD", "EUR", "GBP", "JPY", "CAD", "AUD"];
const unitsOfMeasurement = ["Items", "Hours", "Days", "Kilograms", "TEU", "Cubic Meters", "Tons", "Pallets"];

// Helper function to format dates for HTML date inputs
const formatDateForInput = (dateString?: string) => {
  if (!dateString) return '';
  try {
    return new Date(dateString).toISOString().slice(0, 10);
  } catch {
    return '';
  }
};

const newRate = ref<Rate>({
  id: "",
  amount: 0,
  flatCost: 0,
  currency: "USD",
  defaultRate: false,
  priority: 0,
  applicableCategory: "",
  applicableFreightKind: "",
});

const errors = ref<Record<string, string>>({});

const sortedRates = computed(() =>
  sortRatesDefaultFirst(rates.value, sortBy.value, sortOrder.value),
);

const hasRates = computed(() => rates.value.length > 0);
const hasDefaultRate = computed(() => rates.value.some(r => r.defaultRate));
const totalRates = computed(() => rates.value.length);

const validateRate = (rate: Rate): boolean => {
  errors.value = {};
  let isValid = true;

  if (rate.amount <= 0) {
    errors.value.amount = t('contractRateManagement.error.amountGreaterThanZero');
    isValid = false;
  }

  if (props.calculationType === 'Quantity' || props.calculationType === 'DateByTEU'
    || props.calculationType === 'Tiered' || props.calculationType === 'Banded') {
    if (rate.startQuantity === undefined || rate.startQuantity < 0) {
      errors.value.startQuantity = t('contractRateManagement.error.startQuantityRequired');
      isValid = false;
    }
    if (rate.endQuantity === undefined || rate.endQuantity <= (rate.startQuantity || 0)) {
      errors.value.endQuantity = t('contractRateManagement.error.endQuantityGreater');
      isValid = false;
    }
    if (!rate.unitOfMeasurement) {
      errors.value.unitOfMeasurement = t('contractRateManagement.error.unitOfMeasurementRequired');
      isValid = false;
    }
  }

  if (props.calculationType === 'Date' || props.calculationType === 'DateByTEU') {
    if (!rate.startDate) {
      errors.value.startDate = t('contractRateManagement.error.startDateRequired');
      isValid = false;
    }
    if (!rate.endDate) {
      errors.value.endDate = t('contractRateManagement.error.endDateRequired');
      isValid = false;
    }
    if (
      rate.startDate &&
      rate.endDate &&
      new Date(rate.endDate) <= new Date(rate.startDate)
    ) {
      errors.value.endDate = t('contractRateManagement.error.endDateAfterStart');
      isValid = false;
    }
  }

  return isValid;
};

const checkOverlap = (rate: Rate): boolean => {
  return checkRateOverlap(rates.value, rate);
};

const handleAddRate = () => {
  newRate.value = {
    id: "",
    amount: 0,
    currency: "USD",
    defaultRate: false,
    priority: rates.value.length,
    applicableCategory: "",
    applicableFreightKind: "",
  };
  editingRate.value = null;
  showAddRate.value = true;
};

const handleSaveRate = () => {
  if (!validateRate(newRate.value)) {
    return;
  }

  if (checkOverlap(newRate.value)) {
    errors.value.overlap = t('contractRateManagement.error.overlap');
    return;
  }

  // If this is set as default, remove default from others
  if (newRate.value.defaultRate) {
    rates.value.forEach(r => r.defaultRate = false);
  }

  const rateToSave = {
    ...newRate.value,
    id: editingRate.value?.id || uuidv4(),
  };

  if (editingRate.value) {
    const index = rates.value.findIndex((r) => r.id === editingRate.value!.id);
    rates.value[index] = rateToSave;
  } else {
    rates.value.push(rateToSave);
  }

  emit("update:rates", rates.value);
  showAddRate.value = false;
  editingRate.value = null;
};

const handleEditRate = (rate: Rate) => {
  editingRate.value = rate;
  newRate.value = {
    ...rate,
    // Format dates for HTML date inputs
    startDate: formatDateForInput(rate.startDate),
    endDate: formatDateForInput(rate.endDate)
  };
  showAddRate.value = true;
};

const handleCopyRate = (rate: Rate) => {
  newRate.value = {
    ...rate,
    id: "",
    defaultRate: false,
    priority: rates.value.length,
    // Format dates for HTML date inputs
    startDate: formatDateForInput(rate.startDate),
    endDate: formatDateForInput(rate.endDate)
  };
  editingRate.value = null;
  showAddRate.value = true;
};

const handleDeleteRate = (rate: Rate) => {
  rateToDelete.value = rate;
  showDeleteConfirm.value = true;
};

const handleBulkDelete = () => {
  if (selectedRates.value.length === 0) return;
  rates.value = rates.value.filter(r => !selectedRates.value.includes(r.id));
  selectedRates.value = [];
  emit("update:rates", rates.value);
};

const confirmDelete = () => {
  if (rateToDelete.value) {
    rates.value = rates.value.filter((r) => r.id !== rateToDelete.value!.id);
    emit("update:rates", rates.value);
    showDeleteConfirm.value = false;
    rateToDelete.value = null;
  }
};

const toggleSort = (newSortBy: typeof sortBy.value) => {
  if (sortBy.value === newSortBy) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortBy.value = newSortBy;
    sortOrder.value = 'asc';
  }
};

const toggleSelectAll = () => {
  if (selectedRates.value.length === rates.value.length) {
    selectedRates.value = [];
  } else {
    selectedRates.value = rates.value.map(r => r.id);
  }
};

const getRateTypeIcon = () => {
  switch (props.calculationType) {
    case 'Quantity': return Package;
    case 'Date': return Calendar;
    case 'DateByTEU': return Clock;
    case 'Tiered': return ArrowUpDown;
    case 'Banded': return ArrowUpDown;
    default: return Hash;
  }
};

const isTieredOrBanded = () =>
  props.calculationType === 'Tiered' || props.calculationType === 'Banded';

const formatDateRange = (startDate?: string, endDate?: string) => {
  if (!startDate || !endDate) return '';
  const start = new Date(startDate).toLocaleDateString();
  const end = new Date(endDate).toLocaleDateString();
  return `${start} - ${end}`;
};

const getInputClasses = (fieldName: string) => {
  return {
    "block w-full rounded-lg border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 transition-colors duration-200": true,
    "border-red-300 focus:border-red-500 focus:ring-red-500": errors.value[fieldName],
  };
};

const getCardClasses = (rate: Rate) => {
  return {
    "group relative bg-white border-2 rounded-xl hover:shadow-lg transition-all duration-200 overflow-hidden": true,
    "border-yellow-200 bg-yellow-50": rate.defaultRate,
    "border-gray-200 hover:border-blue-300": !rate.defaultRate,
    "ring-2 ring-blue-500 border-blue-500": selectedRates.value.includes(rate.id)
  };
};
</script>

<template>
  <div class="space-y-6">

    <!-- Header Section -->
    <div class="bg-white rounded-lg border border-gray-200 p-6">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center space-x-3">
          <component :is="getRateTypeIcon()" class="h-6 w-6 text-blue-500" />
          <div>
            <h3 class="text-lg font-semibold text-gray-900">{{ t('contractRateManagement.rateManagement') }}</h3>
            <p class="text-sm text-gray-500">{{ t('contractRateManagement.configurePricingTiers', { calculationType }) }}</p>
          </div>
        </div>
        <button
          type="button"
          @click="handleAddRate"
          class="inline-flex items-center px-4 py-2 bg-blue-600 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors duration-200"
        >
          <Plus class="h-4 w-4 mr-2" />
          {{ t('contractRateManagement.addRate') }}
        </button>
      </div>

      <!-- Statistics -->
      <div v-if="hasRates" class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div class="bg-gray-50 rounded-lg p-4">
          <div class="flex items-center">
            <Hash class="h-5 w-5 text-gray-400 mr-2" />
            <div>
              <p class="text-sm font-medium text-gray-900">{{ totalRates }}</p>
              <p class="text-xs text-gray-500">{{ t('contractRateManagement.totalRates') }}</p>
            </div>
          </div>
        </div>
        <div class="bg-yellow-50 rounded-lg p-4">
          <div class="flex items-center">
            <Star class="h-5 w-5 text-yellow-500 mr-2" />
            <div>
              <p class="text-sm font-medium text-gray-900">{{ hasDefaultRate ? t('common.yes') : t('common.no') }}</p>
              <p class="text-xs text-gray-500">{{ t('contractRateManagement.defaultRateSet') }}</p>
            </div>
          </div>
        </div>
        <div class="bg-blue-50 rounded-lg p-4">
          <div class="flex items-center">
            <DollarSign class="h-5 w-5 text-blue-500 mr-2" />
            <div>
              <p class="text-sm font-medium text-gray-900">
                {{ rates.length > 0 ? `${Math.min(...rates.map(r => r.amount))} - ${Math.max(...rates.map(r => r.amount))}` : '0' }}
              </p>
              <p class="text-xs text-gray-500">{{ t('contractRateManagement.rateRange') }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Bulk Actions -->
    <div v-if="selectedRates.length > 0" class="bg-blue-50 border border-blue-200 rounded-lg p-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center">
          <span class="text-sm font-medium text-blue-900">
            {{ t('contractRateManagement.ratesSelected', selectedRates.length, { count: selectedRates.length }) }}
          </span>
        </div>
        <div class="flex items-center space-x-3">
          <button
            type="button"
            @click="selectedRates = []"
            class="text-sm text-blue-700 hover:text-blue-900"
          >
            {{ t('contractRateManagement.clearSelection') }}
          </button>
          <button
            type="button"
            @click="handleBulkDelete"
            class="inline-flex items-center px-3 py-1.5 border border-transparent text-sm font-medium rounded-md text-white bg-red-600 hover:bg-red-700"
          >
            <Trash2 class="h-4 w-4 mr-1" />
            {{ t('contractRateManagement.deleteSelected') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Rate List -->
    <div v-if="hasRates" class="space-y-6">

      <!-- Controls -->
      <div class="flex items-center justify-between">
        <div class="flex items-center space-x-4">
          <div class="flex items-center">
            <input
              type="checkbox"
              :checked="selectedRates.length === rates.length && rates.length > 0"
              :indeterminate="selectedRates.length > 0 && selectedRates.length < rates.length"
              @change="toggleSelectAll"
              class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
            />
            <label class="ml-2 text-sm text-gray-700">{{ t('contractRateManagement.selectAll') }}</label>
          </div>
        </div>

        <div class="flex items-center space-x-2">
          <span class="text-sm text-gray-500">{{ t('itemLifecycleView.sortBy') }}</span>
          <button
            type="button"
            @click="toggleSort('priority')"
            :class="[
              'inline-flex items-center px-3 py-1.5 text-sm rounded-md',
              sortBy === 'priority' ? 'bg-blue-100 text-blue-700' : 'text-gray-500 hover:text-gray-700'
            ]"
          >
            {{ t('contractRateManagement.priority') }}
            <ArrowUpDown v-if="sortBy === 'priority'" class="ml-1 h-3 w-3" />
          </button>
          <button
            type="button"
            @click="toggleSort('amount')"
            :class="[
              'inline-flex items-center px-3 py-1.5 text-sm rounded-md',
              sortBy === 'amount' ? 'bg-blue-100 text-blue-700' : 'text-gray-500 hover:text-gray-700'
            ]"
          >
            {{ t('payments.column.alloc.amount') }}
            <ArrowUpDown v-if="sortBy === 'amount'" class="ml-1 h-3 w-3" />
          </button>
          <button
            type="button"
            v-if="calculationType === 'Quantity' || calculationType === 'DateByTEU' || isTieredOrBanded()"
            @click="toggleSort('quantity')"
            :class="[
              'inline-flex items-center px-3 py-1.5 text-sm rounded-md',
              sortBy === 'quantity' ? 'bg-blue-100 text-blue-700' : 'text-gray-500 hover:text-gray-700'
            ]"
          >
            {{ t('contractRateManagement.quantity') }}
            <ArrowUpDown v-if="sortBy === 'quantity'" class="ml-1 h-3 w-3" />
          </button>
          <button
            type="button"
            v-if="calculationType === 'Date' || calculationType === 'DateByTEU'"
            @click="toggleSort('date')"
            :class="[
              'inline-flex items-center px-3 py-1.5 text-sm rounded-md',
              sortBy === 'date' ? 'bg-blue-100 text-blue-700' : 'text-gray-500 hover:text-gray-700'
            ]"
          >
            {{ t('payments.column.alloc.date') }}
            <ArrowUpDown v-if="sortBy === 'date'" class="ml-1 h-3 w-3" />
          </button>
        </div>
      </div>

      <!-- Rates Grid -->
      <div class="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
        <div
          v-for="rate in sortedRates"
          :key="rate.id"
          :class="getCardClasses(rate)"
        >
          <!-- Selection Checkbox -->
          <div class="absolute top-4 left-4 z-10">
            <input
              v-model="selectedRates"
              :value="rate.id"
              type="checkbox"
              class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
            />
          </div>

          <!-- Default Rate Badge -->
          <div v-if="rate.defaultRate" class="absolute top-4 right-4">
            <div class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800 border border-yellow-200">
              <Star class="h-3 w-3 mr-1" />
              {{ t('contractRateManagement.default') }}
            </div>
          </div>

          <div class="p-6 pt-12">
            <!-- Rate Amount -->
            <div class="mb-4">
              <div class="flex items-baseline space-x-1">
                <span class="text-2xl font-bold text-gray-900">{{ rate.amount }}</span>
                <span class="text-lg text-gray-600">{{ rate.currency }}</span>
              </div>
              <p class="text-sm text-gray-500">
                {{ t('contractRateManagement.perUnit', { unit: rate.unitOfMeasurement || t('contractRateManagement.unit') }) }}
              </p>
              <p v-if="rate.flatCost && rate.flatCost > 0" class="text-sm text-gray-500 mt-1">
                {{ t('contractRateManagement.flatCostSummary', { amount: rate.flatCost, currency: rate.currency }) }}
              </p>
            </div>

            <!-- Rate Details -->
            <div class="space-y-3">
              <div v-if="rate.startQuantity !== undefined && rate.endQuantity !== undefined" class="flex items-center text-sm">
                <Package class="h-4 w-4 text-gray-400 mr-2 flex-shrink-0" />
                <span class="text-gray-600">
                  {{ rate.startQuantity }} - {{ rate.endQuantity }} {{ rate.unitOfMeasurement }}
                </span>
              </div>

              <div v-if="rate.startDate && rate.endDate" class="flex items-center text-sm">
                <Calendar class="h-4 w-4 text-gray-400 mr-2 flex-shrink-0" />
                <span class="text-gray-600">
                  {{ formatDateRange(rate.startDate, rate.endDate) }}
                </span>
              </div>

              <div class="flex items-center text-sm">
                <Hash class="h-4 w-4 text-gray-400 mr-2 flex-shrink-0" />
                <span class="text-gray-600">{{ t('contractRateManagement.priorityValue', { priority: rate.priority }) }}</span>
              </div>

              <div v-if="rate.applicableCategory || rate.applicableFreightKind" class="flex flex-wrap gap-1 mt-1">
                <span v-if="rate.applicableCategory" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-800">
                  {{ rate.applicableCategory }}
                </span>
                <span v-if="rate.applicableFreightKind" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800">
                  {{ rate.applicableFreightKind }}
                </span>
              </div>
            </div>

            <!-- Actions -->
            <div class="flex items-center justify-between mt-6 pt-4 border-t border-gray-100">
              <div class="flex space-x-1">
                <button
                  type="button"
                  @click="handleCopyRate(rate)"
                  class="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                  :title="t('contractRateManagement.label.copyRate')"
                >
                  <Copy class="h-4 w-4" />
                </button>
                <button
                  type="button"
                  @click="handleEditRate(rate)"
                  class="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                  :title="t('contractRateManagement.label.editRate')"
                >
                  <Edit3 class="h-4 w-4" />
                </button>
              </div>
              <button
                type="button"
                @click="handleDeleteRate(rate)"
                class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                :title="t('contractRateManagement.label.deleteRate')"
              >
                <Trash2 class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="text-center py-12 bg-white rounded-lg border-2 border-dashed border-gray-300">
      <component :is="getRateTypeIcon()" class="mx-auto h-12 w-12 text-gray-400" />
      <h3 class="mt-4 text-lg font-medium text-gray-900">{{ t('contractRateManagement.noRatesConfigured') }}</h3>
      <p class="mt-2 text-sm text-gray-500 max-w-sm mx-auto">
        {{ t('contractRateManagement.emptyState') }}
      </p>
      <div class="mt-6">
        <button
          type="button"
          @click="handleAddRate"
          class="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700"
        >
          <Plus class="h-4 w-4 mr-2" />
          {{ t('contractRateManagement.addYourFirstRate') }}
        </button>
      </div>
    </div>

    <!-- Add/Edit Rate Modal -->
    <Teleport to="body">
      <div
        v-if="showAddRate"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4"
      >
        <div class="bg-white rounded-xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
          <div class="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 rounded-t-xl">
            <div class="flex justify-between items-center">
              <div>
                <h3 class="text-lg font-semibold text-gray-900">
                  {{ editingRate ? t('contractRateManagement.editRate') : t('contractRateManagement.addNewRate') }}
                </h3>
                <p class="text-sm text-gray-500 mt-1">
                  {{ t('contractRateManagement.configurePricing', { calculationType }) }}
                </p>
              </div>
              <button
                @click="showAddRate = false"
                class="text-gray-400 hover:text-gray-500 p-1 rounded-lg hover:bg-gray-100"
              >
                <X class="h-6 w-6" />
              </button>
            </div>
          </div>

          <form @submit.prevent="handleSaveRate" class="p-6 space-y-6">
            <!-- Rate Amount -->
            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  {{ isTieredOrBanded() ? t('contractRateManagement.unitCost') : t('contractRateManagement.rateAmount') }}
                  <span class="text-red-500">*</span>
                </label>
                <div class="relative rounded-lg shadow-sm">
                  <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <DollarSign class="h-5 w-5 text-gray-400" />
                  </div>
                  <input
                    v-model.number="newRate.amount"
                    type="number"
                    step="0.01"
                    min="0"
                    :class="[getInputClasses('amount'), 'pl-10']"
                    placeholder="0.00"
                  />
                </div>
                <p v-if="errors.amount" class="mt-2 text-sm text-red-600 flex items-center">
                  <AlertCircle class="h-4 w-4 mr-1" />
                  {{ errors.amount }}
                </p>
              </div>

              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  {{ t('payments.form.currency') }} <span class="text-red-500">*</span>
                </label>
                <select v-model="newRate.currency" :class="getInputClasses('currency')">
                  <option v-for="currency in currencies" :key="currency" :value="currency">
                    {{ currency }}
                  </option>
                </select>
              </div>
            </div>

            <!-- Flat Cost (Tiered / Banded only) -->
            <div v-if="isTieredOrBanded()">
              <label class="block text-sm font-medium text-gray-700 mb-2">
                {{ t('contractRateManagement.flatCost') }} <span class="text-gray-400 font-normal">{{ t('contractRateManagement.flatCostHint') }}</span>
              </label>
              <div class="relative rounded-lg shadow-sm">
                <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <DollarSign class="h-5 w-5 text-gray-400" />
                </div>
                <input
                  v-model.number="newRate.flatCost"
                  type="number"
                  step="0.01"
                  min="0"
                  :class="[getInputClasses('flatCost'), 'pl-10']"
                  placeholder="0.00"
                />
              </div>
            </div>

            <!-- Quantity Fields (for Quantity, DateByTEU, Tiered and Banded types) -->
            <div v-if="calculationType === 'Quantity' || calculationType === 'DateByTEU' || isTieredOrBanded()" class="space-y-6">
              <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-2">
                    {{ t('contractRateManagement.startQuantity') }} <span class="text-red-500">*</span>
                  </label>
                  <input
                    v-model.number="newRate.startQuantity"
                    type="number"
                    min="0"
                    :class="getInputClasses('startQuantity')"
                    placeholder="0"
                  />
                  <p v-if="errors.startQuantity" class="mt-2 text-sm text-red-600 flex items-center">
                    <AlertCircle class="h-4 w-4 mr-1" />
                    {{ errors.startQuantity }}
                  </p>
                </div>

                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-2">
                    {{ t('contractRateManagement.endQuantity') }} <span class="text-red-500">*</span>
                  </label>
                  <input
                    v-model.number="newRate.endQuantity"
                    type="number"
                    :min="(newRate.startQuantity || 0) + 1"
                    :class="getInputClasses('endQuantity')"
                    placeholder="100"
                  />
                  <p v-if="errors.endQuantity" class="mt-2 text-sm text-red-600 flex items-center">
                    <AlertCircle class="h-4 w-4 mr-1" />
                    {{ errors.endQuantity }}
                  </p>
                </div>
              </div>

              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  {{ t('contractRateManagement.unitOfMeasurement') }} <span class="text-red-500">*</span>
                </label>
                <select v-model="newRate.unitOfMeasurement" :class="getInputClasses('unitOfMeasurement')">
                  <option value="">{{ t('contractRateManagement.selectUnit') }}</option>
                  <option v-for="unit in unitsOfMeasurement" :key="unit" :value="unit">
                    {{ unit }}
                  </option>
                </select>
                <p v-if="errors.unitOfMeasurement" class="mt-2 text-sm text-red-600 flex items-center">
                  <AlertCircle class="h-4 w-4 mr-1" />
                  {{ errors.unitOfMeasurement }}
                </p>
              </div>
            </div>

            <!-- Date Fields (for Date and DateByTEU types) -->
            <div v-if="calculationType === 'Date' || calculationType === 'DateByTEU'" class="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  {{ t('itemLifecycleView.column.startDate') }} <span class="text-red-500">*</span>
                </label>
                <input
                  v-model="newRate.startDate"
                  type="date"
                  :class="getInputClasses('startDate')"
                />
                <p v-if="errors.startDate" class="mt-2 text-sm text-red-600 flex items-center">
                  <AlertCircle class="h-4 w-4 mr-1" />
                  {{ errors.startDate }}
                </p>
              </div>

              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  {{ t('itemLifecycleView.column.endDate') }} <span class="text-red-500">*</span>
                </label>
                <input
                  v-model="newRate.endDate"
                  type="date"
                  :min="newRate.startDate"
                  :class="getInputClasses('endDate')"
                />
                <p v-if="errors.endDate" class="mt-2 text-sm text-red-600 flex items-center">
                  <AlertCircle class="h-4 w-4 mr-1" />
                  {{ errors.endDate }}
                </p>
              </div>
            </div>

            <!-- Rate Settings -->
            <div class="bg-gray-50 rounded-lg p-4 space-y-4">
              <h4 class="text-sm font-medium text-gray-900">{{ t('contractRateManagement.rateSettings') }}</h4>

              <div class="flex items-center justify-between">
                <div class="flex items-center">
                  <input
                    v-model="newRate.defaultRate"
                    type="checkbox"
                    class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                  />
                  <label class="ml-3 text-sm text-gray-700">
                    {{ t('contractRateManagement.setAsDefaultRate') }}
                  </label>
                </div>
                <div class="flex items-center space-x-3">
                  <label class="text-sm text-gray-700">{{ t('contractRateManagement.priorityLabel') }}</label>
                  <input
                    v-model.number="newRate.priority"
                    type="number"
                    min="0"
                    class="w-20 rounded-lg border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                  />
                </div>
              </div>

              <p class="text-xs text-gray-500">
                {{ t('contractRateManagement.defaultRateHint') }}
              </p>
            </div>

            <!-- Item Filtering -->
            <div class="bg-gray-50 rounded-lg p-4 space-y-4">
              <div>
                <h4 class="text-sm font-medium text-gray-900">{{ t('contractRateManagement.itemFilters') }}</h4>
                <p class="text-xs text-gray-500 mt-0.5">{{ t('contractRateManagement.itemFiltersHint') }}</p>
              </div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-2">{{ t('itemForm.field.category') }}</label>
                  <select v-model="newRate.applicableCategory" :class="getInputClasses('applicableCategory')">
                    <option value="">{{ t('contractRateManagement.allCategories') }}</option>
                    <option value="Import">{{ t('itemForm.category.import') }}</option>
                    <option value="Export">{{ t('itemForm.category.export') }}</option>
                    <option value="Transship">{{ t('itemForm.category.transship') }}</option>
                  </select>
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-2">{{ t('itemForm.field.freightKind') }}</label>
                  <select v-model="newRate.applicableFreightKind" :class="getInputClasses('applicableFreightKind')">
                    <option value="">{{ t('contractRateManagement.allTypes') }}</option>
                    <option value="FCL">{{ t('contractRateManagement.fcl') }}</option>
                    <option value="LCL">{{ t('contractRateManagement.lcl') }}</option>
                    <option value="Empty">{{ t('contractRateManagement.emptyOption') }}</option>
                    <option value="Breakbulk">{{ t('itemForm.freightKind.breakbulk') }}</option>
                    <option value="Ro-Ro">{{ t('itemForm.freightKind.roro') }}</option>
                  </select>
                </div>
              </div>
            </div>

            <!-- Overlap Warning -->
            <div v-if="errors.overlap" class="rounded-lg bg-red-50 border border-red-200 p-4">
              <div class="flex">
                <AlertCircle class="h-5 w-5 text-red-500 flex-shrink-0" />
                <div class="ml-3">
                  <h3 class="text-sm font-medium text-red-800">{{ t('contractRateManagement.rateConflictDetected') }}</h3>
                  <div class="mt-2 text-sm text-red-700">
                    <p>{{ errors.overlap }}</p>
                    <p class="mt-1">{{ t('contractRateManagement.rateConflictHint') }}</p>
                  </div>
                </div>
              </div>
            </div>

            <!-- Actions -->
            <div class="flex justify-end space-x-3 pt-4 border-t border-gray-200">
              <button
                type="button"
                @click="showAddRate = false"
                class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
              >
                {{ t('common.cancel') }}
              </button>
              <button
                type="submit"
                class="px-6 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
              >
                {{ editingRate ? t('contractRateManagement.saveChanges') : t('contractRateManagement.addRate') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

    <!-- Delete Confirmation Modal -->
    <Teleport to="body">
      <div
        v-if="showDeleteConfirm"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4"
      >
        <div class="bg-white rounded-xl shadow-xl max-w-md w-full">
          <div class="p-6">
            <div class="flex items-center space-x-3">
              <div class="flex-shrink-0">
                <AlertCircle class="h-10 w-10 text-red-500" />
              </div>
              <div class="flex-1">
                <h3 class="text-lg font-semibold text-gray-900">{{ t('contractRateManagement.deleteRate') }}</h3>
                <p class="mt-1 text-sm text-gray-600">
                  {{ t('contractRateManagement.deleteRateConfirm') }}
                </p>
              </div>
            </div>
          </div>
          <div class="px-6 py-4 bg-gray-50 rounded-b-xl flex justify-end space-x-3">
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
              {{ t('contractRateManagement.deleteRate') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
