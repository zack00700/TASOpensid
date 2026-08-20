<template>
  <label class="block">
    <span v-if="label" class="block text-[11px] font-medium uppercase tracking-[0.05em] text-tide-ink/60 mb-1.5">
      {{ label }}
      <span v-if="required" class="text-red-500">*</span>
    </span>
    <div class="relative">
      <select
        :value="modelValue"
        :disabled="disabled"
        :class="[
          'block w-full appearance-none border rounded-tide-btn py-2 pl-3 pr-9 text-sm bg-[rgba(255,253,247,0.75)] text-tide-ink transition-colors',
          'focus:outline-none focus:ring-1 focus:ring-tide-blue focus:border-tide-blue',
          hasError ? 'border-tide-rust/50 focus:ring-tide-rust focus:border-tide-rust' : 'border-[rgba(60,50,35,0.14)]',
          disabled && 'bg-[rgba(42,36,30,0.04)] cursor-not-allowed opacity-70'
        ]"
        @change="onChange"
      >
        <slot />
      </select>
      <ChevronDown class="h-4 w-4 text-tide-ink/40 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
    </div>
    <p v-if="error" class="mt-1 text-xs text-tide-rust-deep">{{ error }}</p>
    <p v-else-if="hint" class="mt-1 text-xs text-tide-ink/50">{{ hint }}</p>
  </label>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { ChevronDown } from 'lucide-vue-next';

const props = defineProps<{
  modelValue: string | number | null | undefined;
  label?: string;
  error?: string;
  hint?: string;
  required?: boolean;
  disabled?: boolean;
}>();

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>();

const hasError = computed(() => !!props.error);

function onChange(event: Event) {
  emit('update:modelValue', (event.target as HTMLSelectElement).value);
}
</script>
