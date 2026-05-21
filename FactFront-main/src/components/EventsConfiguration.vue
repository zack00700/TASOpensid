<script setup lang="ts">
import { ref, computed } from "vue";
import { useI18n } from "vue-i18n";
import { v4 as uuidv4 } from "uuid";
import {
  Plus,
  Pencil,
  Trash2,
  Save,
  X,
  AlertCircle,
  ArrowDownCircle,
  ArrowUpCircle,
  CircleDot,
  Check,
  XCircle,
} from "lucide-vue-next";

import { useEventConfig } from "../composables/use.event-config.ts";

const { t } = useI18n();

const {
  errors,
  formData,
  isValid,
  validateForm,
  addEventConfig,
  eventConfigs,
} = useEventConfig();

const showForm = ref(false);
const editingEvent = ref<EventConfig | null>(null);
const showDeleteConfirm = ref(false);
const eventToDelete = ref<EventConfig | null>(null);

const eventTypes = computed(() => [
  { value: "IN", label: t('eventsConfiguration.eventType.in') },
  { value: "OUT", label: t('eventsConfiguration.eventType.out') },
  { value: "INTERMEDIATE", label: t('eventsConfiguration.eventType.intermediate') },
]);

const tableHeaders = computed(() => [
  t('eventsConfiguration.table.eventName'),
  t('eventsConfiguration.table.eventType'),
  t('eventsConfiguration.table.billedEvent'),
  t('eventsConfiguration.table.actions'),
]);

const getEventTypeIcon = (type: string) => {
  switch (type) {
    case "IN":
      return ArrowDownCircle;
    case "OUT":
      return ArrowUpCircle;
    default:
      return CircleDot;
  }
};

const getEventTypeClasses = (type: string) => {
  const baseClasses = "flex items-center space-x-1";
  switch (type) {
    case "IN":
      return `${baseClasses} text-green-600`;
    case "OUT":
      return `${baseClasses} text-red-600`;
    case "INTERMEDIATE":
      return `${baseClasses} text-blue-600`;
    default:
      return baseClasses;
  }
};

const handleAdd = () => {
  formData.value = {
    eventName: "",
    eventType: "IN",
    billedEvent: false,
  };
  editingEvent.value = null;
  showForm.value = true;
};

const handleEdit = (event: EventConfig) => {
  formData.value = {
    name: event.name,
    eventType: event.eventType,
    billedEvent: event.billedEvent,
  };
  editingEvent.value = event;
  showForm.value = true;
};

const handleDelete = (event: EventConfig) => {
  eventToDelete.value = event;
  showDeleteConfirm.value = true;
};

const confirmDelete = () => {
  if (eventToDelete.value) {
    eventConfigs.value = eventConfigs.value.filter(
      (e) => e.id !== eventToDelete.value?.id
    );
    showDeleteConfirm.value = false;
    eventToDelete.value = null;
  }
};

const handleSubmit = () => {
  if (!validateForm()) {
    return;
  }

  if (editingEvent.value) {
    const index = eventConfigs.value.findIndex((e) => e.id === editingEvent.value?.id);
    if (index !== -1) {
      eventConfigs.value[index] = {
        ...formData.value,
        id: editingEvent.value.id,
      };
    }
  } else {
    eventConfigs.value.push({
      ...formData.value,
      id: uuidv4(),
    });

    addEventConfig();
  }

  showForm.value = false;
  editingEvent.value = null;
};

const getInputClasses = (fieldName: keyof typeof formData.value) => {
  return {
    "mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500": true,
    "border-red-300": errors.value[fieldName],
  };
};
</script>

