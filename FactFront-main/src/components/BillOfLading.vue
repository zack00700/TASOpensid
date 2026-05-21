<script setup lang="ts">
import { ref, onMounted, computed, watch, reactive } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  Search,
  Filter,
  Download,
  Plus,
  Pencil,
  Trash2,
  FileText,
  Link,
  X,
  Eye,
  Receipt,
  Loader2,
  Upload,
  CheckCircle,
  AlertCircle,
  FileJson,
  Cloud,
  RefreshCw,
  AlertTriangle,
  Ship,
  Train,
  Truck
} from 'lucide-vue-next';
import DataTable from './ui/DataTable.vue';
import type { Column } from './ui/DataTable.types';
import {
  getContainerCount,
  getTotalWeight,
  getHazmatFlag,
  getCustomsRollup,
  formatWeight,
} from '../utils/bolAggregates';
import AdvancedFilter from './AdvancedFilter.vue';
import BillOfLadingForm from './BillOfLadingForm.vue';
import InvoicePreview from './InvoicePreview.vue';
import Pagination from './Pagination.vue';
import KpiCard from './ui/KpiCard.vue';
import SearchBar from './ui/SearchBar.vue';
import FilterChips from './ui/FilterChips.vue';
import ToastNotification from './ui/ToastNotification.vue';
import PageHeader from './ui/PageHeader.vue';
import { useBillOfLading } from '../composables/useBillOfLading';
import { useKeyboardShortcut } from '../composables/useKeyboardShortcut';
import ColumnPicker from './ui/ColumnPicker.vue';
import StatusBadge from './ui/StatusBadge.vue';
import Button from './ui/Button.vue';
import { useColumnPreferences, type ColumnDefinition } from '../composables/useColumnPreferences';
import invoiceService from '../services/invoiceService';
import { formatUtcDate } from '../utils/date';

const { t } = useI18n();

interface Commodity {
  description: string;
  weightKg: number;
  volumeM3: number;
  packagesNumber: number;
  hazardous: boolean;
  hazardClass?: string;
  unNumber?: string;
}

interface Item {
  id?: string;
  clientId?: string;
  type: 'container' | 'breakbulk' | 'vehicle';
  itemNumber: string;
  status: string;
}

interface BillOfLading {
  id: string;
  blNumber: string;
  status: 'Draft' | 'Final' | 'Cancelled';
  shipper: string;
  consignee: string;
  notifyParty: string;
  transportType: 'Vessel' | 'Train' | 'Truck';
  vessel: string;
  voyage: string;
  portOfLoading: string;
  portOfDischarge: string;
  placeOfDelivery: string;
  driver: string;
  trainNumber: string;
  truckNumber: string;
  commodity: Commodity;
  items: Item[];
  transportSnapshot?: any;
  createdAt: string;
  updatedAt: string;
  // Extended fields (already in backend)
  bookingNumber?: string;
  shippingLine?: string;
  incoterms?: string;
  freightPayableAt?: string;
  freightCharges?: number;
  freightCurrency?: string;
  bolDate?: string;
  houseBolNumber?: string;
  masterBolNumber?: string;
  onBoardDate?: string;
  transshipmentPort?: string;
  documentationComplete?: boolean;
  blType?: string;
}

// Use the pagination composable
const { 
  bills, 
  pagination, 
  loading, 
  error,
  getBills, 
  changePage, 
  changePageSize, 
  applyFilters, 
  clearFilters,
  refreshBills,
  createBill,
  updateBill,
  deleteBill,
  bulkImport
} = useBillOfLading();

// Component state
const showForm = ref(false);
const editingBL = ref<BillOfLading | null>(null);
const showDeleteConfirm = ref(false);
const blToDelete = ref<BillOfLading | null>(null);
const showItemsModal = ref(false);
const selectedBL = ref<BillOfLading | null>(null);

// Search and filter state
const searchQuery = ref('');
const searchDebounceTimer = ref<number | null>(null);
const showFilters = ref(false);
const activeFilters = reactive({
  status: '',
  shipper: '',
  vessel: '',
  transportType: ''
});

// Bulk Import State
const isDragOver = ref(false);
const isUploading = ref(false);
const uploadProgress = ref(0);
const uploadResult = ref<{ success: number; errors: string[] } | null>(null);
const showUploadResult = ref(false);

useKeyboardShortcut('Escape', () => {
  if (showDeleteConfirm.value) showDeleteConfirm.value = false;
  else if (showUploadResult.value) showUploadResult.value = false;
  else if (showItemsModal.value) showItemsModal.value = false;
}, { ignoreInInputs: false });

useKeyboardShortcut('n', () => {
  if (showForm.value || showDeleteConfirm.value || showUploadResult.value || showItemsModal.value) return;
  handleAdd();
});

useKeyboardShortcut('r', () => {
  if (showForm.value || showDeleteConfirm.value || showUploadResult.value || showItemsModal.value) return;
  handleRefresh();
});

const bolColumns: ColumnDefinition[] = [
  { key: 'connaissement',  label: t('billOfLading.column.connaissement'), required: true },
  { key: 'vessel',         label: t('billOfLading.column.vessel') },
  { key: 'eta',            label: t('billOfLading.column.eta') },
  { key: 'containerCount', label: t('billOfLading.column.containerCount') },
  { key: 'customsRollup',  label: t('billOfLading.column.customs') },
  { key: 'hazmat',         label: t('billOfLading.column.hazmat') },
  { key: 'shipper',        label: t('billOfLading.column.shipper') },
  { key: 'consignee',      label: t('billOfLading.column.consignee') },
  { key: 'etd',            label: t('billOfLading.column.etd'),    hiddenByDefault: true },
  { key: 'voyage',         label: t('billOfLading.column.voyage'), hiddenByDefault: true },
  { key: 'totalWeight',    label: t('billOfLading.column.weight'), hiddenByDefault: true },
];
const bolCols = useColumnPreferences('bol', bolColumns);

