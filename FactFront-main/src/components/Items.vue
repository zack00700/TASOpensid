<script setup lang="ts">
import { ref, inject, computed, watch, reactive, onMounted } from "vue";
import { useI18n } from 'vue-i18n';
import { v4 as uuidv4 } from "uuid";

const { t } = useI18n();
import {
  Search,
  Download,
  X,
  Clock,
  Plus,
  Pencil,
  Trash2,
  History,
  MoreVertical,
  BadgeDollarSign,
  Check,
  Filter,
  RefreshCw,
} from "lucide-vue-next";
import DataTable from './ui/DataTable.vue';
import type { Column } from './ui/DataTable.types';
import {
  getDwellDays,
  dwellColorClass,
  getChargingStatus,
  getLastEvent,
  formatRelativeDate,
} from '../utils/itemAggregates';
import AdvancedFilter from "./AdvancedFilter.vue";
import ItemForm from "./ItemForm.vue";
import AddItemEventModal from "./AddItemEventModal.vue";
import ItemEventHistory from "./ItemEventHistory.vue";
import ItemChargeHistory from "./ItemChargeHistory.vue";
import KebabMenu from "./KebabMenu.vue";
import Pagination from "./Pagination.vue";
import StatusBadge from "./ui/StatusBadge.vue";
import SearchBar from "./ui/SearchBar.vue";
import FilterChips from "./ui/FilterChips.vue";
import ToastNotification from "./ui/ToastNotification.vue";
import PageHeader from "./ui/PageHeader.vue";
import { useToast } from "../composables/useToast";
import { Item, Event } from "../types/item";
import { ItemService } from "../services/itemService";

const itemService = ItemService.getInstance();
const $axios = inject('$axios');

import { useItem } from "../composables/use.item.ts";
import { useKeyboardShortcut } from "../composables/useKeyboardShortcut";
import ColumnPicker from './ui/ColumnPicker.vue';
import { useColumnPreferences, type ColumnDefinition } from '../composables/useColumnPreferences';
import Button from './ui/Button.vue';
const { 
  items, 
  pagination, 
  loading, 
  error,
  draftInvoice, 
  getItems, 
  changePage, 
  changePageSize, 
  applyFilters, 
  clearFilters,
  refreshItems 
} = useItem();

// Component state
const showForm = ref(false);
const editingItem = ref<Item | null>(null);
const showDeleteConfirm = ref(false);
const itemToDelete = ref<Item | null>(null);
const showHistory = ref(false);
const showEventForm = ref(false);
const selectedItemForEvent = ref<Item | null>(null);
const selectedItemForHistory = ref<Item | null>(null);
const historyRef = ref<any>(null);
const showCustomerToBillModal = ref(false);

useKeyboardShortcut('Escape', () => {
  if (showDeleteConfirm.value) showDeleteConfirm.value = false;
  else if (showCustomerToBillModal.value) showCustomerToBillModal.value = false;
  else if (showEventForm.value) showEventForm.value = false;
  else if (showHistory.value) showHistory.value = false;
}, { ignoreInInputs: false });

useKeyboardShortcut('n', () => {
  if (showForm.value || showDeleteConfirm.value || showEventForm.value) return;
  handleAdd();
});

useKeyboardShortcut('r', () => {
  if (showForm.value || showDeleteConfirm.value || showEventForm.value || showHistory.value) return;
  handleRefresh();
});

