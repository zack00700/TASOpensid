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
  'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 disabled:pointer-events-none';

const variantClass = computed<string>(() => {
  switch (props.variant) {
    case 'primary':
      return 'bg-blue-600 text-white border border-transparent shadow-sm hover:bg-blue-700 focus-visible:ring-blue-500';
    case 'secondary':
      return 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50 focus-visible:ring-blue-500';
    case 'danger':
      return 'bg-red-600 text-white border border-transparent shadow-sm hover:bg-red-700 focus-visible:ring-red-500';
    case 'ghost':
      return 'bg-transparent text-gray-700 border border-transparent hover:bg-gray-100 focus-visible:ring-gray-400';
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
