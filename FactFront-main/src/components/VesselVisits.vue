<script setup lang="ts">
import { ref, computed } from "vue";
import { useI18n } from "vue-i18n";
import { VesselVisit } from "../types/vessel-visit";
import {
  FileDown,
  ArrowRight,
  Edit,
  ClipboardList,
  Lock,
  BarChart2,
  Calendar,
  Plus,
  Eye,
  X,
} from "lucide-vue-next";
import AdvancedFilter from "./AdvancedFilter.vue";
import VesselVisitForm from "./VesselVisitForm.vue";
import PageHeader from "./ui/PageHeader.vue";
import Button from "./ui/Button.vue";
import SearchInput from "./ui/SearchInput.vue";

import { useRouter } from "vue-router";
import { useVesselVisit } from "../composables/use.vessel-visit";
import { useToast } from "../composables/useToast";
import RecordVesselEventModal from "./RecordVesselEventModal.vue";

const { t } = useI18n();
const { showToast } = useToast();
const router = useRouter();

const { visits, getVesselVisits } = useVesselVisit();
const previewVisit = ref<VesselVisit | null>(null);
const selectedVisit = ref<VesselVisit | null>(null);
const showModal = ref(false);
const showForm = ref(false);
const searchQuery = ref('');
const isExporting = ref(false);
const recordEventOpen = ref(false);

const tableHeaders = computed(() => [
  t('vesselVisits.column.vesselName'),
  t('vesselVisits.column.visitRef'),
  t('vesselVisits.column.phase'),
  t('vesselVisits.column.service'),
  t('vesselVisits.column.polPod'),
  t('vesselVisits.column.etaAta'),
  t('vesselVisits.column.etdAtd'),
  t('vesselVisits.column.finalDest'),
  t('vesselVisits.column.actions'),
]);

const handleFilter = (_filters: any[]) => {
  // Implement filter logic here
};

const formatDateTime = (dateString: string | null) => {
  if (!dateString) return "-";
  return new Date(dateString).toLocaleString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
};

const getPhaseClasses = (phase: string) => {
  const baseClasses = "px-2 py-1 text-xs font-medium rounded-full";
  switch (phase) {
    case "Created":
      return `${baseClasses} bg-blue-100 text-blue-800`;
    case "Active":
      return `${baseClasses} bg-green-100 text-green-800`;
    case "Completed":
      return `${baseClasses} bg-gray-100 text-gray-800`;
    case "Canceled":
      return `${baseClasses} bg-red-100 text-red-800`;
    default:
      return baseClasses;
  }
};

const isSelected = (visit: VesselVisit) =>
  selectedVisit.value?.id != null && selectedVisit.value.id === visit.id;

const toggleSelection = (visit: VesselVisit) => {
  if (isSelected(visit)) {
    selectedVisit.value = null;
  } else {
    selectedVisit.value = visit;
  }
};

const handleNewVisit = () => {
  selectedVisit.value = null;
  showForm.value = true;
};

const handleEditDetails = () => {
  if (!selectedVisit.value) return;
  showForm.value = true;
};

function handleStatistics() {
  const query = selectedVisit.value?.id ? { visit: selectedVisit.value.id } : undefined;
  router.push({ path: '/vessels/statistics', query });
}

function handleRecordEvent() {
  if (!selectedVisit.value) return;
  recordEventOpen.value = true;
}

// Placeholder feedback for actions whose business workflow isn't wired up yet.
// Previously these buttons were silently inert; we surface a clear "coming soon"
// notice so testers know the click registered (TC-05).
function notImplemented(action: string) {
  if (!selectedVisit.value) return;
  // eslint-disable-next-line no-alert
  alert(t('vesselVisits.action.notImplemented', { action }));
}
function handleAdvanceVisit() { notImplemented(t('vesselVisits.button.advanceVisit')); }
function handleUpdateHolds()  { notImplemented(t('vesselVisits.button.updateHolds')); }
function handleExtractEvents() { notImplemented(t('vesselVisits.button.extractEvents')); }

