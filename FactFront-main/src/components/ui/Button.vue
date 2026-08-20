<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :class="[base, variantClass, sizeClass, block && 'w-full', disabled && 'opacity-50 cursor-not-allowed']"
    @click="$emit('click', $event)"
  >
    <Loader2 v-if="loading" class="w-4 h-4 animate-spin" :class="$slots.default ? 'mr-2' : ''" />
    <slot />
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Loader2 } from 'lucide-vue-next';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';
type Size = 'sm' | 'md' | 'lg';

const props = withDefaults(
  defineProps<{
    variant?: Variant;
    size?: Size;
    type?: 'button' | 'submit' | 'reset';
    disabled?: boolean;
    loading?: boolean;
    block?: boolean;
  }>(),
  {
    variant: 'primary',
    size: 'md',
    type: 'button',
  },
);

defineEmits<{ (e: 'click', event: MouseEvent): void }>();

const base =
  'inline-flex items-center justify-center gap-2 rounded-tide-btn font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 disabled:pointer-events-none';

const variantClass = computed<string>(() => {
  switch (props.variant) {
    case 'primary':
      return 'bg-gradient-to-br from-tide-blue-btn to-tide-blue-btn-deep text-tide-paper border border-[rgba(60,50,35,0.15)] shadow-[0_1px_0_rgba(255,255,255,0.30)_inset,0_8px_20px_-8px_rgba(74,117,147,0.45)] hover:from-[#7aa1bd] hover:to-[#5786a0] focus-visible:ring-tide-blue';
    case 'secondary':
      return 'bg-gradient-to-br from-[rgba(255,253,248,0.78)] to-[rgba(252,246,235,0.55)] border border-[rgba(60,50,35,0.12)] shadow-[0_1px_0_rgba(255,255,255,0.65)_inset,0_2px_6px_-2px_rgba(60,50,35,0.10)] text-tide-ink hover:from-[rgba(255,253,248,0.95)] hover:to-[rgba(252,246,235,0.80)] focus-visible:ring-tide-blue';
    case 'danger':
      return 'bg-gradient-to-br from-tide-rust to-tide-rust-deep text-tide-paper border border-[rgba(60,50,35,0.15)] shadow-[0_8px_20px_-8px_rgba(181,99,88,0.45)] hover:brightness-105 focus-visible:ring-tide-rust';
    case 'ghost':
      return 'bg-transparent text-tide-ink/75 border border-transparent hover:bg-[rgba(255,253,247,0.55)] hover:text-tide-ink focus-visible:ring-tide-blue';
  }
});

const sizeClass = computed<string>(() => {
  switch (props.size) {
    case 'sm':
      return 'px-3 py-1.5 text-sm';
    case 'md':
      return 'px-4 py-2 text-sm';
    case 'lg':
      return 'px-5 py-2.5 text-base';
  }
});
</script>