function transportIcon(transportType: string | undefined) {
  if (transportType === 'Vessel') return Ship;
  if (transportType === 'Train') return Train;
  if (transportType === 'Truck') return Truck;
  return Ship;
}

function formatDate(value: string | number | Date | undefined): string {
  if (!value) return '—';
  const d = value instanceof Date ? value : new Date(value);
  if (isNaN(d.getTime())) return '—';
  return d.toLocaleDateString();
}

const dataTableColumns: Column<any>[] = [
  { key: 'connaissement',  label: t('billOfLading.column.connaissement'),                       mobile: 'title' },
  { key: 'vessel',         label: t('billOfLading.column.vessel'),                              mobile: 'meta' },
  { key: 'eta',            label: t('billOfLading.column.eta'),
    format: (v) => v ? formatDate(v as string) : '—',                                           mobile: 'meta' },
  { key: 'containerCount', label: t('billOfLading.column.containerCount'), align: 'right',      mobile: 'meta' },
  { key: 'customsRollup',  label: t('billOfLading.column.customs'),                             mobile: 'meta' },
  { key: 'hazmat',         label: t('billOfLading.column.hazmat'),        align: 'center',      mobile: 'meta' },
  { key: 'shipper',        label: t('billOfLading.column.shipper'),                             mobile: 'hidden' },
  { key: 'consignee',      label: t('billOfLading.column.consignee'),                           mobile: 'hidden' },
  { key: 'etd',            label: t('billOfLading.column.etd'),
    format: (v) => v ? formatDate(v as string) : '—',                                           mobile: 'hidden' },
  { key: 'voyage',         label: t('billOfLading.column.voyage'),                              mobile: 'hidden' },
  { key: 'totalWeight',    label: t('billOfLading.column.weight'),        align: 'right',       mobile: 'hidden' },
];
const dragCounter = ref(0);

// Export State
const isExporting = ref(false);

