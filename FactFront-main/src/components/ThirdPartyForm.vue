<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { useI18n } from 'vue-i18n';
import { X } from "lucide-vue-next";

import { useThirdParty } from "../composables/use.third-party";

const { t } = useI18n();

const {
  formData,
  errors,
  validateForm,
  addThirdParty,
  patchThirdParty,
  originalData,
  currentViewId,
} = useThirdParty();

interface FormData {
  version?: number;
  // Personal Information
  fullName: string;
  jobTitle: string;
  contactNumber: string;
  email: string;

  // Company Information
  companyName: string;
  companyAddress: string;
  industryType: string;
  companyContactPerson: string;
  companyContactEmail: string;

  // Access Information
  accessType: string;
  modulesRequired: string[];

  // Security and Compliance
  identificationType: string;
  identificationNumber: string;
}

const props = defineProps<{
  editMode?: boolean;
  initialData?: any;
  viewOnly?: boolean;
}>();

const emit = defineEmits<{
  (e: "submit", data: FormData): void;
  (e: "cancel"): void;
}>();

const editable = ref(true);
const submitLabel = computed(() =>
  props.editMode ? t('thirdPartyForm.button.saveChanges') : t('thirdPartyForm.button.submitApplication')
);

onMounted(() => {
  if (props.editMode && props.initialData) {
    // Populate form with initial data for editing
    Object.keys(formData.value).forEach((key) => {
      if (key in props.initialData) {
        formData.value[key as keyof FormData] = props.initialData[key];
      }
    });
    originalData.value = { ...(props.initialData as any) };
    currentViewId.value = props.initialData.id;
  }
  editable.value = !props.viewOnly;
});

const industryTypes = computed(() => [
  { value: "Shipping Line", label: t('thirdPartyForm.industry.shippingLine') },
  { value: "Freight Forwarder", label: t('thirdPartyForm.industry.freightForwarder') },
  { value: "Customs Broker", label: t('thirdPartyForm.industry.customsBroker') },
  { value: "Terminal Operator", label: t('thirdPartyForm.industry.terminalOperator') },
  { value: "Trucking Company", label: t('thirdPartyForm.industry.truckingCompany') },
  { value: "Other", label: t('thirdPartyForm.industry.other') },
]);

const accessTypes = computed(() => [
  { value: "View Only", label: t('thirdPartyForm.access.viewOnly') },
  { value: "Data Entry", label: t('thirdPartyForm.access.dataEntry') },
  { value: "Full Access", label: t('thirdPartyForm.access.fullAccess') },
]);

const availableModules = computed(() => [
  { value: "Vessel Scheduling", label: t('thirdPartyForm.module.vesselScheduling') },
  { value: "Yard Management", label: t('thirdPartyForm.module.yardManagement') },
  { value: "Gate Operations", label: t('thirdPartyForm.module.gateOperations') },
  { value: "Billing", label: t('thirdPartyForm.module.billing') },
  { value: "Customs Interface", label: t('thirdPartyForm.module.customsInterface') },
  { value: "Equipment Control", label: t('thirdPartyForm.module.equipmentControl') },
]);

const identificationTypes = computed(() => [
  { value: "Passport", label: t('thirdPartyForm.identification.passport') },
  { value: "Driver's License", label: t('thirdPartyForm.identification.driversLicense') },
  { value: "National ID", label: t('thirdPartyForm.identification.nationalId') },
]);


const handleSubmit = async () => {
  const currentId = props.editMode && props.initialData ? props.initialData.id : undefined;
  if (!validateForm(currentId)) {
    return;
  }

  emit("submit", formData.value);
  if (props.editMode && props.initialData) {
    await patchThirdParty(props.initialData.id, props.initialData.version);
  } else {
    await addThirdParty();
  }
};

