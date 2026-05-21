<script setup lang="ts">
import { inject, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import DOMPurify from 'dompurify';

const { t } = useI18n();
/**
 * Basic diff implementation used when the `diff` package is unavailable.
 * It marks the entire old string as removed and the new one as added
 * whenever they differ.
 */
function diffWords(oldStr: string, newStr: string) {
  if (oldStr === newStr) {
    return [{ value: oldStr }];
  }
  return [
    { value: oldStr, removed: true },
    { value: newStr, added: true },
  ];
}
import { ThirdParty } from '../types/third-party';

const props = defineProps<{ id: string }>();
const $axios = inject('$axios');

interface HistoryEntry {
  id: string;
  version: number;
  data: ThirdParty;
  updatedAt: string;
}

const history = ref<HistoryEntry[]>([]);

const mode = ref<'accordion' | 'timeline'>('accordion');


interface DiffEntry {
  field: string;
  oldValue: any;
  newValue: any;
  oldHtml: string;
  newHtml: string;
}

function flatten(obj: any, prefix = ''): Record<string, any> {
  const res: Record<string, any> = {};
  Object.entries(obj || {}).forEach(([k, v]) => {
    const path = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      Object.assign(res, flatten(v, path));
    } else {
      res[path] = v;
    }
  });
  return res;
}

function escapeHtml(value: string | undefined | null) {
  if (value === undefined || value === null) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function toStringValue(v: any): string {
  if (v === undefined || v === null) return '';
  return typeof v === 'string' ? v : JSON.stringify(v);
}

function highlightDiff(oldVal: any, newVal: any) {
  const oldStr = toStringValue(oldVal);
  const newStr = toStringValue(newVal);
  const parts = diffWords(oldStr, newStr);
  let oldHtml = '';
  let newHtml = '';
  for (const p of parts) {
    const text = escapeHtml(p.value);
    if (p.added) {
      newHtml += `<span class="text-green-600">${text}</span>`;
    } else if (p.removed) {
      oldHtml += `<span class="text-red-600">${text}</span>`;
    } else {
      oldHtml += text;
      newHtml += text;
    }
  }
  return { oldHtml, newHtml };
}

function computeDiff(idx: number): DiffEntry[] {
  const prev = flatten(history.value[idx - 1].data);
  const curr = flatten(history.value[idx].data);
  const keys = new Set([...Object.keys(prev), ...Object.keys(curr)]);
  const result: DiffEntry[] = [];
  for (const key of keys) {
    if (prev[key] !== curr[key]) {
      const { oldHtml, newHtml } = highlightDiff(prev[key], curr[key]);
      result.push({ field: key, oldValue: prev[key], newValue: curr[key], oldHtml, newHtml });
    }
  }
  return result;
}

async function fetchHistory() {
  try {
    const response = await $axios.get(`/third-party/${props.id}/history`);
    history.value = response.data.sort((a: any, b: any) => a.version - b.version);
  } catch (e) {
    console.error(e);
  }
}

onMounted(fetchHistory);

const sanitize = (html: string) => DOMPurify.sanitize(html ?? '');
</script>

<template>
  <div class="space-y-4">
    <div class="flex justify-end space-x-2 text-xs mb-2">
      <button

        class="px-2 py-1 border rounded focus:outline-none focus:ring"
        :class="mode === 'accordion' ? 'bg-blue-500 text-white' : 'bg-white dark:bg-gray-700 dark:text-gray-200'"
        @click="mode = 'accordion'"
      >
        {{ t('thirdPartyHistory.mode.accordion') }}
      </button>
      <button
        class="px-2 py-1 border rounded focus:outline-none focus:ring"
        :class="mode === 'timeline' ? 'bg-blue-500 text-white' : 'bg-white dark:bg-gray-700 dark:text-gray-200'"
        @click="mode = 'timeline'"
      >
        {{ t('thirdPartyHistory.mode.timeline') }}
      </button>
    </div>
    <div v-for="(entry, idx) in history" :key="entry.id">
      <template v-if="idx > 0">
        <details
          v-if="mode === 'accordion'"
          class="border rounded mb-2 dark:border-gray-700"
        >
          <summary class="cursor-pointer px-2 py-1 flex justify-between items-center focus:outline-none focus-visible:ring">
            <span class="text-xs text-gray-600 dark:text-gray-300">{{ t('thirdPartyHistory.versionLabel', { version: entry.version, updatedAt: entry.updatedAt }) }}</span>
          </summary>
          <div class="p-2 text-xs">
            <div class="overflow-x-auto">
              <table class="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                <thead class="bg-gray-50 dark:bg-gray-800 text-gray-600 dark:text-gray-300">
                  <tr>
                    <th class="px-2 py-1 text-left font-medium">{{ t('thirdPartyHistory.column.field') }}</th>
                    <th class="px-2 py-1 text-left font-medium">{{ t('thirdPartyHistory.column.old') }}</th>
                    <th class="px-2 py-1 text-left font-medium">{{ t('thirdPartyHistory.column.new') }}</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
                  <tr v-for="d in computeDiff(idx)" :key="d.field">
                    <td class="px-2 py-1 align-top break-all">{{ d.field }}</td>
                    <td class="px-2 py-1 align-top break-all" v-html="sanitize(d.oldHtml)"></td>
                    <td class="px-2 py-1 align-top break-all" v-html="sanitize(d.newHtml)"></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </details>
        <div v-else class="relative pl-6 mb-4">
          <div class="absolute left-1 top-2 w-3 h-3 bg-blue-500 rounded-full"></div>
          <div class="border rounded p-2 bg-white dark:bg-gray-800 dark:border-gray-700">
            <div class="text-xs text-gray-600 dark:text-gray-300 mb-2">{{ t('thirdPartyHistory.versionLabel', { version: entry.version, updatedAt: entry.updatedAt }) }}</div>
            <div class="space-y-1">
              <div v-for="d in computeDiff(idx)" :key="d.field" class="text-xs">
                <div class="font-semibold">{{ d.field }}</div>
                <div class="flex flex-col sm:flex-row sm:space-x-2">
                  <div class="flex-1 break-all" v-html="sanitize(d.oldHtml)"></div>
                  <div class="flex-1 break-all" v-html="sanitize(d.newHtml)"></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

    </div>
  </div>
</template>