// Initialize data on mount
onMounted(() => {
  getBills(1, 10);
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
  activeFilters.status = '';
  activeFilters.shipper = '';
  activeFilters.vessel = '';
  activeFilters.transportType = '';
  await clearFilters();
};

const handlePageChange = async (page: number) => {
  await changePage(page);
};

const handlePageSizeChange = async (size: number) => {
  await changePageSize(size);
};

const handleRefresh = async () => {
  await refreshBills();
};

const getStatusBadgeClasses = (status: string) => {
  const baseClasses = "px-2 py-1 text-xs font-medium rounded-full";
  switch (status) {
    case 'Final':
      return `${baseClasses} bg-green-100 text-green-800`;
    case 'Draft':
      return `${baseClasses} bg-yellow-100 text-yellow-800`;
    case 'Cancelled':
      return `${baseClasses} bg-red-100 text-red-800`;
    default:
      return baseClasses;
  }
};

const handleAdd = () => {
  editingBL.value = null;
  showForm.value = true;
};

const handleEdit = (bl: BillOfLading) => {
  editingBL.value = bl;
  showForm.value = true;
};

const handleDelete = (bl: BillOfLading) => {
  blToDelete.value = bl;
  showDeleteConfirm.value = true;
};

const confirmDelete = async () => {
  if (blToDelete.value) {
    try {
      await deleteBill(blToDelete.value.id);
      showDeleteConfirm.value = false;
      blToDelete.value = null;
      showToast(t('billOfLading.toast.deleteSuccess'), 'success');
    } catch (error) {
      console.error('Error deleting bill:', error);
      showToast(t('billOfLading.toast.deleteFailed'), 'error');
    }
  }
};

const handleFormSubmit = async (
  formData: Omit<BillOfLading, 'id' | 'createdAt' | 'updatedAt'> | {},
) => {
  try {
    // If formData is empty (items diff was applied), just refresh the list
    if (editingBL.value && Object.keys(formData).length === 0) {
      await refreshBills();
      showForm.value = false;
      editingBL.value = null;
      return;
    }
    
    if (editingBL.value) {
      await updateBill(editingBL.value.id, formData as Omit<BillOfLading, 'id' | 'createdAt' | 'updatedAt'>);
      showToast(t('billOfLading.toast.updateSuccess'), 'success');
    } else {
      await createBill(formData as Omit<BillOfLading, 'id' | 'createdAt' | 'updatedAt'>);
      showToast(t('billOfLading.toast.createSuccess'), 'success');
    }

    showForm.value = false;
    editingBL.value = null;
  } catch (e: any) {
    console.error(e);
    showToast(e?.message || t('billOfLading.toast.saveFailed'), 'error');
  }
};

const handleFormCancel = () => {
  showForm.value = false;
  editingBL.value = null;
};

const toast = ref<{ message: string; type: 'success' | 'error' | 'warning' } | null>(null);
const showToast = (message: string, type: 'success' | 'error' | 'warning' = 'success') => {
  toast.value = { message, type };
  setTimeout(() => (toast.value = null), 3000);
};

// Track loading and recently generated invoices
const invoicing = ref<Record<string, boolean>>({});
const recentlyInvoiced = ref<Record<string, number>>({});
const invoiceConflict = ref<Record<string, boolean>>({});
const INVOICE_GUARD_MS = 10000; // 10 seconds

const previewInvoice = ref<{ id: string; url: string } | null>(null);

// Excel Export Functions
const formatExportData = (bills: BillOfLading[]) => {
  return bills.map(bl => ({
    'BL Number': bl.blNumber,
    'Status': bl.status,
    'Shipper': bl.shipper,
    'Consignee': bl.consignee,
    'Notify Party': bl.notifyParty || '',
    'Transport Type': bl.transportType,
    'Vessel': bl.vessel || '',
    'Voyage': bl.voyage || '',
    'Port of Loading': bl.portOfLoading || '',
    'Port of Discharge': bl.portOfDischarge || '',
    'Place of Delivery': bl.placeOfDelivery || '',
    'Driver': bl.driver || '',
    'Train Number': bl.trainNumber || '',
    'Truck Number': bl.truckNumber || '',
    'Commodity Description': bl.commodity.description,
    'Weight (kg)': bl.commodity.weightKg,
    'Volume (m³)': bl.commodity.volumeM3,
    'Number of Packages': bl.commodity.packagesNumber,
    'Hazardous': bl.commodity.hazardous ? 'Yes' : 'No',
    'Hazard Class': bl.commodity.hazardClass || '',
    'UN Number': bl.commodity.unNumber || '',
    'Number of Items': bl.items?.length || 0,
    'Created Date': formatUtcDate(bl.createdAt),
    'Updated Date': formatUtcDate(bl.updatedAt),
  }));
};

const formatItemsData = (bills: BillOfLading[]) => {
  const itemsData: any[] = [];
  bills.forEach(bl => {
    bl.items?.forEach(item => {
      itemsData.push({
        'BL Number': bl.blNumber,
        'Item ID': item.id || '',
        'Item Type': item.type,
        'Item Number': item.itemNumber,
        'Item Status': item.status,
        'Client ID': item.clientId || '',
      });
    });
  });
  return itemsData;
};

const exportToExcel = async () => {
  try {
    isExporting.value = true;
    showToast(t('billOfLading.toast.exportPreparing'), 'success');

    // Try to use XLSX for Excel export, fallback to CSV
    try {
      const XLSX = await import('xlsx');
      
      const workbook = XLSX.utils.book_new();
      
      const billsData = formatExportData(bills.value);
      const billsWorksheet = XLSX.utils.json_to_sheet(billsData);
      
      const billsColWidths = Object.keys(billsData[0] || {}).map(key => ({
        wch: Math.max(key.length, 12)
      }));
      billsWorksheet['!cols'] = billsColWidths;
      
      const itemsData = formatItemsData(bills.value);
      if (itemsData.length > 0) {
        const itemsWorksheet = XLSX.utils.json_to_sheet(itemsData);
        
        const itemsColWidths = Object.keys(itemsData[0] || {}).map(key => ({
          wch: Math.max(key.length, 12)
        }));
        itemsWorksheet['!cols'] = itemsColWidths;
        
        XLSX.utils.book_append_sheet(workbook, itemsWorksheet, 'Items');
      }
      
      XLSX.utils.book_append_sheet(workbook, billsWorksheet, 'Bills of Lading');
      
      const now = new Date();
      const timestamp = now.toISOString().slice(0, 19).replace(/[T:]/g, '-');
      const filename = `bills-of-lading-${timestamp}.xlsx`;
      
      XLSX.writeFile(workbook, filename);
      
      showToast(t('billOfLading.toast.exportExcelSuccess', { count: bills.value.length }), 'success');

    } catch (xlsxError) {
      console.warn('XLSX not available, falling back to CSV export:', xlsxError);

      const billsData = formatExportData(bills.value);
      const csvContent = convertToCSV(billsData);

      const now = new Date();
      const timestamp = now.toISOString().slice(0, 19).replace(/[T:]/g, '-');
      const filename = `bills-of-lading-${timestamp}.csv`;

      downloadCSV(csvContent, filename);

      showToast(t('billOfLading.toast.exportCsvSuccess', { count: bills.value.length }), 'success');
    }

  } catch (error: any) {
    console.error('Export failed:', error);
    showToast(t('billOfLading.toast.exportFailed', { message: error.message }), 'error');
  } finally {
    isExporting.value = false;
  }
};

// CSV Helper Functions
const convertToCSV = (data: any[]): string => {
  if (!data.length) return '';
  
  const headers = Object.keys(data[0]);
  const csvRows = [
    headers.join(','),
    ...data.map(row => 
      headers.map(header => {
        const value = row[header];
        if (typeof value === 'string' && (value.includes(',') || value.includes('"'))) {
          return `"${value.replace(/"/g, '""')}"`;
        }
        return value;
      }).join(',')
    )
  ];
  
  return csvRows.join('\n');
};

const downloadCSV = (csvContent: string, filename: string): void => {
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  
  if (link.download !== undefined) {
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', filename);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }
};

const isInvoicing = (bl: BillOfLading) => !!invoicing.value[bl.id];
const attemptGenerateInvoice = (bl: BillOfLading) => {
  if (invoiceConflict.value[bl.id]) {
    return;
  }
  handleGenerateInvoice(bl);
};

const handleGenerateInvoice = async (bl: BillOfLading) => {
  const last = recentlyInvoiced.value[bl.id];
  if (last && Date.now() - last < INVOICE_GUARD_MS) {
    return;
  }
  invoicing.value[bl.id] = true;
  try {
    const { invoiceId } = await invoiceService.generateDraft(
      bl.id,
      bl.shipper
    );
    showToast(t('billOfLading.toast.invoiceGenerated'), 'success');
    recentlyInvoiced.value[bl.id] = Date.now();
    if (invoiceId) {
      previewInvoice.value = {
        id: invoiceId,
        url: invoiceService.getInvoicePreviewUrl(invoiceId),
      };
    }
  } catch (e: any) {
    console.error(e);
    if (e?.response?.status === 409) {
      showToast(t('billOfLading.toast.invoiceAlreadyExists'), 'warning');
      invoiceConflict.value[bl.id] = true;
    } else {
      showToast(e?.response?.data || t('billOfLading.toast.invoiceFailed'), 'error');
    }
  } finally {
    invoicing.value[bl.id] = false;
  }
};

// Bulk Import Functions
const handleDragEnter = (e: DragEvent) => {
  e.preventDefault();
  dragCounter.value++;
  if (dragCounter.value === 1) {
    isDragOver.value = true;
  }
};

const handleDragLeave = (e: DragEvent) => {
  e.preventDefault();
  dragCounter.value--;
  if (dragCounter.value === 0) {
    isDragOver.value = false;
  }
};

const handleDragOver = (e: DragEvent) => {
  e.preventDefault();
};

const validateJsonFile = (file: File): Promise<any> => {
  return new Promise((resolve, reject) => {
    if (!file.name.toLowerCase().endsWith('.json')) {
      reject(t('billOfLading.validate.notJson'));
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      reject(t('billOfLading.validate.tooLarge'));
      return;
    }

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const content = JSON.parse(e.target?.result as string);
        if (!Array.isArray(content)) {
          reject(t('billOfLading.validate.notArray'));
          return;
        }
        resolve(content);
      } catch (error) {
        reject(t('billOfLading.validate.invalidJson'));
      }
    };
    reader.onerror = () => reject(t('billOfLading.validate.readError'));
    reader.readAsText(file);
  });
};

