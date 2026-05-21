<script setup lang="ts">
import { onMounted, watch, computed, ref, nextTick } from 'vue';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();
import {
  Filter,
  ChevronDown,
  ChevronUp,
  RotateCcw,
  X,
  FileSearch,
  CheckCircle,
  MoreVertical,
  Eye,
  FileText,
  Trash2,
  TrendingUp,
  TrendingDown,
  Search,
  Calendar,
  Users,
  Building,
  Hash,
  DollarSign
} from 'lucide-vue-next';
import { useInvoice } from '../composables/use.invoice';
import { useKeyboardShortcut } from '../composables/useKeyboardShortcut';
import InvoicePreview from './InvoicePreview.vue';
import InvoiceChargeAudit from './InvoiceChargeAudit.vue';
import invoiceService from '../services/invoiceService';
import KebabMenu from './KebabMenu.vue';
import KpiCard from './ui/KpiCard.vue';
import FilterChips from './ui/FilterChips.vue';
import ToastNotification from './ui/ToastNotification.vue';
import StatusBadge from './ui/StatusBadge.vue';
import ColumnPicker from './ui/ColumnPicker.vue';
import ConfirmDialog from './ui/ConfirmDialog.vue';
import DataTable from './ui/DataTable.vue';
import type { Column } from './ui/DataTable.types';
import PaymentStatusBadge from './ui/PaymentStatusBadge.vue';
import { derivePaymentStatus, getInvoiceOutstanding, type Invoice } from '../types/invoice';
import { useColumnPreferences, type ColumnDefinition } from '../composables/useColumnPreferences';
import PageHeader from './ui/PageHeader.vue';

const { state, displayedCount, fetchInvoices, clearFilters, hydrateFromUrl } =
  useInvoice();

// controls visibility of the filter panel
const filtersOpen = ref(false);

function toggleFilters() {
  filtersOpen.value = !filtersOpen.value;
}

function toggleStatus(status: string) {
  const idx = state.filters.status.indexOf(status);
  if (idx === -1) state.filters.status.push(status);
  else state.filters.status.splice(idx, 1);
}

function removeFilter(key: string, value?: string) {
  const f = state.filters;
  switch (key) {
    case 'status':
      f.status = f.status.filter((s) => s !== value);
      break;
    case 'customerName':
      f.customerName = '';
      break;
    case 'facility':
      f.facility = '';
      break;
    case 'draftNumber':
      f.draftNumber = '';
      break;
    case 'finalNumber':
      f.finalNumber = '';
      break;
    case 'createdDateFrom':
      f.createdDateFrom = null;
      break;
    case 'createdDateTo':
      f.createdDateTo = null;
      break;
  }
}

const activeFilterChips = computed(() => {
  const chips: { key: string; value?: string; label: string }[] = [];
  const f = state.filters;
  f.status.forEach((s) => chips.push({ key: 'status', value: s, label: s }));
  if (f.customerName)
    chips.push({ key: 'customerName', label: f.customerName });
  if (f.facility) chips.push({ key: 'facility', label: f.facility });
  if (f.draftNumber)
    chips.push({ key: 'draftNumber', label: f.draftNumber });
  if (f.finalNumber)
    chips.push({ key: 'finalNumber', label: f.finalNumber });
  if (f.createdDateFrom)
    chips.push({ key: 'createdDateFrom', label: t('invoices.filter.from', { date: f.createdDateFrom }) });
  if (f.createdDateTo)
    chips.push({ key: 'createdDateTo', label: t('invoices.filter.to', { date: f.createdDateTo }) });
  return chips;
});

const hasActiveFilters = computed(() => activeFilterChips.value.length > 0);

const draftPercent = computed(() =>
  state.totalCount ? Math.round((state.statusCounts.DRAFT / state.totalCount) * 100) : 0
);
const finalPercent = computed(() =>
  state.totalCount ? Math.round((state.statusCounts.FINAL / state.totalCount) * 100) : 0
);

const totalTrend = computed(() => state.trends?.totalAmount ?? 0);
const draftTrend = computed(() => state.trends?.status?.DRAFT ?? 0);
const finalTrend = computed(() => state.trends?.status?.FINAL ?? 0);
const displayedTrend = computed(() => state.trends?.displayed ?? 0);

