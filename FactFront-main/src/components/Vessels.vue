<script setup lang="ts">
import { ref, computed, onBeforeMount } from "vue";
import { useI18n } from "vue-i18n";
import { Vessel } from "../types/vessel";
import {
  Filter,
  Download,
  Plus,
  Pencil,
  Trash2,
  Ship,
  Flag,
  Building,
  X,
  WifiOff,
  RefreshCw,
} from "lucide-vue-next";
import AdvancedFilter from "./AdvancedFilter.vue";
import VesselForm from "./VesselForm.vue";
import SearchInput from "./ui/SearchInput.vue";

import { useVessel } from "../composables/use.vessel";
import { useVesselAis } from "../composables/use.vessel.ais";
import type { AisStatus } from "../types/ais";

const { t, locale } = useI18n();

// Instance du composable pour la gestion de la liste
const { vessels, getVessels, deleteVessel } = useVessel();
const { snapshots, health, statusFor, loadSnapshotsForVessels, getHealth } = useVesselAis();

async function refresh() {
  await getVessels();
  await loadSnapshotsForVessels(vessels.value);
  await getHealth();
}

onBeforeMount(async () => {
  await refresh();
});

// État du composant
const showForm = ref(false);
const editingVessel = ref<Vessel | null>(null);
const showDeleteConfirm = ref(false);
const vesselToDelete = ref<Vessel | null>(null);
const searchQuery = ref("");

// Selection state (used for the "Export selected" action below).
const selectedIds = ref<Set<string>>(new Set());

// Filtrage et recherche — every field is normalized to "" so a missing/null
// operator (or any other column) cannot throw .toLowerCase() and silently
// blackhole the whole filter.
const filteredVessels = computed(() => {
  if (!searchQuery.value.trim()) {
    return vessels.value;
  }
  const query = searchQuery.value.toLowerCase();
  const norm = (s: unknown) => (s == null ? '' : String(s)).toLowerCase();
  return vessels.value.filter((vessel) =>
    norm(vessel.name).includes(query) ||
    norm(vessel.imoNumber).includes(query) ||
    norm(vessel.callSign).includes(query) ||
    norm(vessel.flag).includes(query) ||
    norm(vessel.owner).includes(query) ||
    norm(vessel.operator).includes(query) ||
    norm(vessel.vesselType).includes(query)
  );
});

function toggleSelect(id: string | undefined) {
  if (!id) return;
  const next = new Set(selectedIds.value);
  if (next.has(id)) next.delete(id); else next.add(id);
  selectedIds.value = next;
}

const allFilteredSelected = computed(() => {
  const ids = filteredVessels.value.map((v) => v.id).filter(Boolean) as string[];
  return ids.length > 0 && ids.every((id) => selectedIds.value.has(id));
});

function toggleSelectAllFiltered() {
  const ids = filteredVessels.value.map((v) => v.id).filter(Boolean) as string[];
  if (allFilteredSelected.value) {
    const next = new Set(selectedIds.value);
    ids.forEach((id) => next.delete(id));
    selectedIds.value = next;
  } else {
    const next = new Set(selectedIds.value);
    ids.forEach((id) => next.add(id));
    selectedIds.value = next;
  }
}

const tableHeaders = computed(() => [
  t('vessels.column.vesselName'),
  t('vessels.column.imoNumber'),
  t('vessels.column.callSign'),
  t('vessels.column.flag'),
  t('vessels.column.ownerOperator'),
  t('vessels.column.vesselType'),
  t('vessels.column.status'),
  t('vessels.column.position'),
  t('vessels.column.speed'),
  t('vessels.column.aisStatus'),
  t('vessels.column.lastSeen'),
  t('vessels.column.actions'),
]);

const handleFilter = (filters: any[]) => {
  // Implement filter logic here based on your AdvancedFilter component
};

const getStatusBadgeClasses = (status: string) => {
  const baseClasses = "px-2 py-1 text-xs font-medium rounded-full";
  switch (status.toUpperCase()) {
    case "ACTIVE":
      return `${baseClasses} bg-green-100 text-green-800`;
    case "INACTIVE":
      return `${baseClasses} bg-red-100 text-red-800`;
    default:
      return `${baseClasses} bg-gray-100 text-gray-800`;
  }
};

const formatPos = (s: { lat?: number | null; lon?: number | null }) =>
  s.lat != null && s.lon != null ? `${s.lat.toFixed(4)}, ${s.lon.toFixed(4)}` : '—';