const handleDrop = async (e: DragEvent) => {
  e.preventDefault();
  isDragOver.value = false;
  dragCounter.value = 0;

  const files = Array.from(e.dataTransfer?.files || []);
  if (files.length === 0) return;

  const file = files[0];
  
  try {
    isUploading.value = true;
    uploadProgress.value = 10;

    const jsonData = await validateJsonFile(file);
    uploadProgress.value = 30;

    const result = await bulkImport(jsonData);
    uploadProgress.value = 100;

    uploadResult.value = {
      success: result.success || jsonData.length,
      errors: result.errors || []
    };
    showUploadResult.value = true;
    
    showToast(
      t('billOfLading.toast.importSuccess', { count: result.success || jsonData.length }),
      'success'
    );

  } catch (error: any) {
    console.error('Bulk import error:', error);
    uploadResult.value = {
      success: 0,
      errors: [error.message || t('billOfLading.validate.uploadFailed')]
    };
    showUploadResult.value = true;
    showToast(t('billOfLading.toast.importFailed', { message: error.message }), 'error');
  } finally {
    isUploading.value = false;
    uploadProgress.value = 0;
  }
};

const handleFileInputChange = async (e: Event) => {
  const target = e.target as HTMLInputElement;
  const files = target.files;
  if (!files || files.length === 0) return;

  const file = files[0];
  
  const fakeDropEvent = {
    preventDefault: () => {},
    dataTransfer: { files: [file] }
  } as any;
  
  await handleDrop(fakeDropEvent);
  
  target.value = '';
};

const triggerFileInput = () => {
  const fileInput = document.getElementById('bulk-import-input') as HTMLInputElement;
  fileInput?.click();
};

const closeUploadResult = () => {
  showUploadResult.value = false;
  uploadResult.value = null;
};

// Computed properties for UX
const dragOverlayClasses = computed(() => ({
  'opacity-0 pointer-events-none': !isDragOver.value && !isUploading.value,
  'opacity-100': isDragOver.value || isUploading.value,
  'bg-blue-50 border-blue-300': isDragOver.value && !isUploading.value,
  'bg-blue-100 border-blue-400': isUploading.value,
}));

const dropZoneContent = computed(() => {
  if (isUploading.value) {
    return {
      icon: Loader2,
      title: t('billOfLading.dropZone.uploading'),
      subtitle: t('billOfLading.dropZone.uploadingProgress', { pct: uploadProgress.value }),
      spinning: true
    };
  } else if (isDragOver.value) {
    return {
      icon: Cloud,
      title: t('billOfLading.dropZone.dropTitle'),
      subtitle: t('billOfLading.dropZone.dropSubtitle'),
      spinning: false
    };
  } else {
    return {
      icon: FileJson,
      title: t('billOfLading.dropZone.idleTitle'),
      subtitle: t('billOfLading.dropZone.idleSubtitle'),
      spinning: false
    };
  }
});

// Computed properties for UI state
const hasActiveFilters = computed(() => {
  return !!(searchQuery.value || activeFilters.status || activeFilters.shipper ||
            activeFilters.vessel || activeFilters.transportType);
});

