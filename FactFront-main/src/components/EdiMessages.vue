<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  Search,
  Filter,
  RefreshCw,
  ArrowDownCircle,
  ArrowUpCircle,
  Eye,
  X,
  Plus,
  Radio,
} from 'lucide-vue-next';
import Pagination from './Pagination.vue';
import ediService from '../services/ediService';
import type { EdiMessage } from '../types/edi';

const { t } = useI18n();

const tableHeaders = computed(() => [
  t('ediMessages.column.direction'),
  t('ediMessages.column.messageType'),
  t('ediMessages.column.partnerId'),
  t('ediMessages.column.format'),
  t('ediMessages.column.status'),
  t('ediMessages.column.messageDate'),
  t('ediMessages.column.attempts'),
  '',
]);

// ── State ──────────────────────────────────────────────────────────────────

const messages = ref<EdiMessage[]>([]);
const isLoading = ref(false);
const errorMessage = ref('');

// Filters
const filterStatus = ref('');
const filterPartnerId = ref('');

// Pagination
const currentPage = ref(1);
const pageSize = ref(50);
const totalItems = ref(0);
const totalPages = ref(1);

// Detail modal
const showDetailModal = ref(false);
const selectedMessage = ref<EdiMessage | null>(null);

// Ingest modal
const showIngestModal = ref(false);
const isIngesting = ref(false);
const ingestForm = ref<Partial<EdiMessage>>({
  direction: 'INBOUND',
  format: 'EDIFACT',
  messageType: '',
  partnerId: '',
  rawPayload: '',
});

// ── Data fetching ───────────────────────────────────────────────────────────

const fetchMessages = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  try {
    const params: Record<string, any> = {
      page: currentPage.value,
      size: pageSize.value,
    };
    if (filterStatus.value) params.status = filterStatus.value;
    if (filterPartnerId.value) params.partnerId = filterPartnerId.value;

    const data = await ediService.getMessages(params);

    // Support both paginated envelope { content, totalElements, totalPages } and plain array
    if (Array.isArray(data)) {
      messages.value = data;
      totalItems.value = data.length;
      totalPages.value = 1;
    } else {
      messages.value = data.content ?? data.items ?? [];
      totalItems.value = data.totalElements ?? data.totalItems ?? messages.value.length;
      totalPages.value = data.totalPages ?? (Math.ceil(totalItems.value / pageSize.value) || 1);
    }
  } catch (error) {
    errorMessage.value = t('ediMessages.error.failedToLoad');
  } finally {
    isLoading.value = false;
  }
};

onMounted(fetchMessages);

// ── Pagination metadata ─────────────────────────────────────────────────────

const paginationMeta = computed(() => ({
  currentPage: currentPage.value,
  pageSize: pageSize.value,
  totalItems: totalItems.value,
  totalPages: totalPages.value,
  hasNext: currentPage.value < totalPages.value,
  hasPrevious: currentPage.value > 1,
}));

const handlePageChange = (page: number) => {
  currentPage.value = page;
  fetchMessages();
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  currentPage.value = 1;
  fetchMessages();
};

// ── Filters ─────────────────────────────────────────────────────────────────

const applyFilters = () => {
  currentPage.value = 1;
  fetchMessages();
};

const clearFilters = () => {
  filterStatus.value = '';
  filterPartnerId.value = '';
  currentPage.value = 1;
  fetchMessages();
};

const hasActiveFilters = computed(() => filterStatus.value || filterPartnerId.value);

// ── Status badge ────────────────────────────────────────────────────────────

const getStatusBadgeClasses = (status: string) => {
  const base = 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border';
  switch (status) {
    case 'RECEIVED':
      return `${base} bg-blue-100 text-blue-800 border-blue-200`;
    case 'PROCESSING':
      return `${base} bg-yellow-100 text-yellow-800 border-yellow-200`;
    case 'PROCESSED':
      return `${base} bg-green-100 text-green-800 border-green-200`;
    case 'FAILED':
      return `${base} bg-red-100 text-red-800 border-red-200`;
    case 'SKIPPED':
      return `${base} bg-gray-100 text-gray-600 border-gray-200`;
    default:
      return `${base} bg-gray-100 text-gray-600 border-gray-200`;
  }
};

