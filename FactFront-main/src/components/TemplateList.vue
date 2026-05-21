<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { Check } from 'lucide-vue-next';
import templateService, { InvoiceTemplate, InvoiceTemplateType } from '../services/invoiceTemplateService';

const { t } = useI18n();

const templates = ref<InvoiceTemplate[]>([]);
const loading = ref(false);
const activatingId = ref<string | null>(null);

const emit = defineEmits<{ (e: 'edit', id?: string): void }>();

async function fetchTemplates() {
  loading.value = true;
  try {
    templates.value = await templateService.listTemplates();
  } finally {
    loading.value = false;
  }
}

function edit(tmpl: InvoiceTemplate) {
  emit('edit', tmpl.id);
}

function createNew() {
  emit('edit');
}

async function duplicate(tmpl: InvoiceTemplate) {
  const data = await templateService.getTemplate(tmpl.id!);
  delete (data as any).id;
  data.name = `${data.name} ${t('templateList.copy')}`;
  await templateService.createTemplate(data);
  await fetchTemplates();
}

async function toggleArchive(tmpl: InvoiceTemplate) {
  const newStatus = tmpl.status === 'archived' ? 'active' : 'archived';
  await templateService.updateTemplate(tmpl.id!, { ...tmpl, status: newStatus });
  await fetchTemplates();
}

async function activateForType(tmpl: InvoiceTemplate) {
  if (!tmpl.id) return;
  activatingId.value = tmpl.id;
  try {
    await templateService.activateTemplate(tmpl.id);
    await fetchTemplates();
  } catch (err) {
    console.error('Activation failed', err);
  } finally {
    activatingId.value = null;
  }
}

const activeIdsByType = computed<Record<InvoiceTemplateType, string | undefined>>(() => {
  const result: Record<string, string | undefined> = { draft: undefined, final: undefined };
  for (const tmpl of templates.value) {
    if (tmpl.status === 'active' && tmpl.type && tmpl.id) {
      // Backend invariant: at most one active per type. If multiple, take the first one we see.
      if (!result[tmpl.type]) result[tmpl.type] = tmpl.id;
    }
  }
  return result as Record<InvoiceTemplateType, string | undefined>;
});

function isActiveForType(tmpl: InvoiceTemplate): boolean {
  if (!tmpl.type || tmpl.status !== 'active') return false;
  return activeIdsByType.value[tmpl.type] === tmpl.id;
}

function typeBadgeClass(type?: string): string {
  if (type === 'draft') return 'bg-amber-50 text-amber-700 border-amber-200';
  return 'bg-emerald-50 text-emerald-700 border-emerald-200'; // final or default
}

function typeLabel(type?: string): string {
  if (type === 'draft') return 'Draft';
  return 'Final';
}

onMounted(fetchTemplates);
</script>

<template>
  <div>
    <div class="flex justify-between items-center mb-4">
      <h2 class="text-xl font-bold tracking-heading">{{ $t('templateList.title') }}</h2>
      <button
        class="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
        @click="createNew"
      >
        {{ $t('templateList.button.newTemplate') }}
      </button>
    </div>

    <div v-if="loading" class="py-12 text-center text-sm text-slate-500">
      {{ $t('templateList.loading') }}
    </div>

    <div v-else-if="!templates.length" class="py-12 text-center text-sm text-slate-500">
      {{ $t('templateList.empty') }}
    </div>

    <!-- Table layout for the list -->
    <div v-else class="overflow-x-auto rounded-xl border border-slate-200 bg-white">
      <table class="min-w-full">
        <thead class="bg-slate-50">
          <tr>
            <th class="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wider">{{ $t('templateList.column.name') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wider w-24">{{ $t('templateList.column.type') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wider w-28">{{ $t('templateList.column.status') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wider w-44">{{ $t('templateList.column.activeForType') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wider w-32">{{ $t('templateList.column.updated') }}</th>
            <th class="px-4 py-3 text-right text-xs font-medium text-slate-500 uppercase tracking-wider w-56">{{ $t('templateList.column.actions') }}</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr
            v-for="(t, idx) in templates"
            :key="t.id"
            :class="['hover:bg-blue-50 transition-colors', idx % 2 === 1 && 'bg-slate-50/40']"
          >
            <td class="px-4 py-3 whitespace-nowrap text-sm font-medium text-slate-900">
              {{ t.name }}
              <span v-if="t.version" class="text-xs text-slate-400 ml-1">v{{ t.version }}</span>
            </td>
            <td class="px-4 py-3 whitespace-nowrap">
              <span :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border', typeBadgeClass(t.type)]">
                {{ typeLabel(t.type) }}
              </span>
            </td>
            <td class="px-4 py-3 whitespace-nowrap">
              <span :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border',
                t.status === 'active' ? 'bg-blue-50 text-blue-700 border-blue-200' : 'bg-slate-100 text-slate-600 border-slate-200']">
                {{ t.status || 'active' }}
              </span>
            </td>
            <td class="px-4 py-3 whitespace-nowrap text-sm">
              <span v-if="isActiveForType(t)" class="inline-flex items-center gap-1 text-emerald-700 font-medium">
                <Check class="w-4 h-4" /> {{ $t('templateList.active') }} {{ typeLabel(t.type).toLowerCase() }}
              </span>
              <button
                v-else-if="t.status === 'active'"
                class="text-blue-600 hover:underline disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="activatingId === t.id"
                @click="activateForType(t)"
              >
                {{ activatingId === t.id ? '…' : $t('templateList.button.setAsActive', { type: typeLabel(t.type).toLowerCase() }) }}
              </button>
              <span v-else class="text-slate-300">—</span>
            </td>
            <td class="px-4 py-3 whitespace-nowrap text-xs text-slate-500">
              {{ t.lastModified || '—' }}
            </td>
            <td class="px-4 py-3 whitespace-nowrap text-right text-sm font-medium">
              <div class="flex justify-end gap-3">
                <button class="text-blue-600 hover:underline" @click="edit(t)">{{ $t('common.edit') }}</button>
                <button class="text-slate-600 hover:underline" @click="duplicate(t)">{{ $t('templateList.button.duplicate') }}</button>
                <button class="text-amber-700 hover:underline" @click="toggleArchive(t)">
                  {{ t.status === 'archived' ? $t('templateList.button.restore') : $t('templateList.button.archive') }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
