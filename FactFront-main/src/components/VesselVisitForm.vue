<script setup lang="ts">
import { ref, watch } from "vue";
import { Radio, X } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import { VesselVisit } from "../types/vessel-visit";

import { useVesselVisit } from "../composables/use.vessel-visit";
import { useVesselVisitAis } from "../composables/use.vessel.visit.ais";
import ThirdPartyAutocomplete from "./ui/ThirdPartyAutocomplete.vue";

const { t, locale } = useI18n();

const { formData, validateForm, errors, addVesselVisit, updateVesselVisit } = useVesselVisit();
const { suggestion, loadFor } = useVesselVisitAis();
const appliedEta = ref(false);
const appliedAta = ref(false);

const props = defineProps<{
  editMode?: boolean;
  initialData?: VesselVisit;
}>();

const emit = defineEmits<{
  (e: "submit", data: VesselVisit): void;
  (e: "cancel"): void;
}>();

watch(
  () => props.initialData,
  (incoming) => {
    if (!incoming) return;
    const { id: _id, ...rest } = incoming;
    Object.assign(formData.value, rest);
  },
  { immediate: true }
);

watch(
  () => props.initialData?.id,
  async (id) => {
    appliedEta.value = false;
    appliedAta.value = false;
    await loadFor(id);
  },
  { immediate: true }
);

const handleSubmit = async () => {
  if (!validateForm()) {
    return;
  }

  if (props.editMode && props.initialData?.id) {
    await updateVesselVisit(props.initialData.id);
  } else {
    await addVesselVisit();
  }

  emit("submit", formData.value);
};

const facilities = ["Terminal A", "Terminal B", "Terminal C"];
const services = [
  { code: "WCCA", name: "West Coast Central America" },
  { code: "ECSA", name: "East Coast South America" },
  { code: "MED", name: "Mediterranean Service" },
];

const getInputClasses = (fieldName: keyof VesselVisit) => {
  return {
    "mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500": true,
    "border-red-300": errors.value[fieldName],
  };
};

const formatSuggestion = (iso: string): string =>
  new Intl.DateTimeFormat(locale.value || 'en', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(iso));