const itemColumns: ColumnDefinition[] = [
  { key: 'identity',   label: t('items.column.container'),  required: true },
  { key: 'statut',     label: t('items.column.status') },
  { key: 'customs',    label: t('items.column.customs') },
  { key: 'dwellTime',  label: t('items.column.dwell') },
  { key: 'vgm',        label: t('items.column.vgm') },
  { key: 'emptyFull',  label: t('items.column.emptyFull') },
  { key: 'position',   label: t('items.column.position') },
  { key: 'lastEvent',  label: t('items.column.lastEvent') },
  { key: 'charging',   label: t('items.column.charging'),   hiddenByDefault: true },
  { key: 'voyageIn',   label: t('items.column.voyageIn'),   hiddenByDefault: true },
  { key: 'voyageOut',  label: t('items.column.voyageOut'),  hiddenByDefault: true },
  { key: 'hazmat',     label: t('items.column.hazmat'),     hiddenByDefault: true },
  { key: 'reeferTemp', label: t('items.column.reeferTemp'), hiddenByDefault: true },
];
const itemCols = useColumnPreferences('items', itemColumns);
const dataTableColumns: Column<any>[] = [
  { key: 'identity',   label: t('items.column.container'),  sticky: 'left', width: 'min-w-[280px]' },
  { key: 'statut',     label: t('items.column.status'),     width: 'w-28' },
  { key: 'customs',    label: t('items.column.customs'),    width: 'w-32' },
  { key: 'dwellTime',  label: t('items.column.dwell'),      align: 'right', width: 'w-20' },
  { key: 'vgm',        label: t('items.column.vgm'),        align: 'center', width: 'w-24' },
  { key: 'emptyFull',  label: t('items.column.emptyFull'),  width: 'w-24' },
  { key: 'position',   label: t('items.column.position'),   width: 'w-24' },
  { key: 'lastEvent',  label: t('items.column.lastEvent'),  width: 'min-w-[180px]' },
  { key: 'charging',   label: t('items.column.charging'),   width: 'w-28' },
  { key: 'voyageIn',   label: t('items.column.voyageIn'),   width: 'w-28' },
  { key: 'voyageOut',  label: t('items.column.voyageOut'),  width: 'w-28' },
  { key: 'hazmat',     label: t('items.column.hazmat'),     width: 'w-32' },
  { key: 'reeferTemp', label: t('items.column.reeferTemp'), align: 'right', width: 'w-24' },
];
const selectedItemIds = ref<string[]>([]);
const customerToBill = ref("");

// Search and filter state
const searchQuery = ref('');
const searchDebounceTimer = ref<number | null>(null);
const showFilters = ref(false);
const activeFilters = reactive({
  itemType: '',
  status: '',
  ownerId: ''
});

// Initialize data on mount
onMounted(() => {
  getItems();
});

// Debounced search
watch(searchQuery, (newValue) => {
  if (searchDebounceTimer.value) {
    clearTimeout(searchDebounceTimer.value);
  }
  
  searchDebounceTimer.value = setTimeout(() => {
    handleSearch();
  }, 500);
});

const handleSearch = async () => {
  await applyFilters({
    search: searchQuery.value,
    ...activeFilters
  });
};

const handleFilter = async (filters: any[]) => {
  // Convert AdvancedFilter format to our filter format
  const filterObj = filters.reduce((acc, filter) => {
    if (filter.field && filter.value) {
      acc[filter.field] = filter.value;
    }
    return acc;
  }, {});
  
  Object.assign(activeFilters, filterObj);
  await applyFilters({
    search: searchQuery.value,
    ...activeFilters
  });
};

const clearAllFilters = async () => {
  searchQuery.value = '';
  activeFilters.itemType = '';
  activeFilters.status = '';
  activeFilters.ownerId = '';
  await clearFilters();
};

const handlePageChange = async (page: number) => {
  await changePage(page);
};

const handlePageSizeChange = async (size: number) => {
  await changePageSize(size);
};

const handleRefresh = async () => {
  await refreshItems();
};

const handleAdd = () => {
  editingItem.value = null;
  showForm.value = true;
};

const handleEdit = (item: Item) => {

  
  // Convert Item to ItemFormData format
  const formData = {
    _id: item.id || item._id,
    id: item.id || item._id,
    itemNumber: item.itemNumber || "",
    itemType: item.itemType ? String(item.itemType).toLowerCase() : "",
    type: item.type || "",
    ownerId: item.ownerId || "",
    position: item.position || "",
    status: item.status || item.itemStatus || "Available",
    lastInspection: item.lastInspectionDate
      ? (item.lastInspectionDate instanceof Date
          ? item.lastInspectionDate.toISOString().split('T')[0] 
          : new Date(item.lastInspectionDate).toISOString().split('T')[0])
      : "",
    nextInspection: item.nextInspectionDate
      ? (item.nextInspectionDate instanceof Date
          ? item.nextInspectionDate.toISOString().split('T')[0]
          : new Date(item.nextInspectionDate).toISOString().split('T')[0])
      : "",
    notes: item.notes || "",
    lifeCycles: item.lifeCycles || [],
  };
  
  editingItem.value = formData;
  showForm.value = true;
};

