<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import TranslationTable from '../components/i18n/TranslationTable.vue'
import { useI18nStore } from '../stores/i18nStore'
import { SUPPORTED_LOCALES, NATIVE_NAMES, type SupportedLocale } from '../i18n/locales'

const { t } = useI18n()

const store = useI18nStore()

// Target locales = everything except the source 'en'
const targetLocales = SUPPORTED_LOCALES.filter((l) => l !== 'en') as Exclude<SupportedLocale, 'en'>[]

const activeTarget = ref<Exclude<SupportedLocale, 'en'>>(targetLocales[0])
const filterText = ref('')
const untranslatedOnly = ref(false)
const toast = ref<{ kind: 'ok' | 'err'; text: string } | null>(null)

const entries = computed(() => store.entries(activeTarget.value))
const counter = computed(() => {
  const total = entries.value.length
  const translated = entries.value.filter((e) => e.translated).length
  return t('i18nAdmin.counter', { translated, total })
})

async function ensureLoaded(loc: SupportedLocale) {
  if (!store.loadedLocales.has(loc)) {
    await store.loadLocale(loc)
  }
}

onMounted(async () => {
  for (const loc of targetLocales) {
    await ensureLoaded(loc)
  }
})

async function switchTab(loc: Exclude<SupportedLocale, 'en'>) {
  activeTarget.value = loc
  await ensureLoaded(loc)
}

async function onSave(key: string, value: string) {
  try {
    await store.saveEntry(activeTarget.value, key, value)
    toast.value = { kind: 'ok', text: t('i18nAdmin.toast.saved', { key }) }
  } catch (err: any) {
    toast.value = {
      kind: 'err',
      text: err?.response?.data?.message || err?.message || t('i18nAdmin.toast.saveFailed'),
    }
  } finally {
    setTimeout(() => (toast.value = null), 2500)
  }
}

const fileInput = ref<HTMLInputElement | null>(null)
function onImportClick() {
  fileInput.value?.click()
}
async function onImportFile(ev: Event) {
  const file = (ev.target as HTMLInputElement).files?.[0]
  if (!file) return
  const text = await file.text()
  let json: Record<string, string>
  try {
    json = JSON.parse(text)
  } catch {
    toast.value = { kind: 'err', text: t('i18nAdmin.toast.invalidJson') }
    return
  }
  try {
    const updated = await store.importJson(activeTarget.value, json)
    toast.value = { kind: 'ok', text: t('i18nAdmin.toast.imported', { n: updated }) }
  } catch (err: any) {
    toast.value = { kind: 'err', text: err?.message || t('i18nAdmin.toast.importFailed') }
  } finally {
    if (fileInput.value) fileInput.value.value = ''
    setTimeout(() => (toast.value = null), 2500)
  }
}

async function onExport() {
  await store.exportJson(activeTarget.value)
}
</script>

<template>
  <div class="max-w-5xl mx-auto p-6 space-y-6">
    <div class="border-b border-gray-200 pb-4 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-semibold text-gray-900">{{ $t('nav.translations') }}</h1>
        <p class="text-gray-500 text-sm mt-1">
          {{ $t('i18nAdmin.subtitle') }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <input
          ref="fileInput"
          type="file"
          accept="application/json"
          class="hidden"
          @change="onImportFile"
        />
        <button
          type="button"
          class="px-3 py-2 border border-gray-300 text-sm rounded hover:bg-gray-50"
          @click="onImportClick"
        >{{ $t('common.import') }}</button>
        <button
          type="button"
          class="px-3 py-2 border border-gray-300 text-sm rounded hover:bg-gray-50"
          @click="onExport"
        >{{ $t('common.export') }}</button>
      </div>
    </div>

    <!-- Tabs -->
    <div class="border-b border-gray-200">
      <nav class="flex gap-8 -mb-px">
        <button
          v-for="loc in targetLocales"
          :key="loc"
          @click="switchTab(loc)"
          :class="[
            'py-2 px-1 border-b-2 text-sm font-medium transition-colors',
            activeTarget === loc
              ? 'border-blue-500 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
          ]"
        >{{ NATIVE_NAMES[loc] }}</button>
      </nav>
    </div>

    <!-- Toolbar -->
    <div class="flex items-center gap-4">
      <input
        v-model="filterText"
        type="text"
        :placeholder="$t('i18nAdmin.placeholder.filter')"
        class="flex-1 px-3 py-2 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      <label class="inline-flex items-center gap-2 text-sm text-gray-700">
        <input v-model="untranslatedOnly" type="checkbox" class="rounded" />
        {{ $t('i18nAdmin.untranslatedOnly') }}
      </label>
      <span class="text-sm text-gray-500">{{ counter }}</span>
    </div>

    <!-- Toast -->
    <div
      v-if="toast"
      :class="[
        'p-2 text-sm rounded border',
        toast.kind === 'ok'
          ? 'bg-green-50 border-green-200 text-green-800'
          : 'bg-red-50 border-red-200 text-red-800',
      ]"
    >{{ toast.text }}</div>

    <!-- Table -->
    <TranslationTable
      :target-locale="activeTarget"
      :entries="entries"
      :filter-text="filterText"
      :untranslated-only="untranslatedOnly"
      @save="onSave"
    />
  </div>
</template>