function formatExportRows(visitList: VesselVisit[]) {
  return visitList.map((v) => ({
    Vessel: v.vesselId ? `${v.vesselName} (${v.vesselId})` : v.vesselName,
    'Visit Reference': v.visitReference,
    Phase: v.phase,
    Service: v.serviceName ? `${v.service} - ${v.serviceName}` : v.service,
    POL: v.pol,
    POD: v.pod,
    'ETA / ATA': v.ata ? `${formatDateTime(v.eta)} / ${formatDateTime(v.ata)}` : formatDateTime(v.eta),
    'ETD / ATD': v.atd ? `${formatDateTime(v.etd)} / ${formatDateTime(v.atd)}` : formatDateTime(v.etd),
    'Final Destination': v.finalDestination,
  }));
}

function convertToCsv(rows: Record<string, string | number | undefined | null>[]): string {
  if (rows.length === 0) return '';
  const headers = Object.keys(rows[0]);
  const escape = (v: unknown) => {
    if (v === null || v === undefined) return '';
    const s = String(v);
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const lines = [headers.map(escape).join(',')];
  for (const row of rows) {
    lines.push(headers.map((h) => escape(row[h])).join(','));
  }
  return lines.join('\n');
}

function downloadCsvBlob(csv: string, filename: string) {
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.setAttribute('href', url);
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

async function handleExport() {
  if (isExporting.value) return;
  if (!visits.value || visits.value.length === 0) {
    showToast(t('vesselVisits.toast.exportNoData'), 'warning');
    return;
  }
  isExporting.value = true;
  showToast(t('vesselVisits.toast.exportPreparing'), 'success');
  const timestamp = new Date().toISOString().slice(0, 10);
  try {
    const XLSX = await import('xlsx');
    const rows = formatExportRows(visits.value);
    const sheet = XLSX.utils.json_to_sheet(rows);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, sheet, 'Vessel Visits');
    XLSX.writeFile(workbook, `vessel-visits-${timestamp}.xlsx`);
    showToast(t('vesselVisits.toast.exportExcelSuccess', { count: visits.value.length }), 'success');
  } catch (xlsxError) {
    console.warn('XLSX export failed, falling back to CSV:', xlsxError);
    try {
      const rows = formatExportRows(visits.value);
      const csv = convertToCsv(rows);
      downloadCsvBlob(csv, `vessel-visits-${timestamp}.csv`);
      showToast(t('vesselVisits.toast.exportCsvSuccess', { count: visits.value.length }), 'success');
    } catch (csvError: any) {
      console.error('CSV fallback also failed:', csvError);
      showToast(t('vesselVisits.toast.exportFailed', { message: csvError?.message ?? String(csvError) }), 'error');
    }
  } finally {
    isExporting.value = false;
  }
}

// payload from VesselVisitForm's submit emit is intentionally ignored —
// we refetch to get server-canonical state (id, server-side defaults).
const handleFormSubmit = async (_data?: VesselVisit) => {
  await getVesselVisits();
  if (selectedVisit.value?.id) {
    selectedVisit.value =
      visits.value.find((v) => v.id === selectedVisit.value!.id) ?? null;
  }
  showForm.value = false;
};

// Cancel keeps the selection so the user can pick another action without re-clicking the row.
const handleFormCancel = () => {
  showForm.value = false;
};

const openPreview = (visit: VesselVisit) => {
  previewVisit.value = visit;
  showModal.value = true;
};
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <!-- List View -->
    <div v-if="!showForm">
      <PageHeader :title="t('nav.vesselVisits')">
        <template #actions>
          <SearchInput v-model="searchQuery" :placeholder="t('vesselVisits.placeholder.searchVisits')" />
          <Button @click="handleNewVisit">
            <Plus class="h-4 w-4" />
            {{ t('vesselVisits.button.newVisit') }}
          </Button>
        </template>
      </PageHeader>

      <div class="px-6 py-6 space-y-4 bg-white shadow rounded-lg mx-6">

      <!-- Actions Toolbar -->
      <div class="p-4 border-b border-gray-200">
        <div class="flex flex-wrap gap-2">
          <button
            data-test="toolbar-export"
            :disabled="isExporting"
            @click="handleExport"
            class="inline-flex items-center px-3 py-2 border border-gray-300 text-sm font-medium rounded-lg text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <FileDown class="mr-2" :size="18" />
            {{ t('common.export') }}
          </button>
          <button
            data-test="toolbar-advance-visit"
            :disabled="!selectedVisit"
            :aria-disabled="!selectedVisit"
            @click="handleAdvanceVisit"
            class="inline-flex items-center px-3 py-2 border border-gray-300 text-sm font-medium rounded-lg text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <ArrowRight class="mr-2" :size="18" />
            {{ t('vesselVisits.button.advanceVisit') }}
          </button>
          <button
            data-test="toolbar-edit-details"
            :disabled="!selectedVisit"
            :aria-disabled="!selectedVisit"
            @click="handleEditDetails"
            class="inline-flex items-center px-3 py-2 border border-gray-300 text-sm font-medium rounded-lg text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Edit class="mr-2" :size="18" />
            {{ t('vesselVisits.button.editDetails') }}
          </button>
          <button
            data-test="toolbar-record-event"
            :disabled="!selectedVisit"
            :aria-disabled="!selectedVisit"
            @click="handleRecordEvent"
            class="inline-flex items-center px-3 py-2 border border-gray-300 text-sm font-medium rounded-lg text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <ClipboardList class="mr-2" :size="18" />
            {{ t('vesselVisits.button.recordEvent') }}
          </button>
          <button
            data-test="toolbar-update-holds"
            :disabled="!selectedVisit"
            :aria-disabled="!selectedVisit"
            @click="handleUpdateHolds"
            class="inline-flex items-center px-3 py-2 border border-gray-300 text-sm font-medium rounded-lg text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Lock class="mr-2" :size="18" />
            {{ t('vesselVisits.button.updateHolds') }}
          </button>
          <button
            data-test="toolbar-statistics"
            @click="handleStatistics"
            class="inline-flex items-center px-3 py-2 border border-gray-300 text-sm font-medium rounded-lg text-gray-700 bg-white hover:bg-gray-50"
          >
            <BarChart2 class="mr-2" :size="18" />
            {{ t('vesselVisits.button.statistics') }}
          </button>
          <button
            data-test="toolbar-extract-events"
            :disabled="!selectedVisit"
            :aria-disabled="!selectedVisit"
            @click="handleExtractEvents"
            class="inline-flex items-center px-3 py-2 border border-gray-300 text-sm font-medium rounded-lg text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Calendar class="mr-2" :size="18" />
            {{ t('vesselVisits.button.extractEvents') }}
          </button>
        </div>
      </div>

      <!-- Advanced Filter -->
      <AdvancedFilter type="vessels" @filter="handleFilter" />

      <!-- Table -->
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
              v-for="visit in visits"
              :key="visit.id"
              :class="[
                'cursor-pointer',
                isSelected(visit)
                  ? 'bg-blue-50 ring-2 ring-blue-500 ring-inset'
                  : 'hover:bg-gray-50',
              ]"
              @click="toggleSelection(visit)"
            >
              <td class="px-4 py-3 whitespace-nowrap">
                <div class="text-sm font-medium text-gray-900">
                  {{ visit.vesselName }}
                </div>
                <div class="text-sm text-gray-500">{{ visit.vesselId }}</div>
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                {{ visit.visitReference }}
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <span :class="getPhaseClasses(visit.phase)">
                  {{ visit.phase }}
                </span>
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <div class="text-sm text-gray-900">{{ visit.service }}</div>
                <div class="text-sm text-gray-500">{{ visit.serviceName }}</div>
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <div class="text-sm text-gray-900">{{ t('vesselVisits.label.pol') }}: {{ visit.pol }}</div>
                <div class="text-sm text-gray-500">{{ t('vesselVisits.label.pod') }}: {{ visit.pod }}</div>
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <div class="text-sm text-gray-900">
                  {{ t('vesselVisits.label.eta') }}: {{ formatDateTime(visit.eta) }}
                </div>
                <div class="text-sm text-gray-500">
                  {{ t('vesselVisits.label.ata') }}: {{ formatDateTime(visit.ata) }}
                </div>
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <div class="text-sm text-gray-900">
                  {{ t('vesselVisits.label.etd') }}: {{ formatDateTime(visit.etd) }}
                </div>
                <div class="text-sm text-gray-500">
                  {{ t('vesselVisits.label.atd') }}: {{ formatDateTime(visit.atd) }}
                </div>
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                {{ visit.finalDestination }}
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-right text-sm font-medium">
                <button
                  data-test="row-preview"
                  @click.stop="openPreview(visit)"
                  class="text-blue-600 hover:text-blue-900 mr-3"
                  :aria-label="t('vesselVisits.aria.preview')"
                >
                  <Eye class="h-5 w-5" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Mobile card view -->
      <div class="sm:hidden">
        <div v-if="!visits || visits.length === 0" class="bg-white rounded-xl border border-slate-200 p-8 text-center">
          <p class="text-sm text-slate-500">{{ t('vesselVisits.empty.noVisits') }}</p>
        </div>
        <div v-else class="space-y-3">
          <div
            v-for="visit in visits"
            :key="visit.id"
            :class="[
              'bg-white rounded-xl border p-4 shadow-sm cursor-pointer',
              isSelected(visit)
                ? 'border-blue-500 ring-2 ring-blue-500'
                : 'border-slate-200',
            ]"
            @click="toggleSelection(visit)"
          >
            <div class="flex items-start justify-between gap-2 mb-1">
              <p class="font-semibold text-slate-900 text-sm">{{ visit.vesselName || '—' }}</p>
              <span v-if="visit.phase" :class="[
                'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border shrink-0',
                visit.phase === 'Active' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' :
                visit.phase === 'Created' ? 'bg-blue-50 text-blue-700 border-blue-200' :
                visit.phase === 'Completed' ? 'bg-slate-100 text-slate-600 border-slate-200' :
                'bg-red-50 text-red-700 border-red-200'
              ]">{{ visit.phase }}</span>
            </div>
            <p class="text-xs text-slate-500">{{ visit.visitReference || '' }}</p>
            <p class="text-xs text-slate-500">{{ t('vesselVisits.label.pol') }}: {{ visit.pol || '' }} · {{ t('vesselVisits.label.pod') }}: {{ visit.pod || '' }}</p>
            <div class="flex items-center justify-between text-xs text-slate-500 mt-1">
              <span>{{ t('vesselVisits.label.eta') }}: {{ visit.eta ? formatDateTime(visit.eta) : '—' }}</span>
              <span>{{ t('vesselVisits.label.etd') }}: {{ visit.etd ? formatDateTime(visit.etd) : '—' }}</span>
            </div>
          </div>
        </div>
      </div>
      </div>
    </div>

    <!-- Form View -->
    <VesselVisitForm
      v-else
      :edit-mode="!!selectedVisit"
      :initial-data="selectedVisit ?? undefined"
      @submit="handleFormSubmit"
      @cancel="handleFormCancel"
    />

    <!-- Preview Modal -->
    <Teleport to="body">
      <div
        v-if="showModal && previewVisit"
        class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50"
      >
        <div class="bg-white rounded-lg max-w-4xl w-full max-h-[90vh] overflow-hidden">
          <div
            class="px-4 py-3 border-b border-gray-200 flex justify-between items-center"
          >
            <h3 class="text-lg font-medium text-gray-900">
              {{ previewVisit.vesselName }} - {{ previewVisit.visitReference }}
            </h3>
            <button @click="showModal = false" class="text-gray-400 hover:text-gray-500" :aria-label="t('common.close')">
              <X class="h-6 w-6" />
            </button>
          </div>

          <div class="px-4 py-3 overflow-y-auto">
            <div class="grid grid-cols-2 gap-4">
              <template v-for="(value, key) in previewVisit" :key="key">
                <div v-if="key !== 'id'" class="col-span-1">
                  <label class="block text-sm font-medium text-gray-700">
                    {{ key.replace(/([A-Z])/g, " $1").trim() }}
                  </label>
                  <p class="mt-1 text-sm text-gray-900">
                    {{
                      typeof value === "string" && value.includes("T")
                        ? formatDateTime(value)
                        : value || "-"
                    }}
                  </p>
                </div>
              </template>
            </div>
          </div>

          <div class="px-4 py-3 border-t border-gray-200 flex justify-end space-x-3">
            <button
              @click="showModal = false"
              class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              {{ t('common.close') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <RecordVesselEventModal
      :open="recordEventOpen"
      :visit="selectedVisit"
      @close="recordEventOpen = false"
    />
  </div>
</template>
