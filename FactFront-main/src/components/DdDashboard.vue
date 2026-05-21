<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  RefreshCw,
  Filter,
  X,
  Eye,
  TrendingUp,
  AlertTriangle,
  CheckCircle,
  XCircle,
  RotateCcw,
  Plus,
} from 'lucide-vue-next';
import Pagination from './Pagination.vue';
import { getDdSummary, getDdAccruals, applyWaiver, recomputeAccrual } from '../services/ddService';
import type { DdAccrual, DdDashboardSummary, DdWaiver } from '../types/dd';

const { t } = useI18n();

// ── State ──────────────────────────────────────────────────────────────────

const summary = ref<DdDashboardSummary | null>(null);
const accruals = ref<DdAccrual[]>([]);
const isLoading = ref(false);
const isSummaryLoading = ref(false);
const errorMessage = ref('');

// Filters
const filterStatus = ref('');
const filterDdType = ref('');

// Pagination
const currentPage = ref(1);
const pageSize = ref(50);
const totalItems = ref(0);
const totalPages = ref(1);

// Detail modal
const showDetailModal = ref(false);
const selectedAccrual = ref<DdAccrual | null>(null);
const isRecomputing = ref(false);

// Waiver sub-form
const showWaiverForm = ref(false);
const isApplyingWaiver = ref(false);
const waiverForm = ref<DdWaiver>({
  waiverType: 'FULL',
  waivedAmount: undefined,
  reason: '',
  approvedBy: '',
});

// ── Data fetching ───────────────────────────────────────────────────────────

const fetchSummary = async () => {
  isSummaryLoading.value = true;
  try {
    summary.value = await getDdSummary();
  } catch {
    // Non-critical: summary card shows zeros
    summary.value = {
      runningDemurrage: 0,
      runningDetention: 0,
      totalExposure: 0,
      overdueCount: 0,
      waivedCount: 0,
      invoicedCount: 0,
    };
  } finally {
    isSummaryLoading.value = false;
  }
};

const fetchAccruals = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  try {
    const params: Record<string, any> = {
      page: currentPage.value,
      size: pageSize.value,
    };
    if (filterStatus.value) params.status = filterStatus.value;
    if (filterDdType.value) params.ddType = filterDdType.value;

    const data = await getDdAccruals(params);

    if (Array.isArray(data)) {
      accruals.value = data;
      totalItems.value = data.length;
      totalPages.value = 1;
    } else {
      const envelope = data as any;
      accruals.value = envelope.content ?? envelope.items ?? [];
      totalItems.value = envelope.totalElements ?? envelope.totalItems ?? accruals.value.length;
      totalPages.value =
        envelope.totalPages ?? (Math.ceil(totalItems.value / pageSize.value) || 1);
    }
  } catch {
    errorMessage.value = t('ddDashboard.error.failedToLoad');
  } finally {
    isLoading.value = false;
  }
};

onMounted(() => {
  fetchSummary();
  fetchAccruals();
});

// ── Pagination ──────────────────────────────────────────────────────────────

const paginationMeta = computed(() => ({
  currentPage: currentPage.value,
  pageSize: pageSize.value,
  totalItems: totalItems.value,
  totalPages: totalPages.value,
  hasNext: currentPage.value < totalPages.value,
  hasPrevious: currentPage.value > 1,
}));

const handlePageChange = (page: number) => {
  currentPage.value = page;
  fetchAccruals();
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  currentPage.value = 1;
  fetchAccruals();
};

// ── Filters ─────────────────────────────────────────────────────────────────

const applyFilters = () => {
  currentPage.value = 1;
  fetchAccruals();
};

const clearFilters = () => {
  filterStatus.value = '';
  filterDdType.value = '';
  currentPage.value = 1;
  fetchAccruals();
};

const hasActiveFilters = computed(() => filterStatus.value || filterDdType.value);

// ── Badges ───────────────────────────────────────────────────────────────────

const getDdTypeBadgeClasses = (ddType: string) => {
  const base = 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border';
  return ddType === 'DEMURRAGE'
    ? `${base} bg-red-100 text-red-800 border-red-200`
    : `${base} bg-orange-100 text-orange-800 border-orange-200`;
};

