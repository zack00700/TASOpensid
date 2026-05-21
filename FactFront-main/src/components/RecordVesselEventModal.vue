<template>
  <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
    <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-2xl space-y-4 max-h-[90vh] overflow-y-auto">
      <div class="flex justify-between items-center">
        <h2 class="text-lg font-semibold text-gray-900">
          {{ t('vesselEvent.modal.title', { vesselName: visit?.vesselName ?? '' }) }}
        </h2>
        <button type="button" class="text-gray-500" @click="emit('close')" :aria-label="t('common.close')">
          <X class="h-5 w-5" />
        </button>
      </div>

      <div>
        <div class="text-sm font-medium text-gray-700">{{ t('vesselEvent.history.title') }}</div>
        <div v-if="events.length === 0" class="mt-2 text-sm text-gray-500">
          {{ t('vesselEvent.history.empty') }}
        </div>
        <ul v-else class="mt-2 divide-y divide-gray-200 border border-gray-200 rounded-md max-h-40 overflow-y-auto">
          <li v-for="ev in recentEvents" :key="ev.id" class="px-3 py-2 text-sm">
            <div class="flex justify-between">
              <span class="font-medium text-gray-700">{{ eventConfigName(ev.eventId) }}</span>
              <span class="text-gray-500 text-xs">{{ formatDate(ev.eventDate) }}</span>
            </div>
            <div class="text-gray-600 mt-1">{{ ev.notes }}</div>
          </li>
        </ul>
      </div>

      <form @submit.prevent="handleSubmit" class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700">
            {{ t('vesselEvent.form.eventType') }} <span class="text-red-500">*</span>
          </label>
          <input
            v-model="eventQuery"
            type="text"
            :placeholder="t('vesselEvent.form.eventTypePlaceholder')"
            class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:ring-blue-500"
          />
          <ul v-if="filteredConfigs.length" class="border rounded-md mt-1 bg-white max-h-40 overflow-auto">
            <li
              v-for="cfg in filteredConfigs"
              :key="cfg.id"
              :data-test="`event-config-option-${cfg.id}`"
              class="px-3 py-2 hover:bg-gray-100 cursor-pointer"
              :class="{ 'bg-blue-50': selectedConfig?.id === cfg.id }"
              @click="selectConfig(cfg)"
            >
              {{ cfg.eventName }}
            </li>
          </ul>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700">
            {{ t('vesselEvent.form.eventDate') }} <span class="text-red-500">*</span>
          </label>
          <input
            v-model="eventDate"
            type="datetime-local"
            class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:ring-blue-500"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700">
            {{ t('vesselEvent.form.notes') }} <span class="text-red-500">*</span>
            <span class="text-xs text-gray-500 ml-1">{{ notes.length }} / 500</span>
          </label>
          <textarea
            v-model="notes"
            name="notes"
            rows="3"
            maxlength="500"
            :placeholder="t('vesselEvent.form.notesPlaceholder')"
            class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:ring-blue-500"
          ></textarea>
        </div>

        <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

        <div class="flex justify-end gap-2">
          <button type="button" class="px-3 py-2 text-gray-700" @click="emit('close')">
            {{ t('vesselEvent.form.cancel') }}
          </button>
          <button
            type="submit"
            :disabled="saving || !canSubmit"
            class="px-3 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {{ t('vesselEvent.form.submit') }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { X } from 'lucide-vue-next';
import { useVesselEvent } from '../composables/use.vessel-event';
import { useEventConfig } from '../composables/use.event-config';
import type { VesselVisit } from '../types/vessel-visit';
import type { EventConfig } from '../types/event-config';

const props = defineProps<{
  open: boolean;
  visit: VesselVisit | null;
}>();
const emit = defineEmits<{ (e: 'close'): void; (e: 'recorded'): void }>();

const { t } = useI18n();
const { events, getVesselEvents, addVesselEvent } = useVesselEvent();
const { eventConfigs, getEventConfig } = useEventConfig();

const eventQuery = ref('');
const selectedConfig = ref<EventConfig | null>(null);
const eventDate = ref(new Date().toISOString().slice(0, 16));
const notes = ref('');
const saving = ref(false);
const error = ref('');

const recentEvents = computed(() => events.value.slice(0, 5));

const filteredConfigs = computed(() => {
  const list = (eventConfigs.value ?? []) as EventConfig[];
  if (!eventQuery.value.trim()) return list;
  const q = eventQuery.value.trim().toLowerCase();
  return list.filter((c) => c.eventName.toLowerCase().includes(q));
});

const canSubmit = computed(
  () => !!selectedConfig.value && !!eventDate.value && notes.value.trim().length > 0,
);

function eventConfigName(id: string): string {
  const cfg = ((eventConfigs.value ?? []) as EventConfig[]).find((c) => c.id === id);
  return cfg?.eventName ?? id;
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}

function selectConfig(cfg: EventConfig) {
  selectedConfig.value = cfg;
  eventQuery.value = cfg.eventName;
}

async function handleSubmit() {
  if (!props.visit?.id || !selectedConfig.value) return;
  saving.value = true;
  error.value = '';
  try {
    await addVesselEvent(props.visit.id, {
      eventId: selectedConfig.value.id,
      eventDate: new Date(eventDate.value).toISOString(),
      notes: notes.value.trim(),
    });
    emit('recorded');
    await getVesselEvents(props.visit.id);
    selectedConfig.value = null;
    eventQuery.value = '';
    notes.value = '';
    eventDate.value = new Date().toISOString().slice(0, 16);
  } catch (e) {
    error.value = (e as Error)?.message ?? String(e);
  } finally {
    saving.value = false;
  }
}

async function refresh() {
  if (props.visit?.id) {
    await getVesselEvents(props.visit.id);
  }
  await getEventConfig('VESSEL');
}

onMounted(refresh);
watch(
  () => [props.open, props.visit?.id],
  () => {
    if (props.open) refresh();
  },
);
</script>
