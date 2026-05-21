<script setup lang="ts">
import { ref, computed, watch, onMounted } from "vue";
import { useI18n } from 'vue-i18n';
import { X } from "lucide-vue-next";
import { ItemFormData } from "../types/item";
import { useIsoCode } from "../composables/use.iso-code";

const { t } = useI18n();

import {
  Clock,
  Calendar,
  CircleDot,
  ChevronRight
} from "lucide-vue-next";

import { useItem } from "../composables/use.item";
import { useThirdParty } from "../composables/use.third-party";
import ThirdPartyAutocomplete from "./ui/ThirdPartyAutocomplete.vue";
import Input from "./ui/Input.vue";
import Select from "./ui/Select.vue";
const { formData, errors, validateForm, addItem, updateItem } = useItem();
useThirdParty();

const props = defineProps<{
  editMode?: boolean;
  initialData?: ItemFormData;
}>();

const emit = defineEmits<{
  (e: "submit", data: ItemFormData): void;
  (e: "cancel"): void;
}>();

const activeTab = ref<"details" | "portDetails" | "lifeCycles">("details");

const itemTypes = computed(() => [
  { value: "container", label: t('itemForm.itemType.container') },
  { value: "breakbulk", label: t('itemForm.itemType.breakbulk') },
  { value: "vehicle", label: t('itemForm.itemType.vehicle') },
]);

// Port details fields
const portDetails = ref({
  // Commercial / billing
  category:        '' as '' | 'Import' | 'Export' | 'Transship',
  freightKind:     '' as '' | 'FCL' | 'LCL' | 'Empty' | 'Breakbulk' | 'Ro-Ro',
  bookingNumber:   '',
  shipperName:     '',
  consigneeName:   '',
  // Container identification
  containerNumber: '',
  containerType: '' as string,
  emptyStatus: '' as '' | 'FULL' | 'EMPTY' | 'UNKNOWN',
  customsStatus: '' as '' | 'PENDING' | 'CLEARED' | 'HELD' | 'INSPECTED' | 'RELEASED' | 'REFUSED',
  hazmatFlag: false,
  hazmatClass: '',
  unNumber: '',
  reeferFlag: false,
  reeferTemperature: null as number | null,
  oogFlag: false,
  weightVerified: false,
  verifiedWeight: null as number | null,
  condition: '' as '' | 'GOOD' | 'DAMAGED' | 'NEEDS_REPAIR',
  sealNumbersRaw: '',
  inboundVoyage: '',
  outboundVoyage: '',
  gateInDate: '',
  gateOutDate: '',
});

const { isoCodes, getAll: getAllIsoCodes } = useIsoCode();
const activeIsoCodes = computed(() =>
  isoCodes.value
    .filter(c => c.isActive)
    .sort((a, b) => a.code.localeCompare(b.code))
);
onMounted(() => { getAllIsoCodes(false); });

const getLifecycleStatusDisplay = (status?: string) => {

  return status || 'Unknown';
};
// Helper function to extract ID from various backend formats
const extractId = (data: any): string | undefined => {
  if (!data) return undefined;

  // Try different ID field variations
  const idFields = ['id', '_id', 'itemId', 'clientId'];

  for (const field of idFields) {
    if (data[field]) {
      const value = data[field];

      // Handle different formats
      if (typeof value === 'string' && value.trim()) {
        return value;
      }
      if (typeof value === 'object' && value !== null) {
        // Handle MongoDB ObjectId format { $oid: "..." }
        if (value.$oid) return value.$oid;
        if (value.oid) return value.oid;
      }
    }
  }

  return undefined;
};

const convertApiDateToInputDate = (apiDate: any): string => {
  if (!apiDate) {
    return '';
  }

  if (typeof apiDate !== 'string') {
    return '';
  }

  try {
    // For format "2025-09-13T00:00:00.000+00:00", extract "2025-09-13"
    const datePart = apiDate.split('T')[0];

    // Validate it's a proper date format (YYYY-MM-DD)
    if (/^\d{4}-\d{2}-\d{2}$/.test(datePart)) {
      return datePart;
    }

    // Fallback to full date parsing if needed
    const date = new Date(apiDate.replace('+00:00', 'Z'));
    if (!isNaN(date.getTime())) {
      const result = date.toISOString().split('T')[0];
      return result;
    }

    return '';
  } catch (error) {
    console.error('Error converting date:', apiDate, error);
    return '';
  }
};

