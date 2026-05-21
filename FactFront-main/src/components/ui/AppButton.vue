<script setup lang="ts">
defineProps<{
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost' | 'outline'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
  disabled?: boolean
  type?: 'button' | 'submit' | 'reset'
}>()
</script>

<template>
  <button
    :type="type ?? 'button'"
    :disabled="disabled || loading"
    :class="[
      // base
      'inline-flex items-center justify-center gap-2 font-medium rounded-lg transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2',
      // disabled
      (disabled || loading) && 'opacity-50 cursor-not-allowed',
      // sizes
      size === 'sm' && 'px-3 py-1.5 text-xs',
      (!size || size === 'md') && 'px-4 py-2 text-sm',
      size === 'lg' && 'px-5 py-2.5 text-base',
      // variants
      (!variant || variant === 'primary') && 'bg-blue-600 text-white hover:bg-blue-700 focus-visible:ring-blue-500',
      variant === 'secondary' && 'bg-white text-slate-700 border border-slate-300 hover:bg-slate-50 focus-visible:ring-slate-400',
      variant === 'danger' && 'bg-red-600 text-white hover:bg-red-700 focus-visible:ring-red-500',
      variant === 'ghost' && 'text-slate-600 hover:bg-slate-100 focus-visible:ring-slate-400',
      variant === 'outline' && 'border border-blue-600 text-blue-600 hover:bg-blue-50 focus-visible:ring-blue-500',
    ]"
  >
    <svg v-if="loading" class="animate-spin h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24">
      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
    <slot />
  </button>
</template>
