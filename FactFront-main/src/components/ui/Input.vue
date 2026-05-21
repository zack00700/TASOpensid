<template>
  <label class="block">
    <span v-if="label" class="block text-xs font-medium text-gray-700 mb-1">
      {{ label }}
      <span v-if="required" class="text-red-500">*</span>
    </span>
    <div class="relative">
      <component
        :is="iconLeading"
        v-if="iconLeading"
        class="h-4 w-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"
      />
      <input
        ref="inputEl"
        :value="modelValue"
        :type="type"
        :placeholder="placeholder"
        :disabled="disabled"
        :readonly="readonly"
        :required="required"
        :inputmode="inputmode"
        :autocomplete="autocomplete"
        :class="[
          'block w-full border rounded-lg py-2 text-sm bg-white transition-colors',
          'focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500',
          iconLeading ? 'pl-9' : 'pl-3',
          'pr-3',
          hasError ? 'border-red-300 focus:ring-red-500 focus:border-red-500' : 'border-gray-300',
          disabled && 'bg-gray-50 cursor-not-allowed opacity-70'
        ]"
        @input="onInput"
        @blur="$emit('blur', $event)"
        @focus="$emit('focus', $event)"
        @keyup.enter="$emit('enter', $event)"
      />
    </div>
    <p v-if="error" class="mt-1 text-xs text-red-600">{{ error }}</p>
    <p v-else-if="hint" class="mt-1 text-xs text-gray-500">{{ hint }}</p>
  </label>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import type { LucideIcon } from 'lucide-vue-next';

const props = withDefaults(
  defineProps<{
    modelValue: string | number | null;
    label?: string;
    type?: string;
    placeholder?: string;
    error?: string;
    hint?: string;
    required?: boolean;
    disabled?: boolean;
    readonly?: boolean;
    iconLeading?: LucideIcon;
    inputmode?: 'text' | 'numeric' | 'decimal' | 'tel' | 'email' | 'url' | 'search';
    autocomplete?: string;
  }>(),
  {
    type: 'text',
    autocomplete: 'off',
  },
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'blur', event: FocusEvent): void;
  (e: 'focus', event: FocusEvent): void;
  (e: 'enter', event: KeyboardEvent): void;
}>();

const inputEl = ref<HTMLInputElement | null>(null);
const hasError = computed(() => !!props.error);

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value);
}

defineExpose({
  focus: () => inputEl.value?.focus(),
});
</script>
