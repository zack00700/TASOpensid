<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { formatOptionalCurrency } from '../utils/currency';
import {
  RefreshCw, Plus, X, AlertCircle, FileText, ShieldCheck,
  Send, Pause, Ban,
} from 'lucide-vue-next';
import {
  listCustomsDeclarations,
  createCustomsDeclaration,
  submitCustomsDeclaration,
  holdCustomsDeclaration,
  clearCustomsDeclaration,
  rejectCustomsDeclaration,
} from '../services/customsService';
import type {
  CustomsDeclaration,
  CustomsDeclarationStatus,
  CustomsDeclarationType,
} from '../types/customs';

const { t } = useI18n();

// ── State ────────────────────────────────────────────────────────────────────

const declarations = ref<CustomsDeclaration[]>([]);
const isLoading = ref(false);
const errorMessage = ref('');
const filterStatus = ref<CustomsDeclarationStatus | ''>('');
const filterBol = ref('');

// Detail + action modal
const selected = ref<CustomsDeclaration | null>(null);
const showReasonModal = ref(false);
const reasonMode = ref<'hold' | 'reject' | null>(null);
const reasonValue = ref('');
const assessedDuties = ref<number | null>(null);
const isActing = ref(false);

// Create modal
const showCreate = ref(false);
const isCreating = ref(false);
const newDeclaration = ref<CustomsDeclaration>({
  type: 'IMPORT',
  status: 'DRAFT',
  billOfLadingId: '',
  itemIds: [],
});
const itemsInput = ref('');

// ── Data ──────────────────────────────────────────────────────────────────────

const fetchAll = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  try {
    const params: any = {};
    if (filterStatus.value) params.status = filterStatus.value;
    if (filterBol.value) params.billOfLadingId = filterBol.value;
    declarations.value = await listCustomsDeclarations(params);
  } catch {
    errorMessage.value = t('customsDeclarations.error.loadFailed');
  } finally {
    isLoading.value = false;
  }
};

onMounted(fetchAll);

// ── Derived ───────────────────────────────────────────────────────────────────

const stats = computed(() => {
  const init: Record<CustomsDeclarationStatus, number> = {
    DRAFT: 0, SUBMITTED: 0, HELD: 0, CLEARED: 0, REJECTED: 0,
  };
  for (const d of declarations.value) init[d.status]++;
  return init;
});

// ── Badges ────────────────────────────────────────────────────────────────────

const statusBadgeClasses = (status: CustomsDeclarationStatus) => {
  const base = 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border';
  switch (status) {
    case 'DRAFT':     return `${base} bg-[rgba(42,36,30,0.05)] text-tide-ink/80 border-[rgba(60,50,35,0.12)]`;
    case 'SUBMITTED': return `${base} bg-blue-100 text-tide-blue-deep border-blue-200`;
    case 'HELD':      return `${base} bg-yellow-100 text-yellow-800 border-yellow-200`;
    case 'CLEARED':   return `${base} bg-green-100 text-green-800 border-green-200`;
    case 'REJECTED':  return `${base} bg-red-100 text-red-800 border-red-200`;
  }
};

const typeBadgeClasses = (type: CustomsDeclarationType) => {
  const base = 'inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border';
  switch (type) {
    case 'IMPORT':  return `${base} bg-indigo-50 text-indigo-700 border-indigo-200`;
    case 'EXPORT':  return `${base} bg-purple-50 text-purple-700 border-purple-200`;
    case 'TRANSIT': return `${base} bg-orange-50 text-orange-700 border-orange-200`;
  }
};

// ── Actions ───────────────────────────────────────────────────────────────────

const openDetail = (d: CustomsDeclaration) => {
  selected.value = d;
};

const closeDetail = () => {
  selected.value = null;
  reasonMode.value = null;
  showReasonModal.value = false;
};

const handleSubmit = async (d: CustomsDeclaration) => {
  if (!d.id) return;
  isActing.value = true;
  try {
    const updated = await submitCustomsDeclaration(d.id);
    replace(updated);
    selected.value = updated;
  } catch {
    errorMessage.value = t('customsDeclarations.error.submitRefused');
  } finally {
    isActing.value = false;
  }
};

const handleClear = async (d: CustomsDeclaration) => {
  if (!d.id) return;
  isActing.value = true;
  try {
    const updated = await clearCustomsDeclaration(d.id, assessedDuties.value ?? undefined);
    replace(updated);
    selected.value = updated;
    assessedDuties.value = null;
  } catch {
    errorMessage.value = t('customsDeclarations.error.clearRefused');
  } finally {
    isActing.value = false;
  }
};