const openSeaMapUrl = (s: { lat?: number | null; lon?: number | null }) =>
  s.lat != null && s.lon != null ? `https://map.openseamap.org/?zoom=12&lat=${s.lat}&lon=${s.lon}` : '#';

const formatRelative = (iso: string | null | undefined): string => {
  if (!iso) return '—';
  const diffMs = Date.now() - new Date(iso).getTime();
  const seconds = Math.max(0, Math.floor(diffMs / 1000));
  const fmt = new Intl.RelativeTimeFormat(locale.value || 'en', { numeric: 'auto' });
  if (seconds < 60) return fmt.format(-seconds, 'second');
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return fmt.format(-minutes, 'minute');
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return fmt.format(-hours, 'hour');
  const days = Math.floor(hours / 24);
  return fmt.format(-days, 'day');
};

const aisStatusLabel = (status: AisStatus): string => {
  switch (status) {
    case 'live': return t('vessels.ais.status.live');
    case 'lost': return t('vessels.ais.status.lost');
    case 'no-mmsi': return t('vessels.ais.status.noMmsi');
    default: return status;
  }
};

const aisStatusClasses = (status: AisStatus): string => {
  const base = 'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border';
  switch (status) {
    case 'live': return `${base} bg-emerald-50 text-emerald-700 border-emerald-200`;
    case 'lost': return `${base} bg-red-50 text-red-700 border-red-200`;
    case 'no-mmsi': return `${base} bg-slate-100 text-slate-600 border-slate-200`;
    default: return base;
  }
};

const snapshotFor = (vesselId: string | undefined) =>
  vesselId ? snapshots.value.get(vesselId) ?? null : null;

const handleAdd = () => {
  editingVessel.value = null;
  showForm.value = true;
};

const handleEdit = (vessel: Vessel) => {
  editingVessel.value = { ...vessel }; // Clone to avoid reference issues
  showForm.value = true;
};

const handleDelete = (vessel: Vessel) => {
  vesselToDelete.value = vessel;
  showDeleteConfirm.value = true;
};

const confirmDelete = async () => {
  if (!vesselToDelete.value?.id) {
    showDeleteConfirm.value = false;
    vesselToDelete.value = null;
    return;
  }
  // Use the shared $axios instance via the composable so the Bearer token + base URL
  // are applied consistently; the raw fetch() used previously bypassed both and
  // silently left the document in MongoDB.
  const ok = await deleteVessel(vesselToDelete.value.id);
  if (!ok) {
    console.error('Failed to delete vessel');
  } else {
    selectedIds.value.delete(vesselToDelete.value.id);
  }
  showDeleteConfirm.value = false;
  vesselToDelete.value = null;
};

const handleFormSubmit = async (formData: Vessel) => {
  try {
    if (editingVessel.value) {
      // Mode édition - mettre à jour dans la liste locale
      const index = vessels.value.findIndex((v) => v.id === editingVessel.value?.id);
      if (index !== -1) {
        vessels.value[index] = formData;
      }
    } else {
      // Mode création - ajouter à la liste locale
      vessels.value.push(formData);
    }

    // Optionnel : recharger depuis le serveur pour synchroniser
    await getVessels();

  } catch (error) {
    console.error('Error saving vessel:', error);
  }

  showForm.value = false;
  editingVessel.value = null;
};

const handleFormCancel = () => {
  showForm.value = false;
  editingVessel.value = null;
};

const handleExport = () => {
  // Export selection when the user has ticked rows, otherwise export the visible (filtered) list.
  const source = selectedIds.value.size > 0
    ? vessels.value.filter((v) => v.id && selectedIds.value.has(v.id))
    : filteredVessels.value;
  const headers = ['Name', 'IMO Number', 'Call Sign', 'Flag', 'Owner', 'Operator', 'Vessel Type', 'Status'];
  const csvContent = [
    headers.join(','),
    ...source.map((vessel) => [
      vessel.name,
      vessel.imoNumber,
      vessel.callSign,
      vessel.flag,
      vessel.owner,
      vessel.operator,
      vessel.vesselType,
      vessel.status
    ].map((field) => `"${(field ?? '').toString().replace(/"/g, '""')}"`).join(','))
  ].join('\n');

  const blob = new Blob([csvContent], { type: 'text/csv' });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `vessels_${new Date().toISOString().split('T')[0]}.csv`;
  a.click();
  window.URL.revokeObjectURL(url);
};
</script>