const getInputClasses = (fieldName: keyof FormData) => {
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
          {{ editMode ? t('thirdPartyForm.header.editTitle') : t('thirdPartyForm.header.newTitle') }}
        </h2>
        <button @click="emit('cancel')" class="text-gray-400 hover:text-gray-500">
          <X class="h-6 w-6" />
        </button>
      </div>
    </div>

    <form @submit.prevent="handleSubmit" class="p-6 space-y-8">
      <!-- Personal Information -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('thirdPartyForm.section.personalInformation') }}</h3>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('thirdPartyForm.field.fullName') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.fullName"
              type="text"
              :class="getInputClasses('fullName')"
              :disabled="!editable"
            />
            <p v-if="errors.fullName" class="mt-1 text-sm text-red-600">
              {{ errors.fullName }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700"> {{ t('thirdPartyForm.field.jobTitle') }} </label>
            <input
              v-model="formData.jobTitle"
              type="text"
              :class="getInputClasses('jobTitle')"
              :disabled="!editable"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('thirdPartyForm.field.contactNumber') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.contactNumber"
              type="tel"
              :class="getInputClasses('contactNumber')"
              :disabled="!editable"
            />
            <p v-if="errors.contactNumber" class="mt-1 text-sm text-red-600">
              {{ errors.contactNumber }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('thirdPartyForm.field.emailAddress') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.email"
              type="email"
              :class="getInputClasses('email')"
              :disabled="!editable"
            />
            <p v-if="errors.email" class="mt-1 text-sm text-red-600">
              {{ errors.email }}
            </p>
          </div>
        </div>
      </div>

      <!-- Company Information -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('thirdPartyForm.section.companyInformation') }}</h3>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('thirdPartyForm.field.companyName') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.companyName"
              type="text"
              :class="getInputClasses('companyName')"
              :disabled="!editable"
            />
            <p v-if="errors.companyName" class="mt-1 text-sm text-red-600">
              {{ errors.companyName }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('thirdPartyForm.field.industryType') }} <span class="text-red-500">*</span>
            </label>
            <select
              v-model="formData.industryType"
              :class="getInputClasses('industryType')"
              :disabled="!editable"
            >
              <option value="">{{ t('thirdPartyForm.option.selectIndustryType') }}</option>
              <option v-for="type in industryTypes" :key="type.value" :value="type.value">
                {{ type.label }}
              </option>
            </select>
            <p v-if="errors.industryType" class="mt-1 text-sm text-red-600">
              {{ errors.industryType }}
            </p>
          </div>

          <div class="sm:col-span-2">
            <label class="block text-sm font-medium text-gray-700">
              {{ t('thirdPartyForm.field.companyAddress') }} <span class="text-red-500">*</span>
            </label>
            <textarea
              v-model="formData.companyAddress"
              rows="3"
              :class="getInputClasses('companyAddress')"
              :disabled="!editable"
            ></textarea>
            <p v-if="errors.companyAddress" class="mt-1 text-sm text-red-600">
              {{ errors.companyAddress }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('thirdPartyForm.field.companyContactPerson') }}
            </label>
            <input
              v-model="formData.companyContactPerson"
              type="text"
              :class="getInputClasses('companyContactPerson')"
              :disabled="!editable"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('thirdPartyForm.field.companyContactEmail') }}
            </label>
            <input
              v-model="formData.companyContactEmail"
              type="email"
              :class="getInputClasses('companyContactEmail')"
              :disabled="!editable"
            />
            <p v-if="errors.companyContactEmail" class="mt-1 text-sm text-red-600">
              {{ errors.companyContactEmail }}
            </p>
          </div>
        </div>
      </div>

      <!-- Access Information -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('thirdPartyForm.section.accessInformation') }}</h3>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('thirdPartyForm.field.accessType') }} <span class="text-red-500">*</span>
            </label>
            <select v-model="formData.accessType" :class="getInputClasses('accessType')" :disabled="!editable">
              <option value="">{{ t('thirdPartyForm.option.selectAccessType') }}</option>
              <option v-for="type in accessTypes" :key="type.value" :value="type.value">
                {{ type.label }}
              </option>
            </select>
            <p v-if="errors.accessType" class="mt-1 text-sm text-red-600">
              {{ errors.accessType }}
            </p>
          </div>

          <div class="sm:col-span-2">
            <label class="block text-sm font-medium text-gray-700">
              {{ t('thirdPartyForm.field.requiredModules') }} <span class="text-red-500">*</span>
            </label>
            <div class="mt-2 grid grid-cols-2 gap-4">
              <div
                v-for="mod in availableModules"
                :key="mod.value"
                class="flex items-center"
              >
                <input
                  type="checkbox"
                  :value="mod.value"
                  v-model="formData.modulesRequired"
                  class="h-4 w-4 text-blue-600 border-gray-300 rounded"
                  :disabled="!editable"
                />
                <label class="ml-2 text-sm text-gray-700">{{ mod.label }}</label>
              </div>
            </div>
            <p v-if="errors.modulesRequired" class="mt-1 text-sm text-red-600">
              {{ errors.modulesRequired }}
            </p>
          </div>


        </div>
      </div>

      <!-- Security and Compliance -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 mb-4">{{ t('thirdPartyForm.section.securityAndCompliance') }}</h3>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('thirdPartyForm.field.identificationType') }} <span class="text-red-500">*</span>
            </label>
            <select
              v-model="formData.identificationType"
              :class="getInputClasses('identificationType')"
              :disabled="!editable"
            >
              <option value="">{{ t('thirdPartyForm.option.selectIdentificationType') }}</option>
              <option v-for="type in identificationTypes" :key="type.value" :value="type.value">
                {{ type.label }}
              </option>
            </select>
            <p v-if="errors.identificationType" class="mt-1 text-sm text-red-600">
              {{ errors.identificationType }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">
              {{ t('thirdPartyForm.field.identificationNumber') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.identificationNumber"
              type="text"
              :class="getInputClasses('identificationNumber')"
              :disabled="!editable"
            />
            <p v-if="errors.identificationNumber" class="mt-1 text-sm text-red-600">
              {{ errors.identificationNumber }}
            </p>
          </div>


        </div>
      </div>

      <!-- Form Actions -->
      <div class="flex justify-end space-x-4">
        <button
          type="button"
          @click="emit('cancel')"
          class="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          v-if="!editable"
          type="button"
          @click="editable = true"
          class="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          {{ t('common.edit') }}
        </button>
        <button
          v-else
          type="submit"
          class="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700"
        >
          {{ submitLabel }}
        </button>
      </div>
    </form>
  </div>
</template>