const openReason = (mode: 'hold' | 'reject') => {
  reasonMode.value = mode;
  reasonValue.value = '';
  showReasonModal.value = true;
};

const submitReason = async () => {
  if (!selected.value?.id || !reasonValue.value.trim() || !reasonMode.value) return;
  isActing.value = true;
  try {
    const updated = reasonMode.value === 'hold'
      ? await holdCustomsDeclaration(selected.value.id, reasonValue.value)
      : await rejectCustomsDeclaration(selected.value.id, reasonValue.value);
    replace(updated);
    selected.value = updated;
    showReasonModal.value = false;
  } catch {
    errorMessage.value = t('customsDeclarations.error.actionRefused', { mode: reasonMode.value });
  } finally {
    isActing.value = false;
  }
};

const replace = (d: CustomsDeclaration) => {
  const idx = declarations.value.findIndex((x) => x.id === d.id);
  if (idx >= 0) declarations.value[idx] = d;
  else declarations.value.unshift(d);
};

// ── Create modal ──────────────────────────────────────────────────────────────

const openCreate = () => {
  newDeclaration.value = {
    type: 'IMPORT',
    status: 'DRAFT',
    billOfLadingId: '',
    itemIds: [],
  };
  itemsInput.value = '';
  showCreate.value = true;
};

const submitCreate = async () => {
  const itemIds = itemsInput.value
    .split(/[,\s\n]+/)
    .map((s) => s.trim())
    .filter(Boolean);
  if (!newDeclaration.value.billOfLadingId || itemIds.length === 0) {
    errorMessage.value = t('customsDeclarations.error.bolAndItemRequired');
    return;
  }
  isCreating.value = true;
  try {
    const payload: CustomsDeclaration = { ...newDeclaration.value, itemIds };
    const created = await createCustomsDeclaration(payload);
    replace(created);
    showCreate.value = false;
  } catch {
    errorMessage.value = t('customsDeclarations.error.createRefused');
  } finally {
    isCreating.value = false;
  }
};

// ── Formatters ────────────────────────────────────────────────────────────────

const formatDate = (s?: string | null) =>
  !s ? '—' : new Date(s).toLocaleString();

const formatMoney = (amount?: number | null, currency?: string | null) =>
  formatOptionalCurrency(amount, currency);
</script>