const activeFilterChips = computed(() => {
  const chips: { key: string; label: string }[] = [];
  if (searchQuery.value)           chips.push({ key: 'search',        label: `"${searchQuery.value}"` });
  if (activeFilters.status)        chips.push({ key: 'status',        label: t('billOfLading.filter.statusChip', { value: activeFilters.status }) });
  if (activeFilters.shipper)       chips.push({ key: 'shipper',       label: t('billOfLading.filter.shipperChip', { value: activeFilters.shipper }) });
  if (activeFilters.vessel)        chips.push({ key: 'vessel',        label: t('billOfLading.filter.vesselChip', { value: activeFilters.vessel }) });
  if (activeFilters.transportType) chips.push({ key: 'transportType', label: t('billOfLading.filter.transportChip', { value: activeFilters.transportType }) });
  return chips;
});

function removeFilter(key: string) {
  if (key === 'search')        searchQuery.value = '';
  else if (key === 'status')        activeFilters.status = '';
  else if (key === 'shipper')       activeFilters.shipper = '';
  else if (key === 'vessel')        activeFilters.vessel = '';
  else if (key === 'transportType') activeFilters.transportType = '';
  handleSearch();
}

const blStats = computed(() => {
  const b = bills.value ?? [];
  return {
    total:     pagination.value?.totalItems ?? b.length,
    draft:     b.filter(x => x.status === 'Draft').length,
    final:     b.filter(x => x.status === 'Final').length,
    hazardous: b.filter(x => x.commodity?.hazardous).length,
  };
});
</script>

