<script setup lang="ts">
import { ref, watch } from 'vue'

interface Entry {
  key: string
  source: string
  target: string
  translated: boolean
}

const props = defineProps<{ entry: Entry }>()
const emit = defineEmits<{ (e: 'save', key: string, value: string): void }>()

const draft = ref(props.entry.target)
const reverted = ref(false)

watch(
  () => props.entry.target,
  (v) => {
    draft.value = v
  },
)

function commitIfChanged() {
  if (reverted.value) {
    reverted.value = false
    return
  }
  if (draft.value === props.entry.target) return
  emit('save', props.entry.key, draft.value)
}

function onEnter(ev: KeyboardEvent) {
  ;(ev.target as HTMLInputElement).blur()
  commitIfChanged()
}

function onEsc() {
  draft.value = props.entry.target
  reverted.value = true
}
</script>

<template>
  <tr class="border-b last:border-0">
    <td class="py-2 pr-4 font-mono text-xs text-gray-600">{{ entry.key }}</td>
    <td class="py-2 pr-4 text-sm text-gray-700">{{ entry.source }}</td>
    <td class="py-2 pr-2">
      <div class="flex items-center gap-2">
        <input
          v-model="draft"
          type="text"
          class="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500"
          @blur="commitIfChanged"
          @keydown.enter="onEnter"
          @keydown.esc="onEsc"
        />
        <span v-if="!entry.translated" data-untranslated class="text-xs text-amber-600">✱</span>
      </div>
    </td>
  </tr>
</template>
