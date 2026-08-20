<template>
  <label class="block">
    <span v-if="label" class="block text-[11px] font-medium uppercase tracking-[0.05em] text-tide-ink/60 mb-1.5">
      {{ label }}
      <span v-if="required" class="text-red-500">*</span>
    </span>
    <div class="relative">
      <component
        :is="iconLeading"
        v-if="iconLeading"
        class="h-4 w-4 text-tide-ink/40 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"
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
          'block w-full border rounded-tide-btn py-2 text-sm bg-[rgba(255,253,247,0.75)] text-tide-ink transition-colors',
          'focus:outline-none focus:ring-1 focus:ring-tide-blue focus:border-tide-blue',
          iconLeading ? 'pl-9' : 'pl-3',
          'pr-3',
          hasError ? 'border-tide-rust/50 focus:ring-tide-rust focus:border-tide-rust' : 'border-[rgba(60,50,35,0.14)]',
          disabled && 'bg-[rgba(42,36,30,0.04)] cursor-not-allowed opacity-70'
        ]"
        @input="onInput"
        @blur="$emit('blur', $event)"
        @focus="$emit('focus', $event)"
        @keyup.enter="$emit('enter', $event)"
      />
    </div>
    <p v-if="error" class="mt-1 text-xs text-tide-rust-deep">{{ error }}</p>
    <p v-else-if="hint" class="mt-1 text-xs text-tide-ink/50">{{ hint }}</p>
  </label>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import type { FunctionalComponent } from 'vue';

const props = withDefaults(
  defineProps<{
    modelValue: string | number | null | undefined;
    label?: string;
    type?: string;
    placeholder?: string;
    error?: string;
    hint?: string;
    required?: boolean;
    disabled?: boolean;
    readonly?: boolean;
    iconLeading?: FunctionalComponent<any>;
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