const toDatetimeLocal = (iso: string): string => {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const applyEta = () => {
  if (suggestion.value?.suggestedEta) {
    formData.value.eta = toDatetimeLocal(suggestion.value.suggestedEta);
    appliedEta.value = true;
  }
};

const applyAta = () => {
  if (suggestion.value?.suggestedAta) {
    formData.value.ata = toDatetimeLocal(suggestion.value.suggestedAta);
    appliedAta.value = true;
  }
};
</script>

<template>
  <div class="bg-white shadow rounded-lg">
    <div class="px-6 py-4 border-b border-gray-200">
      <div class="flex justify-between items-center">
        <h2 class="text-lg font-semibold text-gray-900">
          {{ editMode ? t('vesselVisitForm.title.edit') : t('vesselVisitForm.title.new') }}
        </h2>
        <button @click="emit('cancel')" class="text-gray-400 hover:text-gray-500">
          <X class="h-6 w-6" />
        </button>
      </div>
    </div>

    <form @submit.prevent="handleSubmit" class="p-6 space-y-6">
      <!-- Vessel Information -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('vesselVisitForm.section.vesselInformation') }}</h3>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.vesselName') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.vesselName"
              type="text"
              :class="getInputClasses('vesselName')"
            />
            <p v-if="errors.vesselName" class="mt-1 text-sm text-red-600">
              {{ errors.vesselName }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.vesselId') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.vesselId"
              type="text"
              :class="getInputClasses('vesselId')"
            />
            <p v-if="errors.vesselId" class="mt-1 text-sm text-red-600">
              {{ errors.vesselId }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.service') }} <span class="text-red-500">*</span>
            </label>
            <select
              v-model="formData.service"
              :class="getInputClasses('service')"
              @change="
                formData.serviceName =
                  services.find((s) => s.code === formData.service)?.name || ''
              "
            >
              <option value="">{{ t('vesselVisitForm.placeholder.selectService') }}</option>
              <option
                v-for="service in services"
                :key="service.code"
                :value="service.code"
              >
                {{ service.code }} - {{ service.name }}
              </option>
            </select>
            <p v-if="errors.service" class="mt-1 text-sm text-red-600">
              {{ errors.service }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700"> {{ t('vesselVisitForm.field.facility') }} </label>
            <select v-model="formData.facility" :class="getInputClasses('facility')">
              <option value="">{{ t('vesselVisitForm.placeholder.selectFacility') }}</option>
              <option v-for="facility in facilities" :key="facility" :value="facility">
                {{ facility }}
              </option>
            </select>
          </div>
        </div>
      </div>

      <!-- Port Information -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('vesselVisitForm.section.portInformation') }}</h3>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-3">
          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.pol') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.pol"
              type="text"
              :placeholder="t('vesselVisitForm.placeholder.unlocode')"
              :class="getInputClasses('pol')"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.pod') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.pod"
              type="text"
              :placeholder="t('vesselVisitForm.placeholder.unlocode')"
              :class="getInputClasses('pod')"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.finalDestination') }}
            </label>
            <input
              v-model="formData.finalDestination"
              type="text"
              :placeholder="t('vesselVisitForm.placeholder.unlocode')"
              :class="getInputClasses('finalDestination')"
            />
          </div>
        </div>
      </div>

      <!-- Schedule Information -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('vesselVisitForm.section.scheduleInformation') }}</h3>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div>
            <div
              v-if="suggestion?.suggestedEta && !appliedEta"
              data-test="ais-suggestion-eta"
              class="mb-2 px-3 py-2 bg-blue-50 border border-blue-200 text-blue-800 text-sm rounded flex items-center justify-between gap-2"
            >
              <span class="flex items-center gap-2">
                <Radio class="h-4 w-4 shrink-0" />
                {{ t('vesselVisitForm.aisSuggestion.eta', { time: formatSuggestion(suggestion.suggestedEta) }) }}
              </span>
              <button
                type="button"
                @click="applyEta"
                data-test="ais-suggestion-eta-apply"
                class="text-blue-600 hover:underline font-medium text-sm whitespace-nowrap"
              >{{ t('vesselVisitForm.aisSuggestion.apply') }}</button>
            </div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.eta') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.eta"
              type="datetime-local"
              :class="getInputClasses('eta')"
            />
            <p v-if="errors.eta" class="mt-1 text-sm text-red-600">
              {{ errors.eta }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.etd') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.etd"
              type="datetime-local"
              :min="formData.eta"
              :class="getInputClasses('etd')"
            />
            <p v-if="errors.etd" class="mt-1 text-sm text-red-600">
              {{ errors.etd }}
            </p>
          </div>

          <div>
            <div
              v-if="suggestion?.suggestedAta && !appliedAta"
              data-test="ais-suggestion-ata"
              class="mb-2 px-3 py-2 bg-blue-50 border border-blue-200 text-blue-800 text-sm rounded flex items-center justify-between gap-2"
            >
              <span class="flex items-center gap-2">
                <Radio class="h-4 w-4 shrink-0" />
                {{ t('vesselVisitForm.aisSuggestion.ata', { time: formatSuggestion(suggestion.suggestedAta) }) }}
              </span>
              <button
                type="button"
                @click="applyAta"
                data-test="ais-suggestion-ata-apply"
                class="text-blue-600 hover:underline font-medium text-sm whitespace-nowrap"
              >{{ t('vesselVisitForm.aisSuggestion.apply') }}</button>
            </div>
            <label class="block text-sm font-medium text-gray-700"> {{ t('vesselVisitForm.field.ata') }} </label>
            <input
              v-model="formData.ata"
              type="datetime-local"
              :class="getInputClasses('ata')"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700"> {{ t('vesselVisitForm.field.atd') }} </label>
            <input
              v-model="formData.atd"
              type="datetime-local"
              :class="getInputClasses('atd')"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700"> {{ t('vesselVisitForm.field.beginReceive') }} </label>
            <input
              v-model="formData.beginReceive"
              type="datetime-local"
              :class="getInputClasses('beginReceive')"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700"> {{ t('vesselVisitForm.field.emptyPickup') }} </label>
            <input
              v-model="formData.emptyPickup"
              type="datetime-local"
              :class="getInputClasses('emptyPickup')"
            />
          </div>
        </div>
      </div>

      <!-- Cut-off Times -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('vesselVisitForm.section.cutOffTimes') }}</h3>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-3">
          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.dryCargoCutOff') }}
            </label>
            <input
              v-model="formData.dryCutoff"
              type="datetime-local"
              :class="getInputClasses('dryCutoff')"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.reeferCutOff') }}
            </label>
            <input
              v-model="formData.reeferCutoff"
              type="datetime-local"
              :class="getInputClasses('reeferCutoff')"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.hazardousCutOff') }}
            </label>
            <input
              v-model="formData.hazCutoff"
              type="datetime-local"
              :class="getInputClasses('hazCutoff')"
            />
          </div>
        </div>
      </div>

      <!-- Voyage Information -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('vesselVisitForm.section.voyageInformation') }}</h3>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.inboundVoyage') }}
            </label>
            <input
              v-model="formData.inboundVoyage"
              type="text"
              :class="getInputClasses('inboundVoyage')"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.outboundVoyage') }}
            </label>
            <input
              v-model="formData.outboundVoyage"
              type="text"
              :class="getInputClasses('outboundVoyage')"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.inboundCaptain') }}
            </label>
            <input
              v-model="formData.inboundCaptain"
              type="text"
              :class="getInputClasses('inboundCaptain')"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('vesselVisitForm.field.outboundCaptain') }}
            </label>
            <input
              v-model="formData.outboundCaptain"
              type="text"
              :class="getInputClasses('outboundCaptain')"
            />
          </div>

          <div>
            <ThirdPartyAutocomplete
              v-model="formData.lineOperator"
              :label="t('vesselVisitForm.field.lineOperator')"
              industry-type="Shipping Line"
              :input-class="getInputClasses('lineOperator')"
            />
          </div>
        </div>
      </div>

      <!-- Additional Information -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('vesselVisitForm.section.additionalInformation') }}</h3>
        <div>
          <label class="block text-sm font-medium text-gray-700"> {{ t('vesselVisitForm.field.notes') }} </label>
          <textarea
            v-model="formData.notes"
            rows="3"
            :class="getInputClasses('notes')"
          ></textarea>
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
          {{ editMode ? t('vesselVisitForm.button.saveChanges') : t('vesselVisitForm.button.createVisit') }}
        </button>
      </div>
    </form>
  </div>
</template>
