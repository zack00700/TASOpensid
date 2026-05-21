<script setup lang="ts">
import { computed } from 'vue'
import TranslationRow from './TranslationRow.vue'
import type { SupportedLocale } from '../../i18n/locales'

interface Entry {
  key: string
  source: string
  target: string
  translated: boolean
}

const props = defineProps<{
  targetLocale: SupportedLocale
  entries: Entry[]
  filterText: string
  untranslatedOnly: boolean
}>()

const emit = defineEmits<{ (e: 'save', key: string, value: string): void }>()

const filtered = computed<Entry[]>(() => {
  const q = props.filterText.trim().toLowerCase()
  return props.entries.filter((entry) => {
    if (props.untranslatedOnly && entry.translated) return false
    if (q === '') return true
    return (
      entry.key.toLowerCase().includes(q) ||
      entry.source.toLowerCase().includes(q) ||
      entry.target.toLowerCase().includes(q)
    )
  })
})

defineExpose({ filtered })
</script>

<template>
  <table class="w-full text-sm">
    <thead>
      <tr class="border-b text-left text-xs uppercase text-gray-500">
        <th class="pb-2 pr-4 w-1/3">Key</th>
        <th class="pb-2 pr-4 w-1/3">EN (source)</th>
        <th class="pb-2 pr-2 w-1/3">{{ targetLocale.toUpperCase() }} (editing)</th>
      </tr>
    </thead>
    <tbody>
      <TranslationRow
        v-for="entry in filtered"
        :key="entry.key"
        :entry="entry"
        @save="(k, v) => emit('save', k, v)"
      />
      <tr v-if="filtered.length === 0">
        <td colspan="3" class="py-8 text-center text-gray-400 text-sm">
          No keys match the current filter.
        </td>
      </tr>
    </tbody>
  </table>
</template>
