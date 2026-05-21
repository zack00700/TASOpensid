<template>
  <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
    <form
      class="bg-white rounded-lg shadow-xl p-6 w-full max-w-md space-y-4"
      @submit.prevent="onSubmit"
    >
      <h2 class="text-lg font-semibold text-gray-900">{{ t('createThirdPartyModal.title') }}</h2>

      <div>
        <label class="block text-sm font-medium text-gray-700">
          {{ t('createThirdPartyModal.field.companyName') }}<span class="text-red-500 ml-0.5">*</span>
        </label>
        <input
          name="companyName"
          v-model="form.companyName"
          required
          class="w-full px-3 py-2 border border-gray-300 rounded"
        />
      </div>

      <div>
        <label class="block text-sm font-medium text-gray-700">
          {{ t('createThirdPartyModal.field.industryType') }}<span class="text-red-500 ml-0.5">*</span>
        </label>
        <select
          name="industryType"
          v-model="form.industryType"
          required
          class="w-full px-3 py-2 border border-gray-300 rounded"
        >
          <option v-for="opt in industryOptions" :key="opt" :value="opt">{{ opt }}</option>
        </select>
      </div>

      <div>
        <label class="block text-sm font-medium text-gray-700">
          {{ t('createThirdPartyModal.field.companyAddress') }}<span class="text-red-500 ml-0.5">*</span>
        </label>
        <input
          name="companyAddress"
          v-model="form.companyAddress"
          required
          class="w-full px-3 py-2 border border-gray-300 rounded"
        />
      </div>

      <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

      <div class="flex justify-end gap-2">
        <button type="button" class="px-3 py-2 text-gray-700" @click="emit('close')">
          {{ t('createThirdPartyModal.cancel') }}
        </button>
        <button
          type="submit"
          :disabled="saving"
          class="px-3 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
        >
          {{ t('createThirdPartyModal.submit') }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useThirdParty } from '../../composables/use.third-party';
import type { ThirdParty } from '../../types/third-party';

const props = defineProps<{
  open: boolean;
  initialName?: string;
  initialIndustryType?: string;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'created', tp: ThirdParty): void;
}>();

const { t } = useI18n();
const { createMinimal } = useThirdParty();

const industryOptions = [
  'Shipping Line',
  'Freight Forwarder',
  'Customs Broker',
  'Terminal Operator',
  'Trucking Company',
  'Other',
];

const form = reactive({
  companyName: props.initialName ?? '',
  industryType: props.initialIndustryType ?? '',
  companyAddress: '',
});

watch(
  () => [props.initialName, props.initialIndustryType, props.open],
  () => {
    if (props.open) {
      form.companyName = props.initialName ?? '';
      form.industryType = props.initialIndustryType ?? '';
      form.companyAddress = '';
      error.value = '';
    }
  },
);

const saving = ref(false);
const error = ref('');

async function onSubmit() {
  saving.value = true;
  error.value = '';
  try {
    const created = await createMinimal({
      companyName: form.companyName.trim(),
      industryType: form.industryType,
      companyAddress: form.companyAddress.trim(),
    });
    emit('created', created);
    emit('close');
  } catch (e) {
    error.value = (e as Error).message || t('createThirdPartyModal.error.generic');
  } finally {
    saving.value = false;
  }
}
</script>