function getRowAmount(inv: any): number {
  const raw = inv.totalAmount ?? inv.TotalAmount ?? inv.amount;
  const num = typeof raw === 'number' ? raw : parseFloat(raw);
  return Number.isFinite(num) ? num : 0;
}

function formatCurrency(amount: unknown) {
  const num = typeof amount === 'number' ? amount : parseFloat(String(amount));
  return (Number.isFinite(num) ? num : 0).toLocaleString('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    maximumFractionDigits: 0,
  });
}

function formatDate(value: string | number | Date) {
  if (!value) return '—';
  const date = value instanceof Date ? value : new Date(value);
  if (isNaN(date.getTime())) return '—';
  return date.toLocaleString(undefined, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

const sortBy = computed<string>(() => state.sort.split(':')[0]);
const sortDir = computed<'asc' | 'desc'>(() => (state.sort.split(':')[1] === 'desc' ? 'desc' : 'asc'));

function onSortChange(payload: { by: string; dir: 'asc' | 'desc' }): void {
  // Backend convention: amount sort key is PascalCase 'TotalAmount' even though the column key is 'totalAmount'.
  const by = payload.by === 'totalAmount' ? 'TotalAmount' : payload.by;
  state.sort = `${by}:${payload.dir}`;
}

function changePage(delta: number) {
  const newPage = state.page + delta;
  if (newPage < 1) return;
  state.page = newPage;
}

function updateUrl() {
  const params = new URLSearchParams();
  const f = state.filters;
  if (f.status.length) params.set('status', f.status.join(','));
  if (f.customerName) params.set('customerName', f.customerName);
  if (f.facility) params.set('facility', f.facility);
  if (f.draftNumber) params.set('draftNumber', f.draftNumber);
  if (f.finalNumber) params.set('finalNumber', f.finalNumber);
  if (f.createdDateFrom) params.set('createdDateFrom', f.createdDateFrom);
  if (f.createdDateTo) params.set('createdDateTo', f.createdDateTo);
  params.set('page', String(state.page));
  params.set('pageSize', String(state.pageSize));
  params.set('sort', state.sort);
  const query = params.toString();
  const newUrl = `${window.location.pathname}?${query}`;
  window.history.replaceState(null, '', newUrl);
}

function selectStatusFilter(status: 'DRAFT' | 'FINAL') {
  state.filters.status = [status];
  state.page = 1;
  updateUrl();
  fetchInvoices();
}

function showAll() {
  clearFilters();
  updateUrl();
  fetchInvoices();
}

let timer: number | undefined;
function scheduleFetch() {
  clearTimeout(timer);
  timer = window.setTimeout(() => {
    fetchInvoices();
    updateUrl();
  }, 300);
}

// Prevent initial hydration from triggering extra fetches
const isHydrating = ref(true);

const previewInvoice =
  ref<{ id: string; displayId: string; status: string; url: string } | null>(null);
const finalizeTarget = ref<{ id: string; displayId: string } | null>(null);
const deleteTarget = ref<{ id: string; displayId: string } | null>(null);

// Used by useColumnPreferences (storage key shape unchanged — except the now-redundant 'actions' row is removed because DataTable owns the actions column).
const invoiceColumns: ColumnDefinition[] = [
  { key: 'draftNumber',  label: t('invoices.column.draftNumber') },
  { key: 'finalNumber',  label: t('invoices.column.finalNumber') },
  { key: 'status',       label: t('invoices.column.status') },
  { key: 'customerName', label: t('invoices.column.customerName') },
  { key: 'facility',     label: t('invoices.column.facility') },
  { key: 'createdDate',  label: t('invoices.column.createdDate') },
  { key: 'totalAmount',  label: t('invoices.column.totalAmount') },
  { key: 'paymentStatus', label: t('invoices.column.paymentStatus') },
  { key: 'outstanding',   label: t('invoices.column.outstanding') },
  { key: 'dueDate',       label: t('invoices.column.dueDate') },
];

// Full column metadata for <DataTable>
const dataTableColumns: Column<any>[] = [
  { key: 'draftNumber',  label: t('invoices.column.draftNumber'),   sortable: true, mobile: 'subtitle' },
  { key: 'finalNumber',  label: t('invoices.column.finalNumber'),   sortable: true, mobile: 'title' },
  { key: 'status',       label: t('invoices.column.status'),        sortable: true, mobile: 'meta' },
  { key: 'customerName', label: t('invoices.column.customerName'),  sortable: true, mobile: 'meta' },
  { key: 'facility',     label: t('invoices.column.facility'),      sortable: true, mobile: 'hidden' },
  { key: 'createdDate',  label: t('invoices.column.createdDateShort'), sortable: true, format: (v) => formatDate(v as string), mobile: 'meta' },
  { key: 'totalAmount',  label: t('invoices.column.totalAmount'),   sortable: true, align: 'right', format: (_, row: any) => formatCurrency(getRowAmount(row)), mobile: 'meta' },
  { key: 'paymentStatus', label: t('invoices.column.paymentStatus'), sortable: false, mobile: 'meta' },
  { key: 'outstanding',   label: t('invoices.column.outstanding'),  sortable: false, align: 'right', mobile: 'meta' },
  { key: 'dueDate',       label: t('invoices.column.dueDate'),      sortable: true,  format: (v) => v ? formatDate(v as string) : '—', mobile: 'meta' },
];
const invoiceCols = useColumnPreferences('invoices', invoiceColumns);

useKeyboardShortcut('r', () => {
  if (deleteTarget.value || finalizeTarget.value) return;
  fetchInvoices();
});
const isDeleting = ref(false);
const toast = ref<{ message: string; type: 'error' | 'success' } | null>(null);

const showChargeAudit = ref(false);
const chargeAuditInvoiceId = ref<string | null>(null);
const chargeAuditInvoiceNumber = ref<string>('');

function openChargeAudit(invoice: any) {
  chargeAuditInvoiceId.value = invoice.id ?? invoice._id;
  chargeAuditInvoiceNumber.value = invoice.finalNumber ?? invoice.draftNumber ?? '';
  showChargeAudit.value = true;
}
function closeChargeAudit() {
  showChargeAudit.value = false;
  chargeAuditInvoiceId.value = null;
}

function showToast(message: string, type: 'error' | 'success' = 'error') {
  toast.value = { message, type };
  setTimeout(() => (toast.value = null), 3000);
}

const displayedTotalAmount = computed(() =>
  state.items.reduce((sum, inv) => sum + getRowAmount(inv), 0)
);

function openPreview(id: string) {
  if (!id) {
    console.error('openPreview: missing invoiceId on row');
    showToast(t('invoices.toast.idMissing'));
    return;
  }
  const inv =
    state.items.find((i: any) => i._id === id || i.id === id || i.invoiceId === id) || {};
  previewInvoice.value = {
    id: String(id),
    displayId: String(inv.finalNumber || inv.draftNumber || id),
    status: inv.status,
    url: '',
  };
  try {
    const url = invoiceService.getInvoicePreviewUrl(id);
    previewInvoice.value.url = url;
  } catch (e: any) {
    console.error(e);
    previewInvoice.value = null;
    showToast(t('invoices.toast.previewFailed'));
  }
}

function openFinalize(inv: any) {
  const id = inv?._id || inv?.id || inv?.invoiceId;
  if (!id) {
    console.error('openFinalize: missing invoiceId on row');
    showToast(t('invoices.toast.idMissing'));
    return;
  }
  finalizeTarget.value = {
    id: String(id),
    displayId: String(inv.finalNumber || inv.draftNumber || id),
  };
}

function openDelete(inv: any) {
  const id = inv?._id || inv?.id || inv?.invoiceId;
  if (!id) {
    console.error('openDelete: missing invoiceId on row');
    showToast(t('invoices.toast.idMissing'));
    return;
  }
  deleteTarget.value = {
    id: String(id),
    displayId: String(inv.finalNumber || inv.draftNumber || inv.displayId || id),
  };
}

async function finalizeInvoice() {
  if (!finalizeTarget.value) return;
  try {
    await invoiceService.finalize(finalizeTarget.value.id);
    await fetchInvoices();
    showToast(t('invoices.toast.finalizeSuccess'), 'success');
  } catch (e: any) {
    console.error(e);
    showToast(e?.response?.data?.message || t('invoices.toast.finalizeFailed'));
  } finally {
    finalizeTarget.value = null;
  }
}

async function deleteInvoice() {
  if (!deleteTarget.value) return;
  isDeleting.value = true;
  try {
    await invoiceService.delete(deleteTarget.value.id);
    await fetchInvoices();
    if (previewInvoice.value && previewInvoice.value.id === deleteTarget.value.id) {
      previewInvoice.value = null;
    }
    showToast(t('invoices.toast.deleteSuccess'), 'success');
  } catch (e: any) {
    const status = e?.response?.status;
    if (status === 404) {
      showToast(t('invoices.toast.notFound'));
    } else if (status === 409) {
      showToast(t('invoices.toast.onlyDraftDeletable'));
    } else {
      showToast(e?.response?.data?.message || t('invoices.toast.deleteFailed'));
    }
  } finally {
    isDeleting.value = false;
    deleteTarget.value = null;
  }
}

function closePreview() {
  previewInvoice.value = null;
}

async function finalizePreview() {
  if (!previewInvoice.value) return;
  try {
    await invoiceService.finalize(previewInvoice.value.id);
    await fetchInvoices();
    const inv =
      state.items.find(
        (i: any) => i._id === previewInvoice.value!.id || i.id === previewInvoice.value!.id || i.invoiceId === previewInvoice.value!.id
      ) || {};
    previewInvoice.value.status = inv.status || 'FINAL';
    previewInvoice.value.displayId = String(inv.finalNumber || inv.draftNumber || previewInvoice.value.id);
    try {
      previewInvoice.value.url = invoiceService.getInvoicePreviewUrl(previewInvoice.value.id);
    } catch (e) {
      console.error(e);
    }
  } catch (e) {
    console.error(e);
    showToast(t('invoices.toast.finalizeFailed'));
  }
}

function openDeleteFromPreview() {
  if (previewInvoice.value) {
    openDelete(previewInvoice.value);
  }
}

watch(
  () => state.filters,
  () => {
    if (!isHydrating.value) scheduleFetch();
  },
  { deep: true }
);
watch([() => state.page, () => state.pageSize, () => state.sort], () => {
  if (!isHydrating.value) scheduleFetch();
});

onMounted(async () => {
  hydrateFromUrl();
  await nextTick();
  isHydrating.value = false;
  fetchInvoices();
});
</script>

<template>
  <div class="min-h-screen bg-slate-50">
    <!-- Page Header -->
    <PageHeader
      :title="t('invoices.title')"
      :subtitle="t('invoices.subtitle')"
      :count="state.totalCount"
    >
      <template #kpi>
          <KpiCard
            :label="t('invoices.kpi.totalAmount')"
            :value="formatCurrency(state.totalAmount)"
            :sub-label="t('invoices.kpi.totalAmountSub', { count: state.totalCount })"
            :trend="totalTrend || undefined"
            color="slate"
            :clickable="true"
            @click="showAll"
          >
            <template #icon><DollarSign class="w-5 h-5" /></template>
          </KpiCard>

          <KpiCard
            :label="t('invoices.kpi.draftInvoices')"
            :value="formatCurrency(state.statusAmounts.DRAFT)"
            :sub-label="t('invoices.kpi.draftInvoicesSub', { count: state.statusCounts.DRAFT, pct: draftPercent })"
            :trend="draftTrend || undefined"
            color="amber"
            :clickable="true"
            @click="selectStatusFilter('DRAFT')"
          >
            <template #icon><FileText class="w-5 h-5" /></template>
          </KpiCard>

          <KpiCard
            :label="t('invoices.kpi.finalInvoices')"
            :value="formatCurrency(state.statusAmounts.FINAL)"
            :sub-label="t('invoices.kpi.finalInvoicesSub', { count: state.statusCounts.FINAL, pct: finalPercent })"
            :trend="finalTrend || undefined"
            color="emerald"
            :clickable="true"
            @click="selectStatusFilter('FINAL')"
          >
            <template #icon><CheckCircle class="w-5 h-5" /></template>
          </KpiCard>

          <KpiCard
            :label="t('invoices.kpi.displayed')"
            :value="displayedCount"
            :sub-label="t('invoices.kpi.displayedSub')"
            :trend="displayedTrend || undefined"
            color="blue"
            :clickable="true"
            @click="showAll"
          >
            <template #icon><Eye class="w-5 h-5" /></template>
          </KpiCard>
      </template>
    </PageHeader>

    <!-- Status bar -->
    <div v-if="state.updatedAt || state.loading" class="flex items-center justify-between px-6 py-2 bg-white border-b border-slate-100">
      <span v-if="state.updatedAt" class="text-xs text-slate-400">{{ t('invoices.updatedAt', { time: state.updatedAt.toLocaleTimeString() }) }}</span>
      <div v-if="state.loading" class="flex items-center text-xs text-blue-600 ml-auto">
        <div class="animate-spin rounded-full h-3 w-3 border-b-2 border-blue-600 mr-1.5"></div>
        {{ t('common.loading') }}
      </div>
    </div>

    <!-- Main Content -->
    <div class="px-6 py-6 space-y-6">
      <!-- Error Message -->
      <div
        v-if="state.error"
        class="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700"
      >
        {{ state.error }}
      </div>

      <!-- Filters -->
      <div class="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <button
          class="w-full flex items-center justify-between p-4 hover:bg-slate-50 transition-colors"
          @click="toggleFilters"
          :aria-expanded="filtersOpen"
          aria-controls="invoice-filters"
        >
          <div class="flex items-center gap-3">
            <div class="p-2 bg-slate-100 rounded-lg">
              <Filter :class="['w-4 h-4', hasActiveFilters ? 'text-blue-600' : 'text-slate-600']" />
            </div>
            <div>
              <h3 class="font-medium text-slate-900">{{ t('invoices.filter.title') }}</h3>
              <p class="text-sm text-slate-600">
                {{ hasActiveFilters ? t('invoices.filter.activeCount', { count: activeFilterChips.length }) : t('invoices.filter.none') }}
              </p>
            </div>
          </div>
          <ChevronDown v-if="!filtersOpen" class="w-5 h-5 text-slate-400" />
          <ChevronUp v-else class="w-5 h-5 text-slate-400" />
        </button>

        <Transition name="slide">
          <div v-show="filtersOpen" id="invoice-filters" class="border-t border-slate-200">
            <!-- Filter Chips -->
            <FilterChips
              :chips="activeFilterChips"
              @remove="removeFilter"
              @clear-all="clearFilters"
            />

            <!-- Filter Controls -->
            <div class="p-4">
              <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                <div>
                  <label class="flex items-center gap-2 text-sm font-medium text-slate-700 mb-3">
                    <CheckCircle class="w-4 h-4" />
                    {{ t('invoices.filter.status') }}
                  </label>
                  <div class="flex gap-2">
                    <button
                      v-for="s in ['DRAFT', 'FINAL']"
                      :key="s"
                      @click="toggleStatus(s)"
                      :aria-pressed="state.filters.status.includes(s)"
                      class="px-4 py-2 rounded-lg border text-sm font-medium transition-colors"
                      :class="state.filters.status.includes(s)
                        ? 'bg-blue-600 text-white border-blue-600 hover:bg-blue-700'
                        : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'"
                    >
                      {{ s }}
                    </button>
                  </div>
                </div>

                <div>
                  <label class="flex items-center gap-2 text-sm font-medium text-slate-700 mb-3">
                    <Users class="w-4 h-4" />
                    {{ t('invoices.filter.customer') }}
                  </label>
                  <div class="relative">
                    <Search class="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input
                      type="text"
                      v-model="state.filters.customerName"
                      class="w-full pl-10 pr-4 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-colors"
                      :placeholder="t('invoices.placeholder.searchCustomers')"
                    />
                  </div>
                </div>

                <div>
                  <label class="flex items-center gap-2 text-sm font-medium text-slate-700 mb-3">
                    <Building class="w-4 h-4" />
                    {{ t('invoices.filter.facility') }}
                  </label>
                  <div class="relative">
                    <Search class="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input
                      type="text"
                      v-model="state.filters.facility"
                      class="w-full pl-10 pr-4 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-colors"
                      :placeholder="t('invoices.placeholder.searchFacilities')"
                    />
                  </div>
                </div>

                <div>
                  <label class="flex items-center gap-2 text-sm font-medium text-slate-700 mb-3">
                    <Hash class="w-4 h-4" />
                    {{ t('invoices.filter.draftNumber') }}
                  </label>
                  <input
                    type="text"
                    v-model="state.filters.draftNumber"
                    class="w-full px-4 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-colors"
                    :placeholder="t('invoices.placeholder.draftNumber')"
                  />
                </div>

                <div>
                  <label class="flex items-center gap-2 text-sm font-medium text-slate-700 mb-3">
                    <Hash class="w-4 h-4" />
                    {{ t('invoices.filter.finalNumber') }}
                  </label>
                  <input
                    type="text"
                    v-model="state.filters.finalNumber"
                    class="w-full px-4 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-colors"
                    :placeholder="t('invoices.placeholder.finalNumber')"
                  />
                </div>

                <div>
                  <label class="flex items-center gap-2 text-sm font-medium text-slate-700 mb-3">
                    <Calendar class="w-4 h-4" />
                    {{ t('invoices.filter.dateRange') }}
                  </label>
                  <div class="grid grid-cols-2 gap-2">
                    <input
                      type="date"
                      v-model="state.filters.createdDateFrom"
                      class="px-3 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-colors text-sm"
                    />
                    <input
                      type="date"
                      v-model="state.filters.createdDateTo"
                      class="px-3 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-colors text-sm"
                    />
                  </div>
                </div>
              </div>

              <div class="mt-6 flex justify-end">
                <button
                  class="flex items-center gap-2 px-4 py-2 text-sm text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition-colors"
                  @click="clearFilters"
                  :aria-label="t('invoices.button.resetFilters')"
                >
                  <RotateCcw class="w-4 h-4" />
                  <span>{{ t('invoices.button.resetFilters') }}</span>
                </button>
              </div>
            </div>
          </div>
        </Transition>
      </div>

      <!-- Table toolbar -->
      <div class="flex justify-end">
        <ColumnPicker
          :toggleable="invoiceCols.toggleable.value"
          :hidden-count="invoiceCols.hiddenCount.value"
          :is-visible="invoiceCols.isVisible"
          :toggle="invoiceCols.toggle"
          :reset="invoiceCols.reset"
          @reset="invoiceCols.reset"
        />
      </div>

      <!-- Table -->
      <DataTable
        :rows="state.items"
        :columns="dataTableColumns.filter(c => invoiceCols.isVisible(c.key))"
        row-key="id"
        :sort-by="sortBy"
        :sort-dir="sortDir"
        :loading="state.loading"
        :empty="{
          title: t('invoices.empty.title'),
          description: hasActiveFilters ? t('invoices.empty.descriptionFiltered') : t('invoices.empty.description'),
          actionLabel: hasActiveFilters ? t('invoices.empty.clearFilters') : undefined,
        }"
        @update:sort="onSortChange"
        @empty-action="clearFilters"
      >
        <template #cell-status="{ row }">
          <StatusBadge :status="(row as any).status" />
        </template>

        <template #cell-customerName="{ row }">
          <div class="max-w-xs">
            <a v-if="(row as any).customerId"
               :href="`/customers/${(row as any).customerId}`"
               class="text-sm font-medium text-blue-600 hover:text-blue-700 hover:underline truncate block">
              {{ (row as any).customerName }}
            </a>
            <span v-else class="text-sm text-slate-900 truncate block">{{ (row as any).customerName }}</span>
          </div>
        </template>

        <template #cell-facility="{ row }">
          <div class="max-w-xs">
            <a v-if="(row as any).facilityId"
               :href="`/facilities/${(row as any).facilityId}`"
               class="text-sm font-medium text-blue-600 hover:text-blue-700 hover:underline truncate block">
              {{ (row as any).facility }}
            </a>
            <span v-else class="text-sm text-slate-900 truncate block">{{ (row as any).facility || '—' }}</span>
          </div>
        </template>

        <template #cell-draftNumber="{ row }">
          <span class="text-sm font-medium text-slate-900" :title="(row as any).draftNumber || '—'">
            {{ (row as any).draftNumber || '—' }}
          </span>
        </template>

        <template #cell-finalNumber="{ row }">
          <span class="text-sm font-medium text-slate-900" :title="(row as any).finalNumber || '—'">
            {{ (row as any).finalNumber || '—' }}
          </span>
        </template>

        <template #cell-paymentStatus="{ row }">
          <PaymentStatusBadge :status="derivePaymentStatus(row as Invoice)" />
        </template>

        <template #cell-outstanding="{ row }">
          <span :class="getInvoiceOutstanding(row as Invoice) > 0 ? 'text-amber-700 font-medium' : 'text-slate-400'">
            {{ formatCurrency(getInvoiceOutstanding(row as Invoice)) }}
          </span>
        </template>

        <template #cell-dueDate="{ row }">
          <span :class="derivePaymentStatus(row as Invoice) === 'OVERDUE' ? 'text-red-600 font-medium' : 'text-slate-700'">
            {{ (row as Invoice).dueDate ? formatDate((row as Invoice).dueDate!) : '—' }}
          </span>
        </template>

        <template #row-actions="{ row }">
          <KebabMenu>
            <template #trigger="{ toggle, refEl, isOpen }">
              <button
                class="text-slate-400 hover:text-slate-600 p-2 rounded-lg hover:bg-slate-100 transition-colors"
                @click.stop="toggle()"
                :ref="refEl"
                :aria-label="t('invoices.aria.invoiceActions')"
                :aria-expanded="isOpen ? 'true' : 'false'"
              >
                <MoreVertical class="h-5 w-5" />
              </button>
            </template>
            <template #content="{ close }">
              <div class="py-1">
                <button
                  class="flex items-center w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-slate-100 gap-3"
                  role="menuitem"
                  :aria-label="t('invoices.aria.previewInvoice')"
                  @click="openPreview((row as any)._id || (row as any).id); close()"
                >
                  <Eye class="w-4 h-4" />
                  <span>{{ t('invoices.button.preview') }}</span>
                </button>
                <button
                  v-if="(row as any).status === 'DRAFT'"
                  class="flex items-center w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-slate-100 gap-3"
                  role="menuitem"
                  :aria-label="t('invoices.aria.finalizeInvoice')"
                  @click="openFinalize(row); close()"
                >
                  <FileText class="w-4 h-4" />
                  <span>{{ t('invoices.button.finalize') }}</span>
                </button>
                <button
                  v-if="(row as any).status === 'DRAFT'"
                  class="flex items-center w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 gap-3"
                  role="menuitem"
                  :aria-label="t('invoices.aria.deleteInvoice')"
                  :disabled="isDeleting"
                  :class="isDeleting ? 'opacity-50 cursor-not-allowed' : ''"
                  @click="openDelete(row); close()"
                  :title="t('invoices.aria.deleteDraftIrreversible')"
                >
                  <Trash2 class="w-4 h-4" />
                  <span>{{ t('invoices.button.delete') }}</span>
                </button>
                <button
                  class="flex items-center w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-slate-100 gap-3"
                  role="menuitem"
                  :aria-label="t('invoices.aria.viewChargeAudit')"
                  @click="openChargeAudit(row); close()"
                >
                  <span>{{ t('invoices.button.viewCharges') }}</span>
                </button>
              </div>
            </template>
          </KebabMenu>
        </template>

        <template #mobile-card="{ row }">
          <div class="bg-white rounded-xl border border-slate-200 p-4 shadow-sm">
            <div class="flex items-start justify-between gap-2 mb-2">
              <div>
                <p class="font-semibold text-slate-900 text-sm">{{ (row as any).finalNumber || (row as any).draftNumber || '—' }}</p>
                <p class="text-xs text-slate-500 mt-0.5">{{ (row as any).customerName || '—' }}</p>
              </div>
              <StatusBadge :status="(row as any).status" />
            </div>
            <div class="flex items-center justify-between text-xs text-slate-500">
              <span>{{ (row as any).createdDate ? new Date((row as any).createdDate).toLocaleDateString() : '—' }}</span>
              <span class="font-semibold text-slate-900 text-sm">{{ formatCurrency(getRowAmount(row)) }}</span>
            </div>
          </div>
        </template>

        <template #footer>
          <!-- Footer: usage summary + pagination + page size -->
          <div class="bg-slate-50 px-6 py-3 text-sm text-slate-600 border-b border-slate-200">
            <div class="flex items-center justify-between">
              <div>
                {{ t('invoices.footer.summary', { count: displayedCount, total: formatCurrency(displayedTotalAmount) }) }}
              </div>
              <div>
                {{ t('invoices.footer.showing', { from: ((state.page - 1) * state.pageSize) + 1, to: Math.min(state.page * state.pageSize, state.totalCount), total: state.totalCount }) }}
              </div>
            </div>
          </div>
          <div class="bg-white px-6 py-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-3">
                <button
                  class="inline-flex items-center px-4 py-2 border border-slate-300 text-sm font-medium rounded-lg text-slate-700 bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  @click="changePage(-1)"
                  :disabled="state.page === 1"
                >
                  {{ t('invoices.button.previous') }}
                </button>
                <span class="text-sm text-slate-700 px-4 py-2">
                  {{ t('invoices.footer.page', { page: state.page }) }}
                </span>
                <button
                  class="inline-flex items-center px-4 py-2 border border-slate-300 text-sm font-medium rounded-lg text-slate-700 bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  @click="changePage(1)"
                  :disabled="state.page * state.pageSize >= state.totalCount"
                >
                  {{ t('invoices.button.next') }}
                </button>
              </div>
              <div class="flex items-center gap-2">
                <label class="text-sm text-slate-600">{{ t('invoices.footer.show') }}</label>
                <select
                  v-model.number="state.pageSize"
                  class="border border-slate-300 rounded-lg px-2 py-1 text-sm bg-white"
                >
                  <option :value="10">10</option>
                  <option :value="20">20</option>
                  <option :value="50">50</option>
                </select>
              </div>
            </div>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- Toast -->
    <ToastNotification :toast="toast" @dismiss="toast = null" />

    <!-- Finalize Modal -->
    <ConfirmDialog
      :open="finalizeTarget !== null"
      tone="success"
      :title="t('invoices.confirm.finalizeTitle')"
      :confirm-label="t('invoices.button.finalize')"
      @update:open="(v) => { if (!v) finalizeTarget = null; }"
      @confirm="finalizeInvoice"
    >
      <template v-if="finalizeTarget">
        {{ t('invoices.confirm.finalizeBody', { id: finalizeTarget.displayId }) }}
      </template>
    </ConfirmDialog>

    <!-- Delete Modal -->
    <ConfirmDialog
      :open="deleteTarget !== null"
      tone="danger"
      :title="t('invoices.confirm.deleteTitle')"
      :loading="isDeleting"
      @update:open="(v) => { if (!v && !isDeleting) deleteTarget = null; }"
      @confirm="deleteInvoice"
    >
      <template v-if="deleteTarget">
        {{ t('invoices.confirm.deleteBody', { id: deleteTarget.displayId }) }}
      </template>
    </ConfirmDialog>

    <!-- Invoice Preview Component -->
    <InvoicePreview
      v-if="previewInvoice"
      :invoice-id="previewInvoice.id"
      :display-id="previewInvoice.displayId"
      :preview-url="previewInvoice.url"
      :status="previewInvoice.status"
      :deleting="isDeleting"
      @close="closePreview"
      @finalize="finalizePreview"
      @delete="openDeleteFromPreview"
    />

    <!-- Charge Audit Modal -->
    <InvoiceChargeAudit
      :invoice-id="chargeAuditInvoiceId"
      :invoice-number="chargeAuditInvoiceNumber"
      @close="closeChargeAudit"
    />
  </div>
</template>

<style scoped>
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>