<template>
  <div class="relative">
    <input
      :value="modelValue ?? ''"
      type="datetime-local"
      :min="min"
      :disabled="disabled"
      class="block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 pr-16"
      v-bind="$attrs"
      @input="onInput"
    />
    <button
      type="button"
      :disabled="disabled"
      :data-test="nowDataTest"
      class="absolute right-1 top-1/2 -translate-y-1/2 text-xs font-medium px-2 py-0.5 rounded text-blue-700 hover:bg-blue-50 disabled:opacity-50 disabled:cursor-not-allowed"
      @click="setNow"
    >
      {{ t('common.now') }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, useAttrs } from 'vue';
import { useI18n } from 'vue-i18n';

defineOptions({ inheritAttrs: false });

defineProps<{
  modelValue: string | null | undefined;
  disabled?: boolean;
  min?: string;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
}>();

const { t } = useI18n();
const attrs = useAttrs();

const nowDataTest = computed(() => {
  const dt = attrs['data-test'];
  return typeof dt === 'string' ? `${dt}-now` : undefined;
});

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value);
}

function setNow() {
  // datetime-local expects local time; toISOString() returns UTC, so we
  // shift by the local TZ offset before slicing to YYYY-MM-DDTHH:MM.
  const now = new Date();
  const tzOffsetMs = now.getTimezoneOffset() * 60_000;
  const local = new Date(now.getTime() - tzOffsetMs).toISOString().slice(0, 16);
  emit('update:modelValue', local);
}
</script>