watch(
  () => props.initialData,
  (newData) => {
    if (props.editMode && newData) {
      const extractedId = extractId(newData);

      // ✅ CHANGE THESE FIELD NAMES TOO
      const convertedLastInspection = newData.lastInspection || '';
      const convertedNextInspection = newData.nextInspection || '';

      formData.value = {
        id: extractedId,
        _id: extractedId,
        itemNumber: newData.itemNumber || "",
        itemType: newData.itemType ? String(newData.itemType).toLowerCase() : "",
        type: newData.type || "",
        status: newData.status || newData.itemStatus || "Available",
        ownerId: newData.ownerId || "",
        position: newData.position || "",
        // ✅ THESE ARE ALREADY IN THE CORRECT FORMAT
        lastInspection: convertedLastInspection,
        nextInspection: convertedNextInspection,
        notes: newData.notes || "",
        lifeCycles: newData.lifeCycles || [],
      };

      // Populate port details from initial data
      const nd = newData as any;
      portDetails.value = {
        category:        nd.category        || '',
        freightKind:     nd.freightKind     || '',
        bookingNumber:   nd.bookingNumber   || '',
        shipperName:     nd.shipperName     || '',
        consigneeName:   nd.consigneeName   || '',
        containerNumber: nd.containerNumber || '',
        containerType: nd.containerType || '',
        emptyStatus: nd.emptyStatus || '',
        customsStatus: nd.customsStatus || '',
        hazmatFlag: nd.hazmatFlag ?? false,
        hazmatClass: nd.hazmatClass || '',
        unNumber: nd.unNumber || '',
        reeferFlag: nd.reeferFlag ?? false,
        reeferTemperature: nd.reeferTemperature ?? null,
        oogFlag: nd.oogFlag ?? false,
        weightVerified: nd.weightVerified ?? false,
        verifiedWeight: nd.verifiedWeight ?? null,
        condition: nd.condition || '',
        sealNumbersRaw: Array.isArray(nd.sealNumbers) ? nd.sealNumbers.join(', ') : (nd.sealNumbers || ''),
        inboundVoyage: nd.inboundVoyage || '',
        outboundVoyage: nd.outboundVoyage || '',
        gateInDate: nd.gateInDate || '',
        gateOutDate: nd.gateOutDate || '',
      };
    }
  },
  { immediate: true }
);


const handleSubmit = async () => {


  if (!validateForm()) {
    return;
  }

  try {
    if (props.editMode) {

      const hasId = formData.value._id || formData.value.id;
      const hasItemNumber = formData.value.itemNumber;

      if (!hasId && !hasItemNumber) {
        console.error("❌ No ID or itemNumber available for update. Form data:", formData.value);
        console.error("❌ Initial data was:", props.initialData);
        throw new Error("Item ID or itemNumber is required for updating");
      }

      if (!hasId) {
        console.warn("⚠️ No ID found, using itemNumber as identifier:", hasItemNumber);
        // Set itemNumber as the ID for the update
        formData.value._id = hasItemNumber;
        formData.value.id = hasItemNumber;
      }


      await updateItem();
    } else {

      await addItem();
    }

    // Merge port details into form data before emitting
    const sealNumbers = portDetails.value.sealNumbersRaw
      ? portDetails.value.sealNumbersRaw.split(',').map(s => s.trim()).filter(s => s.length > 0)
      : [];

    emit("submit", {
      ...formData.value,
      category:        portDetails.value.category        || undefined,
      freightKind:     portDetails.value.freightKind     || undefined,
      bookingNumber:   portDetails.value.bookingNumber   || undefined,
      shipperName:     portDetails.value.shipperName     || undefined,
      consigneeName:   portDetails.value.consigneeName   || undefined,
      containerNumber: portDetails.value.containerNumber || undefined,
      containerType: portDetails.value.containerType || undefined,
      emptyStatus: portDetails.value.emptyStatus || undefined,
      customsStatus: portDetails.value.customsStatus || undefined,
      hazmatFlag: portDetails.value.hazmatFlag || undefined,
      hazmatClass: portDetails.value.hazmatFlag ? portDetails.value.hazmatClass || undefined : undefined,
      unNumber: portDetails.value.hazmatFlag ? portDetails.value.unNumber || undefined : undefined,
      reeferFlag: portDetails.value.reeferFlag || undefined,
      reeferTemperature: portDetails.value.reeferFlag ? portDetails.value.reeferTemperature ?? undefined : undefined,
      oogFlag: portDetails.value.oogFlag || undefined,
      weightVerified: portDetails.value.weightVerified || undefined,
      verifiedWeight: portDetails.value.weightVerified ? portDetails.value.verifiedWeight ?? undefined : undefined,
      condition: portDetails.value.condition || undefined,
      sealNumbers: sealNumbers.length > 0 ? sealNumbers : undefined,
      inboundVoyage: portDetails.value.inboundVoyage || undefined,
      outboundVoyage: portDetails.value.outboundVoyage || undefined,
      gateInDate: portDetails.value.gateInDate || undefined,
      gateOutDate: portDetails.value.gateOutDate || undefined,
    } as ItemFormData);
  } catch (error) {
    console.error("Failed to save item:", error);
    // Show user-friendly error message
    alert(t('itemForm.alert.failedToSave', { message: error.message || t('itemForm.alert.unknownError') }));
  }
};

