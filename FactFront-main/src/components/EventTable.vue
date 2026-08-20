<script setup lang="ts">
import { Event, EventType } from '../types/item';

const props = defineProps<{ events: Event[] }>();

const getEventTypeClasses = (type?: EventType) => {
  const base = 'px-2 py-1 text-xs font-medium rounded-full';
  switch (type) {
    case 'IN':
      return `${base} bg-green-100 text-green-800`;
    case 'OUT':
      return `${base} bg-red-100 text-red-800`;
    case 'INTERMEDIATE':
      return `${base} bg-blue-100 text-blue-800`;
    default:
      return base;
  }
};

const formatDateTime = (date?: string) => {
  return date ? new Date(date).toLocaleString() : '-';
};
</script>

<template>
  <div class="overflow-x-auto">
    <table class="min-w-full divide-y divide-[rgba(42,36,30,0.08)]">
      <thead class="bg-[rgba(252,247,238,0.55)]">
        <tr>
          <th class="px-6 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">
            Event Date
          </th>
          <th class="px-6 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">
            Event Name
          </th>
          <th class="px-6 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">
            Event Type
          </th>
        </tr>
      </thead>
      <tbody class="bg-[rgba(255,253,247,0.92)] divide-y divide-[rgba(42,36,30,0.08)]">
        <tr v-for="event in props.events" :key="event.id" class="hover:bg-[rgba(252,247,238,0.55)]">
          <td class="px-6 py-4 whitespace-nowrap text-sm text-tide-ink/55">
            {{ formatDateTime(event.timestamp) }}
          </td>
          <td class="px-6 py-4 whitespace-nowrap text-sm text-tide-ink/55">
            {{ event.notes || '-' }}
          </td>
          <td class="px-6 py-4 whitespace-nowrap text-sm">
            <span :class="getEventTypeClasses(event.eventType as EventType)">
              {{ event.eventType || '-' }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
