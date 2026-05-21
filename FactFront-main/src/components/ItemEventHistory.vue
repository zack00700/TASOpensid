<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { X } from 'lucide-vue-next';

const { t } = useI18n();
import api from '../plugin/axios';
import EventTable from './EventTable.vue';
import { Event } from '../types/item';

// API types for this view (narrow local definitions)
interface Lifecycle {
  id: string;
  status: 'IN_PROGRESS' | 'COMPLETED' | 'CANCELED';
  startTime: string;
  endTime?: string;
  // New slim payload: lifecycles carry only event ids (no expansion)
  events?: Array<{ eventId?: string }>; // backward compatibility
  eventIds?: string[]; // preferred shape
}

interface EventLookupItem {
  id: string;
  eventDate?: string;
  eventName?: string;
  eventType?: 'IN' | 'INTERMEDIATE' | 'OUT';
}

const props = defineProps<{ itemId: string }>();
const emit = defineEmits<{ (e: 'close'): void }>();

const lifecycles = ref<Lifecycle[]>([]);
const loading = ref(false);
const error = ref('');
const statusFilter = ref('');
const sortDesc = ref(true);
// Map of eventId -> lookup payload
const eventsById = ref<Record<string, EventLookupItem>>({});

const fetchData = async () => {
  loading.value = true;
  error.value = '';
  try {
    // 1) Fetch lifecycles (slim: ids only)
    const res = await api.get(`/items/${props.itemId}/lifecycles`);
    lifecycles.value = res.data || [];

    // 2) Collect unique event ids across all lifecycles
    const idSet = new Set<string>();
    lifecycles.value.forEach((lc) => {
      // Support both shapes until fully migrated
      lc.eventIds?.forEach((id) => id && idSet.add(id));
      lc.events?.forEach((e) => e?.eventId && idSet.add(e.eventId));
    });

    const ids = Array.from(idSet);
    eventsById.value = {};

    if (ids.length) {
      // 3) Batch lookup for event details
      const lookupRes = await api.post(`/item-events/lookup`, { ids });
      const data = lookupRes.data;
      const items: EventLookupItem[] = Array.isArray(data)
        ? data
        : data?.items ?? data?.events ?? [];
      const map: Record<string, EventLookupItem> = {};
      items.forEach((it) => {
        if (it?.id) map[it.id] = it;
      });
      eventsById.value = map;
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
};

onMounted(fetchData);

const filtered = computed(() => {
  let arr = lifecycles.value;
  if (statusFilter.value) {
    arr = arr.filter((l) => l.status === statusFilter.value);
  }
  arr = arr.slice().sort((a, b) => {
    const diff =
      new Date(a.startTime).getTime() - new Date(b.startTime).getTime();
    return sortDesc.value ? -diff : diff;
  });
  return arr;
});

const refresh = () => fetchData();
defineExpose({ refresh });

const flatEvents = computed<Event[]>(() => {
  const result: Event[] = [];
  filtered.value.forEach((lc) => {
    const ids: string[] = [];
    if (lc.eventIds?.length) ids.push(...lc.eventIds);
    if (lc.events?.length)
      lc.events.forEach((e) => e?.eventId && ids.push(e.eventId));

    ids.forEach((id) => {
      const look = eventsById.value[id];
      result.push({
        id: id,
        timestamp: look?.eventDate,
        type: look?.eventType as any,
        itemId: props.itemId,
        lifecycleId: lc.id,
        notes: look?.eventName,
      });
    });
  });
  return result
    .filter((e) => e) // safety
    .sort(
      (a, b) =>
        new Date(b.timestamp || '').getTime() - new Date(a.timestamp || '').getTime()
    );
});
</script>

<template>
  <div class="bg-white rounded-lg shadow p-6 w-full max-h-[80vh] overflow-y-auto">
    <div class="flex justify-between items-center mb-4">
      <h3 class="text-lg font-medium text-gray-900">{{ t('itemEventHistory.title') }}</h3>
      <button @click="emit('close')" :aria-label="t('common.close')">
        <X class="h-5 w-5 text-gray-500" />
      </button>
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <select v-model="statusFilter" class="border-gray-300 rounded-md text-sm">
        <option value="">{{ t('payments.filter.allStatuses') }}</option>
        <option value="IN_PROGRESS">IN_PROGRESS</option>
        <option value="COMPLETED">COMPLETED</option>
        <option value="CANCELED">CANCELED</option>
      </select>
      <button
        @click="sortDesc = !sortDesc"
        class="text-sm text-blue-600 hover:underline"
      >
        {{ sortDesc ? t('itemEventHistory.sortDesc') : t('itemEventHistory.sortAsc') }}
      </button>
      <button
        @click="refresh"
        class="text-sm text-blue-600 hover:underline"
        :aria-label="t('itemEventHistory.refresh')"
      >
        {{ t('itemEventHistory.refresh') }}
      </button>
    </div>
    <div v-if="loading" class="text-center text-gray-500">{{ t('common.loading') }}</div>
    <div
      v-else-if="error"
      class="bg-red-100 text-red-700 p-2 rounded mb-4"
    >
      {{ error }}
      <button @click="refresh" class="ml-2 underline">{{ t('itemEventHistory.retry') }}</button>
    </div>
    <div v-else-if="flatEvents.length === 0" class="text-center text-gray-500">
      {{ t('itemEventHistory.empty') }}
    </div>
    <div v-else>
      <EventTable :events="flatEvents" />
    </div>
  </div>
</template>
