<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
import { 
  Clock, 
  Calendar, 
  AlertCircle, 
  ArrowDownCircle, 
  ArrowUpCircle, 
  CircleDot,
  Filter,
  ChevronDown,
  ChevronUp,
  Eye,
  RefreshCw
} from 'lucide-vue-next';
import { Item, Lifecycle, Event } from '../types/item';
import { ItemService } from '../services/itemService';

const props = defineProps<{
  item: Item;
}>();

const itemService = ItemService.getInstance();

// State management
const loading = ref(false);
const statusFilter = ref('');
const sortField = ref<'startTime' | 'endTime' | 'duration' | 'status'>('startTime');
const sortDesc = ref(true);
const expandedRows = ref<Set<string>>(new Set());
const selectedLifecycleId = ref<string | null>(null);

// Filter and sort lifecycles
const filteredLifecycles = computed(() => {
  let cycles = props.item.lifeCycles || [];
  
  // Filter by status
  if (statusFilter.value) {
    cycles = cycles.filter(lc => lc.status === statusFilter.value);
  }
  
  // Sort
  cycles = cycles.slice().sort((a, b) => {
    let comparison = 0;
    
    switch (sortField.value) {
      case 'startTime':
        comparison = new Date(a.startTime || '').getTime() - new Date(b.startTime || '').getTime();
        break;
      case 'endTime':
        const aEnd = a.endTime ? new Date(a.endTime).getTime() : Date.now();
        const bEnd = b.endTime ? new Date(b.endTime).getTime() : Date.now();
        comparison = aEnd - bEnd;
        break;
      case 'duration':
        const aDuration = itemService.getLifecycleDuration(a);
        const bDuration = itemService.getLifecycleDuration(b);
        comparison = aDuration - bDuration;
        break;
      case 'status':
        comparison = (a.status || '').localeCompare(b.status || '');
        break;
    }
    
    return sortDesc.value ? -comparison : comparison;
  });
  
  return cycles;
});