const getInputClasses = (fieldName: keyof ItemFormData) => {
  return {
    "w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900 placeholder-gray-500": true,
    "border-red-300 focus:border-red-500 focus:ring-red-500/20": errors.value[fieldName],
  };
};



const getTabClasses = (tab: string) => {
  const isActive = activeTab.value === tab;
  return {
    "px-6 py-3 text-sm font-medium rounded-xl transition-all duration-200": true,
    "bg-blue-50 text-blue-700 border border-blue-200": isActive,
    "text-gray-600 hover:text-gray-900 hover:bg-gray-50": !isActive,
  };
};

// Convert form data to Item type for lifecycle view
const itemData = computed(() => ({
  id: formData.value.itemNumber,
  type: formData.value.itemType,
  status: formData.value.status,
  ownerCode: formData.value.ownerId,
  currentLifecycleId: formData.value.lifeCycles?.find((l) => l.status === "In Progress")?.id,
  lifeCycles: formData.value.lifeCycles || [],
}));
</script>

<template>
  <div class="bg-white rounded-2xl shadow-sm border border-gray-100">
    <!-- Header -->
    <div class="px-8 py-6 border-b border-gray-100">
      <div class="flex justify-between items-center">
        <div>
          <h2 class="text-2xl font-semibold text-gray-900 mb-1">
            {{ editMode ? t('itemForm.header.editTitle') : t('itemForm.header.newTitle') }}
          </h2>
          <p class="text-gray-500 text-sm">
            {{ editMode ? t('itemForm.header.editSubtitle') : t('itemForm.header.newSubtitle') }}
          </p>
          <!-- Debug info in development -->
          <p v-if="editMode && formData._id" class="text-xs text-gray-400 mt-1">
            {{ t('itemForm.label.id', { id: formData._id }) }}
          </p>
        </div>
        <button @click="emit('cancel')"
          class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-xl transition-all duration-200">
          <X class="h-5 w-5" />
        </button>
      </div>
    </div>

    <!-- Tabs -->
    <div v-if="editMode" class="px-8 pt-6">
      <nav class="flex space-x-3">
        <button @click="activeTab = 'details'" :class="getTabClasses('details')">
          {{ t('itemForm.tab.details') }}
        </button>
        <button @click="activeTab = 'portDetails'" :class="getTabClasses('portDetails')">
          {{ t('itemForm.tab.portDetails') }}
        </button>
        <button @click="activeTab = 'lifeCycles'" :class="getTabClasses('lifeCycles')">
          {{ t('itemForm.tab.lifecycles') }}
        </button>
      </nav>
    </div>

    <!--div v-if="editMode" class="text-xs text-gray-400 p-2 bg-gray-50 rounded mb-4">
      DEBUG: type="{{ formData.type }}" | status="{{ formData.status }}" | itemType="{{ formData.itemType }}"
    </div-->

    <!-- Item Details Form -->
    <form v-if="activeTab === 'details'" @submit.prevent="handleSubmit" class="p-8 space-y-8">
      <!-- Basic Information -->
      <div class="space-y-6">
        <div class="border-l-4 border-blue-500 pl-4">
          <h3 class="text-lg font-semibold text-gray-900">{{ t('itemForm.section.basicInfo') }}</h3>
          <p class="text-sm text-gray-500 mt-1">{{ t('itemForm.section.basicInfoSub') }}</p>
        </div>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <Input
            v-model="formData.itemNumber"
            :label="t('itemForm.field.itemNumber')"
            :placeholder="t('itemForm.placeholder.itemNumber')"
            required
            :disabled="editMode"
            :error="errors.itemNumber"
          />

          <Select
            v-model="formData.itemType"
            :label="t('itemForm.field.itemType')"
            required
            :error="errors.itemType"
          >
            <option value="">{{ t('itemForm.option.selectItemType') }}</option>
            <option v-for="type in itemTypes" :key="type.value" :value="type.value">
              {{ type.label }}
            </option>
          </Select>

          <Select
            v-if="formData.itemType === 'container'"
            v-model="formData.type"
            :label="t('itemForm.field.type')"
            required
            :error="errors.type"
          >
            <option value="">{{ t('itemForm.option.selectContainerType') }}</option>
            <option v-for="c in activeIsoCodes" :key="c.code" :value="c.code">
              {{ c.code }} — {{ c.description }}
            </option>
          </Select>
          <Input
            v-else
            v-model="formData.type"
            :label="t('itemForm.field.type')"
            :placeholder="formData.itemType === 'vehicle' ? t('itemForm.placeholder.vehicleType') : t('itemForm.placeholder.type')"
            required
            :error="errors.type"
          />

          <div>
            <ThirdPartyAutocomplete
              v-model="formData.ownerId"
              :label="t('itemForm.field.ownerId')"
              required
              :placeholder="t('itemForm.placeholder.ownerId')"
              :input-class="getInputClasses('ownerId')"
            />
            <p v-if="errors.ownerId" class="text-sm text-red-600 mt-1">{{ errors.ownerId }}</p>
          </div>

          <Input v-model="formData.position" :label="t('itemForm.field.position')" :placeholder="t('itemForm.placeholder.position')" />

          <Select v-model="formData.status" :label="t('itemForm.field.status')">
            <option value="Available">{{ t('itemForm.status.available') }}</option>
            <option value="In Use">{{ t('itemForm.status.inUse') }}</option>
            <option value="Maintenance">{{ t('itemForm.status.maintenance') }}</option>
            <option value="Out of Service">{{ t('itemForm.status.outOfService') }}</option>
          </Select>
        </div>
      </div>

      <!-- Inspection Information -->
      <div class="space-y-6">
        <div class="border-l-4 border-green-500 pl-4">
          <h3 class="text-lg font-semibold text-gray-900">{{ t('itemForm.section.inspection') }}</h3>
          <p class="text-sm text-gray-500 mt-1">{{ t('itemForm.section.inspectionSub') }}</p>
        </div>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <Input
            v-model="formData.lastInspection"
            :label="t('itemForm.field.lastInspection')"
            type="date"
          />
          <Input
            v-model="formData.nextInspection"
            :label="t('itemForm.field.nextInspection')"
            type="date"
            :error="errors.nextInspection"
          />
        </div>
      </div>

      <!-- Additional Information -->
      <div class="space-y-6">
        <div class="border-l-4 border-purple-500 pl-4">
          <h3 class="text-lg font-semibold text-gray-900">{{ t('itemForm.section.additional') }}</h3>
          <p class="text-sm text-gray-500 mt-1">{{ t('itemForm.section.additionalSub') }}</p>
        </div>

        <div class="space-y-2">
          <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.notes') }}</label>
          <textarea v-model="formData.notes" rows="4" :class="getInputClasses('notes')"
            :placeholder="t('itemForm.placeholder.notes')"></textarea>
        </div>
      </div>

      <!-- Form Actions -->
      <div class="flex justify-end space-x-4 pt-6 border-t border-gray-100">
        <button type="button" @click="emit('cancel')"
          class="px-6 py-3 border border-gray-200 rounded-xl text-sm font-medium text-gray-700 hover:bg-gray-50 hover:border-gray-300 transition-all duration-200">
          {{ t('common.cancel') }}
        </button>
        <button type="submit"
          class="px-6 py-3 bg-blue-600 border border-blue-600 rounded-xl text-sm font-medium text-white hover:bg-blue-700 hover:border-blue-700 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 shadow-sm">
          {{ editMode ? t('itemForm.button.saveChanges') : t('itemForm.button.createItem') }}
        </button>
      </div>
    </form>

    <!-- Port Details Tab -->
    <div v-else-if="activeTab === 'portDetails'" class="p-8 space-y-8">
      <div class="border-l-4 border-teal-500 pl-4">
        <h3 class="text-lg font-semibold text-gray-900">{{ t('itemForm.tab.portDetails') }}</h3>
        <p class="text-sm text-gray-500 mt-1">{{ t('itemForm.section.portDetailsSub') }}</p>
      </div>

      <!-- Commercial Information -->
      <div class="space-y-6">
        <div class="border-l-4 border-amber-500 pl-4">
          <h4 class="text-base font-semibold text-gray-900">{{ t('itemForm.section.commercial') }}</h4>
          <p class="text-xs text-gray-400 mt-0.5">{{ t('itemForm.section.commercialSub') }}</p>
        </div>
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.category') }}</label>
            <select
              v-model="portDetails.category"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all bg-white text-gray-900"
            >
              <option value="">{{ t('itemForm.option.notDefined') }}</option>
              <option value="Import">{{ t('itemForm.category.import') }}</option>
              <option value="Export">{{ t('itemForm.category.export') }}</option>
              <option value="Transship">{{ t('itemForm.category.transship') }}</option>
            </select>
          </div>
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.freightKind') }}</label>
            <select
              v-model="portDetails.freightKind"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all bg-white text-gray-900"
            >
              <option value="">{{ t('itemForm.option.notDefined') }}</option>
              <option value="FCL">{{ t('itemForm.freightKind.fcl') }}</option>
              <option value="LCL">{{ t('itemForm.freightKind.lcl') }}</option>
              <option value="Empty">{{ t('itemForm.freightKind.empty') }}</option>
              <option value="Breakbulk">{{ t('itemForm.freightKind.breakbulk') }}</option>
              <option value="Ro-Ro">{{ t('itemForm.freightKind.roro') }}</option>
            </select>
          </div>
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.bookingNumber') }}</label>
            <input
              v-model="portDetails.bookingNumber"
              type="text"
              :placeholder="t('itemForm.placeholder.bookingNumber')"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all bg-white text-gray-900 placeholder-gray-400"
            />
          </div>
          <div class="space-y-2">
            <!-- empty cell for alignment -->
          </div>
          <div class="space-y-2">
            <ThirdPartyAutocomplete
              v-model="portDetails.shipperName"
              :label="t('itemForm.field.shipperName')"
              :placeholder="t('itemForm.placeholder.shipperName')"
            />
          </div>
          <div class="space-y-2">
            <ThirdPartyAutocomplete
              v-model="portDetails.consigneeName"
              :label="t('itemForm.field.consigneeName')"
              :placeholder="t('itemForm.placeholder.consigneeName')"
            />
          </div>
        </div>
      </div>

      <!-- Container Identification -->
      <div class="space-y-6">
        <div class="border-l-4 border-blue-500 pl-4">
          <h4 class="text-base font-semibold text-gray-900">{{ t('itemForm.section.containerId') }}</h4>
        </div>
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.containerNumber') }}</label>
            <input
              v-model="portDetails.containerNumber"
              type="text"
              :placeholder="t('itemForm.placeholder.containerNumber')"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900 placeholder-gray-500"
            />
          </div>
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.containerType') }}</label>
            <select
              v-model="portDetails.containerType"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900"
            >
              <option value="">{{ t('itemForm.option.selectContainerType') }}</option>
              <option v-for="c in activeIsoCodes" :key="c.code" :value="c.code">
                {{ c.code }} — {{ c.description }}
              </option>
            </select>
          </div>
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.emptyStatus') }}</label>
            <select
              v-model="portDetails.emptyStatus"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900"
            >
              <option value="">{{ t('itemForm.emptyStatus.unknown') }}</option>
              <option value="FULL">{{ t('itemForm.emptyStatus.full') }}</option>
              <option value="EMPTY">{{ t('itemForm.emptyStatus.empty') }}</option>
              <option value="UNKNOWN">{{ t('itemForm.emptyStatus.unknown') }}</option>
            </select>
          </div>
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.condition') }}</label>
            <select
              v-model="portDetails.condition"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900"
            >
              <option value="">{{ t('itemForm.option.selectCondition') }}</option>
              <option value="GOOD">{{ t('itemForm.condition.good') }}</option>
              <option value="DAMAGED">{{ t('itemForm.condition.damaged') }}</option>
              <option value="NEEDS_REPAIR">{{ t('itemForm.condition.needsRepair') }}</option>
            </select>
          </div>
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.customsStatus') }}</label>
            <select
              v-model="portDetails.customsStatus"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900"
            >
              <option value="">{{ t('itemForm.option.selectCustomsStatus') }}</option>
              <option value="PENDING">PENDING</option>
              <option value="CLEARED">CLEARED</option>
              <option value="HELD">HELD</option>
              <option value="INSPECTED">INSPECTED</option>
              <option value="RELEASED">RELEASED</option>
              <option value="REFUSED">REFUSED</option>
            </select>
          </div>
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.sealNumbers') }}</label>
            <input
              v-model="portDetails.sealNumbersRaw"
              type="text"
              :placeholder="t('itemForm.placeholder.sealNumbers')"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900 placeholder-gray-500"
            />
          </div>
        </div>
      </div>

      <!-- Flags -->
      <div class="space-y-6">
        <div class="border-l-4 border-orange-500 pl-4">
          <h4 class="text-base font-semibold text-gray-900">{{ t('itemForm.section.specialFlags') }}</h4>
        </div>
        <div class="space-y-4">
          <!-- Hazmat -->
          <div class="flex items-center space-x-3">
            <input
              id="hazmatFlag"
              v-model="portDetails.hazmatFlag"
              type="checkbox"
              class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
            />
            <label for="hazmatFlag" class="text-sm font-medium text-gray-700">{{ t('itemForm.flag.hazmat') }}</label>
          </div>
          <div v-if="portDetails.hazmatFlag" class="ml-7 grid grid-cols-1 gap-4 lg:grid-cols-2">
            <div class="space-y-2">
              <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.hazmatClass') }}</label>
              <input
                v-model="portDetails.hazmatClass"
                type="text"
                :placeholder="t('itemForm.placeholder.hazmatClass')"
                class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900 placeholder-gray-500"
              />
            </div>
            <div class="space-y-2">
              <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.unNumber') }}</label>
              <input
                v-model="portDetails.unNumber"
                type="text"
                :placeholder="t('itemForm.placeholder.unNumber')"
                class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900 placeholder-gray-500"
              />
            </div>
          </div>

          <!-- Reefer -->
          <div class="flex items-center space-x-3">
            <input
              id="reeferFlag"
              v-model="portDetails.reeferFlag"
              type="checkbox"
              class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
            />
            <label for="reeferFlag" class="text-sm font-medium text-gray-700">{{ t('itemForm.flag.reefer') }}</label>
          </div>
          <div v-if="portDetails.reeferFlag" class="ml-7">
            <div class="space-y-2 max-w-xs">
              <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.reeferTemperature') }}</label>
              <input
                v-model.number="portDetails.reeferTemperature"
                type="number"
                step="0.5"
                :placeholder="t('itemForm.placeholder.reeferTemperature')"
                class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900 placeholder-gray-500"
              />
            </div>
          </div>

          <!-- OOG -->
          <div class="flex items-center space-x-3">
            <input
              id="oogFlag"
              v-model="portDetails.oogFlag"
              type="checkbox"
              class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
            />
            <label for="oogFlag" class="text-sm font-medium text-gray-700">{{ t('itemForm.flag.oog') }}</label>
          </div>

          <!-- Weight Verified -->
          <div class="flex items-center space-x-3">
            <input
              id="weightVerified"
              v-model="portDetails.weightVerified"
              type="checkbox"
              class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
            />
            <label for="weightVerified" class="text-sm font-medium text-gray-700">{{ t('itemForm.flag.weightVerified') }}</label>
          </div>
          <div v-if="portDetails.weightVerified" class="ml-7">
            <div class="space-y-2 max-w-xs">
              <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.verifiedWeight') }}</label>
              <input
                v-model.number="portDetails.verifiedWeight"
                type="number"
                min="0"
                step="1"
                :placeholder="t('itemForm.placeholder.verifiedWeight')"
                class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900 placeholder-gray-500"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Voyage & Gate -->
      <div class="space-y-6">
        <div class="border-l-4 border-indigo-500 pl-4">
          <h4 class="text-base font-semibold text-gray-900">{{ t('itemForm.section.voyageGate') }}</h4>
        </div>
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.inboundVoyage') }}</label>
            <input
              v-model="portDetails.inboundVoyage"
              type="text"
              :placeholder="t('itemForm.placeholder.inboundVoyage')"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900 placeholder-gray-500"
            />
          </div>
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.outboundVoyage') }}</label>
            <input
              v-model="portDetails.outboundVoyage"
              type="text"
              :placeholder="t('itemForm.placeholder.outboundVoyage')"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900 placeholder-gray-500"
            />
          </div>
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.gateInDate') }}</label>
            <input
              v-model="portDetails.gateInDate"
              type="datetime-local"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900"
            />
          </div>
          <div class="space-y-2">
            <label class="block text-sm font-medium text-gray-700">{{ t('itemForm.field.gateOutDate') }}</label>
            <input
              v-model="portDetails.gateOutDate"
              type="datetime-local"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 bg-white text-gray-900"
            />
          </div>
        </div>
      </div>

      <!-- Form Actions -->
      <div class="flex justify-end space-x-4 pt-6 border-t border-gray-100">
        <button type="button" @click="emit('cancel')"
          class="px-6 py-3 border border-gray-200 rounded-xl text-sm font-medium text-gray-700 hover:bg-gray-50 hover:border-gray-300 transition-all duration-200">
          {{ t('common.cancel') }}
        </button>
        <button type="button" @click="handleSubmit"
          class="px-6 py-3 bg-blue-600 border border-blue-600 rounded-xl text-sm font-medium text-white hover:bg-blue-700 hover:border-blue-700 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 shadow-sm">
          {{ editMode ? t('itemForm.button.saveChanges') : t('itemForm.button.createItem') }}
        </button>
      </div>
    </div>

    <!-- Lifecycles View - IMPROVED VERSION -->
    <div v-else-if="activeTab === 'lifeCycles'" class="p-8">
      <div class="border-l-4 border-indigo-500 pl-4 mb-6">
        <h3 class="text-lg font-semibold text-gray-900">{{ t('itemForm.section.itemLifecycles') }}</h3>
        <p class="text-sm text-gray-500 mt-1">{{ t('itemForm.section.itemLifecyclesSub') }}</p>
      </div>

      <!-- Lifecycle Summary Cards -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <div class="bg-blue-50 border border-blue-200 rounded-lg p-4">
          <div class="flex items-center">
            <div class="flex-shrink-0">
              <Clock class="h-8 w-8 text-blue-600" />
            </div>
            <div class="ml-3">
              <p class="text-sm font-medium text-blue-900">{{ t('itemForm.lifecycle.active') }}</p>
              <p class="text-2xl font-bold text-blue-600">
                {{itemData.lifeCycles?.filter(lc => lc.status === 'In Progress').length || 0}}
              </p>
            </div>
          </div>
        </div>

        <div class="bg-green-50 border border-green-200 rounded-lg p-4">
          <div class="flex items-center">
            <div class="flex-shrink-0">
              <Calendar class="h-8 w-8 text-green-600" />
            </div>
            <div class="ml-3">
              <p class="text-sm font-medium text-green-900">{{ t('itemForm.lifecycle.completed') }}</p>
              <p class="text-2xl font-bold text-green-600">
                {{itemData.lifeCycles?.filter(lc => lc.status === 'Completed').length || 0}}
              </p>
            </div>
          </div>
        </div>

        <div class="bg-gray-50 border border-gray-200 rounded-lg p-4">
          <div class="flex items-center">
            <div class="flex-shrink-0">
              <CircleDot class="h-8 w-8 text-gray-600" />
            </div>
            <div class="ml-3">
              <p class="text-sm font-medium text-gray-900">{{ t('itemForm.lifecycle.total') }}</p>
              <p class="text-2xl font-bold text-gray-600">
                {{ itemData.lifeCycles?.length || 0 }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Lifecycle List -->
      <div class="bg-white border border-gray-200 rounded-lg">
        <!-- Header -->
        <div class="px-6 py-4 border-b border-gray-200 bg-gray-50">
          <div class="flex items-center justify-between">
            <h4 class="text-sm font-medium text-gray-900">{{ t('itemForm.lifecycle.historyTitle') }}</h4>
            <div class="flex items-center space-x-2">
              <span class="text-xs text-gray-500">
                {{ t('itemForm.lifecycle.totalCount', { count: itemData.lifeCycles?.length || 0 }) }}
              </span>
            </div>
          </div>
        </div>

        <!-- No lifecycles -->
        <div v-if="!itemData.lifeCycles?.length" class="px-6 py-12 text-center">
          <Calendar class="mx-auto h-12 w-12 text-gray-400 mb-4" />
          <p class="text-sm text-gray-500">{{ t('itemForm.lifecycle.empty') }}</p>
          <p class="text-xs text-gray-400 mt-1">{{ t('itemForm.lifecycle.emptyHint') }}</p>
        </div>

        <!-- Lifecycle items -->
        <div class="divide-y divide-gray-200">
          <div v-for="(lifecycle, index) in itemData.lifeCycles" :key="lifecycle.lifecycleId || lifecycle.id || index"
            class="px-6 py-4 hover:bg-gray-50">
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-4">
                <!-- Status Indicator -->
                <div class="flex-shrink-0">
                  <div :class="{
                    'h-3 w-3 rounded-full': true,
                    'bg-blue-500': lifecycle.status === 'In Progress',
                    'bg-green-500': lifecycle.status === 'Completed',
                    'bg-red-500': lifecycle.status === 'Cancelled',
                    'bg-gray-400': !lifecycle.status
                  }"></div>
                </div>

                <!-- Lifecycle Info -->
                <div class="flex-1 min-w-0">
                  <div class="flex items-center space-x-3">
                    <span :class="{
                      'px-2 py-1 text-xs font-medium rounded-full': true,
                      'bg-blue-100 text-blue-800': lifecycle.status === 'In Progress',
                      'bg-green-100 text-green-800': lifecycle.status === 'Completed',
                      'bg-red-100 text-red-800': lifecycle.status === 'Cancelled',
                      'bg-gray-100 text-gray-800': !lifecycle.status
                    }">
                      {{ lifecycle.status || 'Unknown' }}
                    </span>

                    <span class="text-sm text-gray-500">
                      {{ (lifecycle.lifecycleId || lifecycle.id) ? `ID: ${(lifecycle.lifecycleId ||
                        lifecycle.id).substring(0, 8)}...` : `Lifecycle ${index + 1}` }}
                    </span>
                  </div>

                  <!-- Dates -->
                  <div class="mt-1 flex items-center space-x-4 text-sm text-gray-500">
                    <span v-if="lifecycle.startTime">
                      {{ t('itemForm.lifecycle.started', { date: new Date(lifecycle.startTime).toLocaleDateString() }) }}
                    </span>
                    <span v-if="lifecycle.endTime">
                      {{ t('itemForm.lifecycle.ended', { date: new Date(lifecycle.endTime).toLocaleDateString() }) }}
                    </span>
                    <span v-if="!lifecycle.endTime && lifecycle.status === 'In Progress'">
                      <Clock class="inline h-3 w-3 mr-1" />
                      {{ t('itemForm.lifecycle.ongoing') }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- Event Count -->
              <div class="flex items-center space-x-2 text-sm text-gray-500">
                <span class="flex items-center">
                  <CircleDot class="h-4 w-4 mr-1" />
                  {{ t('itemForm.lifecycle.eventCount', { count: lifecycle.events?.length || 0 }) }}
                </span>
              </div>
            </div>

            <!-- Events Preview -->
            <div v-if="lifecycle.events?.length" class="mt-3 ml-7">
              <details class="group">
                <summary class="cursor-pointer text-sm text-blue-600 hover:text-blue-800 flex items-center">
                  <ChevronRight class="h-4 w-4 transition-transform group-open:rotate-90" />
                  <span class="ml-1">{{ t('itemForm.lifecycle.viewEvents', { count: lifecycle.events.length }) }}</span>
                </summary>

                <div class="mt-2 space-y-2 pl-5 border-l-2 border-gray-200">
                  <div v-for="event in lifecycle.events" :key="event.id" class="flex items-start space-x-2 text-sm">
                    <div :class="{
                      'mt-0.5 h-2 w-2 rounded-full': true,
                      'bg-green-500': event.eventType === 'IN',
                      'bg-red-500': event.eventType === 'OUT',
                      'bg-blue-500': event.eventType === 'INTERMEDIATE',
                      'bg-gray-400': !event.eventType
                    }"></div>
                    <div class="flex-1">
                      <div class="flex items-center justify-between">
                        <span class="font-medium text-gray-900">
                          {{ event.eventType || event.type || t('itemForm.lifecycle.event') }}
                        </span>
                        <span class="text-gray-500">
                          {{ event.timestamp ? new Date(event.timestamp).toLocaleString() : (event.eventDate ? new
                            Date(event.eventDate).toLocaleString() : t('itemForm.lifecycle.noDate')) }}
                        </span>
                      </div>
                      <p v-if="event.notes" class="text-gray-600 mt-1">{{ event.notes }}</p>
                      <p v-if="event.location" class="text-gray-500">{{ t('itemForm.lifecycle.location', { location: event.location }) }}</p>
                    </div>
                  </div>
                </div>
              </details>
            </div>
          </div>
        </div>
      </div>

      <!-- Form Actions -->
      <div class="flex justify-end space-x-4 pt-6 border-t border-gray-100 mt-8">
        <button type="button" @click="emit('cancel')"
          class="px-6 py-3 border border-gray-200 rounded-xl text-sm font-medium text-gray-700 hover:bg-gray-50 hover:border-gray-300 transition-all duration-200">
          {{ t('common.cancel') }}
        </button>
        <button type="button" @click="activeTab = 'details'"
          class="px-6 py-3 bg-blue-600 border border-blue-600 rounded-xl text-sm font-medium text-white hover:bg-blue-700 hover:border-blue-700 focus:ring-2 focus:ring-blue-500/20 transition-all duration-200 shadow-sm">
          {{ t('itemForm.button.backToDetails') }}
        </button>
      </div>
    </div>
  </div>
</template>