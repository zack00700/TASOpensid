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
      'inline-flex items-center justify-center gap-2 font-medium rounded-tide-btn transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2',
      // disabled
      (disabled || loading) && 'opacity-50 cursor-not-allowed',
      // sizes
      size === 'sm' && 'px-3 py-1.5 text-xs',
      (!size || size === 'md') && 'px-4 py-2 text-sm',
      size === 'lg' && 'px-5 py-2.5 text-base',
      // variants
      (!variant || variant === 'primary') && 'bg-gradient-to-br from-tide-blue-btn to-tide-blue-btn-deep text-tide-paper hover:brightness-105 focus-visible:ring-tide-blue',
      variant === 'secondary' && 'bg-[rgba(255,253,247,0.75)] text-tide-ink border border-[rgba(60,50,35,0.12)] hover:bg-[rgba(255,253,247,0.95)] focus-visible:ring-tide-blue',
      variant === 'danger' && 'bg-gradient-to-br from-tide-rust to-tide-rust-deep text-tide-paper hover:brightness-105 focus-visible:ring-tide-rust',
      variant === 'ghost' && 'text-tide-ink/70 hover:bg-[rgba(255,253,247,0.6)] focus-visible:ring-tide-blue',
      variant === 'outline' && 'border border-tide-blue text-tide-blue-deep hover:bg-[rgba(90,138,171,0.10)] focus-visible:ring-tide-blue',
    ]"
  >
    <svg v-if="loading" class="animate-spin h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24">
      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
    <slot />
  </button>
</template>
