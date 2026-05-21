<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  Plus,
  Pencil,
  Trash2,
  Filter,
  RefreshCw,
  Sliders,
  XCircle,
} from 'lucide-vue-next';
import { getDdRules, createDdRule, updateDdRule, deleteDdRule } from '../services/ddService';
import type { DdRule, DdType, DdClockAnchor } from '../types/dd';
import PageHeader from './ui/PageHeader.vue';
import Button from './ui/Button.vue';
import Select from './ui/Select.vue';

const { t } = useI18n();

// ── State ──────────────────────────────────────────────────────────────────

const rules = ref<DdRule[]>([]);
const isLoading = ref(false);
const filterDdType = ref('');

// Create/Edit form
const showForm = ref(false);
const editingRule = ref<DdRule | null>(null);
const isSaving = ref(false);

// Delete modal
const showDeleteConfirm = ref(false);
const ruleToDelete = ref<DdRule | null>(null);

// ── Form model ──────────────────────────────────────────────────────────────

const emptyForm = (): DdRule => ({
  ruleName: '',
  ddType: 'DEMURRAGE',
  clockAnchor: 'DISCHARGE',
  carrierId: '',
  containerTypeCode: '',
  freeDays: 0,
  tiers: [],
  includeHolidays: false,
  includeWeekends: false,
  status: 'ACTIVE',
  notes: '',
});

const form = ref<DdRule>(emptyForm());

// ── Constants ───────────────────────────────────────────────────────────────

const DD_TYPES: DdType[] = ['DEMURRAGE', 'DETENTION'];
const CLOCK_ANCHORS: DdClockAnchor[] = ['DISCHARGE', 'GATE_IN', 'DOCS_READY', 'CUSTOMS_CLEARED'];

const tableHeaders = computed(() => [
  t('ddRules.column.ruleName'),
  t('ddRules.column.ddType'),
  t('ddRules.column.clockAnchor'),
  t('ddRules.column.carrierId'),
  t('ddRules.column.freeDays'),
  t('ddRules.column.status'),
  t('ddRules.column.actions'),
]);

// ── Computed ────────────────────────────────────────────────────────────────

const filteredRules = computed(() => {
  if (!filterDdType.value) return rules.value;
  return rules.value.filter(r => r.ddType === filterDdType.value);
});

// ── Data fetching ───────────────────────────────────────────────────────────

const fetchRules = async () => {
  isLoading.value = true;
  try {
    rules.value = await getDdRules();
  } catch {
    alert(t('ddRules.dialog.failedToLoad'));
  } finally {
    isLoading.value = false;
  }
};

onMounted(fetchRules);

// ── Badges ───────────────────────────────────────────────────────────────────

const getDdTypeBadgeClasses = (ddType: string) => {
  const base = 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border';
  return ddType === 'DEMURRAGE'
    ? `${base} bg-red-100 text-red-800 border-red-200`
    : `${base} bg-orange-100 text-orange-800 border-orange-200`;
};

const getStatusBadgeClasses = (status?: string) => {
  const base = 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border';
  return status === 'ACTIVE'
    ? `${base} bg-green-100 text-green-800 border-green-200`
    : `${base} bg-gray-100 text-gray-600 border-gray-200`;
};

// ── CRUD actions ─────────────────────────────────────────────────────────────

const handleAdd = () => {
  editingRule.value = null;
  form.value = emptyForm();
  showForm.value = true;
};

const handleEdit = (rule: DdRule) => {
  editingRule.value = { ...rule };
  form.value = { ...rule };
  showForm.value = true;
};

const handleDelete = (rule: DdRule) => {
  ruleToDelete.value = rule;
  showDeleteConfirm.value = true;
};

const confirmDelete = async () => {
  if (!ruleToDelete.value?.id) return;
  try {
    await deleteDdRule(ruleToDelete.value.id);
    await fetchRules();
  } catch {
    alert(t('ddRules.dialog.failedToDelete'));
  } finally {
    showDeleteConfirm.value = false;
    ruleToDelete.value = null;
  }
};

const handleFormCancel = () => {
  showForm.value = false;
  editingRule.value = null;
};

