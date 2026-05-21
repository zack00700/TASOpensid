<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import fieldService from '../services/fieldService';
import type { Field } from '../types/field';

const { t } = useI18n();

const fields = ref<Field[]>([]);
const search = ref('');
const pageFilter = ref('');
const showAddLanguage = ref(false);
const newLanguage = ref('');
const selectedLanguage = ref('en');
const viewMode = ref<'table' | 'preview'>('table');
const loading = ref(false);
const saveStatus = ref<{ [key: string]: 'idle' | 'saving' | 'saved' | 'error' }>({});
const bulkTranslateMode = ref(false);
const selectedFields = ref<Set<string>>(new Set());

// Language management
const languages = computed(() => {
  const set = new Set<string>();
  fields.value.forEach(f => {
    Object.keys(f.translations || {}).forEach(l => set.add(l));
  });
  return ['en', ...Array.from(set).filter(l => l !== 'en')];
});

const languageNames: { [key: string]: string } = {
  en: 'English',
  es: 'Español',
  fr: 'Français',
  de: 'Deutsch',
  it: 'Italiano',
  pt: 'Português',
  ru: 'Русский',
  ja: '日本語',
  ko: '한국어',
  zh: '中文'
};

const pages = computed(() => {
  const set = new Set<string>();
  fields.value.forEach(f => f.pages?.forEach(p => set.add(p)));
  return Array.from(set);
});

const filteredFields = computed(() => {
  return fields.value.filter(f => {
    const q = search.value.toLowerCase();
    const matchesSearch =
      f.key.toLowerCase().includes(q) ||
      f.defaultValue.toLowerCase().includes(q) ||
      Object.values(f.translations || {}).some(t =>
        t.toLowerCase().includes(q)
      );
    const matchesPage = !pageFilter.value || f.pages?.includes(pageFilter.value);
    return matchesSearch && matchesPage;
  });
});

// Translation completeness tracking
const translationStats = computed(() => {
  const stats: { [lang: string]: { total: number; completed: number; percentage: number } } = {};

  languages.value.forEach(lang => {
    const total = fields.value.length;
    const completed = fields.value.filter(f =>
      f.translations[lang] && f.translations[lang].trim() !== ''
    ).length;
    stats[lang] = {
      total,
      completed,
      percentage: total > 0 ? Math.round((completed / total) * 100) : 0
    };
  });

  return stats;
});

// Field status helpers
const getFieldTranslationStatus = (field: Field, lang: string) => {
  if (!field.translations[lang] || field.translations[lang].trim() === '') {
    return 'missing';
  }
  return 'complete';
};

const isFieldSelected = (fieldKey: string) => selectedFields.value.has(fieldKey);

// Core functions
async function loadFields() {
  loading.value = true;
  try {
    fields.value = await fieldService.getFields();
  } catch (error) {
    console.error('Failed to load fields:', error);
  } finally {
    loading.value = false;
  }
}

function addLanguage() {
  const code = newLanguage.value.trim().toLowerCase();
  if (!code || languages.value.includes(code)) return;

  loading.value = true;
  try {
    fieldService.addLanguage({ code });
    fields.value.forEach(f => {
      if (!f.translations) f.translations = {};
      f.translations[code] = '';
    });
    newLanguage.value = '';
    showAddLanguage.value = false;
  } catch (error) {
    console.error('Failed to add language:', error);
  } finally {
    loading.value = false;
  }
}

async function saveTranslation(field: Field, lang: string) {
  const saveKey = `${field.key}-${lang}`;
  saveStatus.value[saveKey] = 'saving';

  try {
    await fieldService.updateTranslation(field.key, lang, field.translations[lang]);
    saveStatus.value[saveKey] = 'saved';
    setTimeout(() => {
      if (saveStatus.value[saveKey] === 'saved') {
        saveStatus.value[saveKey] = 'idle';
      }
    }, 2000);
  } catch (error) {
    saveStatus.value[saveKey] = 'error';
    console.error('Failed to save translation:', error);
  }
}

function toggleFieldSelection(fieldKey: string) {
  if (selectedFields.value.has(fieldKey)) {
    selectedFields.value.delete(fieldKey);
  } else {
    selectedFields.value.add(fieldKey);
  }
}

function selectAllFields() {
  filteredFields.value.forEach(f => selectedFields.value.add(f.key));
}

function clearFieldSelection() {
  selectedFields.value.clear();
}