const handleDelete = (item: Item) => {
  itemToDelete.value = item;
  showDeleteConfirm.value = true;
};

const confirmDelete = async () => {
  if (itemToDelete.value) {
    // In a real implementation, you'd call an API to delete the item
    // For now, just refresh the current page
    await refreshItems();
    showDeleteConfirm.value = false;
    itemToDelete.value = null;
  }
};

const handleFormSubmit = async (formData: any) => {
  try {
    // The composable handles the actual creation/update
    showForm.value = false;
    editingItem.value = null;
    
    // Refresh the current page to show changes
    await refreshItems();
  } catch (error) {
    console.error('Error submitting form:', error);
    // Handle error (show notification, etc.)
  }
};

const handleFormCancel = () => {
  showForm.value = false;
  editingItem.value = null;
};

const handleViewHistory = (item: Item) => {
  selectedItemForHistory.value = item;
  showHistory.value = true;
};

const handleStartLifecycle = async (item: Item) => {
  try {
    const event: Event = {
      id: uuidv4(),
      timestamp: new Date().toISOString(),
      type: "IN",
      itemId: item.id,
      lifecycleId: "",
      location: item.position,
    };

    await itemService.addEvent(item, event);
    await refreshItems(); // Refresh to show updated item
  } catch (error) {
    console.error("Error starting lifecycle:", error);
  }
};

const handleEndLifecycle = async (item: Item) => {
  try {
    const event: Event = {
      id: uuidv4(),
      timestamp: new Date().toISOString(),
      type: "OUT",
      itemId: item.id,
      lifecycleId: item.currentLifecycleId!,
      location: item.position,
    };

    await itemService.addEvent(item, event);
    await refreshItems(); // Refresh to show updated item
  } catch (error) {
    console.error("Error ending lifecycle:", error);
  }
};

const handleAddEvent = (item: Item) => {
  selectedItemForEvent.value = item;
  showEventForm.value = true;
};

const handleEventSuccess = async () => {
  const id = selectedItemForEvent.value?.id;
  showEventForm.value = false;
  selectedItemForEvent.value = null;
  showToast(t('items.toast.eventAdded'), 'success');
  
  await refreshItems(); // Refresh to show changes
  
  if (showHistory.value && selectedItemForHistory.value?.id === id) {
    historyRef.value?.refresh();
  }
};

const handleEventCancel = () => {
  showEventForm.value = false;
  selectedItemForEvent.value = null;
};

const handleHistoryClose = () => {
  showHistory.value = false;
  selectedItemForHistory.value = null;
};

const showChargeHistory = ref(false);
const chargeHistoryItemId = ref<string | null>(null);
const chargeHistoryItemNumber = ref<string>('');

function openChargeHistory(item: any) {
  chargeHistoryItemId.value = item.id ?? item._id;
  chargeHistoryItemNumber.value = item.itemNumber ?? item.number ?? '';
  showChargeHistory.value = true;
}
function closeChargeHistory() {
  showChargeHistory.value = false;
  chargeHistoryItemId.value = null;
}

const handleCustomerToBill = () => {
  showCustomerToBillModal.value = true;
};

const billCustomer = async () => {
  try {
    const result = await draftInvoice(selectedItemIds.value, customerToBill.value);
    await refreshItems();
    showCustomerToBillModal.value = false;
    showToast(t('items.toast.draftInvoiceCreated', { id: result.invoiceId }), 'success');
  } catch (err: any) {
    const msg = err?.response?.data?.message || t('items.toast.invoiceGenerateFailed');
    showToast(msg, 'error');
  }
};

// BL lookup map
const bolsMap = ref<Record<string, any>>({});