const getStatusBadgeClasses = (status: string) => {
  const base = 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border';
  switch (status) {
    case 'RUNNING':
      return `${base} bg-yellow-100 text-yellow-800 border-yellow-200`;
    case 'STOPPED':
      return `${base} bg-gray-100 text-gray-600 border-gray-200`;
    case 'INVOICED':
      return `${base} bg-blue-100 text-blue-800 border-blue-200`;
    case 'WAIVED':
      return `${base} bg-green-100 text-green-800 border-green-200`;
    case 'CANCELLED':
      return `${base} bg-gray-100 text-gray-500 border-gray-200`;
    default:
      return `${base} bg-gray-100 text-gray-600 border-gray-200`;
  }
};

// ── Formatters ───────────────────────────────────────────────────────────────

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '—';
  try {
    return new Date(dateStr).toLocaleString();
  } catch {
    return dateStr;
  }
};

const formatCurrency = (amount?: number) => {
  if (amount == null) return '—';
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `$${amount.toFixed(2)}`;
  }
};

// ── Detail modal ─────────────────────────────────────────────────────────────

const openDetail = (accrual: DdAccrual) => {
  selectedAccrual.value = accrual;
  showDetailModal.value = true;
  showWaiverForm.value = false;
  waiverForm.value = { waiverType: 'FULL', waivedAmount: undefined, reason: '', approvedBy: '' };
};

const closeDetail = () => {
  showDetailModal.value = false;
  selectedAccrual.value = null;
  showWaiverForm.value = false;
};

const handleRecompute = async () => {
  if (!selectedAccrual.value?.id) return;
  isRecomputing.value = true;
  try {
    const updated = await recomputeAccrual(selectedAccrual.value.id);
    selectedAccrual.value = updated;
    await fetchAccruals();
  } catch {
    alert(t('ddDashboard.dialog.failedToRecomputeAccrual'));
  } finally {
    isRecomputing.value = false;
  }
};

const handleApplyWaiver = async () => {
  if (!selectedAccrual.value?.id) return;
  if (!waiverForm.value.reason?.trim()) {
    alert(t('ddDashboard.dialog.reasonIsRequired'));
    return;
  }
  isApplyingWaiver.value = true;
  try {
    const updated = await applyWaiver(selectedAccrual.value.id, waiverForm.value);
    selectedAccrual.value = updated;
    showWaiverForm.value = false;
    waiverForm.value = { waiverType: 'FULL', waivedAmount: undefined, reason: '', approvedBy: '' };
    await fetchAccruals();
    await fetchSummary();
  } catch {
    alert(t('ddDashboard.dialog.failedToApplyWaiver'));
  } finally {
    isApplyingWaiver.value = false;
  }
};

// ── Table headers ────────────────────────────────────────────────────────────

const tableHeaders = computed(() => [
  t('ddDashboard.column.containerNumber'),
  t('ddDashboard.column.type'),
  t('ddDashboard.column.status'),
  t('ddDashboard.column.daysElapsed'),
  t('ddDashboard.column.chargeableDays'),
  t('ddDashboard.column.totalAmount'),
  t('ddDashboard.column.clockStart'),
  '',
]);