const handleFormSubmit = async () => {
  if (!form.value.ruleName.trim()) {
    alert(t('ddRules.dialog.ruleNameRequired'));
    return;
  }
  isSaving.value = true;
  try {
    if (editingRule.value?.id) {
      await updateDdRule(editingRule.value.id, form.value);
    } else {
      await createDdRule(form.value);
    }
    showForm.value = false;
    editingRule.value = null;
    await fetchRules();
  } catch {
    alert(editingRule.value ? t('ddRules.dialog.failedToUpdate') : t('ddRules.dialog.failedToCreate'));
  } finally {
    isSaving.value = false;
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50">

    <!-- ── List View ──────────────────────────────────────────────────── -->
    <div v-if="!showForm">
      <PageHeader :title="t('nav.ddRules')" :count="filteredRules.length">
        <template #actions>
          <Button variant="secondary" :disabled="isLoading" @click="fetchRules">
            <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': isLoading }" />
            {{ t('applicationUsers.button.refresh') }}
          </Button>
          <Button @click="handleAdd">
            <Plus class="h-4 w-4" />
            {{ t('ddRules.button.newRule') }}
          </Button>
        </template>
      </PageHeader>

      <div class="px-6 py-6 space-y-6">

      <!-- Filter Bar -->
      <div class="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
        <div class="p-6">
          <div class="flex flex-col sm:flex-row gap-4">
            <Select v-model="filterDdType" :label="t('ddRules.field.type')" class="sm:w-48">
              <option value="">{{ t('ddRules.filter.allTypes') }}</option>
              <option value="DEMURRAGE">{{ t('ddDashboard.ddType.demurrage') }}</option>
              <option value="DETENTION">{{ t('ddDashboard.ddType.detention') }}</option>
            </Select>
          </div>
        </div>
      </div>

      <!-- Table -->
      <div class="bg-white shadow-sm rounded-lg border border-gray-200 overflow-hidden">

        <!-- Desktop table -->
        <div class="hidden sm:block overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50">
              <tr>
                <th
                  v-for="header in tableHeaders"
                  :key="header"
                  class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
                >
                  {{ header }}
                </th>
              </tr>
            </thead>
            <tbody class="bg-white divide-y divide-gray-200">
              <!-- Loading skeleton -->
              <tr v-if="isLoading" v-for="i in 5" :key="i">
                <td v-for="j in 7" :key="j" class="px-4 py-3">
                  <div class="h-4 bg-gray-200 rounded animate-pulse w-3/4"></div>
                </td>
              </tr>

              <tr
                v-else
                v-for="rule in filteredRules"
                :key="rule.id"
                class="hover:bg-gray-50 transition-colors duration-150"
              >
                <!-- Rule Name -->
                <td class="px-4 py-3">
                  <div class="max-w-xs">
                    <div class="text-sm font-semibold text-gray-900 truncate">{{ rule.ruleName }}</div>
                    <div v-if="rule.containerTypeCode" class="text-xs text-gray-500 truncate">
                      {{ t('ddRules.label.container', { code: rule.containerTypeCode }) }}
                    </div>
                  </div>
                </td>

                <!-- DD Type -->
                <td class="px-4 py-3">
                  <span :class="getDdTypeBadgeClasses(rule.ddType)">{{ rule.ddType }}</span>
                </td>

                <!-- Clock Anchor -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-700 font-mono">{{ rule.clockAnchor }}</span>
                </td>

                <!-- Carrier ID -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-700">{{ rule.carrierId || '—' }}</span>
                </td>

                <!-- Free Days -->
                <td class="px-4 py-3">
                  <span class="text-sm font-medium text-gray-900">{{ rule.freeDays }}</span>
                </td>

                <!-- Status -->
                <td class="px-4 py-3">
                  <span :class="getStatusBadgeClasses(rule.status)">{{ rule.status ?? '—' }}</span>
                </td>

                <!-- Actions -->
                <td class="px-4 py-3">
                  <div class="flex items-center space-x-2">
                    <button
                      @click="handleEdit(rule)"
                      class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                      :title="t('ddRules.action.editRule')"
                      :aria-label="t('ddRules.action.editRule')"
                    >
                      <Pencil class="h-4 w-4" />
                    </button>
                    <button
                      @click="handleDelete(rule)"
                      class="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                      :title="t('ddRules.action.deleteRule')"
                      :aria-label="t('ddRules.action.deleteRule')"
                    >
                      <Trash2 class="h-4 w-4" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Mobile cards -->
        <div class="sm:hidden p-4">
          <div v-if="isLoading" class="space-y-3">
            <div v-for="i in 4" :key="i" class="bg-white rounded-xl border border-slate-200 p-4 animate-pulse">
              <div class="h-4 bg-gray-200 rounded w-1/2 mb-2"></div>
              <div class="h-3 bg-gray-200 rounded w-3/4"></div>
            </div>
          </div>
          <div v-else-if="!filteredRules.length" class="bg-white rounded-xl border border-slate-200 p-8 text-center">
            <p class="text-sm text-slate-500">{{ t('ddRules.empty.noRulesFound') }}</p>
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="rule in filteredRules"
              :key="rule.id"
              class="bg-white rounded-xl border border-slate-200 p-4 shadow-sm"
            >
              <div class="flex items-start justify-between gap-2 mb-2">
                <div>
                  <p class="font-semibold text-slate-900 text-sm">{{ rule.ruleName }}</p>
                  <p class="text-xs text-slate-500 mt-0.5">{{ t('ddRules.label.anchorFreeDays', { anchor: rule.clockAnchor, days: rule.freeDays }) }}</p>
                </div>
                <span :class="getStatusBadgeClasses(rule.status)">{{ rule.status ?? '—' }}</span>
              </div>
              <div class="flex items-center justify-between mb-3">
                <span :class="getDdTypeBadgeClasses(rule.ddType)">{{ rule.ddType }}</span>
                <span class="text-xs text-slate-500">{{ rule.carrierId || '—' }}</span>
              </div>
              <div class="flex justify-end space-x-2">
                <button
                  @click="handleEdit(rule)"
                  class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                  :aria-label="t('ddRules.action.editRule')"
                >
                  <Pencil class="h-4 w-4" />
                </button>
                <button
                  @click="handleDelete(rule)"
                  class="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                  :aria-label="t('ddRules.action.deleteRule')"
                >
                  <Trash2 class="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty State (desktop) -->
        <div v-if="!isLoading && filteredRules.length === 0" class="hidden sm:block text-center py-12">
          <Sliders class="mx-auto h-12 w-12 text-gray-400" />
          <h3 class="mt-2 text-sm font-medium text-gray-900">
            {{ filterDdType ? t('ddRules.empty.noRulesFound') : t('ddRules.empty.noDdRules') }}
          </h3>
          <p class="mt-1 text-sm text-gray-500">
            {{ filterDdType ? t('ddRules.empty.tryClearingFilter') : t('ddRules.empty.getStarted') }}
          </p>
          <div class="mt-6">
            <button
              v-if="!filterDdType"
              @click="handleAdd"
              class="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700"
            >
              <Plus class="h-4 w-4 mr-2" />
              {{ t('ddRules.button.newRule') }}
            </button>
          </div>
        </div>
      </div>
      </div>
    </div>

    <!-- ── Create / Edit Form View ────────────────────────────────────── -->
    <div v-else class="max-w-2xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <!-- Form Header -->
      <div class="mb-8">
        <h1 class="text-2xl font-bold text-gray-900">
          {{ editingRule ? t('ddRules.header.editTitle') : t('ddRules.header.newTitle') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500">
          {{ editingRule ? t('ddRules.header.editSubtitle') : t('ddRules.header.newSubtitle') }}
        </p>
      </div>

      <form @submit.prevent="handleFormSubmit" class="space-y-6">
        <div class="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
          <h2 class="text-base font-semibold text-gray-900 mb-4">{{ t('ddRules.section.ruleDetails') }}</h2>
          <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">

            <!-- Rule Name -->
            <div class="sm:col-span-2">
              <label class="block text-sm font-medium text-gray-700 mb-1">
                {{ t('ddRules.field.ruleName') }} <span class="text-red-500">*</span>
              </label>
              <input
                v-model="form.ruleName"
                type="text"
                required
                :placeholder="t('ddRules.placeholder.ruleName')"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <!-- DD Type -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">
                {{ t('ddRules.field.ddType') }} <span class="text-red-500">*</span>
              </label>
              <select
                v-model="form.ddType"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              >
                <option v-for="t in DD_TYPES" :key="t" :value="t">{{ t }}</option>
              </select>
            </div>

            <!-- Clock Anchor -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">
                {{ t('ddRules.field.clockAnchor') }} <span class="text-red-500">*</span>
              </label>
              <select
                v-model="form.clockAnchor"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              >
                <option v-for="a in CLOCK_ANCHORS" :key="a" :value="a">{{ a }}</option>
              </select>
            </div>

            <!-- Free Days -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">
                {{ t('ddRules.field.freeDays') }} <span class="text-red-500">*</span>
              </label>
              <input
                v-model.number="form.freeDays"
                type="number"
                min="0"
                placeholder="0"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <!-- Carrier ID -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('ddRules.field.carrierId') }}</label>
              <input
                v-model="form.carrierId"
                type="text"
                :placeholder="t('ddRules.placeholder.carrierId')"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <!-- Container Type Code -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('ddRules.field.containerTypeCode') }}</label>
              <input
                v-model="form.containerTypeCode"
                type="text"
                :placeholder="t('ddRules.placeholder.containerTypeCode')"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <!-- Status -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('ddRules.field.status') }}</label>
              <select
                v-model="form.status"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              >
                <option value="ACTIVE">{{ t('ddRules.status.active') }}</option>
                <option value="INACTIVE">{{ t('ddRules.status.inactive') }}</option>
              </select>
            </div>

            <!-- Include Holidays -->
            <div class="flex items-center space-x-3 pt-2">
              <input
                id="includeHolidays"
                v-model="form.includeHolidays"
                type="checkbox"
                class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
              />
              <label for="includeHolidays" class="text-sm font-medium text-gray-700">
                {{ t('ddRules.field.includeHolidays') }}
              </label>
            </div>

            <!-- Include Weekends -->
            <div class="flex items-center space-x-3 pt-2">
              <input
                id="includeWeekends"
                v-model="form.includeWeekends"
                type="checkbox"
                class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
              />
              <label for="includeWeekends" class="text-sm font-medium text-gray-700">
                {{ t('ddRules.field.includeWeekends') }}
              </label>
            </div>

            <!-- Notes -->
            <div class="sm:col-span-2">
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('ddRules.field.notes') }}</label>
              <textarea
                v-model="form.notes"
                rows="3"
                :placeholder="t('ddRules.placeholder.notes')"
                class="block w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
          </div>
        </div>

        <!-- Form Actions -->
        <div class="flex justify-end space-x-3 pb-8">
          <button
            type="button"
            @click="handleFormCancel"
            class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="submit"
            :disabled="isSaving"
            class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <span v-if="isSaving">{{ t('ddRules.button.saving') }}</span>
            <span v-else>{{ editingRule ? t('ddRules.button.updateRule') : t('ddRules.button.createRule') }}</span>
          </button>
        </div>
      </form>
    </div>

    <!-- ── Delete Confirmation Modal ──────────────────────────────────── -->
    <Teleport to="body">
      <div
        v-if="showDeleteConfirm"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4"
      >
        <div class="bg-white rounded-xl shadow-xl max-w-md w-full">
          <div class="p-6">
            <div class="flex items-center space-x-3">
              <div class="flex-shrink-0">
                <XCircle class="h-10 w-10 text-red-500" />
              </div>
              <div class="flex-1">
                <h3 class="text-lg font-semibold text-gray-900">{{ t('ddRules.delete.title') }}</h3>
                <i18n-t keypath="ddRules.delete.confirm" tag="p" class="mt-1 text-sm text-gray-600">
                  <template #name>
                    <strong>{{ ruleToDelete?.ruleName }}</strong>
                  </template>
                </i18n-t>
              </div>
            </div>
          </div>
          <div class="px-4 py-3 bg-gray-50 rounded-b-xl flex justify-end space-x-3">
            <button
              @click="showDeleteConfirm = false"
              class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              @click="confirmDelete"
              class="px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500"
            >
              {{ t('ddRules.button.deleteRule') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

  </div>
</template>
