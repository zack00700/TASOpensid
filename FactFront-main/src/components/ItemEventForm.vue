<script setup lang="ts">
import { ref, computed, watchEffect } from "vue";
import { useI18n } from "vue-i18n";
import { Event } from "../types/item";
import { EventConfig } from "../types/event-config";
import { X } from "lucide-vue-next";
import { v4 as uuidv4 } from "uuid";

const { t } = useI18n();

const props = defineProps<{
  itemId: string;
  lifecycleId?: string;
  eventConfigs: EventConfig[] | null;
}>();

const emit = defineEmits<{
  (e: "submit", event: Event): void;
  (e: "cancel"): void;
}>();

const availableEventConfigs = computed(() => props.eventConfigs ?? []);

const eventData = ref<Omit<Event, "id">>({
  timestamp: new Date().toISOString(),
  eventType: "",
  itemId: props.itemId,
  lifecycleId: props.lifecycleId || "",
  location: "",
  notes: "",
  metadata: {},
});

watchEffect(() => {
  if (!eventData.value.eventType && availableEventConfigs.value.length > 0) {
    const first = availableEventConfigs.value[0];
    // eventType may be a string or an object with name property
    eventData.value.eventType =
      typeof first.eventType === "string"
        ? first.eventType
        : first.eventType.name;
  }
});

const errors = ref<Record<string, string>>({});

const validateForm = () => {
  errors.value = {};
  let isValid = true;

  if (!eventData.value.timestamp) {
    errors.value.timestamp = t('itemEventForm.error.timestampRequired');
    isValid = false;
  }

  if (!eventData.value.eventType) {
    errors.value.eventType = t('itemEventForm.error.eventTypeRequired');
    isValid = false;
  }

  if (!eventData.value.location) {
    errors.value.location = t('itemEventForm.error.locationRequired');
    isValid = false;
  }

  return isValid;
};

const handleSubmit = () => {
  if (!validateForm()) {
    return;
  }

  const event: Event = {
    id: uuidv4(),
    ...eventData.value,
  };

  emit("submit", event);
};

const getInputClasses = (fieldName: string) => {
  return {
    "mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500": true,
    "border-red-300": errors.value[fieldName],
  };
};
</script>

<template>
  <div class="bg-white shadow rounded-lg">
    <div class="px-6 py-4 border-b border-gray-200">
      <div class="flex justify-between items-center">
        <h2 class="text-lg font-semibold text-gray-900">{{ t('itemEventForm.title') }}</h2>
        <button @click="emit('cancel')" class="text-gray-400 hover:text-gray-500">
          <X class="h-6 w-6" />
        </button>
      </div>
    </div>

    <form @submit.prevent="handleSubmit" class="p-6 space-y-6">
      <!-- Event Details -->
      <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
        <div>
          <label class="block text-sm font-medium text-gray-700">
            {{ t('itemEventForm.field.eventType') }} <span class="text-red-500">*</span>
          </label>
          <select
            v-model="eventData.eventType"
            :class="getInputClasses('eventType')"
          >
            <option
              v-for="config in availableEventConfigs"
              :key="config.id"
              :value="typeof config.eventType === 'string' ? config.eventType : config.eventType.name"
            >
              {{ config.eventName }}
            </option>
          </select>
          <p v-if="errors.eventType" class="mt-1 text-sm text-red-600">
            {{ errors.eventType }}
          </p>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700">
            {{ t('itemEventForm.field.timestamp') }} <span class="text-red-500">*</span>
          </label>
          <input
            v-model="eventData.timestamp"
            type="datetime-local"
            :class="getInputClasses('timestamp')"
          />
          <p v-if="errors.timestamp" class="mt-1 text-sm text-red-600">
            {{ errors.timestamp }}
          </p>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700">
            {{ t('itemEventForm.field.location') }} <span class="text-red-500">*</span>
          </label>
          <input
            v-model="eventData.location"
            type="text"
            :class="getInputClasses('location')"
          />
          <p v-if="errors.location" class="mt-1 text-sm text-red-600">
            {{ errors.location }}
          </p>
        </div>

        <div class="sm:col-span-2">
          <label class="block text-sm font-medium text-gray-700">{{ t('payments.field.notes') }}</label>
          <textarea
            v-model="eventData.notes"
            rows="3"
            :class="getInputClasses('notes')"
          ></textarea>
        </div>

        <!-- Metadata Fields -->
        <div class="sm:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-2">
            {{ t('itemEventForm.field.additionalInformation') }}
          </label>
          <div class="space-y-4">
            <div class="flex items-center space-x-4">
              <input
                type="text"
                :placeholder="t('itemEventForm.placeholder.key')"
                class="flex-1 rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"
                @input="e => eventData.metadata![e.target.value] = ''"
              />
              <input
                type="text"
                :placeholder="t('itemEventForm.placeholder.value')"
                class="flex-1 rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"
                @input="e => eventData.metadata![Object.keys(eventData.metadata!)[0]] = e.target.value"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Form Actions -->
      <div class="flex justify-end space-x-3">
        <button
          type="button"
          @click="emit('cancel')"
          class="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="submit"
          class="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700"
        >
          {{ t('itemEventForm.title') }}
        </button>
      </div>
    </form>
  </div>
</template>
