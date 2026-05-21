<template>
  <div class="tp-ac">
    <label v-if="label" class="block text-sm font-medium text-gray-700 mb-1">
      {{ label }}<span v-if="required" class="text-red-500 ml-0.5">*</span>
    </label>
    <TypeaheadInput
      :model-value="modelValue"
      :suggestions="suggestions"
      :placeholder="placeholder"
      :disabled="disabled"
      :input-class="inputClass"
      @update:model-value="onInput"
      @select="onSelect"
      @blur="touched = true"
    />
    <button
      v-if="showCreateFooter"
      type="button"
      class="mt-1 text-sm text-blue-600 hover:underline"
      @click="openCreate"
    >
      + {{ t('thirdPartyAutocomplete.create.button', { name: modelValue }) }}
    </button>
    <p v-if="errorMessage" class="mt-1 text-sm text-red-600">{{ errorMessage }}</p>
    <CreateThirdPartyModal
      :open="modalOpen"
      :initial-name="modelValue"
      :initial-industry-type="industryType"
      @close="modalOpen = false"
      @created="onCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import TypeaheadInput from './TypeaheadInput.vue';
import CreateThirdPartyModal from './CreateThirdPartyModal.vue';
import { useThirdParty } from '../../composables/use.third-party';
import { useAuthStore } from '../../stores/authStore';
import type { ThirdParty } from '../../types/third-party';

type IndustryType =
  | 'Shipping Line'
  | 'Freight Forwarder'
  | 'Customs Broker'
  | 'Terminal Operator'
  | 'Trucking Company'
  | 'Other';

const props = defineProps<{
  modelValue: string;
  label?: string;
  industryType?: IndustryType;
  required?: boolean;
  disabled?: boolean;
  placeholder?: string;
  inputClass?: string | Record<string, boolean>;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'select', value: ThirdParty): void;
}>();

const { t } = useI18n();
const { thirdParties } = useThirdParty();
const auth = useAuthStore();
const canCreate = computed(() => auth.isAdmin());

const touched = ref(false);

const filtered = computed<ThirdParty[]>(() => {
  const all = thirdParties.value ?? [];
  return props.industryType
    ? all.filter((tp) => tp.industryType === props.industryType)
    : all;
});

const suggestions = computed<string[]>(() =>
  filtered.value.map((tp) => tp.companyName).filter(Boolean),
);

const isValidSelection = computed(() => {
  const v = props.modelValue?.trim();
  if (!v) return !props.required;
  return filtered.value.some((tp) => tp.companyName === v);
});

const errorMessage = computed(() =>
  touched.value && props.required && !isValidSelection.value
    ? t('thirdPartyAutocomplete.error.unknown')
    : '',
);

const showCreateFooter = computed(
  () =>
    canCreate.value &&
    !!props.modelValue?.trim() &&
    !filtered.value.some((tp) => tp.companyName === props.modelValue.trim()),
);

const modalOpen = ref(false);
function openCreate() {
  modalOpen.value = true;
}

function onCreated(tp: ThirdParty) {
  emit('update:modelValue', tp.companyName);
  emit('select', tp);
}

function onInput(v: string) {
  emit('update:modelValue', v);
}

function onSelect(v: string) {
  const match = filtered.value.find((tp) => tp.companyName === v);
  if (match) emit('select', match);
}
</script>