<template>
  <div 
    class="relative"
    @dragenter="handleDragEnter"
    @dragleave="handleDragLeave"
    @dragover="handleDragOver"
    @drop="handleDrop"
  >
    <!-- Hidden file input -->
    <input
      id="bulk-import-input"
      type="file"
      accept=".json"
      class="hidden"
      @change="handleFileInputChange"
    />

    <!-- Drag overlay -->
    <div 
      :class="[
        'absolute inset-0 z-50 transition-all duration-300 ease-in-out',
        'border-2 border-dashed rounded-lg',
        'flex flex-col items-center justify-center',
        'backdrop-blur-sm',
        dragOverlayClasses
      ]"
      @click="!isUploading ? triggerFileInput() : null"
    >
      <div class="text-center p-8 max-w-md mx-auto">
        <div class="mb-4">
          <component 
            :is="dropZoneContent.icon" 
            :class="[
              'h-16 w-16 mx-auto text-blue-500 transition-all duration-300',
              dropZoneContent.spinning ? 'animate-spin' : 'animate-bounce'
            ]"
          />
        </div>
        <h3 class="text-xl font-semibold text-gray-800 mb-2">
          {{ dropZoneContent.title }}
        </h3>
        <p class="text-gray-600 mb-4">
          {{ dropZoneContent.subtitle }}
        </p>
        <div v-if="isUploading" class="w-full bg-gray-200 rounded-full h-2 mb-4">
          <div 
            class="bg-blue-600 h-2 rounded-full transition-all duration-300 ease-out"
            :style="{ width: `${uploadProgress}%` }"
          ></div>
        </div>
        <div v-if="!isUploading" class="text-sm text-gray-500">
          {{ t('billOfLading.dropZone.hint') }}
        </div>
      </div>
    </div>

    <!-- Page Header -->
    <PageHeader
      :title="t('billOfLading.title')"
      :subtitle="t('billOfLading.subtitle')"
      :count="pagination?.totalItems ?? 0"
    >
      <template #actions>
        <button
          @click="triggerFileInput"
          class="hidden sm:inline-flex items-center px-3 py-2 border border-orange-300 text-sm font-medium rounded-lg text-orange-700 bg-orange-50 hover:bg-orange-100 transition-colors"
        >
          <Upload class="h-4 w-4 mr-2" />
          {{ t('billOfLading.button.importJson') }}
        </button>
        <button
          @click="exportToExcel"
          :disabled="isExporting || bills.length === 0"
          class="hidden sm:inline-flex items-center px-3 py-2 border border-slate-200 text-sm font-medium rounded-lg text-slate-700 bg-white hover:bg-slate-50 disabled:opacity-50 transition-colors"
        >
          <Loader2 v-if="isExporting" class="h-4 w-4 mr-2 animate-spin" />
          <Download v-else class="h-4 w-4 mr-2" />
          {{ isExporting ? t('billOfLading.button.exporting') : t('billOfLading.button.exportExcel') }}
        </button>
        <button
          @click="handleAdd"
          class="inline-flex items-center px-3 py-2 border border-transparent text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700 shadow-sm"
        >
          <Plus class="h-4 w-4 sm:mr-2" />
          <span class="hidden sm:inline">{{ t('billOfLading.button.newBl') }}</span>
        </button>
      </template>
      <template #kpi>
        <KpiCard :label="t('billOfLading.kpi.total')" :value="blStats.total" :sub-label="t('billOfLading.kpi.allStatuses')" color="slate">
          <template #icon>
            <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5"><path d="M9 2a1 1 0 000 2h2a1 1 0 100-2H9z"/><path fill-rule="evenodd" d="M4 5a2 2 0 012-2 3 3 0 003 3h2a3 3 0 003-3 2 2 0 012 2v11a2 2 0 01-2 2H6a2 2 0 01-2-2V5zm3 4a1 1 0 000 2h.01a1 1 0 100-2H7zm3 0a1 1 0 000 2h3a1 1 0 100-2h-3zm-3 4a1 1 0 100 2h.01a1 1 0 100-2H7zm3 0a1 1 0 100 2h3a1 1 0 100-2h-3z" clip-rule="evenodd"/></svg>
          </template>
        </KpiCard>
        <KpiCard :label="t('billOfLading.kpi.draft')" :value="blStats.draft" :sub-label="blStats.total ? t('billOfLading.kpi.pagePct', { pct: Math.round(blStats.draft/blStats.total*100) }) : ''" color="amber">
          <template #icon>
            <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5"><path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z"/></svg>
          </template>
        </KpiCard>
        <KpiCard :label="t('billOfLading.kpi.final')" :value="blStats.final" :sub-label="blStats.total ? t('billOfLading.kpi.pagePct', { pct: Math.round(blStats.final/blStats.total*100) }) : ''" color="emerald">
          <template #icon>
            <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
          </template>
        </KpiCard>
        <KpiCard :label="t('billOfLading.kpi.hazardous')" :value="blStats.hazardous" :sub-label="t('billOfLading.kpi.currentPage')" color="blue">
          <template #icon>
            <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5"><path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/></svg>
          </template>
        </KpiCard>
      </template>
    </PageHeader>

    <!-- List View -->
    <div v-if="!showForm" class="bg-white shadow rounded-lg mt-4">
      <!-- Header — mobile secondary actions + search -->
      <div class="px-4 py-3 border-b border-gray-200 space-y-3">

        <!-- Row 2: Search + Filters -->
        <SearchBar
          v-model="searchQuery"
          :placeholder="t('billOfLading.placeholder.search')"
          :filters-active="hasActiveFilters"
          :loading="loading"
          @toggle-filters="showFilters = !showFilters"
        >
          <template #actions>
            <button
              @click="handleRefresh"
              :disabled="loading"
              class="inline-flex items-center p-2 border border-gray-300 rounded-lg text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 flex-shrink-0"
              :aria-label="t('billOfLading.action.refresh')"
            >
              <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
            </button>
          </template>
        </SearchBar>

        <!-- Row 3 (mobile only): Bulk Import + Export — secondary actions -->
        <div class="flex items-center gap-2 sm:hidden">
          <button
            @click="triggerFileInput"
            class="flex-1 inline-flex items-center justify-center px-3 py-2 border border-orange-300 text-sm font-medium rounded-lg text-orange-700 bg-orange-50 hover:bg-orange-100 transition-colors"
          >
            <Upload class="h-4 w-4 mr-2" />
            {{ t('billOfLading.button.bulkImport') }}
          </button>
          <button
            @click="exportToExcel"
            :disabled="isExporting || bills.length === 0"
            class="flex-1 inline-flex items-center justify-center px-3 py-2 border border-gray-300 text-sm font-medium rounded-lg text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <Loader2 v-if="isExporting" class="h-4 w-4 mr-2 animate-spin" />
            <Download v-else class="h-4 w-4 mr-2" />
            {{ isExporting ? t('billOfLading.button.exportingShort') : t('common.export') }}
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
        type="bills" 
        @filter="handleFilter" 
        class="border-b border-gray-200"
      />

      <!-- Loading State -->
      <div v-if="loading" class="px-6 py-12 text-center">
        <RefreshCw class="h-8 w-8 animate-spin mx-auto text-gray-400 mb-4" />
        <p class="text-gray-500">{{ t('billOfLading.label.loadingBills') }}</p>
      </div>

      <!-- Error State -->
      <div v-else-if="error" class="px-6 py-12 text-center">
        <div class="text-red-500 mb-4">
          <X class="h-8 w-8 mx-auto mb-2" />
          <p>{{ t('billOfLading.label.errorLoadingBills') }} {{ error }}</p>
        </div>
        <button
          @click="handleRefresh"
          class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          {{ t('billOfLading.button.tryAgain') }}
        </button>
      </div>

      <!-- Table desktop + mobile (one DataTable) -->
      <div v-else>
        <div class="flex justify-end pb-2">
          <ColumnPicker
            :toggleable="bolCols.toggleable.value"
            :hidden-count="bolCols.hiddenCount.value"
            :is-visible="bolCols.isVisible"
            :toggle="bolCols.toggle"
            :reset="bolCols.reset"
            @reset="bolCols.reset"
          />
        </div>

        <DataTable
          :rows="bills"
          :columns="dataTableColumns.filter(c => bolCols.isVisible(c.key))"
          row-key="id"
          :loading="loading"
          :empty="{ title: t('billOfLading.empty.title'), description: t('billOfLading.empty.description') }"
        >
          <template #cell-connaissement="{ row }">
            <div class="space-y-0.5">
              <div class="flex items-center gap-2">
                <component :is="transportIcon((row as BillOfLading).transportType)" class="w-4 h-4 text-slate-500" />
                <span class="font-mono font-semibold text-sm text-slate-900">{{ (row as BillOfLading).blNumber }}</span>
                <StatusBadge :status="(row as BillOfLading).status" />
              </div>
              <div v-if="(row as BillOfLading).portOfLoading || (row as BillOfLading).portOfDischarge"
                   class="text-xs text-slate-500">
                {{ (row as BillOfLading).portOfLoading || '—' }}
                <span class="mx-1">→</span>
                {{ (row as BillOfLading).portOfDischarge || '—' }}
              </div>
            </div>
          </template>

          <template #cell-vessel="{ row }">
            {{ (row as BillOfLading).transportSnapshot?.vesselName ?? '—' }}
          </template>

          <template #cell-eta="{ row }">
            {{ (row as BillOfLading).transportSnapshot?.eta
                ? formatDate((row as BillOfLading).transportSnapshot!.eta!)
                : '—' }}
          </template>

          <template #cell-etd="{ row }">
            {{ (row as BillOfLading).transportSnapshot?.etd
                ? formatDate((row as BillOfLading).transportSnapshot!.etd!)
                : '—' }}
          </template>

          <template #cell-containerCount="{ row }">
            {{ getContainerCount(row as any) }}
          </template>

          <template #cell-customsRollup="{ row }">
            <StatusBadge v-if="getCustomsRollup(row as any)"
                         :status="getCustomsRollup(row as any)!" />
            <span v-else class="text-slate-400">—</span>
          </template>

          <template #cell-hazmat="{ row }">
            <span v-if="getHazmatFlag(row as any)"
                  class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-red-50 text-red-700 border border-red-200">
              <AlertTriangle class="w-3 h-3" /> HAZ
            </span>
            <span v-else class="text-slate-300">—</span>
          </template>

          <template #cell-shipper="{ row }">
            {{ (row as BillOfLading).shipper ?? '—' }}
          </template>

          <template #cell-consignee="{ row }">
            {{ (row as BillOfLading).consignee ?? '—' }}
          </template>

          <template #cell-voyage="{ row }">
            {{ (row as BillOfLading).voyage ?? '—' }}
          </template>

          <template #cell-totalWeight="{ row }">
            {{ formatWeight(getTotalWeight(row as any)) }}
          </template>

          <template #row-actions="{ row }">
            <div class="flex items-center justify-end gap-1">
              <button
                v-if="!invoiceConflict[(row as BillOfLading).id]"
                @click="attemptGenerateInvoice(row as BillOfLading)"
                :disabled="isInvoicing(row as BillOfLading)"
                class="inline-flex items-center p-1.5 rounded text-green-600 hover:bg-green-50 hover:text-green-800 disabled:opacity-40"
                :title="t('billOfLading.action.generateInvoice')"
              >
                <Loader2 v-if="isInvoicing(row as BillOfLading)" class="h-4 w-4 animate-spin" />
                <Receipt v-else class="h-4 w-4" />
              </button>
              <button
                @click="handleEdit(row as BillOfLading)"
                class="inline-flex items-center p-1.5 rounded text-blue-600 hover:bg-blue-50"
                :title="t('billOfLading.action.edit')"
              >
                <Pencil class="h-4 w-4" />
              </button>
              <button
                v-if="(row as BillOfLading).status === 'Draft'"
                @click="handleDelete(row as BillOfLading)"
                class="inline-flex items-center p-1.5 rounded text-red-500 hover:bg-red-50"
                :title="t('billOfLading.action.delete')"
              >
                <Trash2 class="h-4 w-4" />
              </button>
            </div>
          </template>
        </DataTable>
      </div>

      <!-- Pagination -->
      <Pagination
        v-if="!loading && bills.length > 0 && pagination"
        :pagination="pagination"
        @page-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />
    </div>

    <!-- Form View -->
    <BillOfLadingForm
      v-else
      :edit-mode="!!editingBL"
      :initial-data="editingBL"
      @submit="handleFormSubmit"
      @cancel="handleFormCancel"
    />

    <!-- Upload Result Modal -->
    <Teleport to="body">
      <div v-if="showUploadResult && uploadResult" class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50" @click.self="showUploadResult = false">
        <div class="bg-white rounded-lg p-6 max-w-md w-full mx-4">
          <div class="text-center">
            <div class="mb-4">
              <CheckCircle v-if="uploadResult.errors.length === 0" class="mx-auto h-12 w-12 text-green-500" />
              <AlertCircle v-else class="mx-auto h-12 w-12 text-yellow-500" />
            </div>
            
            <h3 class="text-lg font-medium text-gray-900 mb-4">
              {{ uploadResult.errors.length === 0 ? t('billOfLading.importModal.titleCompleted') : t('billOfLading.importModal.titleResults') }}
            </h3>

            <div class="bg-gray-50 rounded-lg p-4 mb-4">
              <div class="flex justify-between items-center mb-2">
                <span class="text-sm font-medium text-gray-700">{{ t('billOfLading.importModal.successfullyImported') }}</span>
                <span class="text-sm font-bold text-green-600">{{ uploadResult.success }}</span>
              </div>

              <div v-if="uploadResult.errors.length > 0" class="flex justify-between items-center">
                <span class="text-sm font-medium text-gray-700">{{ t('billOfLading.importModal.errors') }}</span>
                <span class="text-sm font-bold text-red-600">{{ uploadResult.errors.length }}</span>
              </div>
            </div>

            <div v-if="uploadResult.errors.length > 0" class="text-left mb-4">
              <h4 class="text-sm font-medium text-gray-700 mb-2">{{ t('billOfLading.importModal.errorDetails') }}</h4>
              <div class="bg-red-50 border border-red-200 rounded-md p-3 max-h-32 overflow-y-auto">
                <ul class="text-xs text-red-700 space-y-1">
                  <li v-for="error in uploadResult.errors.slice(0, 5)" :key="error">
                    • {{ error }}
                  </li>
                  <li v-if="uploadResult.errors.length > 5" class="font-medium">
                    {{ t('billOfLading.importModal.moreErrors', { count: uploadResult.errors.length - 5 }) }}
                  </li>
                </ul>
              </div>
            </div>

            <button
              @click="closeUploadResult"
              class="w-full px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              {{ t('common.close') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Items Modal -->
    <Teleport to="body">
      <div v-if="showItemsModal && selectedBL" class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50" @click.self="showItemsModal = false">
        <div class="bg-white rounded-lg p-6 max-w-2xl w-full">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-medium text-gray-900">
              {{ t('billOfLading.itemsModal.title', { blNumber: selectedBL.blNumber }) }}
            </h3>
            <button
              @click="showItemsModal = false"
              class="text-gray-400 hover:text-gray-500"
              :aria-label="t('common.close')"
            >
              <X class="h-6 w-6" />
            </button>
          </div>

          <div class="mt-4">
            <h4 class="text-sm font-medium text-gray-700 mb-2">{{ t('billOfLading.itemsModal.commodityDetails') }}</h4>
            <div class="bg-gray-50 p-4 rounded-lg">
              <dl class="grid grid-cols-2 gap-4">
                <div>
                  <dt class="text-sm font-medium text-gray-500">{{ t('billOfLading.field.description') }}</dt>
                  <dd class="mt-1 text-sm text-gray-900">{{ selectedBL.commodity.description }}</dd>
                </div>
                <div>
                  <dt class="text-sm font-medium text-gray-500">{{ t('billOfLading.field.weight') }}</dt>
                  <dd class="mt-1 text-sm text-gray-900">{{ selectedBL.commodity.weightKg }} kg</dd>
                </div>
                <div>
                  <dt class="text-sm font-medium text-gray-500">{{ t('billOfLading.field.volume') }}</dt>
                  <dd class="mt-1 text-sm text-gray-900">{{ selectedBL.commodity.volumeM3 }} m³</dd>
                </div>
                <div>
                  <dt class="text-sm font-medium text-gray-500">{{ t('billOfLading.field.packages') }}</dt>
                  <dd class="mt-1 text-sm text-gray-900">{{ selectedBL.commodity.packagesNumber }}</dd>
                </div>
              </dl>
            </div>
          </div>

          <div class="mt-6">
            <div class="flex justify-between items-center mb-4">
              <h4 class="text-sm font-medium text-gray-700">{{ t('billOfLading.itemsModal.linkedItems') }}</h4>
              <button class="inline-flex items-center text-sm text-blue-600 hover:text-blue-900">
                <Link class="h-4 w-4 mr-1" />
                {{ t('billOfLading.itemsModal.linkNewItem') }}
              </button>
            </div>
            
            <div class="bg-white shadow overflow-hidden border border-gray-200 sm:rounded-lg">
              <table class="min-w-full divide-y divide-gray-200">
                <thead class="bg-gray-50">
                  <tr>
                    <th v-for="header in [t('billOfLading.itemsModal.colType'), t('billOfLading.itemsModal.colNumber'), t('billOfLading.itemsModal.colStatus'), t('billOfLading.itemsModal.colActions')]"
                        :key="header"
                        class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
                    >
                      {{ header }}
                    </th>
                  </tr>
                </thead>
                <tbody class="bg-white divide-y divide-gray-200">
                  <tr v-for="item in selectedBL.items ?? []" :key="item.id">
                    <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {{ item.type }}
                    </td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {{ item.itemNumber }}
                    </td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {{ item.status }}
                    </td>
                    <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                      <button class="text-blue-600 hover:text-blue-900" :aria-label="t('billOfLading.action.view')">
                        <Eye class="h-5 w-5" />
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="mt-6 flex justify-end">
            <Button variant="secondary" @click="showItemsModal = false">{{ t('common.close') }}</Button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Delete Confirmation Modal -->
    <Teleport to="body">
      <div v-if="showDeleteConfirm" class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50" @click.self="showDeleteConfirm = false">
        <div class="bg-white rounded-lg p-6 max-w-md w-full">
          <div class="text-center">
            <FileText class="mx-auto h-12 w-12 text-red-500" />
            <h3 class="mt-4 text-lg font-medium text-gray-900">{{ t('billOfLading.confirm.deleteTitle') }}</h3>
            <p class="mt-2 text-sm text-gray-500">
              {{ t('billOfLading.confirm.deleteBody', { blNumber: blToDelete?.blNumber }) }}
            </p>
          </div>
          <div class="mt-6 flex justify-end space-x-3">
            <Button variant="secondary" @click="showDeleteConfirm = false">{{ t('common.cancel') }}</Button>
            <Button variant="danger" @click="confirmDelete">{{ t('common.delete') }}</Button>
          </div>
        </div>
      </div>
    </Teleport>

    <ToastNotification :toast="toast" @dismiss="toast = null" />
    
    <InvoicePreview
      v-if="previewInvoice"
      :invoice-id="previewInvoice.id"
      :preview-url="previewInvoice.url"
      status="Draft"
      @close="previewInvoice = null"
    />
  </div>
</template>

<style scoped>
/* Custom animations for smooth transitions */
.animate-bounce {
  animation: bounce 2s infinite;
}

@keyframes bounce {
  0%, 20%, 50%, 80%, 100% {
    transform: translateY(0);
  }
  40% {
    transform: translateY(-10px);
  }
  60% {
    transform: translateY(-5px);
  }
}

/* Smooth drag overlay transitions */
.drag-overlay-enter-active,
.drag-overlay-leave-active {
  transition: all 0.3s ease-in-out;
}

.drag-overlay-enter-from,
.drag-overlay-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

/* Progress bar animation */
.progress-bar-fill {
  transition: width 0.5s ease-out;
}

/* Hover effects for better interactivity */
.upload-zone:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1);
}

/* Success/error state animations */
.result-icon {
  animation: popIn 0.5s ease-out;
}

@keyframes popIn {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  50% {
    transform: scale(1.1);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>