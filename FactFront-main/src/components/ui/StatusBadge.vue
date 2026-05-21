<script setup lang="ts">
defineProps<{ status: string }>()

// Semantic tone → Tailwind classes. Lookups are case-insensitive so back-ends
// that emit "DRAFT", "Draft" or "draft" all render the same way.
const TONES = {
  warning: 'bg-amber-50 text-amber-700 border-amber-200',
  success: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  info:    'bg-blue-50 text-blue-700 border-blue-200',
  neutral: 'bg-gray-100 text-gray-600 border-gray-200',
  danger:  'bg-red-50 text-red-700 border-red-200',
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