async function fetchBol(bolId: string) {
  if (!bolId || bolsMap.value[bolId]) return;
  try {
    const { data } = await $axios.get(`/billoflading/${encodeURIComponent(bolId)}`);
    bolsMap.value[bolId] = data;
  } catch {
    // silently ignore — fall back to displaying id
  }
}

watch(
  items,
  (newItems) => {
    (newItems ?? []).forEach((it) => {
      const id = (it as any).billOfLadingId;
      if (id) fetchBol(id);
    });
  },
  { immediate: true }
);

// Invoice handling (existing code)
const invoices = ref<Record<string, any>>({});
const loadingInvoices = reactive(new Set<string>());
const invoiceErrors = reactive(new Set<string>());

async function fetchInvoice(invoiceId: string, force = false) {
  if (!invoiceId) return;
  if (!force && invoices.value[invoiceId]) return;
  if (loadingInvoices.has(invoiceId)) return;

  loadingInvoices.add(invoiceId);
  invoiceErrors.delete(invoiceId);

  try {
    const { data } = await $axios.get(`/invoice/${encodeURIComponent(invoiceId)}`);
    invoices.value[invoiceId] = data;
  } catch (e) {
    invoiceErrors.add(invoiceId);
  } finally {
    loadingInvoices.delete(invoiceId);
  }
}

watch(
  items,
  (newItems) => {
    (newItems ?? []).forEach((it) => {
      const id = (it as any).relatedInvoice;
      if (id) fetchInvoice(id);
    });
  },
  { immediate: true }
);

type InvoiceState = 'DRAFT' | 'FINAL' | 'NONE' | 'LOADING' | 'ERROR';

function deriveInvoice(
  item: Item
): { state: InvoiceState; label: string; tooltip: string } {
  const invoiceId = (item as any).relatedInvoice;
  if (!invoiceId)
    return { state: 'NONE', label: '—', tooltip: t('items.invoice.none') };

  if (loadingInvoices.has(invoiceId))
    return { state: 'LOADING', label: t('common.loading'), tooltip: t('items.invoice.loading') };

  if (invoiceErrors.has(invoiceId))
    return { state: 'ERROR', label: t('items.invoice.error'), tooltip: t('items.invoice.errorTooltip') };

  const inv = invoices.value[invoiceId];
  if (!inv)
    return { state: 'NONE', label: '—', tooltip: t('items.invoice.none') };

  if (inv.status === 'DRAFT')
    return {
      state: 'DRAFT',
      label: t('items.invoice.draft'),
      tooltip: inv.draftNumber ? t('items.invoice.draftTooltip', { number: inv.draftNumber }) : t('items.invoice.draftFallback'),
    };
  if (inv.status === 'FINAL' || inv.status === 'ISSUED')
    return {
      state: 'FINAL',
      label: t('items.invoice.invoiced'),
      tooltip: inv.finalNumber ? t('items.invoice.finalTooltip', { number: inv.finalNumber }) : t('items.invoice.finalFallback'),
    };
  return { state: 'NONE', label: '—', tooltip: t('items.invoice.none') };
}

const viewItems = computed(() =>
  (items.value ?? []).map((it) => ({
    ...it,
    _invoice: deriveInvoice(it as Item),
  }))
);

const getStatusBadgeClasses = (status: string) => {
  const baseClasses = "px-2 py-1 text-xs font-medium rounded-full";
  switch (status) {
    case "Available":
      return `${baseClasses} bg-green-100 text-green-800`;
    case "In Use":
      return `${baseClasses} bg-blue-100 text-blue-800`;
    case "Maintenance":
      return `${baseClasses} bg-yellow-100 text-yellow-800`;
    case "Out of Service":
      return `${baseClasses} bg-red-100 text-red-800`;
    default:
      return baseClasses;
  }
};

// Computed properties for UI state
const hasActiveFilters = computed(() => {
  return !!(searchQuery.value || activeFilters.itemType || activeFilters.status || activeFilters.ownerId);
});