async function bulkCopyTranslations(fromLang: string, toLang: string) {
  const selectedFieldsList = Array.from(selectedFields.value);
  if (selectedFieldsList.length === 0) return;

  loading.value = true;
  try {
    for (const fieldKey of selectedFieldsList) {
      const field = fields.value.find(f => f.key === fieldKey);
      if (field && field.translations[fromLang]) {
        field.translations[toLang] = field.translations[fromLang];
        await saveTranslation(field, toLang);
      }
    }
    clearFieldSelection();
    bulkTranslateMode.value = false;
  } catch (error) {
    console.error('Bulk copy failed:', error);
  } finally {
    loading.value = false;
  }
}

function exportTranslations() {
  const data = {
    languages: languages.value,
    fields: fields.value.map(f => ({
      key: f.key,
      defaultValue: f.defaultValue,
      pages: f.pages,
      translations: f.translations
    }))
  };

  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'translations.json';
  a.click();
  URL.revokeObjectURL(url);
}

// Auto-save on input with debouncing
const debouncedSave = (() => {
  const timeouts: { [key: string]: NodeJS.Timeout } = {};
  return (field: Field, lang: string) => {
    const key = `${field.key}-${lang}`;
    clearTimeout(timeouts[key]);
    timeouts[key] = setTimeout(() => {
      saveTranslation(field, lang);
    }, 1000);
  };
})();

onMounted(loadFields);
</script>