<template>
  <div class="min-h-screen bg-[rgba(252,247,238,0.55)]">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <!-- Header -->
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold leading-7 text-tide-ink sm:text-3xl">
            {{ t('customsDeclarations.title') }}
          </h1>
          <p class="mt-1 text-sm text-tide-ink/55">
            <i18n-t keypath="customsDeclarations.subtitle" tag="span">
              <template #cleared><strong>{{ t('customsDeclarations.status.cleared') }}</strong></template>
            </i18n-t>
          </p>
        </div>
        <div class="flex gap-3">
          <button
            @click="fetchAll"
            :disabled="isLoading"
            class="inline-flex items-center px-4 py-2 border border-[rgba(60,50,35,0.16)] rounded-lg text-sm font-medium text-tide-ink/80 bg-[rgba(255,253,247,0.92)] hover:bg-[rgba(252,247,238,0.55)] disabled:opacity-50"
          >
            <RefreshCw class="h-4 w-4 mr-2" :class="{ 'animate-spin': isLoading }" />
            {{ t('customsDeclarations.button.refresh') }}
          </button>
          <button
            @click="openCreate"
            class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg text-sm font-medium text-white bg-tide-blue-btn-deep hover:bg-tide-blue-deep"
          >
            <Plus class="h-4 w-4 mr-2" />
            {{ t('customsDeclarations.button.newDeclaration') }}
          </button>
        </div>
      </div>

      <!-- Error -->
      <div v-if="errorMessage" class="mb-6 bg-red-50 border border-red-200 rounded-lg p-4 flex items-start gap-3">
        <AlertCircle class="h-5 w-5 text-red-500 flex-shrink-0 mt-0.5" />
        <span class="text-sm text-red-700 flex-1">{{ errorMessage }}</span>
        <button @click="errorMessage = ''" class="text-red-500 hover:text-red-700">
          <X class="h-4 w-4" />
        </button>
      </div>

      <!-- KPIs -->
      <div class="grid grid-cols-2 sm:grid-cols-5 gap-4 mb-6">
        <div v-for="(count, status) in stats" :key="status"
             class="bg-[rgba(255,253,247,0.92)] rounded-lg border border-[rgba(60,50,35,0.12)] shadow-sm p-4">
          <p class="text-xs font-medium text-tide-ink/55 uppercase tracking-wide">{{ status }}</p>
          <p class="mt-2 text-2xl font-bold text-tide-ink">{{ count }}</p>
        </div>
      </div>

      <!-- Filters -->
      <div class="bg-[rgba(255,253,247,0.92)] rounded-lg shadow-sm border border-[rgba(60,50,35,0.12)] p-4 mb-6 flex gap-3">
        <select
          v-model="filterStatus"
          @change="fetchAll"
          class="border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm bg-[rgba(255,253,247,0.92)] focus:outline-none focus:ring-1 focus:ring-tide-blue"
        >
          <option value="">{{ t('customsDeclarations.filter.allStatuses') }}</option>
          <option value="DRAFT">{{ t('customsDeclarations.status.draft') }}</option>
          <option value="SUBMITTED">{{ t('customsDeclarations.status.submitted') }}</option>
          <option value="HELD">{{ t('customsDeclarations.status.held') }}</option>
          <option value="CLEARED">{{ t('customsDeclarations.status.cleared') }}</option>
          <option value="REJECTED">{{ t('customsDeclarations.status.rejected') }}</option>
        </select>
        <input
          v-model="filterBol"
          @keyup.enter="fetchAll"
          type="text"
          :placeholder="t('customsDeclarations.placeholder.filterByBolId')"
          class="flex-1 border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue"
        />
      </div>

      <!-- Table -->
      <div class="bg-[rgba(255,253,247,0.92)] shadow-sm rounded-lg border border-[rgba(60,50,35,0.12)] overflow-hidden">
        <table class="min-w-full divide-y divide-[rgba(42,36,30,0.08)]">
          <thead class="bg-[rgba(252,247,238,0.55)]">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">{{ t('customsDeclarations.column.reference') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">{{ t('customsDeclarations.column.type') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">{{ t('customsDeclarations.column.bol') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">{{ t('customsDeclarations.column.items') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">{{ t('customsDeclarations.column.value') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">{{ t('customsDeclarations.column.status') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase tracking-wider">{{ t('customsDeclarations.column.submitted') }}</th>
            </tr>
          </thead>
          <tbody class="bg-[rgba(255,253,247,0.92)] divide-y divide-[rgba(42,36,30,0.08)]">
            <tr v-if="isLoading" v-for="i in 4" :key="`s-${i}`">
              <td v-for="j in 7" :key="j" class="px-4 py-3">
                <div class="h-4 bg-gray-200 rounded animate-pulse w-3/4"></div>
              </td>
            </tr>

            <tr v-else v-for="d in declarations" :key="d.id"
                class="hover:bg-[rgba(252,247,238,0.55)] cursor-pointer"
                @click="openDetail(d)">
              <td class="px-4 py-3 whitespace-nowrap">
                <span class="text-sm font-mono font-medium text-tide-ink">{{ d.declarationReference || '—' }}</span>
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <span :class="typeBadgeClasses(d.type)">{{ d.type }}</span>
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-tide-ink/80 font-mono">{{ d.billOfLadingId }}</td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-tide-ink/80">{{ d.itemIds?.length ?? 0 }}</td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-tide-ink">
                {{ formatMoney(d.totalDeclaredValue, d.currency) }}
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <span :class="statusBadgeClasses(d.status)">{{ d.status }}</span>
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-sm text-tide-ink/55">{{ formatDate(d.submittedAt) }}</td>
            </tr>

            <tr v-if="!isLoading && declarations.length === 0">
              <td colspan="7" class="px-4 py-12 text-center text-sm text-tide-ink/55">
                <FileText class="mx-auto h-10 w-10 text-gray-300 mb-2" />
                {{ t('customsDeclarations.empty') }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Detail Modal -->
    <Teleport to="body">
      <div
        v-if="selected"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4"
        @click.self="closeDetail"
      >
        <div class="bg-[rgba(255,253,247,0.92)] rounded-xl shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col">
          <div class="p-6 border-b border-[rgba(60,50,35,0.12)] flex items-center justify-between">
            <div class="flex items-center gap-3">
              <FileText class="h-5 w-5 text-tide-blue-deep" />
              <div>
                <div class="flex items-center gap-2">
                  <h3 class="text-lg font-semibold text-tide-ink">
                    {{ selected.declarationReference || t('customsDeclarations.detail.declaration') }}
                  </h3>
                  <span :class="typeBadgeClasses(selected.type)">{{ selected.type }}</span>
                  <span :class="statusBadgeClasses(selected.status)">{{ selected.status }}</span>
                </div>
                <p class="text-xs text-tide-ink/55 font-mono mt-0.5">{{ selected.id }}</p>
              </div>
            </div>
            <button @click="closeDetail" class="text-tide-ink/40 hover:text-tide-ink/70">
              <X class="h-5 w-5" />
            </button>
          </div>

          <div class="overflow-y-auto p-6 space-y-4 flex-1">
            <div class="grid grid-cols-2 gap-4 text-sm">
              <div>
                <p class="text-xs font-medium text-tide-ink/55 uppercase">{{ t('customsDeclarations.detail.bol') }}</p>
                <p class="mt-1 font-mono text-tide-ink">{{ selected.billOfLadingId }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-tide-ink/55 uppercase">{{ t('customsDeclarations.detail.declarant') }}</p>
                <p class="mt-1 text-tide-ink">{{ selected.declarantName || '—' }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-tide-ink/55 uppercase">{{ t('customsDeclarations.detail.portOfEntry') }}</p>
                <p class="mt-1 text-tide-ink">{{ selected.portOfEntryCode || '—' }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-tide-ink/55 uppercase">{{ t('customsDeclarations.detail.declaredValue') }}</p>
                <p class="mt-1 text-tide-ink">{{ formatMoney(selected.totalDeclaredValue, selected.currency) }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-tide-ink/55 uppercase">{{ t('customsDeclarations.detail.assessedDuties') }}</p>
                <p class="mt-1 text-tide-ink">{{ formatMoney(selected.assessedDuties, selected.currency) }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-tide-ink/55 uppercase">{{ t('customsDeclarations.detail.submittedAt') }}</p>
                <p class="mt-1 text-tide-ink">{{ formatDate(selected.submittedAt) }}</p>
              </div>
            </div>

            <div>
              <p class="text-xs font-medium text-tide-ink/55 uppercase mb-1">{{ t('customsDeclarations.detail.itemsCovered', { count: selected.itemIds.length }) }}</p>
              <div class="flex flex-wrap gap-1.5">
                <span v-for="id in selected.itemIds" :key="id"
                      class="inline-flex items-center px-2 py-0.5 rounded text-xs font-mono bg-[rgba(42,36,30,0.05)] text-tide-ink/80 border border-[rgba(60,50,35,0.12)]">
                  {{ id }}
                </span>
              </div>
            </div>

            <div v-if="selected.holdReason" class="bg-yellow-50 border border-yellow-200 rounded-lg p-3">
              <p class="text-xs font-medium text-yellow-800 uppercase">{{ t('customsDeclarations.detail.holdReason') }}</p>
              <p class="mt-1 text-sm text-yellow-900">{{ selected.holdReason }}</p>
            </div>
            <div v-if="selected.rejectionReason" class="bg-red-50 border border-red-200 rounded-lg p-3">
              <p class="text-xs font-medium text-red-800 uppercase">{{ t('customsDeclarations.detail.rejectionReason') }}</p>
              <p class="mt-1 text-sm text-red-900">{{ selected.rejectionReason }}</p>
            </div>
            <div v-if="selected.notes">
              <p class="text-xs font-medium text-tide-ink/55 uppercase mb-1">{{ t('customsDeclarations.detail.notes') }}</p>
              <p class="text-sm text-tide-ink/80 bg-[rgba(252,247,238,0.55)] border border-[rgba(60,50,35,0.12)] rounded-lg p-3">{{ selected.notes }}</p>
            </div>
          </div>

          <!-- Actions -->
          <div class="px-6 py-4 bg-[rgba(252,247,238,0.55)] rounded-b-xl border-t border-[rgba(60,50,35,0.12)] flex flex-wrap gap-2 justify-end">
            <button
              v-if="selected.status === 'DRAFT'"
              @click="handleSubmit(selected)"
              :disabled="isActing"
              class="inline-flex items-center px-3 py-2 rounded-lg text-sm font-medium text-white bg-tide-blue-btn-deep hover:bg-tide-blue-deep disabled:opacity-50"
            >
              <Send class="h-4 w-4 mr-1.5" />
              {{ t('customsDeclarations.button.submit') }}
            </button>
            <button
              v-if="selected.status === 'SUBMITTED'"
              @click="openReason('hold')"
              :disabled="isActing"
              class="inline-flex items-center px-3 py-2 rounded-lg text-sm font-medium text-yellow-800 bg-yellow-100 hover:bg-yellow-200 disabled:opacity-50"
            >
              <Pause class="h-4 w-4 mr-1.5" />
              {{ t('customsDeclarations.button.putOnHold') }}
            </button>
            <div v-if="selected.status === 'SUBMITTED' || selected.status === 'HELD'"
                 class="flex items-center gap-2">
              <input
                v-model.number="assessedDuties"
                type="number"
                min="0"
                step="0.01"
                :placeholder="t('customsDeclarations.placeholder.dutiesOptional')"
                class="w-32 border border-[rgba(60,50,35,0.16)] rounded-lg py-1.5 px-2 text-sm"
              />
              <button
                @click="handleClear(selected)"
                :disabled="isActing"
                class="inline-flex items-center px-3 py-2 rounded-lg text-sm font-medium text-white bg-green-600 hover:bg-green-700 disabled:opacity-50"
              >
                <ShieldCheck class="h-4 w-4 mr-1.5" />
                {{ t('customsDeclarations.button.clear') }}
              </button>
            </div>
            <button
              v-if="selected.status === 'SUBMITTED' || selected.status === 'HELD'"
              @click="openReason('reject')"
              :disabled="isActing"
              class="inline-flex items-center px-3 py-2 rounded-lg text-sm font-medium text-white bg-red-600 hover:bg-red-700 disabled:opacity-50"
            >
              <Ban class="h-4 w-4 mr-1.5" />
              {{ t('customsDeclarations.button.reject') }}
            </button>
            <button
              @click="closeDetail"
              class="px-3 py-2 border border-[rgba(60,50,35,0.16)] rounded-lg text-sm font-medium text-tide-ink/80 hover:bg-[rgba(42,36,30,0.05)]"
            >
              {{ t('customsDeclarations.button.close') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Reason Modal (hold/reject) -->
    <Teleport to="body">
      <div
        v-if="showReasonModal"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-[60] p-4"
        @click.self="showReasonModal = false"
      >
        <div class="bg-[rgba(255,253,247,0.92)] rounded-xl shadow-xl w-full max-w-md">
          <div class="p-5 border-b border-[rgba(60,50,35,0.12)] flex items-center justify-between">
            <h3 class="text-lg font-semibold text-tide-ink">
              {{ reasonMode === 'hold' ? t('customsDeclarations.reason.holdTitle') : t('customsDeclarations.reason.rejectTitle') }}
            </h3>
            <button @click="showReasonModal = false" class="text-tide-ink/40 hover:text-tide-ink/70">
              <X class="h-5 w-5" />
            </button>
          </div>
          <div class="p-5 space-y-3">
            <textarea
              v-model="reasonValue"
              rows="4"
              :placeholder="reasonMode === 'hold' ? t('customsDeclarations.placeholder.holdReason') : t('customsDeclarations.placeholder.rejectReason')"
              class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue"
            ></textarea>
          </div>
          <div class="px-5 py-3 bg-[rgba(252,247,238,0.55)] rounded-b-xl border-t border-[rgba(60,50,35,0.12)] flex justify-end gap-2">
            <button
              @click="showReasonModal = false"
              class="px-3 py-1.5 border border-[rgba(60,50,35,0.16)] rounded-lg text-sm font-medium text-tide-ink/80 hover:bg-[rgba(42,36,30,0.05)]"
            >
              {{ t('customsDeclarations.button.cancel') }}
            </button>
            <button
              @click="submitReason"
              :disabled="isActing || !reasonValue.trim()"
              class="inline-flex items-center px-3 py-1.5 rounded-lg text-sm font-medium text-white disabled:opacity-50"
              :class="reasonMode === 'hold' ? 'bg-yellow-600 hover:bg-yellow-700' : 'bg-red-600 hover:bg-red-700'"
            >
              <RefreshCw v-if="isActing" class="h-4 w-4 mr-1.5 animate-spin" />
              {{ t('customsDeclarations.button.confirm') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Create Modal -->
    <Teleport to="body">
      <div
        v-if="showCreate"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4"
        @click.self="showCreate = false"
      >
        <div class="bg-[rgba(255,253,247,0.92)] rounded-xl shadow-xl w-full max-w-xl">
          <div class="p-6 border-b border-[rgba(60,50,35,0.12)] flex items-center justify-between">
            <h3 class="text-lg font-semibold text-tide-ink">{{ t('customsDeclarations.create.title') }}</h3>
            <button @click="showCreate = false" class="text-tide-ink/40 hover:text-tide-ink/70">
              <X class="h-5 w-5" />
            </button>
          </div>
          <form @submit.prevent="submitCreate" class="p-6 space-y-4">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-medium text-tide-ink/80 mb-1">{{ t('customsDeclarations.field.type') }} <span class="text-red-500">*</span></label>
                <select
                  v-model="newDeclaration.type"
                  class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue"
                >
                  <option value="IMPORT">{{ t('customsDeclarations.type.import') }}</option>
                  <option value="EXPORT">{{ t('customsDeclarations.type.export') }}</option>
                  <option value="TRANSIT">{{ t('customsDeclarations.type.transit') }}</option>
                </select>
              </div>
              <div>
                <label class="block text-xs font-medium text-tide-ink/80 mb-1">{{ t('customsDeclarations.field.portOfEntry') }}</label>
                <input
                  v-model="newDeclaration.portOfEntryCode"
                  type="text"
                  :placeholder="t('customsDeclarations.placeholder.portOfEntry')"
                  class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue"
                />
              </div>
            </div>
            <div>
              <label class="block text-xs font-medium text-tide-ink/80 mb-1">{{ t('customsDeclarations.field.bolId') }} <span class="text-red-500">*</span></label>
              <input
                v-model="newDeclaration.billOfLadingId"
                type="text"
                required
                class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm font-mono focus:outline-none focus:ring-1 focus:ring-tide-blue"
              />
            </div>
            <div>
              <label class="block text-xs font-medium text-tide-ink/80 mb-1">{{ t('customsDeclarations.field.itemsList') }} <span class="text-red-500">*</span></label>
              <textarea
                v-model="itemsInput"
                rows="3"
                :placeholder="t('customsDeclarations.placeholder.itemsList')"
                class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm font-mono focus:outline-none focus:ring-1 focus:ring-tide-blue"
              ></textarea>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-medium text-tide-ink/80 mb-1">{{ t('customsDeclarations.field.declarant') }}</label>
                <input
                  v-model="newDeclaration.declarantName"
                  type="text"
                  class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue"
                />
              </div>
              <div>
                <label class="block text-xs font-medium text-tide-ink/80 mb-1">{{ t('customsDeclarations.field.taxId') }}</label>
                <input
                  v-model="newDeclaration.declarantTaxId"
                  type="text"
                  class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue"
                />
              </div>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-medium text-tide-ink/80 mb-1">{{ t('customsDeclarations.field.declaredValue') }}</label>
                <input
                  v-model.number="newDeclaration.totalDeclaredValue"
                  type="number"
                  min="0"
                  step="0.01"
                  class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue"
                />
              </div>
              <div>
                <label class="block text-xs font-medium text-tide-ink/80 mb-1">{{ t('customsDeclarations.field.currency') }}</label>
                <input
                  v-model="newDeclaration.currency"
                  type="text"
                  :placeholder="t('customsDeclarations.placeholder.currency')"
                  class="block w-full border border-[rgba(60,50,35,0.16)] rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-tide-blue"
                />
              </div>
            </div>
            <div class="flex justify-end gap-2 pt-2">
              <button
                type="button"
                @click="showCreate = false"
                :disabled="isCreating"
                class="px-4 py-2 border border-[rgba(60,50,35,0.16)] rounded-lg text-sm font-medium text-tide-ink/80 hover:bg-[rgba(252,247,238,0.55)]"
              >
                {{ t('customsDeclarations.button.cancel') }}
              </button>
              <button
                type="submit"
                :disabled="isCreating"
                class="inline-flex items-center px-4 py-2 rounded-lg text-sm font-medium text-white bg-tide-blue-btn-deep hover:bg-tide-blue-deep disabled:opacity-50"
              >
                <RefreshCw v-if="isCreating" class="h-4 w-4 mr-2 animate-spin" />
                {{ t('customsDeclarations.button.createDraft') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </div>
</template>