<template>
  <div>
    <!-- List View -->
    <div v-if="!showForm" class="bg-white shadow rounded-lg">
      <!-- Header -->
      <div class="px-4 py-3 border-b border-gray-200">
        <div class="flex justify-between items-center">
          <h2 class="text-lg font-semibold text-gray-900">{{ t('eventsConfiguration.title') }}</h2>
          <button
            @click="handleAdd"
            class="inline-flex items-center px-3 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700"
          >
            <Plus class="h-4 w-4 mr-2" />
            {{ t('eventsConfiguration.action.newEvent') }}
          </button>
        </div>
      </div>

      <!-- Table -->
      <div class="overflow-x-auto">
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
            <tr v-for="event in eventConfigs" :key="event.id" class="hover:bg-gray-50">
              <td class="px-4 py-3 whitespace-nowrap">
                <div class="text-sm font-medium text-gray-900">
                  {{ event.eventName }}
                </div>
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <div :class="getEventTypeClasses(event.eventType)">
                  <component :is="getEventTypeIcon(event.eventType)" class="h-4 w-4" />
                  <span class="text-sm">{{
                    eventTypes.find((t) => t.value === event.eventType)?.label
                  }}</span>
                </div>
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <span class="inline-flex items-center">
                  <component
                    :is="event.billedEvent ? Check : XCircle"
                    :class="event.billedEvent ? 'text-green-500' : 'text-red-500'"
                    class="h-5 w-5"
                  />
                </span>
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-right text-sm font-medium">
                <button
                  @click="handleEdit(event)"
                  class="text-blue-600 hover:text-blue-900 mr-3"
                  :aria-label="t('common.edit')"
                >
                  <Pencil class="h-5 w-5" />
                </button>
                <button
                  @click="handleDelete(event)"
                  class="text-red-600 hover:text-red-900"
                  :aria-label="t('common.delete')"
                >
                  <Trash2 class="h-5 w-5" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Form View -->
    <div v-else class="bg-white shadow rounded-lg">
      <div class="px-4 py-3 border-b border-gray-200">
        <div class="flex justify-between items-center">
          <h2 class="text-lg font-semibold text-gray-900">
            {{ editingEvent ? t('eventsConfiguration.form.editTitle') : t('eventsConfiguration.form.newTitle') }}
          </h2>
          <button @click="showForm = false" class="text-gray-400 hover:text-gray-500" :aria-label="t('common.close')">
            <X class="h-6 w-6" />
          </button>
        </div>
      </div>

      <form @submit.prevent="handleSubmit" class="p-6 space-y-6">
        <!-- Event Information -->
        <div class="space-y-6">
          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('eventsConfiguration.field.eventName') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.eventName"
              type="text"
              :placeholder="t('eventsConfiguration.placeholder.eventName')"
              :class="getInputClasses('name')"
            />
            <p v-if="errors.name" class="mt-1 text-sm text-red-600">
              {{ errors.name }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('eventsConfiguration.field.eventType') }} <span class="text-red-500">*</span>
            </label>
            <div class="mt-2 space-y-4">
              <div
                v-for="type in eventTypes"
                :key="type.value"
                class="relative flex items-center"
              >
                <div class="flex items-center h-5">
                  <input
                    type="radio"
                    :id="type.value"
                    :value="type.value"
                    v-model="formData.eventType"
                    name="event-type"
                    class="h-4 w-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                  />
                </div>
                <div class="ml-3 text-sm">
                  <label :for="type.value" class="font-medium text-gray-700">{{
                    type.label
                  }}</label>
                </div>
              </div>
            </div>
            <p v-if="errors.eventType" class="mt-1 text-sm text-red-600">
              {{ errors.eventType }}
            </p>
          </div>

          <div>
            <div class="relative flex items-start">
              <div class="flex items-center h-5">
                <input
                  v-model="formData.billedEvent"
                  type="checkbox"
                  class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                />
              </div>
              <div class="ml-3 text-sm">
                <label class="font-medium text-gray-700">{{ t('eventsConfiguration.field.billedEvent') }}</label>
                <p class="text-gray-500">{{ t('eventsConfiguration.field.billedEventHelp') }}</p>
              </div>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('eventConfig.field.scope') }}
            </label>
            <select
              v-model="formData.scope"
              class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:ring-blue-500"
            >
              <option value="ITEM">{{ t('eventConfig.scope.ITEM') }}</option>
              <option value="VESSEL">{{ t('eventConfig.scope.VESSEL') }}</option>
              <option value="BOTH">{{ t('eventConfig.scope.BOTH') }}</option>
            </select>
          </div>
        </div>

        <!-- Form Actions -->
        <div class="flex justify-end space-x-3">
          <button
            type="button"
            @click="showForm = false"
            class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="submit"
            class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700"
          >
            <Save class="h-4 w-4 mr-2" />
            {{ editingEvent ? t('eventsConfiguration.action.saveChanges') : t('eventsConfiguration.action.createEvent') }}
          </button>
        </div>
      </form>
    </div>

    <!-- Delete Confirmation Modal -->
    <Teleport to="body">
      <div
        v-if="showDeleteConfirm"
        class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50"
      >
        <div class="bg-white rounded-lg p-6 max-w-md w-full">
          <div class="text-center">
            <AlertCircle class="mx-auto h-12 w-12 text-red-500" />
            <h3 class="mt-4 text-lg font-medium text-gray-900">{{ t('eventsConfiguration.delete.title') }}</h3>
            <p class="mt-2 text-sm text-gray-500">
              {{ t('eventsConfiguration.delete.confirm', { name: eventToDelete?.name }) }}
            </p>
          </div>
          <div class="mt-6 flex justify-end space-x-3">
            <button
              @click="showDeleteConfirm = false"
              class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              @click="confirmDelete"
              class="px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-red-600 hover:bg-red-700"
            >
              {{ t('common.delete') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
