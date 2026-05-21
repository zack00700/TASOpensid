<script setup lang="ts">
import { ref, toRef, watch } from "vue";
import { X } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import { Vessel } from "../types/vessel";
import { useVessel } from "../composables/use.vessel";
import ThirdPartyAutocomplete from './ui/ThirdPartyAutocomplete.vue';

const { t } = useI18n();

const props = defineProps<{
  editMode?: boolean;
  initialData?: Vessel | null;
}>();

const emit = defineEmits<{
  (e: "submit", data: Vessel): void;
  (e: "cancel"): void;
}>();

// Passer initialData au composable
const { formData, errors, validateForm, addVessel, updateVessel } = useVessel(toRef(props, 'initialData'));

const vesselTypes = [
  "Container Ship",
  "Bulk Carrier",
  "Tanker",
  "General Cargo",
  "Ro-Ro",
  "Car Carrier",
  "LNG Carrier",
  "Cruise Ship",
  "Trailing Suction Hopper Dredger",
  "TUG",
  "Offshore Supply Vessel",
  "Fishing Vessel",
  "Other"
];

const handleSubmit = async () => {
  if (!validateForm()) {
    return;
  }

  if (props.editMode) {
    await updateVessel();
  } else {
    await addVessel();
  }

  emit("submit", formData.value);
};

const getInputClasses = (fieldName: keyof Vessel) => {
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
        <h2 class="text-lg font-semibold text-gray-900">
          {{ editMode ? t('vesselForm.title.edit') : t('vesselForm.title.new') }}
        </h2>
        <button @click="emit('cancel')" class="text-gray-400 hover:text-gray-500">
          <X class="h-6 w-6" />
        </button>
      </div>
    </div>

    <form @submit.prevent="handleSubmit" class="p-6 space-y-6">
      <!-- Basic Information -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('vesselForm.section.basicInformation') }}</h3>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselForm.field.vesselName') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.name"
              type="text"
              :class="getInputClasses('name')"
            />
            <p v-if="errors.name" class="mt-1 text-sm text-red-600">
              {{ errors.name }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselForm.field.imoNumber') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.imoNumber"
              type="text"
              :placeholder="t('vesselForm.placeholder.imoNumber')"
              :class="getInputClasses('imoNumber')"
            />
            <p v-if="errors.imoNumber" class="mt-1 text-sm text-red-600">
              {{ errors.imoNumber }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselForm.field.mmsi') }}
            </label>
            <input
              data-test="vessel-form-mmsi"
              v-model="formData.mmsi"
              type="text"
              inputmode="numeric"
              maxlength="9"
              :placeholder="t('vesselForm.placeholder.mmsi')"
              :class="getInputClasses('mmsi')"
            />
            <p class="mt-1 text-xs text-gray-500">{{ t('vesselForm.help.mmsi') }}</p>
            <p v-if="errors.mmsi" class="mt-1 text-sm text-red-600">{{ errors.mmsi }}</p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselForm.field.callSign') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.callSign"
              type="text"
              :class="getInputClasses('callSign')"
            />
            <p v-if="errors.callSign" class="mt-1 text-sm text-red-600">
              {{ errors.callSign }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselForm.field.flag') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.flag"
              type="text"
              :class="getInputClasses('flag')"
            />
            <p v-if="errors.flag" class="mt-1 text-sm text-red-600">
              {{ errors.flag }}
            </p>
          </div>
        </div>
      </div>

      <!-- Owner/Operator Information -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('vesselForm.section.ownerOperator') }}</h3>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div>
            <ThirdPartyAutocomplete
              v-model="formData.owner"
              :label="t('vesselForm.field.owner')"
              industry-type="Shipping Line"
              required
              :input-class="getInputClasses('owner')"
            />
            <p v-if="errors.owner" class="mt-1 text-sm text-red-600">{{ errors.owner }}</p>
          </div>

          <div>
            <ThirdPartyAutocomplete
              v-model="formData.operator"
              :label="t('vesselForm.field.operator')"
              industry-type="Shipping Line"
              :input-class="getInputClasses('operator')"
            />
          </div>
        </div>
      </div>

      <!-- Vessel Details -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('vesselForm.section.vesselDetails') }}</h3>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselForm.field.vesselType') }} <span class="text-red-500">*</span>
            </label>
            <select
              v-model="formData.vesselType"
              :class="getInputClasses('vesselType')"
            >
              <option value="">{{ t('vesselForm.placeholder.selectVesselType') }}</option>
              <option v-for="type in vesselTypes" :key="type" :value="type">
                {{ type }}
              </option>
            </select>
            <p v-if="errors.vesselType" class="mt-1 text-sm text-red-600">
              {{ errors.vesselType }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700"> {{ t('vesselForm.field.status') }} </label>
            <select
              v-model="formData.status"
              :class="getInputClasses('status')"
            >
              <option value="Active">{{ t('vesselForm.status.active') }}</option>
              <option value="Inactive">{{ t('vesselForm.status.inactive') }}</option>
            </select>
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
          {{ editMode ? t('vesselForm.button.saveChanges') : t('vesselForm.button.createVessel') }}
        </button>
      </div>
    </form>


  </div>
</template>
