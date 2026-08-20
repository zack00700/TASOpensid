<script setup lang="ts">
import { ref, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { Download } from 'lucide-vue-next';
import AdvancedFilter from './AdvancedFilter.vue';
import EventTable from './EventTable.vue';
import { Event } from '../types/item';
import SearchInput from './ui/SearchInput.vue';

const { t } = useI18n();

const searchQuery = ref('');

// Sample data
const events = ref<Event[]>([
  {
    id: '1',
    timestamp: '2024-03-15T10:00:00Z',
    eventType: 'IN',
    itemId: 'CMAU5984269',
    lifecycleId: 'LC001',
    location: 'Gate-In',
    notes: 'Container arrived at terminal'
  },
  {
    id: '2',
    timestamp: '2024-03-15T14:30:00Z',
    eventType: 'INTERMEDIATE',
    itemId: 'CMAU5984269',
    lifecycleId: 'LC001',
    location: 'Yard-A12',
    notes: 'Moved to storage location'
  }
]);

const selectedDateRange = ref<'today' | 'week' | 'month' | 'custom'>('today');
const customStartDate = ref('');
const customEndDate = ref('');

const dateRanges = computed(() => [
  { value: 'today' as const, label: t('events.range.today') },
  { value: 'week' as const, label: t('events.range.week') },
  { value: 'month' as const, label: t('events.range.month') },
  { value: 'custom' as const, label: t('events.range.custom') },
]);

const filteredEvents = computed(() => {
  // Implement date filtering logic here
  return events.value;
});

const handleFilter = (_filters: any[]) => {
  // Implement filter logic here
};

const exportEvents = () => {
  // Implement export logic
};
</script>

<template>
  <div class="bg-[rgba(255,253,247,0.92)] shadow rounded-lg">
    <!-- Header -->
    <div class="px-4 py-3 border-b border-[rgba(60,50,35,0.12)]">
      <div class="flex justify-between items-center">
        <h2 class="text-lg font-semibold text-tide-ink">{{ t('events.eventLog') }}</h2>
        <div class="flex space-x-4">
          <SearchInput v-model="searchQuery" :placeholder="t('events.placeholder.searchEvents')" />
          <button
            @click="exportEvents"
            class="inline-flex items-center px-3 py-2 border border-[rgba(60,50,35,0.16)] shadow-sm text-sm font-medium rounded-md text-tide-ink/80 bg-[rgba(255,253,247,0.92)] hover:bg-[rgba(252,247,238,0.55)]"
          >
            <Download class="h-4 w-4 mr-2" />
            {{ t('common.export') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Quick Filters -->
    <div class="px-4 py-3 border-b border-[rgba(60,50,35,0.12)]">
      <div class="flex items-center space-x-4">
        <span class="text-sm font-medium text-tide-ink/80">{{ t('events.timeRange') }}</span>
        <div class="flex space-x-2">
          <button
            v-for="range in dateRanges"
            :key="range.value"
            @click="selectedDateRange = range.value"
            :class="[
              'px-3 py-2 text-sm font-medium rounded-md',
              selectedDateRange === range.value
                ? 'bg-blue-100 text-tide-blue-deep'
                : 'text-tide-ink/80 hover:bg-[rgba(42,36,30,0.05)]'
            ]"
          >
            {{ range.label }}
          </button>
        </div>
      </div>

      <!-- Custom Date Range -->
      <div v-if="selectedDateRange === 'custom'" class="mt-4 flex items-center space-x-4">
        <div>
          <label class="block text-sm font-medium text-tide-ink/80">{{ t('events.startDate') }}</label>
          <input
            v-model="customStartDate"
            type="date"
            class="mt-1 block w-full rounded-md border-[rgba(60,50,35,0.16)] shadow-sm focus:ring-tide-blue focus:border-tide-blue"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-tide-ink/80">{{ t('events.endDate') }}</label>
          <input
            v-model="customEndDate"
            type="date"
            class="mt-1 block w-full rounded-md border-[rgba(60,50,35,0.16)] shadow-sm focus:ring-tide-blue focus:border-tide-blue"
          />
        </div>
      </div>
    </div>

    <!-- Advanced Filter -->
    <AdvancedFilter type="events" @filter="handleFilter" />

    <!-- Events Table -->
    <div class="p-6">
      <EventTable :events="filteredEvents" />
    </div>
  </div>
</template>