const dailyLogHeaders = computed(() => [
  t('ddDashboard.dailyLog.dayNumber'),
  t('ddDashboard.dailyLog.date'),
  t('ddDashboard.dailyLog.freeDay'),
  t('ddDashboard.dailyLog.holiday'),
  t('ddDashboard.dailyLog.chargeAmount'),
  t('ddDashboard.dailyLog.rateBand'),
]);
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <!-- Page Header -->
      <div class="mb-8">
        <div class="md:flex md:items-center md:justify-between">
          <div class="min-w-0 flex-1">
            <h1 class="text-2xl font-bold leading-7 text-gray-900 sm:truncate sm:text-3xl">
              {{ t('ddDashboard.title') }}
            </h1>
            <div class="mt-2 flex items-center text-sm text-gray-500 space-x-4">
              <span class="flex items-center">
                <TrendingUp class="h-4 w-4 mr-1 text-blue-500" />
                {{ t('ddDashboard.accrualCount', totalItems, { count: totalItems }) }}
              </span>
            </div>
          </div>
          <div class="mt-4 flex md:ml-4 md:mt-0 space-x-3">
            <button
              @click="() => { fetchSummary(); fetchAccruals(); }"
              class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
              :disabled="isLoading"
            >
              <RefreshCw class="h-4 w-4 mr-2" :class="{ 'animate-spin': isLoading }" />
              {{ t('billOfLadingForm.button.refresh') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Stats Row -->
      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4 mb-8">
        <!-- Running Demurrage -->
        <div class="bg-white rounded-lg border border-gray-200 shadow-sm p-4">
          <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.stat.runningDemurrage') }}</p>
          <div class="mt-2 flex items-end justify-between">
            <p class="text-2xl font-bold text-gray-900">
              <span v-if="isSummaryLoading" class="h-6 w-8 bg-gray-200 rounded animate-pulse inline-block"></span>
              <span v-else>{{ summary?.runningDemurrage ?? 0 }}</span>
            </p>
            <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800 border border-blue-200">D</span>
          </div>
        </div>

        <!-- Running Detention -->
        <div class="bg-white rounded-lg border border-gray-200 shadow-sm p-4">
          <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.stat.runningDetention') }}</p>
          <div class="mt-2 flex items-end justify-between">
            <p class="text-2xl font-bold text-gray-900">
              <span v-if="isSummaryLoading" class="h-6 w-8 bg-gray-200 rounded animate-pulse inline-block"></span>
              <span v-else>{{ summary?.runningDetention ?? 0 }}</span>
            </p>
            <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-orange-100 text-orange-800 border border-orange-200">D</span>
          </div>
        </div>

        <!-- Total Exposure -->
        <div class="bg-white rounded-lg border border-gray-200 shadow-sm p-4 sm:col-span-1">
          <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.stat.totalExposure') }}</p>
          <div class="mt-2">
            <p class="text-lg font-bold text-red-600">
              <span v-if="isSummaryLoading" class="h-6 w-16 bg-gray-200 rounded animate-pulse inline-block"></span>
              <span v-else>{{ formatCurrency(summary?.totalExposure) }}</span>
            </p>
          </div>
        </div>

        <!-- Overdue -->
        <div class="bg-white rounded-lg border border-gray-200 shadow-sm p-4">
          <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.stat.overdue') }}</p>
          <div class="mt-2 flex items-end justify-between">
            <p class="text-2xl font-bold text-gray-900">
              <span v-if="isSummaryLoading" class="h-6 w-8 bg-gray-200 rounded animate-pulse inline-block"></span>
              <span v-else>{{ summary?.overdueCount ?? 0 }}</span>
            </p>
            <AlertTriangle class="h-5 w-5 text-orange-400" />
          </div>
        </div>

        <!-- Waived -->
        <div class="bg-white rounded-lg border border-gray-200 shadow-sm p-4">
          <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.stat.waived') }}</p>
          <div class="mt-2 flex items-end justify-between">
            <p class="text-2xl font-bold text-gray-900">
              <span v-if="isSummaryLoading" class="h-6 w-8 bg-gray-200 rounded animate-pulse inline-block"></span>
              <span v-else>{{ summary?.waivedCount ?? 0 }}</span>
            </p>
            <CheckCircle class="h-5 w-5 text-green-400" />
          </div>
        </div>

        <!-- Invoiced -->
        <div class="bg-white rounded-lg border border-gray-200 shadow-sm p-4">
          <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.stat.invoiced') }}</p>
          <div class="mt-2 flex items-end justify-between">
            <p class="text-2xl font-bold text-gray-900">
              <span v-if="isSummaryLoading" class="h-6 w-8 bg-gray-200 rounded animate-pulse inline-block"></span>
              <span v-else>{{ summary?.invoicedCount ?? 0 }}</span>
            </p>
            <XCircle class="h-5 w-5 text-gray-400" />
          </div>
        </div>
      </div>

      <!-- Filter Bar -->
      <div class="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
        <div class="p-6">
          <div class="flex flex-col sm:flex-row gap-4">
            <!-- Status filter -->
            <div class="sm:w-48">
              <label class="block text-xs font-medium text-gray-700 mb-1">{{ t('ddDashboard.filter.status') }}</label>
              <select
                v-model="filterStatus"
                class="block w-full border border-gray-300 rounded-lg py-2.5 px-3 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              >
                <option value="">{{ t('payments.filter.allStatuses') }}</option>
                <option value="RUNNING">{{ t('ddDashboard.status.running') }}</option>
                <option value="STOPPED">{{ t('ddDashboard.status.stopped') }}</option>
                <option value="INVOICED">{{ t('ddDashboard.status.invoiced') }}</option>
                <option value="WAIVED">{{ t('ddDashboard.status.waived') }}</option>
                <option value="CANCELLED">{{ t('ddDashboard.status.cancelled') }}</option>
              </select>
            </div>

            <!-- DD Type filter -->
            <div class="sm:w-48">
              <label class="block text-xs font-medium text-gray-700 mb-1">{{ t('ddDashboard.filter.type') }}</label>
              <select
                v-model="filterDdType"
                class="block w-full border border-gray-300 rounded-lg py-2.5 px-3 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              >
                <option value="">{{ t('ddDashboard.filter.allTypes') }}</option>
                <option value="DEMURRAGE">{{ t('ddDashboard.ddType.demurrage') }}</option>
                <option value="DETENTION">{{ t('ddDashboard.ddType.detention') }}</option>
              </select>
            </div>

            <!-- Filter actions -->
            <div class="flex items-end gap-2">
              <button
                @click="applyFilters"
                class="inline-flex items-center px-4 py-2.5 border border-transparent rounded-lg text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
              >
                <Filter class="h-4 w-4 mr-1.5" />
                {{ t('payments.button.apply') }}
              </button>
              <button
                v-if="hasActiveFilters"
                @click="clearFilters"
                class="inline-flex items-center px-4 py-2.5 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
              >
                <X class="h-4 w-4 mr-1.5" />
                {{ t('payments.button.clear') }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Error Banner -->
      <div v-if="errorMessage" class="mb-6 bg-red-50 border border-red-200 rounded-lg p-4 flex items-center space-x-3">
        <X class="h-5 w-5 text-red-500 flex-shrink-0" />
        <span class="text-sm text-red-700">{{ errorMessage }}</span>
        <button @click="errorMessage = ''" class="ml-auto text-red-500 hover:text-red-700">
          <X class="h-4 w-4" />
        </button>
      </div>

      <!-- Table Card -->
      <div class="bg-white shadow-sm rounded-lg border border-gray-200 overflow-hidden">

        <!-- Desktop table -->
        <div class="hidden sm:block overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50">
              <tr>
                <th
                  v-for="(header, idx) in tableHeaders"
                  :key="idx"
                  class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
                >
                  {{ header }}
                </th>
              </tr>
            </thead>
            <tbody class="bg-white divide-y divide-gray-200">
              <!-- Loading skeleton -->
              <tr v-if="isLoading" v-for="i in 5" :key="i">
                <td v-for="j in 8" :key="j" class="px-4 py-3">
                  <div class="h-4 bg-gray-200 rounded animate-pulse w-3/4"></div>
                </td>
              </tr>

              <!-- Data rows -->
              <tr
                v-else
                v-for="accrual in accruals"
                :key="accrual.id"
                class="hover:bg-gray-50 transition-colors duration-150 cursor-pointer"
                @click="openDetail(accrual)"
              >
                <!-- Container # -->
                <td class="px-4 py-3">
                  <span class="text-sm font-mono font-medium text-gray-900">
                    {{ accrual.containerNumber || '—' }}
                  </span>
                </td>

                <!-- Type -->
                <td class="px-4 py-3">
                  <span :class="getDdTypeBadgeClasses(accrual.ddType)">{{ accrual.ddType }}</span>
                </td>

                <!-- Status -->
                <td class="px-4 py-3">
                  <span :class="getStatusBadgeClasses(accrual.status)">{{ accrual.status }}</span>
                </td>

                <!-- Days Elapsed -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-700">{{ accrual.totalDaysElapsed ?? '—' }}</span>
                </td>

                <!-- Chargeable Days -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-700">{{ accrual.chargeableDays ?? '—' }}</span>
                </td>

                <!-- Total Amount -->
                <td class="px-4 py-3">
                  <span class="text-sm font-medium text-gray-900">
                    {{ formatCurrency(accrual.totalAccruedAmount) }}
                  </span>
                </td>

                <!-- Clock Start -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-500 whitespace-nowrap">{{ formatDate(accrual.clockStart) }}</span>
                </td>

                <!-- Actions -->
                <td class="px-4 py-3" @click.stop>
                  <button
                    @click="openDetail(accrual)"
                    class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                    :title="t('payments.action.viewDetails')"
                    :aria-label="t('ddDashboard.label.viewAccrualDetails')"
                  >
                    <Eye class="h-4 w-4" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Mobile card list -->
        <div class="sm:hidden p-4">
          <div v-if="isLoading" class="space-y-3">
            <div v-for="i in 4" :key="i" class="bg-white rounded-xl border border-slate-200 p-4 animate-pulse">
              <div class="h-4 bg-gray-200 rounded w-1/2 mb-2"></div>
              <div class="h-3 bg-gray-200 rounded w-3/4"></div>
            </div>
          </div>
          <div v-else-if="!accruals.length" class="bg-white rounded-xl border border-slate-200 p-8 text-center">
            <p class="text-sm text-slate-500">{{ t('ddDashboard.empty.noAccrualsFound') }}</p>
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="accrual in accruals"
              :key="accrual.id"
              class="bg-white rounded-xl border border-slate-200 p-4 shadow-sm cursor-pointer hover:border-blue-300 transition-colors"
              @click="openDetail(accrual)"
            >
              <div class="flex items-start justify-between gap-2 mb-2">
                <div>
                  <p class="font-semibold text-slate-900 text-sm font-mono">
                    {{ accrual.containerNumber || '—' }}
                  </p>
                  <p class="text-xs text-slate-500 mt-0.5">
                    {{ t('ddDashboard.chargeableDaysCount', { count: accrual.chargeableDays ?? 0 }) }}
                  </p>
                </div>
                <span :class="getStatusBadgeClasses(accrual.status)">{{ accrual.status }}</span>
              </div>
              <div class="flex items-center justify-between text-xs text-slate-500 mt-1">
                <span :class="getDdTypeBadgeClasses(accrual.ddType)">{{ accrual.ddType }}</span>
                <span class="font-medium text-gray-800">{{ formatCurrency(accrual.totalAccruedAmount) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state (desktop) -->
        <div v-if="!isLoading && accruals.length === 0" class="hidden sm:block text-center py-12">
          <TrendingUp class="mx-auto h-12 w-12 text-gray-400" />
          <h3 class="mt-2 text-sm font-medium text-gray-900">{{ t('ddDashboard.empty.noAccruals') }}</h3>
          <p class="mt-1 text-sm text-gray-500">
            {{ hasActiveFilters ? t('ddDashboard.empty.noAccrualsMatchFilters') : t('ddDashboard.empty.noDdAccruals') }}
          </p>
          <div class="mt-6">
            <button
              v-if="hasActiveFilters"
              @click="clearFilters"
              class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
            >
              {{ t('payments.button.clearFilters') }}
            </button>
          </div>
        </div>

        <!-- Pagination -->
        <Pagination
          v-if="totalItems > 0"
          :pagination="paginationMeta"
          @page-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- ── Detail Modal ──────────────────────────────────────────────────── -->
    <Teleport to="body">
      <div
        v-if="showDetailModal && selectedAccrual"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4"
        @click.self="closeDetail"
      >
        <div class="bg-white rounded-xl shadow-xl w-full max-w-3xl max-h-[90vh] flex flex-col">

          <!-- Modal Header -->
          <div class="flex items-center justify-between p-6 border-b border-gray-200">
            <div class="flex items-center space-x-3">
              <TrendingUp class="h-6 w-6 text-blue-600" />
              <div>
                <div class="flex items-center space-x-2">
                  <h3 class="text-lg font-semibold text-gray-900">
                    {{ selectedAccrual.containerNumber || selectedAccrual.itemId }}
                  </h3>
                  <span :class="getDdTypeBadgeClasses(selectedAccrual.ddType)">
                    {{ selectedAccrual.ddType }}
                  </span>
                  <span :class="getStatusBadgeClasses(selectedAccrual.status)">
                    {{ selectedAccrual.status }}
                  </span>
                </div>
                <p class="text-xs text-gray-500 font-mono mt-0.5">{{ selectedAccrual.id }}</p>
              </div>
            </div>
            <button
              @click="closeDetail"
              class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
            >
              <X class="h-5 w-5" />
            </button>
          </div>

          <!-- Modal Body -->
          <div class="overflow-y-auto flex-1 p-6 space-y-6">

            <!-- Info grid -->
            <div class="grid grid-cols-2 sm:grid-cols-3 gap-4">
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.detail.carrierId') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ selectedAccrual.carrierId || '—' }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.detail.freeDaysGranted') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ selectedAccrual.freeDaysGranted ?? '—' }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.detail.clockStart') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ formatDate(selectedAccrual.clockStart) }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.detail.clockStop') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ formatDate(selectedAccrual.clockStop) }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.detail.totalDaysElapsed') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ selectedAccrual.totalDaysElapsed ?? '—' }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.detail.chargeableDays') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ selectedAccrual.chargeableDays ?? '—' }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.detail.totalAccrued') }}</p>
                <p class="mt-1 text-sm font-semibold text-gray-900">
                  {{ formatCurrency(selectedAccrual.totalAccruedAmount) }}
                </p>
              </div>
              <div v-if="selectedAccrual.invoiceId">
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.column.alloc.invoiceId') }}</p>
                <p class="mt-1 text-sm font-mono text-gray-900 truncate" :title="selectedAccrual.invoiceId">
                  {{ selectedAccrual.invoiceId }}
                </p>
              </div>
            </div>

            <!-- Notes -->
            <div v-if="selectedAccrual.notes">
              <p class="text-xs font-medium text-gray-500 uppercase tracking-wide mb-1">{{ t('itemForm.field.notes') }}</p>
              <div class="bg-gray-50 border border-gray-200 rounded-lg p-3 text-sm text-gray-700">
                {{ selectedAccrual.notes }}
              </div>
            </div>

            <!-- Daily Log -->
            <div v-if="selectedAccrual.dailyLog && selectedAccrual.dailyLog.length">
              <p class="text-xs font-medium text-gray-500 uppercase tracking-wide mb-2">{{ t('ddDashboard.detail.dailyLog') }}</p>
              <div class="overflow-x-auto">
                <table class="min-w-full divide-y divide-gray-200 text-sm border border-gray-200 rounded-lg overflow-hidden">
                  <thead class="bg-gray-50">
                    <tr>
                      <th
                        v-for="(h, hIdx) in dailyLogHeaders"
                        :key="hIdx"
                        class="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
                      >
                        {{ h }}
                      </th>
                    </tr>
                  </thead>
                  <tbody class="bg-white divide-y divide-gray-100">
                    <tr v-for="entry in selectedAccrual.dailyLog" :key="entry.dayNumber">
                      <td class="px-3 py-2 text-gray-900 font-medium">{{ entry.dayNumber }}</td>
                      <td class="px-3 py-2 text-gray-700 whitespace-nowrap">{{ formatDate(entry.date) }}</td>
                      <td class="px-3 py-2">
                        <span v-if="entry.isFreeDay" class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">{{ t('common.yes') }}</span>
                        <span v-else class="text-gray-400 text-xs">{{ t('common.no') }}</span>
                      </td>
                      <td class="px-3 py-2">
                        <span v-if="entry.isHoliday" class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">{{ t('common.yes') }}</span>
                        <span v-else class="text-gray-400 text-xs">{{ t('common.no') }}</span>
                      </td>
                      <td class="px-3 py-2 text-gray-900">{{ formatCurrency(entry.chargeAmount) }}</td>
                      <td class="px-3 py-2 text-gray-700">{{ entry.rateBandLabel || '—' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Waivers -->
            <div>
              <div class="flex items-center justify-between mb-2">
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ddDashboard.waivers.title') }}</p>
                <button
                  v-if="!showWaiverForm"
                  @click="showWaiverForm = true"
                  class="inline-flex items-center px-3 py-1.5 border border-gray-300 rounded-lg text-xs font-medium text-gray-700 bg-white hover:bg-gray-50"
                >
                  <Plus class="h-3.5 w-3.5 mr-1" />
                  {{ t('ddDashboard.waivers.applyWaiver') }}
                </button>
              </div>

              <!-- Existing waivers list -->
              <div
                v-if="selectedAccrual.waivers && selectedAccrual.waivers.length"
                class="space-y-2 mb-4"
              >
                <div
                  v-for="w in selectedAccrual.waivers"
                  :key="w.waiverId"
                  class="bg-green-50 border border-green-200 rounded-lg p-3"
                >
                  <div class="flex items-start justify-between gap-2">
                    <div>
                      <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800 border border-green-200">
                        {{ w.waiverType }}
                      </span>
                      <p class="mt-1 text-sm text-gray-700">{{ w.reason || '—' }}</p>
                    </div>
                    <div class="text-right text-xs text-gray-500 shrink-0">
                      <p v-if="w.waivedAmount != null">{{ formatCurrency(w.waivedAmount) }}</p>
                      <p v-if="w.extensionDays">{{ t('ddDashboard.waivers.extensionDays', { days: w.extensionDays }) }}</p>
                      <p v-if="w.approvedBy">{{ t('ddDashboard.waivers.approvedByLine', { name: w.approvedBy }) }}</p>
                      <p v-if="w.approvedAt">{{ formatDate(w.approvedAt) }}</p>
                    </div>
                  </div>
                </div>
              </div>
              <div
                v-else-if="!showWaiverForm"
                class="text-sm text-gray-400 italic"
              >
                {{ t('ddDashboard.waivers.noneApplied') }}
              </div>

              <!-- Waiver sub-form -->
              <div v-if="showWaiverForm" class="bg-gray-50 border border-gray-200 rounded-lg p-4 space-y-3">
                <p class="text-sm font-medium text-gray-700">{{ t('ddDashboard.waivers.newWaiver') }}</p>

                <!-- Waiver type -->
                <div>
                  <label class="block text-xs font-medium text-gray-700 mb-1">
                    {{ t('ddDashboard.waivers.field.waiverType') }} <span class="text-red-500">*</span>
                  </label>
                  <select
                    v-model="waiverForm.waiverType"
                    class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                  >
                    <option value="FULL">{{ t('ddDashboard.waivers.type.full') }}</option>
                    <option value="PARTIAL">{{ t('ddDashboard.waivers.type.partial') }}</option>
                    <option value="FREE_DAYS_EXTENSION">{{ t('ddDashboard.waivers.type.freeDaysExtension') }}</option>
                    <option value="RATE_REDUCTION">{{ t('ddDashboard.waivers.type.rateReduction') }}</option>
                  </select>
                </div>

                <!-- Waived amount -->
                <div>
                  <label class="block text-xs font-medium text-gray-700 mb-1">{{ t('ddDashboard.waivers.field.waivedAmount') }}</label>
                  <input
                    v-model.number="waiverForm.waivedAmount"
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="0.00"
                    class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                  />
                </div>

                <!-- Reason -->
                <div>
                  <label class="block text-xs font-medium text-gray-700 mb-1">
                    {{ t('ddDashboard.waivers.field.reason') }} <span class="text-red-500">*</span>
                  </label>
                  <textarea
                    v-model="waiverForm.reason"
                    rows="2"
                    :placeholder="t('ddDashboard.waivers.placeholder.reason')"
                    class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500 resize-none"
                  ></textarea>
                </div>

                <!-- Approved by -->
                <div>
                  <label class="block text-xs font-medium text-gray-700 mb-1">{{ t('ddDashboard.waivers.field.approvedBy') }}</label>
                  <input
                    v-model="waiverForm.approvedBy"
                    type="text"
                    :placeholder="t('ddDashboard.waivers.placeholder.approvedBy')"
                    class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                  />
                </div>

                <div class="flex justify-end space-x-2 pt-1">
                  <button
                    type="button"
                    @click="showWaiverForm = false"
                    :disabled="isApplyingWaiver"
                    class="px-3 py-1.5 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                  >
                    {{ t('common.cancel') }}
                  </button>
                  <button
                    type="button"
                    @click="handleApplyWaiver"
                    :disabled="isApplyingWaiver"
                    class="inline-flex items-center px-3 py-1.5 border border-transparent rounded-lg text-sm font-medium text-white bg-green-600 hover:bg-green-700 disabled:opacity-50"
                  >
                    <RefreshCw v-if="isApplyingWaiver" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
                    {{ isApplyingWaiver ? t('ddDashboard.waivers.applying') : t('ddDashboard.waivers.applyWaiver') }}
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Modal Footer -->
          <div class="px-6 py-4 bg-gray-50 rounded-b-xl border-t border-gray-200 flex items-center justify-between">
            <button
              @click="handleRecompute"
              :disabled="isRecomputing"
              class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50"
            >
              <RotateCcw class="h-4 w-4 mr-2" :class="{ 'animate-spin': isRecomputing }" />
              {{ isRecomputing ? t('ddDashboard.button.recomputing') : t('ddDashboard.button.recompute') }}
            </button>
            <button
              @click="closeDetail"
              class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              {{ t('common.close') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
