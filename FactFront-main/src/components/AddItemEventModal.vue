<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { X } from 'lucide-vue-next';
import api from '../plugin/axios';
import type { AddEventPayload } from '../services/itemService';

const { t } = useI18n();

interface EventSuggestion {
  id: string;
  eventName: string;
  eventConfig?: {
    eventType: 'IN' | 'INTERMEDIATE' | 'OUT';
  };
}

const props = defineProps<{ itemId: string }>();
const emit = defineEmits<{ (e: 'success'): void; (e: 'cancel'): void }>();

const query = ref('');
const suggestions = ref<EventSuggestion[]>([]);
const cache = ref<Record<string, EventSuggestion[]>>({});
const selected = ref<EventSuggestion | null>(null);
const eventDate = ref(new Date().toISOString().slice(0, 16));
const isSubmitting = ref(false);
const submitError = ref('');
const errors = ref<{ event?: string; eventDate?: string }>({});
let debounceHandle: any = null;

const fetchSuggestions = async (q: string) => {
  if (cache.value[q]) {
    suggestions.value = cache.value[q];
    return;
  }
  try {
    const res = await api.get('/event', { params: { q } });
    suggestions.value = res.data;
    cache.value[q] = res.data;
    const keys = Object.keys(cache.value);
    if (keys.length > 5) {
      delete cache.value[keys[0]];
    }
  } catch (e) {
    // silent
  }
};

watch(query, (val) => {
  selected.value = null;
  submitError.value = '';
  if (debounceHandle) clearTimeout(debounceHandle);
  if (!val) {
    suggestions.value = [];
    return;
  }
  debounceHandle = setTimeout(() => fetchSuggestions(val), 300);
});

const selectEvent = (e: EventSuggestion) => {
  selected.value = e;
  query.value = e.eventName;
  suggestions.value = [];
};

const validate = () => {
  errors.value = {};
  if (!selected.value) errors.value.event = t('addItemEventModal.error.eventRequired');
  if (!eventDate.value) errors.value.eventDate = t('addItemEventModal.error.eventDateRequired');
  return Object.keys(errors.value).length === 0;
};

const handleSubmit = async () => {
  if (!validate()) return;
  const payload: AddEventPayload = {
    eventId: selected.value!.id,
    eventDate: new Date(eventDate.value).toISOString(),
  };
  isSubmitting.value = true;
  submitError.value = '';
  try {
    await api.post(`/items/${props.itemId}/event`, payload);
    emit('success');
  } catch (e: any) {
    submitError.value = e.response?.data?.message || e.message;
  } finally {
    isSubmitting.value = false;
  }
};

const selectedEventType = computed(() => {
  const et = selected.value?.eventConfig?.eventType as any;
  return typeof et === 'string' ? et : et?.name || '';
});
</script>

<template>
  <div class="bg-white rounded-lg shadow p-6 w-full">
    <div class="flex justify-between items-center mb-4">
      <h3 class="text-lg font-medium text-gray-900">{{ t('addItemEventModal.title') }}</h3>
      <button @click="emit('cancel')" :aria-label="t('common.close')">
        <X class="h-5 w-5 text-gray-500" />
      </button>
    </div>
    <form @submit.prevent="handleSubmit" class="space-y-4">
      <div>
        <label class="block text-sm font-medium text-gray-700">{{ t('addItemEventModal.field.event') }}<span class="text-red-500">*</span></label>
        <input
          v-model="query"
          type="text"
          :disabled="isSubmitting"
          class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"
        />
        <ul
          v-if="suggestions.length"
          class="border rounded-md mt-1 bg-white max-h-40 overflow-auto"
        >
          <li
            v-for="s in suggestions"
            :key="s.id"
            @mousedown.prevent="selectEvent(s)"
            class="px-3 py-2 hover:bg-gray-100 cursor-pointer"
          >
            {{ s.eventName }}
          </li>
        </ul>
        <p v-if="errors.event" class="mt-1 text-sm text-red-600">{{ errors.event }}</p>
      </div>
      <div v-if="selectedEventType" class="text-sm text-gray-600">
        {{ t('addItemEventModal.label.type', { type: selectedEventType }) }}
      </div>
      <div>
        <label class="block text-sm font-medium text-gray-700">{{ t('addItemEventModal.field.eventDateTime') }}<span class="text-red-500">*</span></label>
        <input
          v-model="eventDate"
          type="datetime-local"
          :disabled="isSubmitting"
          class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"
        />
        <p v-if="errors.eventDate" class="mt-1 text-sm text-red-600">{{ errors.eventDate }}</p>
      </div>
      <p v-if="submitError" class="text-sm text-red-600">{{ submitError }}</p>
      <div class="flex justify-end space-x-3 pt-2">
        <button
          type="button"
          @click="emit('cancel')"
          :disabled="isSubmitting"
          class="px-4 py-2 border border-gray-300 rounded-md text-sm text-gray-700 hover:bg-gray-50"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="submit"
          :disabled="isSubmitting"
          class="px-4 py-2 rounded-md text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50"
        >
          {{ t('addItemEventModal.action.addEvent') }}
        </button>
      </div>
    </form>
  </div>
</template>