<template>
  <div>
    <!-- List View -->
    <div v-if="!showForm" class="bg-white shadow rounded-lg">
      <!-- Header -->
      <div class="px-4 py-3 border-b border-gray-200">
        <div class="flex flex-col sm:flex-row sm:justify-between sm:items-center gap-3">
          <h2 class="text-lg font-semibold text-gray-900">
            {{ t('vessels.title', { count: filteredVessels.length }) }}
          </h2>
          <div class="flex flex-wrap items-center gap-2">
            <SearchInput
              v-model="searchQuery"
              :placeholder="t('vessels.placeholder.searchVessels')"
              class="w-full sm:w-auto"
            />
            <button
              @click="handleExport"
              :title="t('common.export')"
              class="inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50"
            >
              <Download class="h-4 w-4 sm:mr-2" />
              <span class="hidden sm:inline">{{ t('common.export') }}</span>
            </button>
            <button
              data-test="vessels-refresh"
              @click="refresh"
              :title="t('vessels.button.refresh')"
              class="inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50"
            >
              <RefreshCw class="h-4 w-4 sm:mr-2" />
              <span class="hidden sm:inline">{{ t('vessels.button.refresh') }}</span>
            </button>
            <button
              @click="handleAdd"
              :title="t('vessels.button.addVessel')"
              class="inline-flex items-center px-3 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700"
            >
              <Plus class="h-4 w-4 sm:mr-2" />
              <span class="hidden sm:inline">{{ t('vessels.button.addVessel') }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- AIS health banner -->
      <div
        v-if="health && !health.connected"
        data-test="ais-offline-banner"
        class="mx-4 mt-3 px-3 py-2 bg-amber-50 border border-amber-200 text-amber-800 text-sm rounded"
      >
        <WifiOff class="inline h-4 w-4 mr-2 align-text-bottom" />
        {{ t('vessels.ais.banner.offline') }}
      </div>

      <!-- Advanced Filter -->
      <AdvancedFilter type="vessels" @filter="handleFilter" />

      <!-- Empty State -->
      <div v-if="filteredVessels.length === 0" class="text-center py-12">
        <Ship class="mx-auto h-12 w-12 text-gray-400" />
        <h3 class="mt-2 text-sm font-medium text-gray-900">{{ t('vessels.empty.title') }}</h3>
        <p class="mt-1 text-sm text-gray-500">
          {{ searchQuery ? t('vessels.empty.descriptionFiltered') : t('vessels.empty.description') }}
        </p>
        <div v-if="!searchQuery" class="mt-6">
          <button
            @click="handleAdd"
            class="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700"
          >
            <Plus class="h-4 w-4 mr-2" />
            {{ t('vessels.button.addVessel') }}
          </button>
        </div>
      </div>

      <!-- Table -->
      <div v-else class="overflow-x-auto">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-3 py-3 w-8">
                <input
                  type="checkbox"
                  :checked="allFilteredSelected"
                  :indeterminate.prop="!allFilteredSelected && selectedIds.size > 0"
                  @change="toggleSelectAllFiltered"
                  data-test="vessels-select-all"
                  :title="t('vessels.action.selectAll')"
                />
              </th>
              <th
                v-for="(header, idx) in tableHeaders"
                :key="header"
                :class="[
                  'px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider',
                  idx === tableHeaders.length - 1 ? 'sticky right-0 bg-gray-50 shadow-[-4px_0_4px_-4px_rgba(0,0,0,0.06)]' : '',
                ]"
              >
                {{ header }}
              </th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr
              v-for="vessel in filteredVessels"
              :key="vessel.id || vessel.name"
              :data-test="'row-' + vessel.id"
              class="group hover:bg-gray-50 transition-colors duration-150"
            >
              <td class="px-3 py-3 w-8">
                <input
                  type="checkbox"
                  :checked="vessel.id ? selectedIds.has(vessel.id) : false"
                  :disabled="!vessel.id"
                  @change="toggleSelect(vessel.id)"
                  :data-test="'vessels-select-' + vessel.id"
                />
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <div class="flex items-center">
                  <Ship class="h-5 w-5 text-gray-400 mr-2 flex-shrink-0" />
                  <div class="text-sm font-medium text-gray-900">{{ vessel.name }}</div>
                </div>
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                {{ vessel.imoNumber }}
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                {{ vessel.callSign }}
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <div class="flex items-center">
                  <Flag class="h-4 w-4 text-gray-400 mr-2 flex-shrink-0" />
                  <span class="text-sm text-gray-900">{{ vessel.flag }}</span>
                </div>
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <div class="flex items-center">
                  <Building class="h-4 w-4 text-gray-400 mr-2 flex-shrink-0" />
                  <div>
                    <div class="text-sm text-gray-900">{{ vessel.owner }}</div>
                    <div v-if="vessel.operator && vessel.operator !== vessel.owner"
                         class="text-sm text-gray-500">
                      {{ vessel.operator }}
                    </div>
                  </div>
                </div>
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                {{ vessel.vesselType }}
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <span :class="getStatusBadgeClasses(vessel.status)">
                  {{ vessel.status }}
                </span>
              </td>
              <!-- Position -->
              <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                <template v-if="snapshotFor(vessel.id)">
                  <a
                    :href="openSeaMapUrl(snapshotFor(vessel.id)!)"
                    target="_blank"
                    rel="noopener"
                    class="text-blue-600 hover:underline"
                  >{{ formatPos(snapshotFor(vessel.id)!) }}</a>
                </template>
                <template v-else>—</template>
              </td>
              <!-- Speed -->
              <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                <template v-if="snapshotFor(vessel.id)?.sog != null">{{ snapshotFor(vessel.id)!.sog!.toFixed(1) }} kn</template>
                <template v-else>—</template>
              </td>
              <!-- AIS Status badge -->
              <td class="px-4 py-3 whitespace-nowrap">
                <span :class="aisStatusClasses(statusFor(vessel))">{{ aisStatusLabel(statusFor(vessel)) }}</span>
              </td>
              <!-- Last seen -->
              <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-500">
                {{ formatRelative(snapshotFor(vessel.id)?.lastSeen) }}
              </td>
              <td class="sticky right-0 bg-white group-hover:bg-gray-50 transition-colors duration-150 px-4 py-3 whitespace-nowrap text-right text-sm font-medium shadow-[-4px_0_4px_-4px_rgba(0,0,0,0.06)]">
                <div class="flex items-center justify-end space-x-2">
                  <button
                    @click="handleEdit(vessel)"
                    class="text-blue-600 hover:text-blue-900 p-1 rounded hover:bg-blue-50 transition-colors duration-150"
                    :title="t('vessels.aria.editVessel')"
                  >
                    <Pencil class="h-4 w-4" />
                  </button>
                  <button
                    @click="handleDelete(vessel)"
                    class="text-red-600 hover:text-red-900 p-1 rounded hover:bg-red-50 transition-colors duration-150"
                    :title="t('vessels.aria.deleteVessel')"
                  >
                    <Trash2 class="h-4 w-4" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination could be added here if needed -->
    </div>

    <!-- Form View -->
    <VesselForm
      v-else
      :edit-mode="!!editingVessel"
      :initial-data="editingVessel"
      @submit="handleFormSubmit"
      @cancel="handleFormCancel"
    />

    <!-- Delete Confirmation Modal -->
    <Teleport to="body">
      <div
        v-if="showDeleteConfirm"
        class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50 p-4"
        @click.self="showDeleteConfirm = false"
      >
        <div class="bg-white rounded-lg p-6 max-w-md w-full shadow-xl">
          <div class="text-center">
            <div class="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100">
              <Ship class="h-6 w-6 text-red-600" />
            </div>
            <h3 class="mt-4 text-lg font-medium text-gray-900">{{ t('vessels.confirm.deleteTitle') }}</h3>
            <p class="mt-2 text-sm text-gray-500">
              {{ t('vessels.confirm.deleteBody', { name: vesselToDelete?.name }) }}
            </p>
          </div>
          <div class="mt-6 flex justify-end space-x-3">
            <button
              @click="showDeleteConfirm = false"
              class="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              @click="confirmDelete"
              class="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500"
            >
              {{ t('common.delete') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* Animations pour les transitions */
.transition-colors {
  transition-property: color, background-color, border-color;
  transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1);
}

/* Amélioration de l'accessibilité pour les boutons d'action */
button:focus {
  outline: 2px solid transparent;
  outline-offset: 2px;
}

/* Style pour la recherche */
input[type="text"]:focus {
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}
</style>