const activeFilterChips = computed(() => {
  const chips: { key: string; label: string }[] = [];
  if (searchQuery.value) chips.push({ key: 'search', label: `"${searchQuery.value}"` });
  if (activeFilters.itemType) chips.push({ key: 'itemType', label: t('items.filter.typeChip', { value: activeFilters.itemType }) });
  if (activeFilters.status) chips.push({ key: 'status', label: t('items.filter.statusChip', { value: activeFilters.status }) });
  if (activeFilters.ownerId) chips.push({ key: 'ownerId', label: t('items.filter.ownerChip', { value: activeFilters.ownerId }) });
  return chips;
});

function removeFilter(key: string) {
  if (key === 'search') { searchQuery.value = ''; handleSearch(); }
  else if (key === 'itemType') { activeFilters.itemType = ''; handleSearch(); }
  else if (key === 'status') { activeFilters.status = ''; handleSearch(); }
  else if (key === 'ownerId') { activeFilters.ownerId = ''; handleSearch(); }
}

const { toast, showToast, dismissToast } = useToast();
</script>

<template>
  <div>
    <!-- Page Header (shown when list is visible) -->
    <PageHeader
      v-if="!showForm && !showHistory"
      :title="t('items.title')"
      :subtitle="t('items.subtitle')"
      :count="pagination?.totalItems ?? 0"
    >
      <template #actions>
        <button
          class="hidden sm:inline-flex items-center px-3 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-40"
          @click="handleCustomerToBill"
          :disabled="selectedItemIds.length === 0"
        >
          <BadgeDollarSign class="h-4 w-4 mr-2" />
          {{ t('items.button.invoice', { count: selectedItemIds.length }) }}
        </button>
        <button
          class="hidden sm:inline-flex items-center px-3 py-2 border border-slate-200 text-sm font-medium rounded-lg text-slate-700 bg-white hover:bg-slate-50"
          :aria-label="t('items.aria.exportItems')"
        >
          <Download class="h-4 w-4 mr-2" />
          {{ t('common.export') }}
        </button>
        <button
          @click="handleAdd"
          class="inline-flex items-center px-3 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-green-600 hover:bg-green-700"
        >
          <Plus class="h-4 w-4 sm:mr-2" />
          <span class="hidden sm:inline">{{ t('items.button.add') }}</span>
        </button>
      </template>
    </PageHeader>

    <!-- List View -->
    <div v-if="!showForm && !showHistory" class="bg-white shadow rounded-lg mt-4">
      <!-- Search + filters header -->
      <div class="px-4 py-3 border-b border-gray-200 space-y-3">

        <!-- Row 2: Search + Filters -->
        <SearchBar
          v-model="searchQuery"
          :placeholder="t('items.placeholder.search')"
          :filters-active="hasActiveFilters"
          :loading="loading"
          @toggle-filters="showFilters = !showFilters"
        >
          <template #actions>
            <button
              @click="handleRefresh"
              :disabled="loading"
              class="inline-flex items-center p-2 border border-gray-300 rounded-lg text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 flex-shrink-0"
              :aria-label="t('items.aria.refresh')"
            >
              <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
            </button>
          </template>
        </SearchBar>

        <!-- Row 3 (mobile only): Make Invoice — contextual, shown only when items selected -->
        <div v-if="selectedItemIds.length > 0" class="sm:hidden">
          <button
            class="w-full inline-flex items-center justify-center px-4 py-2.5 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700"
            @click="handleCustomerToBill"
          >
            <BadgeDollarSign class="h-4 w-4 mr-2" />
            {{ t('items.button.makeInvoice', { count: selectedItemIds.length }) }}
          </button>
        </div>

        <!-- Active Filters Display -->
        <FilterChips
          :chips="activeFilterChips"
          @remove="removeFilter"
          @clear-all="clearAllFilters"
        />

      </div>

      <!-- Advanced Filter -->
      <AdvancedFilter 
        v-if="showFilters"
        type="items" 
        @filter="handleFilter" 
        class="border-b border-gray-200"
      />

      <!-- Loading State -->
      <div v-if="loading" class="px-6 py-12 text-center">
        <RefreshCw class="h-8 w-8 animate-spin mx-auto text-gray-400 mb-4" />
        <p class="text-gray-500">{{ t('items.label.loading') }}</p>
      </div>

      <!-- Error State -->
      <div v-else-if="error" class="px-6 py-12 text-center">
        <div class="text-red-500 mb-4">
          <X class="h-8 w-8 mx-auto mb-2" />
          <p>{{ t('items.label.errorLoading', { error }) }}</p>
        </div>
        <button
          @click="handleRefresh"
          class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          {{ t('items.button.tryAgain') }}
        </button>
      </div>

      <!-- Table -->
      <div v-else class="px-6">
        <!-- Table toolbar -->
        <div class="flex justify-end pb-2">
          <ColumnPicker
            :toggleable="itemCols.toggleable.value"
            :hidden-count="itemCols.hiddenCount.value"
            :is-visible="itemCols.isVisible"
            :toggle="itemCols.toggle"
            :reset="itemCols.reset"
            @reset="itemCols.reset"
          />
        </div>
        <!-- Items table — DataTable v2 with sticky identity + selectable -->
        <DataTable
          :rows="viewItems"
          :columns="dataTableColumns.filter(c => itemCols.isVisible(c.key))"
          row-key="id"
          selectable
          :is-selectable="(row) => (row as any)._invoice?.state === 'NONE'"
          v-model:selected="selectedItemIds"
          :loading="loading"
          :empty="{
            title: hasActiveFilters ? t('items.empty.titleFiltered') : t('items.empty.title'),
            description: hasActiveFilters ? t('items.empty.descriptionFiltered') : t('items.empty.description'),
            actionLabel: hasActiveFilters ? t('items.empty.clearFilters') : t('items.button.add'),
          }"
          @empty-action="hasActiveFilters ? clearAllFilters() : handleAdd()"
          @row-dblclick="(row) => handleEdit(row as Item)"
        >
          <!-- Sticky identity (2 lines): containerNumber + type + flags / bookingNumber -->
          <template #cell-identity="{ row }">
            <div class="space-y-0.5 min-w-0">
              <div class="flex items-center gap-1.5 flex-wrap">
                <span class="font-mono font-semibold text-sm text-slate-900 tracking-wide">
                  {{ (row as Item).containerNumber || (row as Item).itemNumber }}
                </span>
                <span v-if="(row as Item).containerType"
                      class="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-600">
                  {{ (row as Item).containerType }}
                </span>
                <span v-if="(row as Item).hazmatFlag" :title="t('items.badge.hazmat')" class="text-sm leading-none">🔺</span>
                <span v-if="(row as Item).reeferFlag" :title="t('items.badge.reefer')" class="text-sm leading-none">❄️</span>
                <span v-if="(row as Item).oogFlag"
                      class="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-red-50 text-red-600"
                      :title="t('items.badge.oog')">OOG</span>
              </div>
              <div v-if="(row as Item).bookingNumber" class="text-xs text-slate-500">
                BKG&nbsp;{{ (row as Item).bookingNumber }}
              </div>
            </div>
          </template>

          <template #cell-statut="{ row }">
            <StatusBadge :status="(row as Item).status || ''" />
          </template>

          <template #cell-customs="{ row }">
            <StatusBadge v-if="(row as Item).customsStatus"
                         :status="(row as Item).customsStatus!" />
            <span v-else class="text-slate-400">—</span>
          </template>

          <template #cell-dwellTime="{ row }">
            <span v-if="getDwellDays(row as Item) !== null"
                  :class="dwellColorClass(getDwellDays(row as Item)!)">
              {{ getDwellDays(row as Item) }}d
            </span>
            <span v-else class="text-slate-400">—</span>
          </template>

          <template #cell-vgm="{ row }">
            <span v-if="(row as Item).weightVerified"
                  class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200"
                  :title="(row as Item).verifiedWeight ? `${(row as Item).verifiedWeight} kg` : t('items.vgm.verified')">
              <Check class="w-3 h-3" /> VGM
            </span>
            <span v-else
                  class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200"
                  :title="t('items.vgm.notDeclared')">
              <X class="w-3 h-3" /> {{ t('items.vgm.noVgm') }}
            </span>
          </template>

          <template #cell-emptyFull="{ row }">
            <span :class="(row as Item).emptyStatus === 'EMPTY' ? 'text-slate-500' : 'text-slate-900 font-medium'">
              {{ (row as Item).emptyStatus ?? '—' }}
            </span>
          </template>

          <template #cell-position="{ row }">
            <span class="font-mono text-xs">{{ (row as Item).position ?? '—' }}</span>
          </template>

          <template #cell-lastEvent="{ row }">
            <div v-if="getLastEvent(row as Item)" class="text-xs">
              <div>
                <span class="font-medium text-slate-900">{{ getLastEvent(row as Item)?.eventType ?? '—' }}</span>
                <span v-if="getLastEvent(row as Item)?.location" class="text-slate-500">
                  @ {{ getLastEvent(row as Item)?.location }}
                </span>
              </div>
              <div v-if="getLastEvent(row as Item)?.timestamp" class="text-slate-400">
                {{ formatRelativeDate(getLastEvent(row as Item)!.timestamp!) }}
              </div>
            </div>
            <span v-else class="text-slate-400">—</span>
          </template>

          <template #cell-charging="{ row }">
            <span v-if="getChargingStatus(row as Item)?.state === 'free'" class="text-emerald-600 text-xs">{{ t('items.charging.free') }}</span>
            <span v-else-if="getChargingStatus(row as Item)?.state === 'grace'" class="text-amber-600 text-xs">{{ t('items.charging.grace') }}</span>
            <span v-else-if="getChargingStatus(row as Item)?.state === 'past'"
                  class="text-red-600 text-xs font-medium">
              {{ t('items.charging.past', { days: getChargingStatus(row as Item)!.days }) }}
            </span>
            <span v-else class="text-slate-400">—</span>
          </template>

          <template #cell-voyageIn="{ row }">
            {{ (row as Item).inboundVoyage ?? '—' }}
          </template>

          <template #cell-voyageOut="{ row }">
            {{ (row as Item).outboundVoyage ?? '—' }}
          </template>

          <template #cell-hazmat="{ row }">
            <div v-if="(row as Item).hazmatFlag" class="text-xs">
              <span class="font-medium">{{ (row as Item).hazmatClass ?? '?' }}</span>
              <span v-if="(row as Item).unNumber" class="text-slate-500">
                · UN{{ (row as Item).unNumber }}
              </span>
            </div>
            <span v-else class="text-slate-300">—</span>
          </template>

          <template #cell-reeferTemp="{ row }">
            <span v-if="(row as Item).reeferFlag && (row as Item).reeferTemperature != null">
              {{ (row as Item).reeferTemperature }}°C
            </span>
            <span v-else class="text-slate-300">—</span>
          </template>

          <template #row-actions="{ row }">
            <KebabMenu>
              <template #trigger="{ toggle, refEl, isOpen }">
                <button
                  class="text-gray-400 hover:text-gray-700 p-1.5 rounded hover:bg-gray-100"
                  @click.stop="toggle()"
                  :ref="refEl"
                  :aria-label="t('items.aria.rowActions')"
                  :aria-expanded="isOpen ? 'true' : 'false'"
                >
                  <MoreVertical class="h-4 w-4" />
                </button>
              </template>
              <template #content="{ close }">
                <button class="flex w-full items-center gap-2 px-4 py-2 text-sm hover:bg-gray-100"
                  @click="handleViewHistory(row as Item); close()">
                  <History class="w-4 h-4" /><span>{{ t('items.action.eventHistory') }}</span>
                </button>
                <button class="flex w-full items-center gap-2 px-4 py-2 text-sm hover:bg-gray-100"
                  @click="handleAddEvent(row as Item); close()">
                  <Clock class="w-4 h-4" /><span>{{ t('items.action.addEvent') }}</span>
                </button>
                <button class="flex w-full items-center gap-2 px-4 py-2 text-sm hover:bg-gray-100"
                  @click="handleEdit(row as Item); close()">
                  <Pencil class="w-4 h-4" /><span>{{ t('common.edit') }}</span>
                </button>
                <button class="flex w-full items-center gap-2 px-4 py-2 text-sm hover:bg-gray-100"
                  @click="openChargeHistory(row as Item); close()">
                  <span>{{ t('items.action.chargeHistory') }}</span>
                </button>
                <button class="flex w-full items-center gap-2 px-4 py-2 text-sm hover:bg-gray-100 text-red-600"
                  @click="handleDelete(row as Item); close()">
                  <Trash2 class="w-4 h-4" /><span>{{ t('common.delete') }}</span>
                </button>
              </template>
            </KebabMenu>
          </template>
        </DataTable>
      </div>

      <!-- Pagination -->
      <Pagination
        v-if="!loading && viewItems.length > 0 && pagination"
        :pagination="pagination"
        @page-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />
    </div>

    <!-- Form View -->
    <ItemForm
      v-else-if="showForm"
      :edit-mode="!!editingItem"
      :initial-data="editingItem"
      @submit="handleFormSubmit"
      @cancel="handleFormCancel"
    />

    <!-- Delete Confirmation Modal -->
    <Teleport to="body">
      <div
        v-if="showDeleteConfirm"
        class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50"
        @click.self="showDeleteConfirm = false"
      >
        <div class="bg-white rounded-lg p-6 max-w-md w-full">
          <div class="text-center">
            <X class="mx-auto h-12 w-12 text-red-500" />
            <h3 class="mt-4 text-lg font-medium text-gray-900">{{ t('items.confirm.deleteTitle') }}</h3>
            <p class="mt-2 text-sm text-gray-500">
              {{ t('items.confirm.deleteBody', { number: itemToDelete?.itemNumber }) }}
            </p>
          </div>
          <div class="mt-6 flex justify-end space-x-3">
            <Button variant="secondary" @click="showDeleteConfirm = false">{{ t('common.cancel') }}</Button>
            <Button variant="danger" @click="confirmDelete">{{ t('common.delete') }}</Button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Add Event Modal -->
    <Teleport to="body">
      <div
        v-if="showEventForm && selectedItemForEvent"
        class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50"
        @click.self="showEventForm = false"
      >
        <div class="max-w-md w-full">
          <AddItemEventModal
            :item-id="selectedItemForEvent.id"
            @success="handleEventSuccess"
            @cancel="handleEventCancel"
          />
        </div>
      </div>
    </Teleport>

    <!-- Event History Modal -->
    <Teleport to="body">
      <div
        v-if="showHistory && selectedItemForHistory"
        class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50"
        @click.self="showHistory = false"
      >
        <div class="max-w-2xl w-full">
          <ItemEventHistory
            :item-id="selectedItemForHistory.id"
            ref="historyRef"
            @close="handleHistoryClose"
          />
        </div>
      </div>
    </Teleport>

    <!-- Customer to Bill Modal -->
    <Teleport to="body">
      <div
        v-if="showCustomerToBillModal"
        class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50"
        @click.self="showCustomerToBillModal = false"
      >
        <div class="bg-white rounded-lg p-6 max-w-2xl w-full">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-medium text-gray-900">{{ t('items.modal.customerToBillTitle') }}</h3>
            <button
              @click="showCustomerToBillModal = false"
              class="text-gray-400 hover:text-gray-500"
              :aria-label="t('common.close')"
            >
              <X class="h-6 w-6" />
            </button>
          </div>
          <div class="space-y-4">
            <div class="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
              <input
                v-model="customerToBill"
                type="text"
                :placeholder="t('items.placeholder.customerName')"
                class="block w-full text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
            <div class="text-sm text-gray-600">
              {{ t('items.modal.selectedForInvoicing', { count: selectedItemIds.length }) }}
            </div>
            <button
              @click="billCustomer"
              :disabled="!customerToBill || selectedItemIds.length === 0"
              class="px-3 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {{ t('items.button.makeInvoiceSubmit') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Charge History Modal -->
    <ItemChargeHistory
      :item-id="chargeHistoryItemId"
      :item-number="chargeHistoryItemNumber"
      @close="closeChargeHistory"
    />

    <!-- Toast -->
    <ToastNotification :toast="toast" @dismiss="dismissToast" />
  </div>
</template>