<template>
  <div class="max-w-7xl mx-auto p-6">
    <!-- Header -->
    <div class="mb-6">
      <h1 class="text-3xl font-bold text-gray-900 mb-2">{{ t('fields.header.title') }}</h1>
      <p class="text-gray-600">{{ t('fields.header.subtitle') }}</p>
    </div>

    <!-- Controls Bar -->
    <div class="bg-white rounded-lg shadow-sm border p-4 mb-6">
      <div class="flex flex-wrap items-center gap-4 mb-4">
        <!-- Search -->
        <div class="flex-1 min-w-64">
          <input
            v-model="search"
            :placeholder="t('fields.placeholder.search')"
            data-test="search-input"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          />
        </div>

        <!-- Page Filter -->
        <select
          v-model="pageFilter"
          class="border border-gray-300 rounded-lg px-3 py-2 focus:ring-2 focus:ring-blue-500"
          data-test="page-filter"
        >
          <option value="">{{ t('fields.filter.allPages') }}</option>
          <option v-for="p in pages" :key="p" :value="p">{{ p }}</option>
        </select>

        <!-- View Mode Toggle -->
        <div class="flex border border-gray-300 rounded-lg overflow-hidden">
          <button
            @click="viewMode = 'table'"
            :class="[
              'px-4 py-2 text-sm font-medium transition-colors',
              viewMode === 'table'
                ? 'bg-blue-500 text-white'
                : 'bg-white text-gray-700 hover:bg-gray-50'
            ]"
          >
            {{ t('fields.viewMode.table') }}
          </button>
          <button
            @click="viewMode = 'preview'"
            :class="[
              'px-4 py-2 text-sm font-medium transition-colors',
              viewMode === 'preview'
                ? 'bg-blue-500 text-white'
                : 'bg-white text-gray-700 hover:bg-gray-50'
            ]"
          >
            {{ t('fields.viewMode.preview') }}
          </button>
        </div>
      </div>

      <div class="flex flex-wrap items-center gap-4">
        <!-- Language Management -->
        <button
          @click="showAddLanguage = !showAddLanguage"
          data-test="add-language-button"
          class="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg transition-colors flex items-center gap-2"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
          </svg>
          {{ t('fields.button.addLanguage') }}
        </button>

        <!-- Bulk Operations -->
        <button
          @click="bulkTranslateMode = !bulkTranslateMode"
          class="bg-gray-500 hover:bg-gray-600 text-white px-4 py-2 rounded-lg transition-colors flex items-center gap-2"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"/>
          </svg>
          {{ t('fields.button.bulkOperations') }}
        </button>

        <!-- Export -->
        <button
          @click="exportTranslations"
          class="bg-green-500 hover:bg-green-600 text-white px-4 py-2 rounded-lg transition-colors flex items-center gap-2"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
          </svg>
          {{ t('common.export') }}
        </button>
      </div>
    </div>

    <!-- Add Language Form -->
    <div v-if="showAddLanguage" class="bg-blue-50 rounded-lg p-4 mb-6 border border-blue-200">
      <div class="flex items-center gap-4">
        <input
          v-model="newLanguage"
          :placeholder="t('fields.placeholder.languageCode')"
          data-test="new-language-input"
          class="border border-gray-300 rounded-lg px-3 py-2 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
        />
        <button
          @click="addLanguage"
          data-test="save-language-button"
          class="bg-green-500 hover:bg-green-600 text-white px-4 py-2 rounded-lg transition-colors"
        >
          {{ t('fields.button.addLanguage') }}
        </button>
        <button
          @click="showAddLanguage = false"
          class="text-gray-500 hover:text-gray-700"
        >
          {{ t('common.cancel') }}
        </button>
      </div>
    </div>

    <!-- Bulk Operations Panel -->
    <div v-if="bulkTranslateMode" class="bg-yellow-50 rounded-lg p-4 mb-6 border border-yellow-200">
      <div class="flex items-center justify-between mb-4">
        <h3 class="font-medium text-gray-900">{{ t('fields.button.bulkOperations') }}</h3>
        <div class="text-sm text-gray-600">
          {{ t('fields.bulk.selectedCount', { count: selectedFields.size }) }}
        </div>
      </div>
      <div class="flex items-center gap-4">
        <button
          @click="selectAllFields"
          class="text-sm text-blue-600 hover:text-blue-800"
        >
          {{ t('fields.bulk.selectAllVisible') }}
        </button>
        <button
          @click="clearFieldSelection"
          class="text-sm text-gray-600 hover:text-gray-800"
        >
          {{ t('fields.bulk.clearSelection') }}
        </button>
        <div class="flex items-center gap-2 ml-auto">
          <span class="text-sm text-gray-600">{{ t('fields.bulk.copyFrom') }}</span>
          <select class="border border-gray-300 rounded px-2 py-1 text-sm">
            <option v-for="lang in languages" :key="lang" :value="lang">
              {{ languageNames[lang] || lang }}
            </option>
          </select>
          <span class="text-sm text-gray-600">{{ t('fields.bulk.to') }}</span>
          <select class="border border-gray-300 rounded px-2 py-1 text-sm">
            <option v-for="lang in languages" :key="lang" :value="lang">
              {{ languageNames[lang] || lang }}
            </option>
          </select>
          <button class="bg-blue-500 text-white px-3 py-1 rounded text-sm hover:bg-blue-600">
            {{ t('fields.bulk.copy') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Language Statistics -->
    <div v-if="languages.length > 1" class="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-4 mb-6">
      <div
        v-for="lang in languages"
        :key="lang"
        class="bg-white rounded-lg p-4 border shadow-sm"
      >
        <div class="flex items-center justify-between mb-2">
          <h4 class="font-medium text-gray-900">
            {{ languageNames[lang] || lang.toUpperCase() }}
          </h4>
          <span class="text-sm text-gray-500">
            {{ translationStats[lang]?.percentage || 0 }}%
          </span>
        </div>
        <div class="w-full bg-gray-200 rounded-full h-2">
          <div
            class="bg-blue-500 h-2 rounded-full transition-all duration-300"
            :style="{ width: `${translationStats[lang]?.percentage || 0}%` }"
          ></div>
        </div>
        <div class="text-xs text-gray-500 mt-1">
          {{ t('fields.stats.completeRatio', { completed: translationStats[lang]?.completed || 0, total: translationStats[lang]?.total || 0 }) }}
        </div>
      </div>
    </div>

    <!-- Preview Mode -->
    <div v-if="viewMode === 'preview'" class="bg-white rounded-lg shadow-sm border">
      <div class="p-4 border-b">
        <div class="flex items-center gap-4">
          <h3 class="font-medium text-gray-900">{{ t('fields.preview.title') }}</h3>
          <select
            v-model="selectedLanguage"
            class="border border-gray-300 rounded-lg px-3 py-2 focus:ring-2 focus:ring-blue-500"
          >
            <option v-for="lang in languages" :key="lang" :value="lang">
              {{ languageNames[lang] || lang.toUpperCase() }}
            </option>
          </select>
        </div>
      </div>
      <div class="p-6">
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <div
            v-for="field in filteredFields"
            :key="field.key"
            class="border rounded-lg p-4"
          >
            <div class="text-sm text-gray-500 mb-1">{{ field.key }}</div>
            <div class="font-medium">
              {{ field.translations[selectedLanguage] || field.defaultValue }}
            </div>
            <div v-if="field.pages?.length" class="text-xs text-gray-400 mt-1">
              {{ t('fields.preview.pages', { pages: field.pages.join(', ') }) }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Table View -->
    <div v-else class="bg-white rounded-lg shadow-sm border overflow-hidden">
      <div class="overflow-x-auto">
        <table class="min-w-full">
          <thead class="bg-gray-50">
            <tr>
              <th v-if="bulkTranslateMode" class="px-4 py-3 text-left">
                <input
                  type="checkbox"
                  @change="$event.target.checked ? selectAllFields() : clearFieldSelection()"
                  class="rounded border-gray-300"
                />
              </th>
              <th class="px-4 py-3 text-left text-sm font-medium text-gray-900">{{ t('fields.table.key') }}</th>
              <th class="px-4 py-3 text-left text-sm font-medium text-gray-900">{{ t('fields.table.default') }}</th>
              <th class="px-4 py-3 text-left text-sm font-medium text-gray-900">{{ t('fields.table.pages') }}</th>
              <th
                v-for="lang in languages.filter(l => l !== 'en')"
                :key="lang"
                :data-test="`lang-header-${lang}`"
                class="px-4 py-3 text-left text-sm font-medium text-gray-900"
              >
                <div class="flex items-center gap-2">
                  {{ languageNames[lang] || lang.toUpperCase() }}
                  <div
                    :class="[
                      'w-2 h-2 rounded-full',
                      translationStats[lang]?.percentage === 100
                        ? 'bg-green-500'
                        : translationStats[lang]?.percentage > 50
                        ? 'bg-yellow-500'
                        : 'bg-red-500'
                    ]"
                  ></div>
                </div>
              </th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            <tr
              v-for="field in filteredFields"
              :key="field.key"
              class="hover:bg-gray-50"
            >
              <td v-if="bulkTranslateMode" class="px-4 py-3">
                <input
                  type="checkbox"
                  :checked="isFieldSelected(field.key)"
                  @change="toggleFieldSelection(field.key)"
                  class="rounded border-gray-300"
                />
              </td>
              <td class="px-4 py-3 text-sm font-medium text-gray-900">
                {{ field.key }}
              </td>
              <td class="px-4 py-3 text-sm text-gray-700">
                {{ field.defaultValue }}
              </td>
              <td class="px-4 py-3 text-sm text-gray-500">
                <span v-if="field.pages?.length" class="inline-flex flex-wrap gap-1">
                  <span
                    v-for="page in field.pages"
                    :key="page"
                    class="inline-flex items-center px-2 py-1 rounded-full text-xs bg-blue-100 text-blue-800"
                  >
                    {{ page }}
                  </span>
                </span>
              </td>
              <td
                v-for="lang in languages.filter(l => l !== 'en')"
                :key="lang"
                class="px-4 py-3"
              >
                <div class="relative">
                  <input
                    :data-test="`translation-${field.key}-${lang}`"
                    v-model="field.translations[lang]"
                    @input="debouncedSave(field, lang)"
                    @blur="saveTranslation(field, lang)"
                    :placeholder="field.defaultValue"
                    :class="[
                      'w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors',
                      getFieldTranslationStatus(field, lang) === 'missing'
                        ? 'border-red-200 bg-red-50'
                        : 'border-gray-300'
                    ]"
                  />
                  <!-- Save Status Indicator -->
                  <div
                    v-if="saveStatus[`${field.key}-${lang}`] && saveStatus[`${field.key}-${lang}`] !== 'idle'"
                    class="absolute right-2 top-1/2 transform -translate-y-1/2"
                  >
                    <svg
                      v-if="saveStatus[`${field.key}-${lang}`] === 'saving'"
                      class="animate-spin h-4 w-4 text-blue-500"
                      fill="none"
                      viewBox="0 0 24 24"
                    >
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"/>
                    </svg>
                    <svg
                      v-else-if="saveStatus[`${field.key}-${lang}`] === 'saved'"
                      class="h-4 w-4 text-green-500"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
                    </svg>
                    <svg
                      v-else-if="saveStatus[`${field.key}-${lang}`] === 'error'"
                      class="h-4 w-4 text-red-500"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                    </svg>
                  </div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Loading Overlay -->
    <div
      v-if="loading"
      class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
    >
      <div class="bg-white rounded-lg p-6 flex items-center gap-3">
        <svg class="animate-spin h-5 w-5 text-blue-500" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"/>
        </svg>
        <span class="text-gray-700">{{ t('fields.processing') }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Custom scrollbar for table */
.overflow-x-auto::-webkit-scrollbar {
  height: 8px;
}

.overflow-x-auto::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.overflow-x-auto::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.overflow-x-auto::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}
</style>
