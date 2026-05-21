<script setup lang="ts">
import { ref, onBeforeUnmount, onMounted } from 'vue'
import { useI18nStore } from '../stores/i18nStore'
import { SUPPORTED_LOCALES, NATIVE_NAMES, type SupportedLocale } from '../i18n/locales'

const store = useI18nStore()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

function toggle() {
  open.value = !open.value
}

async function pick(loc: SupportedLocale) {
  await store.setLocale(loc)
  open.value = false
}

function onDocClick(ev: MouseEvent) {
  if (!root.value) return
  if (!root.value.contains(ev.target as Node)) open.value = false
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <div ref="root" class="relative inline-block">
    <button
      type="button"
      class="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 rounded"
      @click="toggle"
    >
      {{ store.currentLocale.toUpperCase() }}
      <span class="text-xs">▾</span>
    </button>

    <div
      v-if="open"
      role="menu"
      class="absolute right-0 mt-1 w-40 bg-white border border-gray-200 rounded-md shadow-lg z-50 py-1"
    >
      <button
        v-for="loc in SUPPORTED_LOCALES"
        :key="loc"
        role="menuitem"
        type="button"
        class="w-full flex items-center justify-between px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
        @click="pick(loc)"
      >
        <span>{{ NATIVE_NAMES[loc] }}</span>
        <span v-if="loc === store.currentLocale" class="text-blue-600">✓</span>
      </button>
    </div>
  </div>
</template>
