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
  eventType: undefined,
  itemId: props.itemId,
  lifecycleId: props.lifecycleId || "",
  location: "",
  notes: "",
  metadata: {},
});

// Single optional key/value pair. Kept as local refs and mirrored into
// eventData.metadata only when both sides are meaningful — binding the inputs
// straight to metadata added one entry per keystroke.
const metadataKey = ref("");
const metadataValue = ref("");

watchEffect(() => {
  const key = metadataKey.value.trim();
  eventData.value.metadata = key ? { [key]: metadataValue.value } : {};
});

watchEffect(() => {
  if (!eventData.value.eventType && availableEventConfigs.value.length > 0) {
    const first = availableEventConfigs.value[0];
    // eventType may be a string or an object with name property
    eventData.value.eventType = first.eventType;
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
    "mt-1 block w-full rounded-md border-[rgba(60,50,35,0.16)] shadow-sm focus:border-tide-blue focus:ring-tide-blue": true,
    "border-red-300": errors.value[fieldName],
  };
};
</script>

<template>
  <div class="bg-[rgba(255,253,247,0.92)] shadow rounded-lg">
    <div class="px-6 py-4 border-b border-[rgba(60,50,35,0.12)]">
      <div class="flex justify-between items-center">
        <h2 class="text-lg font-semibold text-tide-ink">{{ t('itemEventForm.title') }}</h2>
        <button @click="emit('cancel')" class="text-tide-ink/40 hover:text-tide-ink/55">
          <X class="h-6 w-6" />
        </button>
      </div>
    </div>

    <form @submit.prevent="handleSubmit" class="p-6 space-y-6">
      <!-- Event Details -->
      <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
        <div>
          <label class="block text-sm font-medium text-tide-ink/80">
            {{ t('itemEventForm.field.eventType') }} <span class="text-red-500">*</span>
          </label>
          <select
            v-model="eventData.eventType"
            :class="getInputClasses('eventType')"
          >
            <option
              v-for="config in availableEventConfigs"
              :key="config.id"
              :value="config.eventType"
            >
              {{ config.eventName }}
            </option>
          </select>
          <p v-if="errors.eventType" class="mt-1 text-sm text-red-600">
            {{ errors.eventType }}
          </p>
        </div>

        <div>
          <label class="block text-sm font-medium text-tide-ink/80">
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
          <label class="block text-sm font-medium text-tide-ink/80">
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
          <label class="block text-sm font-medium text-tide-ink/80">{{ t('payments.field.notes') }}</label>
          <textarea
            v-model="eventData.notes"
            rows="3"
            :class="getInputClasses('notes')"
          ></textarea>
        </div>

        <!-- Metadata Fields -->
        <div class="sm:col-span-2">
          <label class="block text-sm font-medium text-tide-ink/80 mb-2">
            {{ t('itemEventForm.field.additionalInformation') }}
          </label>
          <div class="space-y-4">
            <div class="flex items-center space-x-4">
              <input
                v-model="metadataKey"
                type="text"
                :placeholder="t('itemEventForm.placeholder.key')"
                class="flex-1 rounded-md border-[rgba(60,50,35,0.16)] shadow-sm focus:border-tide-blue focus:ring-tide-blue"
              />
              <input
                v-model="metadataValue"
                type="text"
                :placeholder="t('itemEventForm.placeholder.value')"
                class="flex-1 rounded-md border-[rgba(60,50,35,0.16)] shadow-sm focus:border-tide-blue focus:ring-tide-blue"
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
          class="px-4 py-2 border border-[rgba(60,50,35,0.16)] rounded-md text-sm font-medium text-tide-ink/80 hover:bg-[rgba(252,247,238,0.55)]"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="submit"
          class="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-tide-blue-btn-deep hover:bg-tide-blue-deep"
        >
          {{ t('itemEventForm.title') }}
        </button>
      </div>
    </form>
  </div>
</template>