// ── Detail modal ────────────────────────────────────────────────────────────

const openDetail = (message: EdiMessage) => {
  selectedMessage.value = message;
  showDetailModal.value = true;
};

const closeDetail = () => {
  showDetailModal.value = false;
  selectedMessage.value = null;
};

// ── Ingest modal ────────────────────────────────────────────────────────────

const openIngest = () => {
  ingestForm.value = {
    direction: 'INBOUND',
    format: 'EDIFACT',
    messageType: '',
    partnerId: '',
    rawPayload: '',
  };
  showIngestModal.value = true;
};

const closeIngest = () => {
  showIngestModal.value = false;
};

const submitIngest = async () => {
  isIngesting.value = true;
  try {
    await ediService.receiveMessage(ingestForm.value);
    showIngestModal.value = false;
    await fetchMessages();
  } catch (error) {
    alert(t('ediMessages.alert.failedToIngest'));
  } finally {
    isIngesting.value = false;
  }
};

// ── Helpers ─────────────────────────────────────────────────────────────────

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '—';
  try {
    return new Date(dateStr).toLocaleString();
  } catch {
    return dateStr;
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <!-- Page Header -->
      <div class="mb-8">
        <div class="md:flex md:items-center md:justify-between">
          <div class="min-w-0 flex-1">
            <h1 class="text-2xl font-bold leading-7 text-gray-900 sm:truncate sm:text-3xl">
              {{ t('nav.ediMessages') }}
            </h1>
            <div class="mt-2 flex items-center text-sm text-gray-500 space-x-4">
              <span class="flex items-center">
                <Radio class="h-4 w-4 mr-1 text-blue-500" />
                {{ t('ediMessages.messageCount', totalItems, { count: totalItems }) }}
              </span>
            </div>
          </div>
          <div class="mt-4 flex md:ml-4 md:mt-0 space-x-3">
            <button
              @click="fetchMessages"
              class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
              :disabled="isLoading"
            >
              <RefreshCw class="h-4 w-4 mr-2" :class="{ 'animate-spin': isLoading }" />
              {{ t('billOfLadingForm.button.refresh') }}
            </button>
            <button
              @click="openIngest"
              class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              <Plus class="h-4 w-4 mr-2" />
              {{ t('ediMessages.button.ingestMessage') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Filter Bar -->
      <div class="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
        <div class="p-6">
          <div class="flex flex-col sm:flex-row gap-4">
            <!-- Status filter -->
            <div class="sm:w-48">
              <label class="block text-xs font-medium text-gray-700 mb-1">{{ t('invoices.column.status') }}</label>
              <select
                v-model="filterStatus"
                class="block w-full border border-gray-300 rounded-lg py-2.5 px-3 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              >
                <option value="">{{ t('payments.filter.allStatuses') }}</option>
                <option value="RECEIVED">RECEIVED</option>
                <option value="PROCESSING">PROCESSING</option>
                <option value="PROCESSED">PROCESSED</option>
                <option value="FAILED">FAILED</option>
                <option value="SKIPPED">SKIPPED</option>
              </select>
            </div>

            <!-- Partner ID filter -->
            <div class="flex-1 relative">
              <label class="block text-xs font-medium text-gray-700 mb-1">{{ t('ediMessages.field.partnerId') }}</label>
              <div class="relative">
                <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Search class="h-4 w-4 text-gray-400" />
                </div>
                <input
                  v-model="filterPartnerId"
                  type="text"
                  :placeholder="t('ediMessages.placeholder.filterByPartnerId')"
                  class="block w-full pl-9 pr-3 py-2.5 border border-gray-300 rounded-lg text-sm bg-white placeholder-gray-500 focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                  @keyup.enter="applyFilters"
                />
              </div>
            </div>

            <!-- Filter actions -->
            <div class="flex items-end gap-2">
              <button
                @click="applyFilters"
                class="inline-flex items-center px-4 py-2.5 border border-transparent rounded-lg text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
              >
                <Filter class="h-4 w-4 mr-1.5" />
                {{ t('payments.button.apply') }}
              </button>
              <button
                v-if="hasActiveFilters"
                @click="clearFilters"
                class="inline-flex items-center px-4 py-2.5 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
              >
                <X class="h-4 w-4 mr-1.5" />
                {{ t('payments.button.clear') }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Error Banner -->
      <div v-if="errorMessage" class="mb-6 bg-red-50 border border-red-200 rounded-lg p-4 flex items-center space-x-3">
        <X class="h-5 w-5 text-red-500 flex-shrink-0" />
        <span class="text-sm text-red-700">{{ errorMessage }}</span>
        <button @click="errorMessage = ''" class="ml-auto text-red-500 hover:text-red-700">
          <X class="h-4 w-4" />
        </button>
      </div>

      <!-- Table Card -->
      <div class="bg-white shadow-sm rounded-lg border border-gray-200 overflow-hidden">

        <!-- Desktop table -->
        <div class="hidden sm:block overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50">
              <tr>
                <th v-for="(header, idx) in tableHeaders"
                    :key="idx"
                    class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
                >
                  {{ header }}
                </th>
              </tr>
            </thead>
            <tbody class="bg-white divide-y divide-gray-200">
              <!-- Loading skeleton -->
              <tr v-if="isLoading" v-for="i in 5" :key="i">
                <td v-for="j in 8" :key="j" class="px-4 py-3">
                  <div class="h-4 bg-gray-200 rounded animate-pulse w-3/4"></div>
                </td>
              </tr>

              <!-- Data rows -->
              <tr
                v-else
                v-for="msg in messages"
                :key="msg.id"
                class="hover:bg-gray-50 transition-colors duration-150 cursor-pointer"
                @click="openDetail(msg)"
              >
                <!-- Direction -->
                <td class="px-4 py-3">
                  <div class="flex items-center space-x-1.5">
                    <ArrowDownCircle v-if="msg.direction === 'INBOUND'" class="h-4 w-4 text-green-600 flex-shrink-0" />
                    <ArrowUpCircle v-else class="h-4 w-4 text-blue-600 flex-shrink-0" />
                    <span class="text-sm font-medium text-gray-900">{{ msg.direction }}</span>
                  </div>
                </td>

                <!-- Message Type -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-900 font-mono">{{ msg.messageType || '—' }}</span>
                </td>

                <!-- Partner ID -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-700">{{ msg.partnerId || '—' }}</span>
                </td>

                <!-- Format -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-700 font-mono">{{ msg.format }}</span>
                </td>

                <!-- Status -->
                <td class="px-4 py-3">
                  <span :class="getStatusBadgeClasses(msg.status)">{{ msg.status }}</span>
                </td>

                <!-- Message Date -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-500 whitespace-nowrap">{{ formatDate(msg.messageDate) }}</span>
                </td>

                <!-- Attempts -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-700">{{ msg.attempts ?? 0 }}</span>
                </td>

                <!-- Actions -->
                <td class="px-4 py-3" @click.stop>
                  <button
                    @click="openDetail(msg)"
                    class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                    :title="t('payments.action.viewDetails')"
                    :aria-label="t('ediMessages.aria.viewMessageDetails')"
                  >
                    <Eye class="h-4 w-4" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Mobile card list -->
        <div class="sm:hidden p-4">
          <div v-if="isLoading" class="space-y-3">
            <div v-for="i in 4" :key="i" class="bg-white rounded-xl border border-slate-200 p-4 animate-pulse">
              <div class="h-4 bg-gray-200 rounded w-1/2 mb-2"></div>
              <div class="h-3 bg-gray-200 rounded w-3/4"></div>
            </div>
          </div>
          <div v-else-if="!messages.length" class="bg-white rounded-xl border border-slate-200 p-8 text-center">
            <p class="text-sm text-slate-500">{{ t('ediMessages.empty.notFound') }}</p>
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="bg-white rounded-xl border border-slate-200 p-4 shadow-sm cursor-pointer hover:border-blue-300 transition-colors"
              @click="openDetail(msg)"
            >
              <div class="flex items-start justify-between gap-2 mb-2">
                <div class="flex items-center space-x-2">
                  <ArrowDownCircle v-if="msg.direction === 'INBOUND'" class="h-4 w-4 text-green-600 flex-shrink-0" />
                  <ArrowUpCircle v-else class="h-4 w-4 text-blue-600 flex-shrink-0" />
                  <p class="font-semibold text-slate-900 text-sm">{{ msg.messageType || msg.format }}</p>
                </div>
                <span :class="getStatusBadgeClasses(msg.status)">{{ msg.status }}</span>
              </div>
              <div class="flex items-center justify-between text-xs text-slate-500 mt-1">
                <span>{{ msg.partnerId || '—' }}</span>
                <span>{{ formatDate(msg.messageDate) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state (desktop) -->
        <div v-if="!isLoading && messages.length === 0" class="hidden sm:block text-center py-12">
          <Radio class="mx-auto h-12 w-12 text-gray-400" />
          <h3 class="mt-2 text-sm font-medium text-gray-900">{{ t('ediMessages.empty.title') }}</h3>
          <p class="mt-1 text-sm text-gray-500">
            {{ hasActiveFilters ? t('ediMessages.empty.filtered') : t('ediMessages.empty.description') }}
          </p>
          <div class="mt-6 flex items-center justify-center space-x-3">
            <button
              v-if="hasActiveFilters"
              @click="clearFilters"
              class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
            >
              {{ t('payments.button.clearFilters') }}
            </button>
            <button
              v-if="!hasActiveFilters"
              @click="openIngest"
              class="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700"
            >
              <Plus class="h-4 w-4 mr-2" />
              {{ t('ediMessages.button.ingestMessage') }}
            </button>
          </div>
        </div>

        <!-- Pagination -->
        <Pagination
          v-if="totalItems > 0"
          :pagination="paginationMeta"
          @page-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- ── Detail Modal ──────────────────────────────────────────────────── -->
    <Teleport to="body">
      <div
        v-if="showDetailModal && selectedMessage"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4"
        @click.self="closeDetail"
      >
        <div class="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col">

          <!-- Modal Header -->
          <div class="flex items-center justify-between p-6 border-b border-gray-200">
            <div class="flex items-center space-x-3">
              <ArrowDownCircle v-if="selectedMessage.direction === 'INBOUND'" class="h-6 w-6 text-green-600" />
              <ArrowUpCircle v-else class="h-6 w-6 text-blue-600" />
              <div>
                <h3 class="text-lg font-semibold text-gray-900">
                  {{ t('ediMessages.detail.title') }}
                </h3>
                <p class="text-xs text-gray-500 font-mono mt-0.5">{{ selectedMessage.id }}</p>
              </div>
            </div>
            <button @click="closeDetail" class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
              <X class="h-5 w-5" />
            </button>
          </div>

          <!-- Modal Body -->
          <div class="overflow-y-auto flex-1 p-6 space-y-5">

            <!-- Key fields grid -->
            <div class="grid grid-cols-2 sm:grid-cols-3 gap-4">
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ediMessages.field.direction') }}</p>
                <p class="mt-1 text-sm font-medium text-gray-900">{{ selectedMessage.direction }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ediMessages.field.format') }}</p>
                <p class="mt-1 text-sm font-mono text-gray-900">{{ selectedMessage.format }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('invoices.column.status') }}</p>
                <div class="mt-1">
                  <span :class="getStatusBadgeClasses(selectedMessage.status)">{{ selectedMessage.status }}</span>
                </div>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ediMessages.field.messageType') }}</p>
                <p class="mt-1 text-sm font-mono text-gray-900">{{ selectedMessage.messageType || '—' }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ediMessages.field.partnerId') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ selectedMessage.partnerId || '—' }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ediMessages.field.attempts') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ selectedMessage.attempts ?? 0 }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ediMessages.field.messageDate') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ formatDate(selectedMessage.messageDate) }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ediMessages.field.processedAt') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ formatDate(selectedMessage.processedAt) }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('ediMessages.field.relatedEntity') }}</p>
                <p class="mt-1 text-sm font-mono text-gray-900 truncate" :title="selectedMessage.relatedEntityId || ''">
                  {{ selectedMessage.relatedEntityId || '—' }}
                </p>
              </div>
            </div>

            <!-- Processing Note -->
            <div v-if="selectedMessage.processingNote">
              <p class="text-xs font-medium text-gray-500 uppercase tracking-wide mb-1">{{ t('ediMessages.field.processingNote') }}</p>
              <div class="bg-yellow-50 border border-yellow-200 rounded-lg p-3 text-sm text-yellow-900">
                {{ selectedMessage.processingNote }}
              </div>
            </div>

            <!-- Raw Payload -->
            <div v-if="selectedMessage.rawPayload">
              <p class="text-xs font-medium text-gray-500 uppercase tracking-wide mb-1">{{ t('ediMessages.field.rawPayload') }}</p>
              <pre class="bg-gray-900 text-green-300 rounded-lg p-4 text-xs font-mono overflow-x-auto max-h-64 whitespace-pre-wrap break-all">{{ selectedMessage.rawPayload }}</pre>
            </div>
          </div>

          <!-- Modal Footer -->
          <div class="px-6 py-4 bg-gray-50 rounded-b-xl border-t border-gray-200 flex justify-end">
            <button
              @click="closeDetail"
              class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              {{ t('common.close') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ── Ingest Modal ──────────────────────────────────────────────────── -->
    <Teleport to="body">
      <div
        v-if="showIngestModal"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4"
        @click.self="closeIngest"
      >
        <div class="bg-white rounded-xl shadow-xl w-full max-w-lg">

          <!-- Modal Header -->
          <div class="flex items-center justify-between p-6 border-b border-gray-200">
            <div>
              <h3 class="text-lg font-semibold text-gray-900">{{ t('ediMessages.ingest.title') }}</h3>
              <p class="text-sm text-gray-500 mt-0.5">{{ t('ediMessages.ingest.subtitle') }}</p>
            </div>
            <button @click="closeIngest" class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
              <X class="h-5 w-5" />
            </button>
          </div>

          <!-- Form Body -->
          <div class="p-6 space-y-4">
            <!-- Format -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('ediMessages.field.format') }} <span class="text-red-500">*</span></label>
              <select
                v-model="ingestForm.format"
                class="block w-full border border-gray-300 rounded-lg py-2.5 px-3 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              >
                <option value="EDIFACT">EDIFACT</option>
                <option value="X12">X12</option>
                <option value="CSV">CSV</option>
                <option value="JSON">JSON</option>
                <option value="XML">XML</option>
              </select>
            </div>

            <!-- Message Type -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('ediMessages.field.messageType') }}</label>
              <input
                v-model="ingestForm.messageType"
                type="text"
                :placeholder="t('ediMessages.placeholder.messageType')"
                class="block w-full border border-gray-300 rounded-lg py-2.5 px-3 text-sm placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <!-- Partner ID -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('ediMessages.field.partnerId') }}</label>
              <input
                v-model="ingestForm.partnerId"
                type="text"
                :placeholder="t('ediMessages.placeholder.partnerId')"
                class="block w-full border border-gray-300 rounded-lg py-2.5 px-3 text-sm placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <!-- Raw Payload -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('ediMessages.field.rawPayload') }}</label>
              <textarea
                v-model="ingestForm.rawPayload"
                rows="6"
                :placeholder="t('ediMessages.placeholder.rawPayload')"
                class="block w-full border border-gray-300 rounded-lg py-2.5 px-3 text-sm font-mono placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500 resize-none"
              ></textarea>
            </div>
          </div>

          <!-- Footer -->
          <div class="px-6 py-4 bg-gray-50 rounded-b-xl border-t border-gray-200 flex justify-end space-x-3">
            <button
              @click="closeIngest"
              :disabled="isIngesting"
              class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              @click="submitIngest"
              :disabled="isIngesting"
              class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
            >
              <RefreshCw v-if="isIngesting" class="h-4 w-4 mr-2 animate-spin" />
              <Plus v-else class="h-4 w-4 mr-2" />
              {{ isIngesting ? t('ediMessages.button.ingesting') : t('ediMessages.button.sendMessage') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
