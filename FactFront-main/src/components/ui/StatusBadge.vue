<script setup lang="ts">
defineProps<{ status: string }>()

// Semantic tone → Tailwind classes. Lookups are case-insensitive so back-ends
// that emit "DRAFT", "Draft" or "draft" all render the same way.
const TONES = {
  warning: 'bg-[rgba(184,134,46,0.14)] text-tide-amber-deep border-[rgba(184,134,46,0.28)]',
  success: 'bg-[rgba(101,153,123,0.14)] text-tide-green-deep border-[rgba(101,153,123,0.28)]',
  info:    'bg-[rgba(90,138,171,0.14)] text-tide-blue-deep border-[rgba(90,138,171,0.28)]',
  neutral: 'bg-[rgba(42,36,30,0.06)] text-tide-ink/65 border-[rgba(42,36,30,0.12)]',
  danger:  'bg-[rgba(181,99,88,0.14)] text-tide-rust-deep border-[rgba(181,99,88,0.28)]',
} as const

const STATUS_TONE: Record<string, keyof typeof TONES> = {
  // Invoices
  draft:        'warning',
  final:        'success',
  cancelled:    'neutral',
  // Bill of lading
  // (draft/final already covered)
  // Items
  available:    'success',
  'in use':     'info',
  maintenance:  'warning',
  'out of service': 'danger',
  // Generic
  active:       'success',
  inactive:     'neutral',
  pending:      'warning',
  completed:    'success',
  error:        'danger',
  failed:       'danger',
  processing:   'info',
}

const classes = (s: string) => {
  const key = (s ?? '').toLowerCase().trim()
  const tone = STATUS_TONE[key] ?? 'neutral'
  return TONES[tone]
}
</script>

<template>
  <span
    :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border', classes(status)]"
  >{{ status }}</span>
</template>