// Helper functions
const formatDate = (dateString?: string) => {
  if (!dateString) return '—';
  return new Date(dateString).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const formatDuration = (lifecycle: Lifecycle) => {
  const duration = itemService.getLifecycleDuration(lifecycle);
  const days = Math.floor(duration / (1000 * 60 * 60 * 24));
  const hours = Math.floor((duration % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
  const minutes = Math.floor((duration % (1000 * 60 * 60)) / (1000 * 60));
  
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
};

const getStatusBadgeClasses = (status?: string) => {
  const baseClasses = 'px-2 py-1 text-xs font-medium rounded-full';
  switch (status) {
    case 'In Progress':
      return `${baseClasses} bg-blue-100 text-blue-800`;
    case 'Completed':
      return `${baseClasses} bg-green-100 text-green-800`;
    case 'Cancelled':
      return `${baseClasses} bg-red-100 text-red-800`;
    default:
      return `${baseClasses} bg-gray-100 text-gray-800`;
  }
};

const getEventIcon = (type?: string) => {
  switch (type) {
    case 'IN': return ArrowDownCircle;
    case 'OUT': return ArrowUpCircle;
    default: return CircleDot;
  }
};

const toggleRowExpansion = (lifecycleId: string) => {
  if (expandedRows.value.has(lifecycleId)) {
    expandedRows.value.delete(lifecycleId);
  } else {
    expandedRows.value.add(lifecycleId);
  }
};

const setSortField = (field: typeof sortField.value) => {
  if (sortField.value === field) {
    sortDesc.value = !sortDesc.value;
  } else {
    sortField.value = field;
    sortDesc.value = true;
  }
};

const getSortIcon = (field: string) => {
  if (sortField.value !== field) return null;
  return sortDesc.value ? ChevronDown : ChevronUp;
};

const refresh = () => {
  // In a real app, this would refetch lifecycle data
  loading.value = true;
  setTimeout(() => {
    loading.value = false;
  }, 500);
};

const sortFields = computed(() => [
  { key: 'startTime', label: t('itemLifecycleView.column.startDate') },
  { key: 'endTime', label: t('itemLifecycleView.column.endDate') },
  { key: 'duration', label: t('itemLifecycleView.column.duration') },
  { key: 'status', label: t('itemLifecycleView.column.status') },
]);
</script>

<template>
  <div class="space-y-4">
    <!-- Header with controls (consistent with ItemEventHistory) -->
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-semibold text-gray-900">{{ t('itemLifecycleView.title') }}</h3>
        <p class="text-sm text-gray-500 mt-1">
          {{ t('itemLifecycleView.lifecycleCount', { count: filteredLifecycles.length }) }}
        </p>
      </div>
      <div class="flex items-center space-x-3">
        <button
          @click="refresh"
          :disabled="loading"
          class="inline-flex items-center px-3 py-1.5 text-sm text-gray-600 hover:text-gray-900 disabled:opacity-50"
          :class="{ 'animate-spin': loading }"
        >
          <RefreshCw class="h-4 w-4 mr-1" />
          {{ t('itemLifecycleView.refresh') }}
        </button>
      </div>
    </div>

    <!-- Filters (consistent with ItemEventHistory) -->
    <div class="flex items-center space-x-4 pb-4 border-b border-gray-200">
      <div class="flex items-center space-x-2">
        <Filter class="h-4 w-4 text-gray-400" />
        <select 
          v-model="statusFilter" 
          class="text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
        >
          <option value="">{{ t('payments.filter.allStatuses') }}</option>
          <option value="In Progress">In Progress</option>
          <option value="Completed">Completed</option>
          <option value="Cancelled">Cancelled</option>
        </select>
      </div>
      
      <div class="text-sm text-gray-500">
        {{ t('itemLifecycleView.sortBy') }}
        <button
          v-for="field in sortFields"
          :key="field.key"
          @click="setSortField(field.key)"
          class="ml-2 text-blue-600 hover:text-blue-800 hover:underline"
          :class="{ 'font-medium': sortField === field.key }"
        >
          {{ field.label }}
          <component 
            v-if="getSortIcon(field.key)" 
            :is="getSortIcon(field.key)" 
            class="inline h-3 w-3 ml-1"
          />
        </button>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="text-center py-8">
      <RefreshCw class="h-6 w-6 animate-spin mx-auto text-gray-400 mb-2" />
      <p class="text-sm text-gray-500">{{ t('itemLifecycleView.loading') }}</p>
    </div>

    <!-- Empty state -->
    <div v-else-if="!filteredLifecycles.length" class="text-center py-8">
      <Calendar class="h-12 w-12 mx-auto text-gray-400 mb-4" />
      <p class="text-sm text-gray-500">
        {{ statusFilter ? t('itemLifecycleView.empty.filtered') : t('itemLifecycleView.empty.noLifecycles') }}
      </p>
      <button
        v-if="statusFilter"
        @click="statusFilter = ''"
        class="mt-2 text-sm text-blue-600 hover:underline"
      >
        {{ t('itemLifecycleView.clearFilter') }}
      </button>
    </div>

    <!-- Lifecycle table -->
    <div v-else class="bg-white border border-gray-200 rounded-lg overflow-hidden">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="w-8 px-4 py-3"></th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              {{ t('itemLifecycleView.column.status') }}
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              {{ t('itemLifecycleView.column.startDate') }}
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              {{ t('itemLifecycleView.column.endDate') }}
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              {{ t('itemLifecycleView.column.duration') }}
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              {{ t('itemLifecycleView.column.events') }}
            </th>
            <th class="w-12 px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <template v-for="lifecycle in filteredLifecycles" :key="lifecycle.id">
            <!-- Main lifecycle row -->
            <tr class="hover:bg-gray-50">
              <td class="px-4 py-3">
                <button
                  v-if="lifecycle.events && lifecycle.events.length > 0"
                  @click="toggleRowExpansion(lifecycle.id!)"
                  class="p-1 hover:bg-gray-100 rounded"
                >
                  <ChevronDown 
                    v-if="!expandedRows.has(lifecycle.id!)"
                    class="h-4 w-4 text-gray-400"
                  />
                  <ChevronUp 
                    v-else
                    class="h-4 w-4 text-gray-400"
                  />
                </button>
              </td>
              <td class="px-6 py-3 whitespace-nowrap">
                <span :class="getStatusBadgeClasses(lifecycle.status)">
                  {{ lifecycle.status || 'Unknown' }}
                </span>
              </td>
              <td class="px-6 py-3 whitespace-nowrap text-sm text-gray-900">
                {{ formatDate(lifecycle.startTime) }}
              </td>
              <td class="px-6 py-3 whitespace-nowrap text-sm text-gray-900">
                {{ formatDate(lifecycle.endTime) }}
              </td>
              <td class="px-6 py-3 whitespace-nowrap text-sm text-gray-500">
                {{ formatDuration(lifecycle) }}
              </td>
              <td class="px-6 py-3 whitespace-nowrap text-sm text-gray-500">
                <span class="inline-flex items-center">
                  <Clock class="h-4 w-4 mr-1" />
                  {{ t('itemLifecycleView.eventCount', { count: lifecycle.events?.length || 0 }) }}
                </span>
              </td>
              <td class="px-4 py-3">
                <button
                  v-if="lifecycle.events && lifecycle.events.length > 0"
                  @click="selectedLifecycleId = lifecycle.id!"
                  class="p-1 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded"
                  :title="t('itemLifecycleView.label.viewEvents')"
                >
                  <Eye class="h-4 w-4" />
                </button>
              </td>
            </tr>

            <!-- Expanded events row -->
            <tr v-if="expandedRows.has(lifecycle.id!) && lifecycle.events?.length">
              <td colspan="7" class="px-4 py-3 bg-gray-50">
                <div class="space-y-2 max-h-48 overflow-y-auto">
                  <div class="text-xs font-medium text-gray-500 mb-3">
                    {{ t('itemLifecycleView.eventsTimeline') }}
                  </div>
                  <div 
                    v-for="event in lifecycle.events" 
                    :key="event.id"
                    class="flex items-center space-x-3 py-2 px-3 bg-white rounded border"
                  >
                    <component 
                      :is="getEventIcon(event.eventType)"
                      class="h-4 w-4 flex-shrink-0"
                      :class="{
                        'text-green-600': event.eventType === 'IN',
                        'text-red-600': event.eventType === 'OUT',
                        'text-blue-600': event.eventType === 'INTERMEDIATE'
                      }"
                    />
                    <div class="flex-1 min-w-0">
                      <div class="flex items-center justify-between">
                        <span class="text-sm font-medium text-gray-900">
                          {{ t('itemLifecycleView.eventTypeLabel', { type: event.eventType }) }}
                        </span>
                        <span class="text-xs text-gray-500">
                          {{ formatDate(event.timestamp) }}
                        </span>
                      </div>
                      <p v-if="event.notes" class="text-xs text-gray-600 mt-1 truncate">
                        {{ event.notes }}
                      </p>
                      <p v-if="event.location" class="text-xs text-gray-500">
                        {{ t('itemLifecycleView.eventLocation', { location: event.location }) }}
                      </p>
                    </div>
                  </div>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <!-- Summary statistics (moved to bottom for better flow) -->
    <div v-if="filteredLifecycles.length > 0" class="bg-gray-50 rounded-lg p-4">
      <h4 class="text-sm font-medium text-gray-900 mb-3">{{ t('itemLifecycleView.summary') }}</h4>
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-4">
        <div class="text-center">
          <div class="text-lg font-semibold text-gray-900">
            {{ filteredLifecycles.length }}
          </div>
          <div class="text-xs text-gray-500">{{ t('itemLifecycleView.stat.totalLifecycles') }}</div>
        </div>
        <div class="text-center">
          <div class="text-lg font-semibold text-gray-900">
            {{ filteredLifecycles.filter(lc => lc.status === 'Completed').length }}
          </div>
          <div class="text-xs text-gray-500">{{ t('itemLifecycleView.stat.completed') }}</div>
        </div>
        <div class="text-center">
          <div class="text-lg font-semibold text-gray-900">
            {{ filteredLifecycles.filter(lc => lc.status === 'In Progress').length }}
          </div>
          <div class="text-xs text-gray-500">{{ t('itemLifecycleView.stat.inProgress') }}</div>
        </div>
        <div class="text-center">
          <div class="text-lg font-semibold text-gray-900">
            {{ formatDuration({ 
              startTime: '', 
              endTime: '', 
              status: 'Completed' 
            } as Lifecycle).replace(/\d+/, Math.round(itemService.getAverageLifecycleDuration(props.item) / (1000 * 60 * 60 * 24)).toString()) }}
          </div>
          <div class="text-xs text-gray-500">{{ t('itemLifecycleView.stat.avgDuration') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>