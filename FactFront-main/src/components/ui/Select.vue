<template>
  <label class="block">
    <span v-if="label" class="block text-xs font-medium text-gray-700 mb-1">
      {{ label }}
      <span v-if="required" class="text-red-500">*</span>
    </span>
    <div class="relative">
      <select
        :value="modelValue"
        :disabled="disabled"
        :class="[
          'block w-full appearance-none border rounded-lg py-2 pl-3 pr-9 text-sm bg-white transition-colors',
          'focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500',
          hasError ? 'border-red-300 focus:ring-red-500 focus:border-red-500' : 'border-gray-300',
          disabled && 'bg-gray-50 cursor-not-allowed opacity-70'
        ]"
        @change="onChange"
      >
        <slot />
      </select>
      <ChevronDown class="h-4 w-4 text-gray-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
    </div>
    <p v-if="error" class="mt-1 text-xs text-red-600">{{ error }}</p>
    <p v-else-if="hint" class="mt-1 text-xs text-gray-500">{{ hint }}</p>
  </label>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { ChevronDown } from 'lucide-vue-next';

const props = defineProps<{
  modelValue: string | number | null;
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
