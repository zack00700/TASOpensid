<script setup lang="ts">
import { ref, onMounted, watch, inject, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import ContractRateManagement from './ContractRateManagement.vue';
import FilterBuilder from './FilterBuilder.vue';

const { t } = useI18n();
import {
  X,
  Check,
  AlertCircle,
  Search,
  Calendar,
  Settings,
  FileText,
  Calculator,
  Filter,
  ChevronRight,
  Info,
  Link,
  User
} from 'lucide-vue-next';
import type { EventConfig } from '../types/event-config';
import type { CalcFilter } from '../types/calc-filter';
import type { Tariff } from '../types/contrat';
import { validateContractDates, normalizeContractFormSubmit, formatDay } from '../utils/contract';

interface Contract {
  id: string;
  name: string;
  description: string;
  calculationMode: {
    type: string;
    subType: string;
    eventConfig: EventConfig | null;
    parameters: Record<string, any>;
    filters: CalcFilter[];
  };
  status: 'Active' | 'Disable';
  startDate: string;
  endDate: string;
  rates: any[];
  // N4 extensions
  customerId?: string;
  customerName?: string;
  priority?: number;
  tariffId?: string;
}

const props = defineProps<{
  editMode?: boolean;
  initialData?: Contract;
  loading?: boolean;
}>();

const emit = defineEmits<{
  (e: 'submit', data: any): void;
  (e: 'cancel'): void;
}>();

const $axios = inject<any>('$axios');

// Form Steps
const currentStep = ref(1);
const totalSteps = 4;

const steps = computed(() => [
  { id: 1, name: t('contractForm.step.eventSelection'), icon: Search, description: t('contractForm.step.eventSelectionDesc') },
  { id: 2, name: t('contractForm.step.basicInformation'), icon: FileText, description: t('contractForm.step.basicInformationDesc') },
  { id: 3, name: t('contractForm.step.calculationSetup'), icon: Calculator, description: t('contractForm.step.calculationSetupDesc') },
  { id: 4, name: t('contractForm.step.rateManagement'), icon: Settings, description: t('contractForm.step.rateManagementDesc') }
]);

const formatDate = (value: string) => formatDay(value) ?? '';

// Initialize formData with default values
const formData = ref<Contract>({
  id: '',
  name: '',
  description: '',
  calculationMode: {
    type: 'Quantity',
    subType: 'quantity',
    eventConfig: null,
    parameters: {},
    filters: []
  },
  status: 'Active',
  startDate: '',
  endDate: '',
  rates: [],
  customerId: '',
  customerName: '',
  priority: 0,
  tariffId: ''
});

// Tariff state
const availableTariffs = ref<Tariff[]>([]);
const tariffLoading = ref(false);
const tariffLoadError = ref('');

const fetchTariffs = async () => {
  tariffLoading.value = true;
  tariffLoadError.value = '';
  try {
    const response = await $axios.get('/tariffs');
    availableTariffs.value = response.data;
  } catch {
    tariffLoadError.value = t('contractForm.error.failedToLoadTariffs');
    availableTariffs.value = [];
  } finally {
    tariffLoading.value = false;
  }
};

const selectedTariff = computed(() =>
  availableTariffs.value.find(t => t.id === formData.value.tariffId) ?? null
);

const errors = ref<Record<string, string>>({});
const eventSearch = ref('');
const eventSuggestions = ref<EventConfig[]>([]);
const showSuggestions = ref(false);
const highlightedIndex = ref(-1);
const noResults = ref(false);
let searchTimeout: any = null;

function calcTypeLabel(type: string): string {
  switch (type) {
    case 'Quantity': return t('contractForm.calcType.Quantity');
    case 'Date': return t('contractForm.calcType.Date');
    case 'DateByTEU': return t('contractForm.calcType.DateByTEU');
    case 'Special': return t('contractForm.calcType.Special');
    case 'Tiered': return t('contractForm.calcType.Tiered');
    case 'Banded': return t('contractForm.calcType.Banded');
    default: return type;
  }
}

const calculationModes = computed(() => ({
  Quantity: [
    { value: 'quantity', label: t('contractForm.calcMode.quantity'), description: t('contractForm.calcModeDesc.quantity') },
    { value: 'bl_volume', label: t('contractForm.calcMode.blVolume'), description: t('contractForm.calcModeDesc.blVolume') },
    { value: 'bl_weight', label: t('contractForm.calcMode.blWeight'), description: t('contractForm.calcModeDesc.blWeight') },
    { value: 'yard_item_teu', label: t('contractForm.calcMode.yardItemTeu'), description: t('contractForm.calcModeDesc.yardItemTeu') }
  ],
  Date: [
    { value: 'call_date', label: t('contractForm.calcMode.callDate'), description: t('contractForm.calcModeDesc.callDate') },
    { value: 'event_date', label: t('contractForm.calcMode.eventDate'), description: t('contractForm.calcModeDesc.eventDate') },
    { value: 'in_date', label: t('contractForm.calcMode.inDate'), description: t('contractForm.calcModeDesc.inDate') },
    { value: 'last_sling_date', label: t('contractForm.calcMode.lastSlingDate'), description: t('contractForm.calcModeDesc.lastSlingDate') },
    { value: 'unloading_date', label: t('contractForm.calcMode.unloadingDate'), description: t('contractForm.calcModeDesc.unloadingDate') },
    { value: 'vessel_arrival_date', label: t('contractForm.calcMode.vesselArrivalDate'), description: t('contractForm.calcModeDesc.vesselArrivalDate') },
    { value: 'vessel_availability_date', label: t('contractForm.calcMode.vesselAvailabilityDate'), description: t('contractForm.calcModeDesc.vesselAvailabilityDate') }
  ],
  DateByTEU: [
    { value: 'call_date_teu', label: t('contractForm.calcMode.callDateTeu'), description: t('contractForm.calcModeDesc.callDateTeu') },
    { value: 'event_date_teu', label: t('contractForm.calcMode.eventDateTeu'), description: t('contractForm.calcModeDesc.eventDateTeu') },
    { value: 'in_date_teu', label: t('contractForm.calcMode.inDateTeu'), description: t('contractForm.calcModeDesc.inDateTeu') }
  ],
  Special: [
    { value: 'latest_in_date_bl', label: t('contractForm.calcMode.latestInDateBl'), description: t('contractForm.calcModeDesc.latestInDateBl') },
    { value: 'event_date_weight_volume', label: t('contractForm.calcMode.eventDateWeightVolume'), description: t('contractForm.calcModeDesc.eventDateWeightVolume') }
  ],
  Tiered: [
    { value: 'quantity', label: t('contractForm.calcMode.quantity'), description: t('contractForm.calcModeDesc.tieredQuantity') },
    { value: 'date', label: t('contractForm.calcMode.daysInToday'), description: t('contractForm.calcModeDesc.tieredDaysInToday') },
    { value: 'date_by_teu', label: t('contractForm.calcMode.daysInOut'), description: t('contractForm.calcModeDesc.tieredDaysInOut') },
    { value: 'bl_volume', label: t('contractForm.calcMode.blVolume'), description: t('contractForm.calcModeDesc.tieredBlVolume') },
    { value: 'bl_weight', label: t('contractForm.calcMode.blWeight'), description: t('contractForm.calcModeDesc.tieredBlWeight') }
  ],
  Banded: [
    { value: 'quantity', label: t('contractForm.calcMode.quantity'), description: t('contractForm.calcModeDesc.bandedQuantity') },
    { value: 'date', label: t('contractForm.calcMode.daysInToday'), description: t('contractForm.calcModeDesc.bandedDaysInToday') },
    { value: 'date_by_teu', label: t('contractForm.calcMode.daysInOut'), description: t('contractForm.calcModeDesc.bandedDaysInOut') },
    { value: 'bl_volume', label: t('contractForm.calcMode.blVolume'), description: t('contractForm.calcModeDesc.bandedBlVolume') },
    { value: 'bl_weight', label: t('contractForm.calcMode.blWeight'), description: t('contractForm.calcModeDesc.bandedBlWeight') }
  ]
}));

// Separate validation logic (pure function) from error state mutation
const getValidationErrors = (step: number) => {
  const stepErrors: Record<string, string> = {};

  switch (step) {
    case 1: // Event Selection
      if (!formData.value.calculationMode.eventConfig) {
        stepErrors.eventConfig = t('contractForm.validation.selectValidEvent');
      }
      break;

    case 2: // Basic Information
      if (!formData.value.name) {
        stepErrors.name = t('contractForm.validation.nameRequired');
      }
      if (!formData.value.startDate) {
        stepErrors.startDate = t('contractForm.validation.startDateRequired');
      }
      if (!formData.value.endDate) {
        stepErrors.endDate = t('contractForm.validation.endDateRequired');
      }
      if (!formData.value.status || !['Active', 'Disable'].includes(formData.value.status)) {
        stepErrors.status = t('contractForm.validation.statusRequired');
      }
      if (validateContractDates(formData.value.startDate, formData.value.endDate) === 'endAfterStart') {
        stepErrors.endDate = t('contractForm.validation.endAfterStart');
      }
      break;

    case 3: // Calculation Setup
      // Basic validation already covered by having type/subtype
      break;

    case 4: // Rate Management
      // Optional validation can be added here
      break;
  }

  return stepErrors;
};

// Computed properties
const completedSteps = computed(() => {
  const completed = [];
  
  // Step 1: Event Selection
  if (formData.value.calculationMode.eventConfig) {
    completed.push(1);
  }
  
  // Step 2: Basic Information
  if (formData.value.name && formData.value.startDate && formData.value.endDate && formData.value.status) {
    completed.push(2);
  }
  
  // Step 3: Calculation Setup (always considered complete if we have type/subtype)
  if (formData.value.calculationMode.type && formData.value.calculationMode.subType) {
    completed.push(3);
  }
  
  // Step 4: Rate Management (optional but let's mark as complete if we have at least setup)
  completed.push(4);
  
  return completed;
});

const canProceedToStep = (step: number) => {
  switch (step) {
    case 1: return true;
    case 2: return formData.value.calculationMode.eventConfig !== null;
    case 3: return formData.value.name && formData.value.startDate && formData.value.endDate;
    case 4: return formData.value.calculationMode.type && formData.value.calculationMode.subType;
    default: return false;
  }
};

const isStepValid = (step: number) => completedSteps.value.includes(step);

// Computed property for current step validation
const currentStepErrors = computed(() => getValidationErrors(currentStep.value));
const isCurrentStepValid = computed(() => Object.keys(currentStepErrors.value).length === 0);

// Function to actually set errors and validate (only called on user actions)
const validateCurrentStep = () => {
  const stepErrors = getValidationErrors(currentStep.value);
  errors.value = stepErrors;
  return Object.keys(stepErrors).length === 0;
};

const fetchEvents = async (query: string) => {
  try {
    const response = await $axios.get('/event', { params: { q: query } });
    eventSuggestions.value = response.data;
    noResults.value = response.data.length === 0;
  } catch {
    eventSuggestions.value = [];
    noResults.value = true;
  }
};

const selectEvent = (event: EventConfig) => {
  formData.value.calculationMode.eventConfig = event;
  eventSearch.value = event.eventName;
  showSuggestions.value = false;
  highlightedIndex.value = -1;
  noResults.value = false;
  errors.value.eventConfig = '';
};

const handleEventKeydown = (e: KeyboardEvent) => {
  if (!showSuggestions.value || eventSuggestions.value.length === 0) return;
  if (e.key === 'ArrowDown') {
    e.preventDefault();
    highlightedIndex.value = (highlightedIndex.value + 1) % eventSuggestions.value.length;
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    highlightedIndex.value = (highlightedIndex.value - 1 + eventSuggestions.value.length) % eventSuggestions.value.length;
  } else if (e.key === 'Enter') {
    e.preventDefault();
    if (highlightedIndex.value >= 0) {
      selectEvent(eventSuggestions.value[highlightedIndex.value]);
    }
  }
};

const hideSuggestions = () => {
  setTimeout(() => (showSuggestions.value = false), 100);
};

const nextStep = () => {
  if (validateCurrentStep() && currentStep.value < totalSteps) {
    currentStep.value++;
    // Clear errors when moving to next step
    errors.value = {};
  }
};

const prevStep = () => {
  if (currentStep.value > 1) {
    currentStep.value--;
    // Clear errors when moving to previous step
    errors.value = {};
  }
};

const goToStep = (step: number) => {
  if (canProceedToStep(step)) {
    currentStep.value = step;
    // Clear errors when jumping to a step
    errors.value = {};
  }
};

const handleSubmit = () => {
  // Validate all steps
  for (let i = 1; i <= totalSteps; i++) {
    currentStep.value = i;
    if (!validateCurrentStep()) {
      return;
    }
  }

  const payload = normalizeContractFormSubmit(formData.value);

  emit('submit', payload);
};

const handleRatesUpdate = (newRates: any[]) => {
  formData.value.rates = newRates;
};

const getInputClasses = (fieldName: string) => {
  return {
    'block w-full rounded-lg border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 transition-colors duration-200': true,
    'border-red-300 focus:border-red-500 focus:ring-red-500': errors.value[fieldName]
  };
};

const getStepClasses = (step: number) => {
  const isActive = currentStep.value === step;
  const isCompleted = completedSteps.value.includes(step);
  const canAccess = canProceedToStep(step);
  
  return {
    'flex items-center px-4 py-3 rounded-lg cursor-pointer transition-all duration-200': true,
    'bg-blue-50 border-2 border-blue-200 text-blue-700': isActive,
    'bg-green-50 border-2 border-green-200 text-green-700 hover:bg-green-100': isCompleted && !isActive,
    'bg-gray-50 border-2 border-gray-200 text-gray-400 cursor-not-allowed': !canAccess && !isCompleted && !isActive,
    'bg-white border-2 border-gray-200 text-gray-600 hover:bg-gray-50': canAccess && !isCompleted && !isActive
  };
};

// Watch for event search changes
watch(eventSearch, (val, oldVal) => {
  // Don't clear eventConfig if we're just setting it programmatically during initialization
  // or if the search value matches the current selected event name
  const currentEventName = formData.value.calculationMode.eventConfig?.eventName;
  
  if (val === currentEventName) {
    // This is likely a programmatic set during initialization, don't clear
    return;
  }
  
  // Clear event config only when user is actually searching for a different event
  formData.value.calculationMode.eventConfig = null;
  errors.value.eventConfig = '';
  
  if (searchTimeout) clearTimeout(searchTimeout);
  if (!val) {
    eventSuggestions.value = [];
    noResults.value = false;
    return;
  }
  
  searchTimeout = setTimeout(() => {
    fetchEvents(val);
    showSuggestions.value = true;
  }, 300);
});

// Watch for changes to form data and update errors reactively but safely
watch([
  () => formData.value.calculationMode.eventConfig,
  () => formData.value.name,
  () => formData.value.startDate,
  () => formData.value.endDate,
  () => formData.value.status,
], () => {
  // Only update errors if we currently have errors for this step
  // This prevents validation during initial render but allows clearing errors when user fixes issues
  if (Object.keys(errors.value).length > 0) {
    const stepErrors = getValidationErrors(currentStep.value);
    errors.value = stepErrors;
  }
}, { deep: true });

onMounted(async () => {
  // Pre-fetch tariffs so Step 4 dropdown is ready
  fetchTariffs();

  if (props.editMode && props.initialData) {
    // First, set up the basic form data
    formData.value = {
      ...props.initialData,
      startDate: formatDate(props.initialData.startDate),
      endDate: formatDate(props.initialData.endDate),
      calculationMode: {
        ...props.initialData.calculationMode,
        filters: props.initialData.calculationMode?.filters || [],
        eventConfig: null // Reset this first, we'll set it properly below
      },
      rates: props.initialData.rates || [],
      customerId: props.initialData.customerId ?? '',
      customerName: props.initialData.customerName ?? '',
      priority: props.initialData.priority ?? 0,
      tariffId: props.initialData.tariffId ?? ''
    };

    // Handle event configuration
    const eventConfig = props.initialData.calculationMode?.eventConfig;

    if (eventConfig) {
      // The eventConfig should already be a complete object with eventName
      if (eventConfig.eventName && eventConfig.id) {
        formData.value.calculationMode.eventConfig = { ...eventConfig };
        eventSearch.value = eventConfig.eventName;
      }
      // Fallback: if eventConfig exists but missing eventName, try to fetch
      else if (eventConfig.id && !eventConfig.eventName) {
        try {
          const res = await $axios.get(`/event/${eventConfig.id}`);
          formData.value.calculationMode.eventConfig = res.data;
          eventSearch.value = res.data.eventName;
        } catch (error) {
          console.error('Failed to fetch event details:', error);
          // Set partial data and let user reselect
          formData.value.calculationMode.eventConfig = eventConfig;
        }
      }
    }

    // Edit mode opens on the first wizard step (overview / event config). Jumping straight
    // to step 4 (rates) on edit was disorienting — testers expected to land on the same
    // first screen as create, with all data pre-populated.
    currentStep.value = 1;
  }
});
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      
      <!-- Header -->
      <div class="bg-white rounded-lg shadow-sm border border-gray-200 mb-8">
        <div class="px-6 py-4 border-b border-gray-200">
          <div class="flex justify-between items-center">
            <div>
              <h1 class="text-2xl font-bold text-gray-900">
                {{ props.editMode ? t('contractForm.header.editTitle') : t('contractForm.header.newTitle') }}
              </h1>
              <p class="mt-1 text-sm text-gray-600">
                {{ props.editMode ? t('contractForm.header.editSubtitle') : t('contractForm.header.newSubtitle') }}
              </p>
            </div>
            <button
              @click="emit('cancel')"
              class="p-2 text-gray-400 hover:text-gray-500 hover:bg-gray-100 rounded-lg transition-colors"
            >
              <X class="h-6 w-6" />
            </button>
          </div>
        </div>

        <!-- Progress Steps -->
        <div class="px-6 py-4">
          <div class="grid grid-cols-1 md:grid-cols-4 gap-3">
            <div
              v-for="step in steps"
              :key="step.id"
              @click="goToStep(step.id)"
              :class="getStepClasses(step.id)"
            >
              <div class="flex items-center space-x-3">
                <div class="flex-shrink-0">
                  <div 
                    :class="[
                      'w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium',
                      currentStep === step.id ? 'bg-blue-600 text-white' :
                      completedSteps.includes(step.id) ? 'bg-green-600 text-white' :
                      canProceedToStep(step.id) ? 'bg-gray-200 text-gray-600' :
                      'bg-gray-100 text-gray-400'
                    ]"
                  >
                    <component 
                      :is="completedSteps.includes(step.id) ? Check : step.icon" 
                      class="h-4 w-4" 
                    />
                  </div>
                </div>
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-medium">{{ step.name }}</p>
                  <p class="text-xs opacity-75 hidden sm:block">{{ step.description }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Form Content -->
      <div class="bg-white rounded-lg shadow-sm border border-gray-200">
        <form @submit.prevent="handleSubmit" class="p-6">
          
          <!-- Step 1: Event Selection -->
          <div v-if="currentStep === 1" class="space-y-6">
            <div class="text-center mb-8">
              <Search class="mx-auto h-12 w-12 text-blue-500 mb-4" />
              <h2 class="text-xl font-semibold text-gray-900">{{ t('contractForm.selectEvent') }}</h2>
              <p class="mt-2 text-gray-600">{{ t('contractForm.selectEventDesc') }}</p>
            </div>

            <div class="max-w-md mx-auto">
              <div class="relative">
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  {{ t('contractForm.eventSearch') }} <span class="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  v-model="eventSearch"
                  @focus="showSuggestions = true"
                  @input="showSuggestions = true"
                  @keydown="handleEventKeydown"
                  @blur="hideSuggestions"
                  :class="getInputClasses('eventConfig')"
                  :placeholder="t('contractForm.placeholder.searchForEvents')"
                />
                <ul
                  v-if="showSuggestions && (eventSuggestions.length > 0 || noResults)"
                  class="absolute z-10 mt-1 w-full bg-white border border-gray-300 rounded-lg shadow-lg max-h-60 overflow-auto"
                >
                  <li
                    v-for="(event, index) in eventSuggestions"
                    :key="event.id"
                    @mousedown.prevent="selectEvent(event)"
                    :class="[
                      'cursor-pointer px-4 py-3 hover:bg-gray-50',
                      { 'bg-blue-50 text-blue-700': index === highlightedIndex }
                    ]"
                  >
                    <div class="font-medium">{{ event.eventName }}</div>
                    <div class="text-sm text-gray-500">{{ event.eventType || t('contractForm.standardEvent') }}</div>
                  </li>
                  <li v-if="noResults" class="px-4 py-3 text-sm text-gray-500 text-center">
                    {{ t('contractForm.noEventsFound') }}
                  </li>
                </ul>

                <!-- Selected Event Display -->
                <div v-if="formData.calculationMode.eventConfig" class="mt-4 p-4 bg-green-50 border border-green-200 rounded-lg">
                  <div class="flex items-center space-x-3">
                    <Check class="h-5 w-5 text-green-600" />
                    <div>
                      <p class="font-medium text-green-900">{{ formData.calculationMode.eventConfig.eventName }}</p>
                      <p class="text-sm text-green-700">{{ formData.calculationMode.eventConfig.eventType || t('contractForm.standardEvent') }}</p>
                    </div>
                  </div>
                </div>

                <p v-if="errors.eventConfig" class="mt-2 text-sm text-red-600 flex items-center">
                  <AlertCircle class="h-4 w-4 mr-1" />
                  {{ errors.eventConfig }}
                </p>
              </div>
            </div>
          </div>

          <!-- Step 2: Basic Information -->
          <div v-else-if="currentStep === 2" class="space-y-6">
            <div class="text-center mb-8">
              <FileText class="mx-auto h-12 w-12 text-blue-500 mb-4" />
              <h2 class="text-xl font-semibold text-gray-900">{{ t('contractForm.basicInformation') }}</h2>
              <p class="mt-2 text-gray-600">{{ t('contractForm.basicInformationDesc') }}</p>
            </div>

            <div class="max-w-2xl mx-auto space-y-6">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  {{ t('contractForm.contractName') }} <span class="text-red-500">*</span>
                </label>
                <input
                  v-model="formData.name"
                  type="text"
                  :class="getInputClasses('name')"
                  :placeholder="t('contractForm.placeholder.contractName')"
                />
                <p v-if="errors.name" class="mt-2 text-sm text-red-600 flex items-center">
                  <AlertCircle class="h-4 w-4 mr-1" />
                  {{ errors.name }}
                </p>
              </div>

              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  {{ t('contractForm.description') }}
                </label>
                <textarea
                  v-model="formData.description"
                  rows="3"
                  :class="getInputClasses('description')"
                  :placeholder="t('contractForm.placeholder.description')"
                ></textarea>
              </div>

              <!-- Customer Information -->
              <div class="border border-gray-200 rounded-lg p-5 space-y-4">
                <div class="flex items-center space-x-2 mb-1">
                  <User class="h-5 w-5 text-gray-400" />
                  <h3 class="text-sm font-semibold text-gray-700">{{ t('contractForm.customerInformation') }}</h3>
                  <span class="text-xs text-gray-400 ml-1">{{ t('contractForm.optionalParen') }}</span>
                </div>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">
                      {{ t('contractForm.customerName') }}
                    </label>
                    <input
                      v-model="formData.customerName"
                      type="text"
                      :class="getInputClasses('customerName')"
                      :placeholder="t('contractForm.placeholder.customerName')"
                    />
                  </div>
                  <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">
                      {{ t('contractForm.customerId') }}
                    </label>
                    <input
                      v-model="formData.customerId"
                      type="text"
                      :class="getInputClasses('customerId')"
                      :placeholder="t('contractForm.placeholder.customerId')"
                    />
                  </div>
                </div>
              </div>

              <!-- Priority -->
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  {{ t('contractForm.priority') }}
                </label>
                <input
                  v-model.number="formData.priority"
                  type="number"
                  min="0"
                  max="100"
                  :class="getInputClasses('priority')"
                  placeholder="0"
                />
                <p class="mt-1.5 text-xs text-gray-500 flex items-center space-x-1">
                  <Info class="h-3.5 w-3.5 flex-shrink-0" />
                  <span>{{ t('contractForm.priorityHint') }}</span>
                </p>
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-2">
                    {{ t('contractForm.startDate') }} <span class="text-red-500">*</span>
                  </label>
                  <div class="relative">
                    <input
                      v-model="formData.startDate"
                      type="date"
                      :class="getInputClasses('startDate')"
                    />
                    <Calendar class="absolute right-3 top-3 h-5 w-5 text-gray-400 pointer-events-none" />
                  </div>
                  <p v-if="errors.startDate" class="mt-2 text-sm text-red-600 flex items-center">
                    <AlertCircle class="h-4 w-4 mr-1" />
                    {{ errors.startDate }}
                  </p>
                </div>

                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-2">
                    {{ t('contractForm.endDate') }} <span class="text-red-500">*</span>
                  </label>
                  <div class="relative">
                    <input
                      v-model="formData.endDate"
                      type="date"
                      :min="formData.startDate"
                      :class="getInputClasses('endDate')"
                    />
                    <Calendar class="absolute right-3 top-3 h-5 w-5 text-gray-400 pointer-events-none" />
                  </div>
                  <p v-if="errors.endDate" class="mt-2 text-sm text-red-600 flex items-center">
                    <AlertCircle class="h-4 w-4 mr-1" />
                    {{ errors.endDate }}
                  </p>
                </div>
              </div>

              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  {{ t('contractForm.status') }} <span class="text-red-500">*</span>
                </label>
                <div class="grid grid-cols-2 gap-4">
                  <label class="relative flex items-center p-4 border rounded-lg cursor-pointer hover:bg-gray-50">
                    <input
                      v-model="formData.status"
                      type="radio"
                      value="Active"
                      class="h-4 w-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                    />
                    <div class="ml-3">
                      <div class="text-sm font-medium text-gray-900">{{ t('contractForm.statusOption.active') }}</div>
                      <div class="text-sm text-gray-500">{{ t('contractForm.statusOption.activeDesc') }}</div>
                    </div>
                  </label>
                  <label class="relative flex items-center p-4 border rounded-lg cursor-pointer hover:bg-gray-50">
                    <input
                      v-model="formData.status"
                      type="radio"
                      value="Disable"
                      class="h-4 w-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                    />
                    <div class="ml-3">
                      <div class="text-sm font-medium text-gray-900">{{ t('contractForm.statusOption.disabled') }}</div>
                      <div class="text-sm text-gray-500">{{ t('contractForm.statusOption.disabledDesc') }}</div>
                    </div>
                  </label>
                </div>
                <p v-if="errors.status" class="mt-2 text-sm text-red-600 flex items-center">
                  <AlertCircle class="h-4 w-4 mr-1" />
                  {{ errors.status }}
                </p>
              </div>
            </div>
          </div>

          <!-- Step 3: Calculation Setup -->
          <div v-else-if="currentStep === 3" class="space-y-6">
            <div class="text-center mb-8">
              <Calculator class="mx-auto h-12 w-12 text-blue-500 mb-4" />
              <h2 class="text-xl font-semibold text-gray-900">{{ t('contractForm.calculationConfiguration') }}</h2>
              <p class="mt-2 text-gray-600">{{ t('contractForm.calculationConfigurationDesc') }}</p>
            </div>

            <div class="max-w-2xl mx-auto space-y-8">
              <!-- Calculation Mode Selection -->
              <div class="space-y-4">
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-3">
                    {{ t('contractForm.calculationType') }}
                  </label>
                  <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div
                      v-for="type in Object.keys(calculationModes)"
                      :key="type"
                      @click="formData.calculationMode.type = type; formData.calculationMode.subType = calculationModes[type][0].value"
                      :class="[
                        'relative flex items-center p-4 border-2 rounded-lg cursor-pointer transition-colors',
                        formData.calculationMode.type === type
                          ? 'border-blue-500 bg-blue-50'
                          : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
                      ]"
                    >
                      <div class="flex-1">
                        <div class="text-sm font-medium text-gray-900">{{ calcTypeLabel(type) }}</div>
                        <div class="text-sm text-gray-500">
                          {{ t('contractForm.optionsAvailable', calculationModes[type].length, { count: calculationModes[type].length }) }}
                        </div>
                      </div>
                      <div v-if="formData.calculationMode.type === type" class="ml-3">
                        <Check class="h-5 w-5 text-blue-600" />
                      </div>
                    </div>
                  </div>
                </div>

                <div v-if="formData.calculationMode.type">
                  <label class="block text-sm font-medium text-gray-700 mb-3">
                    {{ t('contractForm.calculationMethod') }}
                  </label>
                  <div class="space-y-3">
                    <div
                      v-for="mode in calculationModes[formData.calculationMode.type]"
                      :key="mode.value"
                      @click="formData.calculationMode.subType = mode.value"
                      :class="[
                        'relative flex items-start p-4 border-2 rounded-lg cursor-pointer transition-colors',
                        formData.calculationMode.subType === mode.value
                          ? 'border-blue-500 bg-blue-50'
                          : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
                      ]"
                    >
                      <div class="flex-1 min-w-0">
                        <div class="text-sm font-medium text-gray-900">{{ mode.label }}</div>
                        <div class="text-sm text-gray-500 mt-1">{{ mode.description }}</div>
                      </div>
                      <div v-if="formData.calculationMode.subType === mode.value" class="ml-3 flex-shrink-0">
                        <Check class="h-5 w-5 text-blue-600" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Filters Section -->
              <div class="border-t border-gray-200 pt-8">
                <div class="flex items-center space-x-2 mb-4">
                  <Filter class="h-5 w-5 text-gray-400" />
                  <h3 class="text-lg font-medium text-gray-900">{{ t('contractForm.calculationFilters') }}</h3>
                  <div class="flex items-center text-sm text-gray-500">
                    <Info class="h-4 w-4 mr-1" />
                    {{ t('contractForm.optional') }}
                  </div>
                </div>
                <p class="text-sm text-gray-600 mb-4">
                  {{ t('contractForm.calculationFiltersDesc') }}
                </p>
                <FilterBuilder v-model="formData.calculationMode.filters" />
              </div>
            </div>
          </div>

          <!-- Step 4: Rate Management -->
          <div v-else-if="currentStep === 4" class="space-y-6">
            <div class="text-center mb-8">
              <Settings class="mx-auto h-12 w-12 text-blue-500 mb-4" />
              <h2 class="text-xl font-semibold text-gray-900">{{ t('contractForm.rateConfiguration') }}</h2>
              <p class="mt-2 text-gray-600">{{ t('contractForm.rateConfigurationDesc') }}</p>
            </div>

            <!-- Tariff Link Section -->
            <div class="max-w-2xl mx-auto border border-gray-200 rounded-lg p-5">
              <div class="flex items-center space-x-2 mb-4">
                <Link class="h-5 w-5 text-gray-400" />
                <h3 class="text-sm font-semibold text-gray-700">{{ t('contractForm.linkToTariff') }}</h3>
                <span class="text-xs text-gray-400 ml-1">{{ t('contractForm.optionalParen') }}</span>
              </div>
              <p class="text-sm text-gray-600 mb-4">
                {{ t('contractForm.linkToTariffDesc') }}
              </p>

              <div v-if="tariffLoadError" class="mb-3 flex items-start space-x-2 text-sm text-yellow-700 bg-yellow-50 border border-yellow-200 rounded-lg px-3 py-2">
                <AlertCircle class="h-4 w-4 flex-shrink-0 mt-0.5" />
                <span>{{ tariffLoadError }}</span>
              </div>

              <div class="space-y-3">
                <label class="block text-sm font-medium text-gray-700">
                  {{ t('contractForm.selectTariff') }}
                </label>
                <div class="relative">
                  <select
                    v-model="formData.tariffId"
                    :disabled="tariffLoading"
                    class="block w-full rounded-lg border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 transition-colors duration-200 disabled:bg-gray-50 disabled:text-gray-400"
                  >
                    <option value="">{{ t('contractForm.noTariffOption') }}</option>
                    <option
                      v-for="tariff in availableTariffs"
                      :key="tariff.id"
                      :value="tariff.id"
                    >
                      {{ tariff.name }}
                      <template v-if="tariff.serviceType"> · {{ tariff.serviceType }}</template>
                    </option>
                  </select>
                  <span v-if="tariffLoading" class="absolute right-9 top-2.5 text-xs text-gray-400">{{ t('contractForm.loadingEllipsis') }}</span>
                </div>

                <!-- Tariff selected notice -->
                <div v-if="selectedTariff" class="flex items-start space-x-2 text-sm text-blue-700 bg-blue-50 border border-blue-200 rounded-lg px-3 py-2">
                  <Info class="h-4 w-4 flex-shrink-0 mt-0.5" />
                  <div>
                    <span class="font-medium">{{ t('contractForm.tariffLinkedNotice') }}</span>
                    <span v-if="selectedTariff.description" class="block text-blue-600 text-xs mt-0.5">{{ selectedTariff.description }}</span>
                    <span class="block text-blue-600 text-xs mt-0.5">
                      {{ t('contractForm.tariffLinkedIgnoreRates') }}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Embedded rate management (always visible so existing data is preserved) -->
            <div :class="['max-w-full transition-opacity duration-200', selectedTariff ? 'opacity-50 pointer-events-none' : '']">
              <div v-if="selectedTariff" class="max-w-2xl mx-auto mb-3 flex items-center space-x-2 text-xs text-gray-400">
                <Info class="h-3.5 w-3.5 flex-shrink-0" />
                <span>{{ t('contractForm.rateManagementDisabledNotice') }}</span>
              </div>
              <ContractRateManagement
                :contract-id="formData.id"
                :calculation-type="formData.calculationMode.type"
                :rates="formData.rates"
                @update:rates="handleRatesUpdate"
              />
            </div>
          </div>

          <!-- Navigation -->
          <div class="flex justify-between items-center pt-8 border-t border-gray-200 mt-8">
            <button
              v-if="currentStep > 1"
              type="button"
              @click="prevStep"
              class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              {{ t('contractForm.button.previous') }}
            </button>
            <div v-else></div>

            <div class="flex items-center space-x-2">
              <span class="text-sm text-gray-500">
                {{ t('contractForm.stepOf', { current: currentStep, total: totalSteps }) }}
              </span>
            </div>

            <div class="flex space-x-3">
              <button
                type="button"
                @click="emit('cancel')"
                class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
              >
                {{ t('common.cancel') }}
              </button>

              <button
                v-if="currentStep < totalSteps"
                type="button"
                @click="nextStep"
                :disabled="!isCurrentStepValid"
                :class="[
                  'inline-flex items-center px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500',
                  isCurrentStepValid
                    ? 'bg-blue-600 hover:bg-blue-700'
                    : 'bg-gray-400 cursor-not-allowed'
                ]"
              >
                {{ t('contractForm.button.next') }}
                <ChevronRight class="ml-2 h-4 w-4" />
              </button>

              <button
                v-else
                type="submit"
                :disabled="props.loading"
                :class="[
                  'inline-flex items-center px-6 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500',
                  props.loading ? 'bg-blue-400 cursor-not-allowed' : 'bg-blue-600 hover:bg-blue-700'
                ]"
              >
                {{ props.loading ? t('contractForm.button.saving') : (props.editMode ? t('contractForm.button.saveChanges') : t('contractForm.button.createContract')) }}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>